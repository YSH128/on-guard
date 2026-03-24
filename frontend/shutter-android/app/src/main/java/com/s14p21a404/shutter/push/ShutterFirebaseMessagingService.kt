package com.s14p21a404.shutter.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kakao.sdk.auth.AuthApiClient
import com.s14p21a404.shutter.data.token.TokenManager
import com.s14p21a404.shutter.di.ServiceLocator
import com.s14p21a404.shutter.presentation.main.MainActivity
import com.s14p21a404.shutter.presentation.service.CriminalLocationBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * FCM 토큰 갱신 및 푸시 메시지 수신 처리.
 *
 * 토큰 갱신 시 카카오 REST API로 직접 등록하고,
 * 푸시 메시지 수신 시 상단바 알림으로 표시한다.
 */
class ShutterFirebaseMessagingService : FirebaseMessagingService() {

    /**
     * FCM 토큰이 갱신될 때 호출.
     * 로그인 상태일 때만 카카오에 재등록한다.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM 토큰 갱신: $token")

        val tokenManager = TokenManager(applicationContext)
        if (!tokenManager.hasToken()) {
            Log.d(TAG, "로그인 전 — 카카오 푸시 토큰 등록 생략")
            return
        }

        val deviceId = tokenManager.getOrCreateDeviceUuid()
        registerTokenWithKakao(token, deviceId)
    }

    /**
     * 푸시 메시지 수신 시 호출.
     * type 필드에 따라 처리를 분기한다.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "푸시 수신 — from: ${message.from}, data: ${message.data}")

        when (message.data["type"]) {
            "location_request" -> {
                Log.d(TAG, "location_request 수신 — requesterKakaoId: ${message.data["requesterKakaoId"]}")
                handleLocationRequest(message.data)
            }
            "criminal_location" -> {
                Log.d(TAG, "criminal_location 수신 — lat: ${message.data["lat"]}, lng: ${message.data["lng"]}")
                handleCriminalLocation(message.data)
            }
            else -> {
                val title = message.notification?.title
                    ?: message.data["title"]
                    ?: "Shutter 알림"
                val body = message.notification?.body
                    ?: message.data["body"]
                    ?: ""
                showNotification(title, body)
            }
        }
    }

    /**
     * 위치 요청 FCM 수신: 내 GPS를 1회 읽어 서버에 신고한다.
     * 상대방(요청자) kakaoId는 requesterKakaoId 필드로 수신한다.
     */
    private fun handleLocationRequest(data: Map<String, String>) {
        val requesterKakaoId = data["requesterKakaoId"]?.toLongOrNull() ?: run {
            Log.w(TAG, "requesterKakaoId 없음 — 위치 신고 생략")
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "위치 권한 없음 — 위치 신고 생략")
            return
        }

        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val location = providers.firstNotNullOfOrNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }

        if (location == null) {
            Log.w(TAG, "위치 없음 — 위치 신고 생략")
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            ServiceLocator.getInstance(applicationContext)
                .criminalLocationApi
                .reportLocation(requesterKakaoId, location.latitude, location.longitude)
                .onSuccess { Log.d(TAG, "위치 신고 완료 (요청자: $requesterKakaoId)") }
                .onFailure { Log.e(TAG, "위치 신고 실패", it) }
        }
    }

    /**
     * 범죄자 위치 FCM 수신: UI 콜백 호출 + 푸시 알림 표시.
     */
    private fun handleCriminalLocation(data: Map<String, String>) {
        val lat = data["lat"]?.toDoubleOrNull() ?: run {
            Log.w(TAG, "lat 없음")
            return
        }
        val lng = data["lng"]?.toDoubleOrNull() ?: run {
            Log.w(TAG, "lng 없음")
            return
        }

        // 1. VideoCallScreen UI에 콜백 (메인 스레드에서 실행)
        Handler(Looper.getMainLooper()).post {
            CriminalLocationBridge.onLocationReceived?.invoke(lat, lng)
        }

        // 2. 푸시 알림 표시
        showCriminalLocationNotification(lat, lng)
    }

    private fun showCriminalLocationNotification(lat: Double, lng: Double) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("통화 상대방 위치 확인")
            .setContentText("위도: %.6f, 경도: %.6f".format(lat, lng))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("통화 상대방의 위치가 감지되었습니다.\n위도: %.6f\n경도: %.6f".format(lat, lng))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        nm.notify(CRIMINAL_LOCATION_NOTIFICATION_ID, notification)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = CHANNEL_ID
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Shutter 딥페이크 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "딥페이크 감지 시 보호자에게 전달되는 긴급 알림"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "ShutterFCMService"
        private const val CHANNEL_ID = "shutter_push_channel_v2"
        private const val NOTIFICATION_ID = 2001
        private const val CRIMINAL_LOCATION_NOTIFICATION_ID = 2002
        private const val KAKAO_PUSH_REGISTER_URL = "https://kapi.kakao.com/v1/push/register"

        private val okHttpClient = OkHttpClient()

        /**
         * 카카오 REST API를 통해 FCM 토큰을 등록한다.
         * Kakao SDK가 관리하는 카카오 액세스 토큰을 사용한다.
         */
        fun registerTokenWithKakao(fcmToken: String, deviceId: String) {
            val kakaoAccessToken = try {
                AuthApiClient.instance.tokenManagerProvider.manager.getToken()?.accessToken
            } catch (e: Exception) {
                Log.e(TAG, "카카오 액세스 토큰 조회 실패", e)
                null
            }

            if (kakaoAccessToken == null) {
                Log.w(TAG, "카카오 액세스 토큰 없음 — FCM 토큰 등록 생략")
                return
            }

            val body = FormBody.Builder()
                .add("push_type", "fcm")
                .add("push_token", fcmToken)
                .add("device_id", deviceId)
                .build()

            val request = Request.Builder()
                .url(KAKAO_PUSH_REGISTER_URL)
                .addHeader("Authorization", "Bearer $kakaoAccessToken")
                .post(body)
                .build()

            Thread {
                try {
                    val response = okHttpClient.newCall(request).execute()
                    val body = response.body?.string() ?: "(empty)"
                    if (response.isSuccessful) {
                        Log.d(TAG, "카카오 푸시 토큰 등록 성공 [${response.code}]: $body")
                    } else {
                        Log.e(TAG, "카카오 푸시 토큰 등록 실패 [${response.code}]: $body")
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.e(TAG, "카카오 푸시 토큰 등록 요청 실패", e)
                }
            }.start()
        }
    }
}
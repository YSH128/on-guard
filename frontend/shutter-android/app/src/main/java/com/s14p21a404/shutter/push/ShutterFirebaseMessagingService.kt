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
 * 푸시 메시지 수신 시:
 *   - INCOMING_CALL → MainActivity로 통화 수신 화면 딥링크
 *   - 그 외 → 상단바 알림 표시
 */
class ShutterFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d(FCM_TAG, "FCM 토큰 갱신: $token")

        val tokenManager = TokenManager(applicationContext)
        if (!tokenManager.hasToken()) {
            Log.d(FCM_TAG, "로그인 전 — 카카오 푸시 토큰 등록 생략")
            return
        }

        val deviceId = tokenManager.getOrCreateDeviceUuid()
        registerTokenWithKakao(token, deviceId)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val type = data["type"]

        when (type) {

            "INCOMING_CALL" -> {
                val callerId = data["caller_id"]?.toLongOrNull() ?: return
                val callerKakaoId = data["caller_kakao_id"]?.toLongOrNull() ?: 0L
                val callerName = data["caller_name"] ?: return
                val channelName = data["channel_name"] ?: return
                handleIncomingCall(callerId, callerKakaoId, callerName, channelName)
            }

            "CALL_ACCEPTED" -> {
                // 발신자는 이미 채널에 joinChannel 되어 있으므로
                // 수신자가 join하면 Agora onUserJoined 이벤트로 자동 연결됨
            }

            "CALL_REJECTED" -> {
                val channelName = data["channel_name"] ?: return
                Log.d(FCM_TAG, "CALL_REJECTED 수신 — channel: $channelName")
                IncomingCallEventBus.notifyCallRejected()
            }

            "location_request" -> {
                Log.d(FCM_TAG, "location_request 수신 — requesterKakaoId: ${data["requesterKakaoId"]}")
                handleLocationRequest(data)
            }

            "criminal_location" -> {
                Log.d(FCM_TAG, "criminal_location 수신 — country: ${data["country"]}")
                handleCriminalLocation(data)
            }

            else -> {
                val title = message.notification?.title ?: data["title"] ?: "Shutter 알림"
                val body = message.notification?.body ?: data["body"] ?: ""
                showNotification(title, body)
            }
        }
    }

    /**
     * INCOMING_CALL 수신 시 MainActivity를 통화 수신 화면으로 이동.
     * 앱이 포그라운드/백그라운드 모두 동작.
     */
    private fun handleIncomingCall(callerId: Long, callerKakaoId: Long, callerName: String, channelName: String) {
        // 1) 풀스크린 알림용 채널 생성
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CALL_CHANNEL_ID,
            "Shutter 수신 전화",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "영상통화 수신 알림"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)

        // 2) MainActivity에 딥링크 Intent — screen=incoming_call
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("screen", "incoming_call")
            putExtra("caller_id", callerId)
            putExtra("caller_kakao_id", callerKakaoId)
            putExtra("caller_name", callerName)
            putExtra("channel_name", channelName)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, CALL_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3) 거절 Intent (알림에서 바로 거절)
        val rejectIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("screen", "reject_call")
            putExtra("channel_name", channelName)
        }
        val rejectPendingIntent = PendingIntent.getActivity(
            this, CALL_REJECT_REQUEST_CODE, rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("📞 수신 전화")
            .setContentText("$callerName 님이 영상통화를 요청했습니다")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)          // 알림 탭 → 수락 화면
            .addAction(
                android.R.drawable.ic_menu_call,
                "수락",
                pendingIntent
            )
            .addAction(
                android.R.drawable.ic_delete,
                "거절",
                rejectPendingIntent
            )
            .build()

        notificationManager.notify(CALL_NOTIFICATION_ID, notification)

        // 4) 앱이 포그라운드일 때도 즉시 화면 전환
        startActivity(intent)
    }

    private fun handleLocationRequest(data: Map<String, String>) {
        val requesterKakaoId = data["requesterKakaoId"]?.toLongOrNull() ?: run {
            Log.w(FCM_TAG, "requesterKakaoId 없음 — 위치 신고 생략")
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(FCM_TAG, "위치 권한 없음 — 위치 신고 생략")
            return
        }

        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val location = providers.firstNotNullOfOrNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }

        if (location == null) {
            Log.w(FCM_TAG, "위치 없음 — 위치 신고 생략")
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            ServiceLocator.getInstance(applicationContext)
                .criminalLocationApi
                .reportLocation(requesterKakaoId, location.latitude, location.longitude)
                .onSuccess { Log.d(FCM_TAG, "위치 신고 완료 (요청자: $requesterKakaoId)") }
                .onFailure { Log.e(FCM_TAG, "위치 신고 실패", it) }
        }
    }

    /**
     * 범죄자 위치 FCM 수신: UI 콜백 호출 + 푸시 알림 표시.
     */
    private fun handleCriminalLocation(data: Map<String, String>) {
        val country = data["country"] ?: run {
            Log.w(FCM_TAG, "country 없음")
            return
        }

        Log.d(FCM_TAG, "criminal_location 국가: $country")

        // 1. VideoCallScreen UI에 콜백 (메인 스레드에서 실행)
        Handler(Looper.getMainLooper()).post {
            CriminalLocationBridge.onLocationReceived?.invoke(country)
        }

        // 2. 푸시 알림 표시
        showCriminalLocationNotification(country)
    }

    private fun showCriminalLocationNotification(country: String) {
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
            .setContentText("통화 상대방 위치: $country")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        nm.notify(CRIMINAL_LOCATION_NOTIFICATION_ID, notification)
    }

    private fun showNotification(title: String, body: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
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
        private const val FCM_TAG = "ShutterFCMService"
        private const val CHANNEL_ID = "shutter_push_channel_v2"
        private const val CALL_CHANNEL_ID = "shutter_call_channel_v1"
        private const val NOTIFICATION_ID = 2001
        private const val CALL_NOTIFICATION_ID = 2002
        private const val CALL_REQUEST_CODE = 3001
        private const val CALL_REJECT_REQUEST_CODE = 3002
        private const val CRIMINAL_LOCATION_NOTIFICATION_ID = 2003
        private const val KAKAO_PUSH_REGISTER_URL = "https://kapi.kakao.com/v1/push/register"

        private val okHttpClient = OkHttpClient()

        fun registerTokenWithKakao(fcmToken: String, deviceId: String) {
            val kakaoAccessToken = try {
                AuthApiClient.instance.tokenManagerProvider.manager.getToken()?.accessToken
            } catch (e: Exception) {
                Log.e(FCM_TAG, "카카오 액세스 토큰 조회 실패", e)
                null
            }

            if (kakaoAccessToken == null) {
                Log.w(FCM_TAG, "카카오 액세스 토큰 없음 — FCM 토큰 등록 생략")
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
                    val responseBody = response.body?.string() ?: "(empty)"
                    if (response.isSuccessful) {
                        Log.d(FCM_TAG, "카카오 푸시 토큰 등록 성공 [${response.code}]: $responseBody")
                    } else {
                        Log.e(FCM_TAG, "카카오 푸시 토큰 등록 실패 [${response.code}]: $responseBody")
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.e(FCM_TAG, "카카오 푸시 토큰 등록 요청 실패", e)
                }
            }.start()
        }
    }
}
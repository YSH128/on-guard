package com.s14p21a404.shutter.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kakao.sdk.auth.AuthApiClient
import com.s14p21a404.shutter.data.token.TokenManager
import com.s14p21a404.shutter.presentation.main.MainActivity
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
     * 알림을 상단바에 표시한다.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "푸시 수신 — from: ${message.from}, data: ${message.data}")

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Shutter 알림"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""

        showNotification(title, body)
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
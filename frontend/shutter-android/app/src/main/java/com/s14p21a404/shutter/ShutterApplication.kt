package com.s14p21a404.shutter

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.google.firebase.FirebaseApp
import com.kakao.sdk.common.KakaoSdk
import com.kakao.vectormap.KakaoMapSdk

class ShutterApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val firebase = FirebaseApp.initializeApp(this)
        Log.d("ShutterApp", "Firebase 초기화: ${if (firebase != null) "성공" else "이미 초기화됨"}")

        // 앱 시작 시 알림 채널 미리 생성 (FCM 수신 전에 채널이 없으면 팝업이 뜨지 않음)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            "shutter_push_channel_v2",
            "OnGuard 딥페이크 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "딥페이크 감지 시 보호자에게 전달되는 긴급 알림"
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)

        val appKey = getString(R.string.kakao_app_key)

        KakaoSdk.init(this, appKey)
        KakaoMapSdk.init(this, appKey)
    }
}
package com.s14p21a404.shutter

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.kakao.sdk.common.KakaoSdk
import com.kakao.vectormap.KakaoMapSdk

class ShutterApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val firebase = FirebaseApp.initializeApp(this)
        Log.d("ShutterApp", "Firebase 초기화: ${if (firebase != null) "성공" else "이미 초기화됨"}")

        val appKey = getString(R.string.kakao_app_key)

        KakaoSdk.init(this, appKey)
        KakaoMapSdk.init(this, appKey)
    }
}
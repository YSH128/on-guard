package com.s14p21a404.shutter

import android.app.Application
import com.kakao.sdk.common.KakaoSdk
import com.kakao.vectormap.KakaoMapSdk

class ShutterApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val appKey = getString(R.string.kakao_app_key)

        KakaoSdk.init(this, appKey)
        KakaoMapSdk.init(this, appKey)
    }
}
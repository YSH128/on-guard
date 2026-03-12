package com.s14p21a404.shutter

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class ShutterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KakaoSdk.init(this, getString(R.string.kakao_app_key))
    }
}
package com.s14p21a404.shutter.presentation.service

/**
 * FCM 위치 알림 수신 시 LocationViewModel에 reload 신호를 전달하는 브릿지.
 * CriminalLocationBridge와 동일한 패턴.
 */
object LocationReloadBridge {
    @Volatile
    var onLocationAlertReceived: (() -> Unit)? = null
}
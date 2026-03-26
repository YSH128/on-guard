package com.s14p21a404.shutter.presentation.service

/**
 * FCM 서비스 → VideoCallScreen / 오버레이 간 위치 수신 콜백 브릿지.
 *
 * criminal_location FCM이 도착하면 FCM 서비스에서 invoke하고,
 * VideoCallScreen 또는 VideoCallDetectService에서 등록/해제한다.
 */
object CriminalLocationBridge {
    var onLocationReceived: ((country: String, lat: Double?, lng: Double?) -> Unit)? = null
}
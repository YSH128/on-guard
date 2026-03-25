package com.s14p21a404.shutter.data.repository

import com.s14p21a404.shutter.network.ApiService
import com.s14p21a404.shutter.network.CriminalLocationReportBody
import com.s14p21a404.shutter.network.CriminalLocationRequestBody

/**
 * 범죄자 위치 추적 API 래퍼.
 *
 * criminal-request: 서버에 상대방 GPS 요청 전송
 * criminal-report:  사일런트 FCM 수신 후 내 GPS를 서버에 전송 (상대방 앱이 호출)
 */
class CriminalLocationApi(private val apiService: ApiService) {

    suspend fun requestLocation(opponentKakaoId: Long): Result<Unit> = runCatching {
        apiService.requestCriminalLocation(CriminalLocationRequestBody(opponentKakaoId))
        Unit
    }

    suspend fun reportLocation(requesterKakaoId: Long, lat: Double, lng: Double): Result<Unit> = runCatching {
        apiService.reportCriminalLocation(CriminalLocationReportBody(requesterKakaoId, lat, lng))
        Unit
    }
}

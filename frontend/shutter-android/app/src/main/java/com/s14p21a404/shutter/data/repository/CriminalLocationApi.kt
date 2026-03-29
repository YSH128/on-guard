package com.s14p21a404.shutter.data.repository

import com.s14p21a404.shutter.network.ApiService
import com.s14p21a404.shutter.network.CriminalLocationReportBody
import com.s14p21a404.shutter.network.CriminalLocationRequestBody
import com.s14p21a404.shutter.network.GpsSessionRequestBody

/**
 * 범죄자 위치 추적 API 래퍼.
 *
 * criminal-request:      서버에 상대방 GPS 요청 전송 (FCM 기반, 기존 방식)
 * criminal-report:       사일런트 FCM 수신 후 내 GPS를 서버에 전송 (상대방 앱이 호출)
 * saveSessionGps:        통화 연결 시 내 GPS를 세션 전용 Redis에 1회 저장 (신규)
 * getOpponentSessionCountry: 딥페이크 감지 시 상대방의 세션 GPS → 국가명 조회 (신규)
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

    /**
     * 통화 연결 시 내 현재 GPS를 통화 세션 전용 Redis 키에 저장.
     * 딥페이크 감지 시 상대방이 이 데이터를 조회해 국가 확인에 사용한다.
     */
    suspend fun saveSessionGps(channelName: String, lat: Double, lng: Double): Result<Unit> = runCatching {
        apiService.postGpsSession(GpsSessionRequestBody(channelName, lat, lng))
        Unit
    }

    /**
     * 딥페이크 감지 시 상대방의 저장된 세션 GPS를 조회해 국가명 반환.
     * 실패 시 "알 수 없는 위치" 반환 (예외를 Result.failure로 래핑)
     */
    suspend fun getOpponentSessionCountry(channelName: String, opponentKakaoId: Long): Result<String> = runCatching {
        apiService.getSessionOpponentGps(channelName, opponentKakaoId).data.country
    }
}


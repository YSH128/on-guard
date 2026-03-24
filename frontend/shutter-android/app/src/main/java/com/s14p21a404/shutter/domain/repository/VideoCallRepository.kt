package com.s14p21a404.shutter.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * 영상통화 상태 Repository 인터페이스
 *
 * Domain 레이어에서 정의하며, Data 레이어에서 구현한다.
 */
interface VideoCallRepository {

    /** 영상통화 상태 관찰 (true = 영상통화 중) */
    fun observeVideoCallState(): Flow<Boolean>

    /**
     * 포그라운드 앱이 영상통화 앱인지 관찰.
     * 영상통화 감지 후, 사용자가 영상통화 화면에 있는지 여부를 반환한다.
     * true = 영상통화 앱이 포그라운드, false = 다른 앱이 포그라운드
     */
    fun observeForegroundIsVideoCallApp(): Flow<Boolean>

    /**
     * 포그라운드 앱이 금융 앱인지 관찰.
     * 딥페이크 통화 감지 후, 사용자가 금융 앱을 열었는지 여부를 반환한다.
     * true = 금융 앱이 포그라운드, false = 금융 앱이 아님
     */
    fun observeForegroundIsFinancialApp(): Flow<Boolean>

    /** 영상통화가 시작된 앱 패키지명을 기록 */
    fun setVideoCallPackage(packageName: String?)

    /** 모니터링 시작 (TelephonyCallback, CameraCallback 등록) */
    fun startMonitoring()

    /** 모니터링 중단 (콜백 해제) */
    fun stopMonitoring()

    // ── 인앱(Agora) 영상통화 지원 ──

    /** 인앱 영상통화 활성/비활성 설정. true이면 외부 앱 감지 조건을 우회하여 즉시 영상통화 상태로 전환. */
    fun setInAppCallActive(active: Boolean)

    /** 현재 인앱 영상통화가 활성 상태인지 반환 */
    fun isInAppCallActive(): Boolean
}


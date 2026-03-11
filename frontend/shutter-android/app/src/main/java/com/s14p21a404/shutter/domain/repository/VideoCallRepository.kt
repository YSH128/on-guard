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

    /** 모니터링 시작 (TelephonyCallback, CameraCallback 등록) */
    fun startMonitoring()

    /** 모니터링 중단 (콜백 해제) */
    fun stopMonitoring()
}


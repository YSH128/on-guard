package com.s14p21a404.shutter.domain.repository

import com.s14p21a404.shutter.domain.model.DeepfakeDetectionResult
import kotlinx.coroutines.flow.Flow

/**
 * 딥페이크 감지 Repository 인터페이스
 *
 * Domain 레이어에서 정의하며, Data 레이어에서 구현한다.
 */
interface DeepfakeDetectionRepository {

    /** 감지 결과를 Flow로 관찰 */
    fun observeDetectionResults(): Flow<DeepfakeDetectionResult>

    /** 감지 시작 */
    fun startDetection()

    /** 감지 중단 */
    fun stopDetection()

    /**
     * 캡처된 프레임을 감지기로 공급
     *
     * @param buffer    RGBA 프레임 byte[]
     * @param width     프레임 너비 (px)
     * @param height    프레임 높이 (px)
     * @param rowStride Image.Plane의 rowStride
     */
    fun feedFrame(buffer: ByteArray, width: Int, height: Int, rowStride: Int)
}


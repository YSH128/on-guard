package com.s14p21a404.shutter.domain.repository

import android.content.Context
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import com.s14p21a404.shutter.domain.model.DeepfakeDetectionResult
import kotlinx.coroutines.flow.Flow

/**
 * 딥페이크 감지 Repository 인터페이스
 *
 * Domain 레이어에서 정의하며, Data 레이어에서 구현한다.
 * 화면 캡처는 startDetection/stopDetection 내부에서 자동 관리된다.
 */
interface DeepfakeDetectionRepository {

    /** 사이클(10프레임) 집계 결과를 Flow로 관찰 */
    fun observeDetectionResults(): Flow<DeepfakeDetectionResult>

    /** 프레임별 실시간 결과를 Flow로 관찰 (딥페이크 확정 후 RoI 실시간 갱신에 사용) */
    fun observeFrameResults(): Flow<DeepfakeDetectionResult>

    /** 감지 시작 (내부적으로 화면 캡처도 함께 시작) */
    fun startDetection()

    /** 감지 중단 (내부적으로 화면 캡처도 함께 중단) */
    fun stopDetection()

    /**
     * MediaProjection을 설정한다.
     * Service에서 사용자 동의 후 획득한 MediaProjection을 data layer에 전달.
     */
    fun setMediaProjection(context: Context, mediaProjection: MediaProjection)


    /**
     * MediaProjection 해제. Service 종료 시 호출.
     */
    fun releaseMediaProjection()

    /**
     * 최신 캡처 프레임(Bitmap)을 반환한다.
     * ROI 크롭 오버레이에 사용.
     */
    fun getLatestBitmap(): Bitmap?
}

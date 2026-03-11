package com.s14p21a404.shutter.data.repository

import android.content.Context
import android.media.projection.MediaProjection
import android.util.Log
import com.s14p21a404.shutter.data.capture.ScreenCaptureManager
import com.s14p21a404.shutter.data.detector.DeepfakeDetector
import com.s14p21a404.shutter.domain.model.DeepfakeDetectionResult
import com.s14p21a404.shutter.domain.repository.DeepfakeDetectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 딥페이크 감지 Repository 구현체
 *
 * DeepfakeDetector 인터페이스를 통해 실제 감지를 수행하고,
 * 결과를 SharedFlow로 방출한다.
 *
 * 화면 캡처(ScreenCaptureManager)는 startDetection/stopDetection 내부에서
 * 자동으로 관리되므로, 외부에서 별도로 캡처를 제어할 필요 없다.
 */
class DeepfakeDetectionRepositoryImpl(
    private val detector: DeepfakeDetector
) : DeepfakeDetectionRepository {

    private val _results = MutableSharedFlow<DeepfakeDetectionResult>(
        replay = 0,
        extraBufferCapacity = 1
    )

    // ── 화면 캡처 내부 관리 ──
    private var captureContext: Context? = null
    private var mediaProjection: MediaProjection? = null
    private var screenCaptureManager: ScreenCaptureManager? = null

    override fun observeDetectionResults(): Flow<DeepfakeDetectionResult> = _results.asSharedFlow()

    override fun startDetection() {
        Log.d(TAG, "감지 시작")
        detector.startDetection { result ->
            Log.d(TAG, "📊 감지 결과: $result")
            _results.tryEmit(result)
        }

        // ── 화면 캡처 시작 ──
        val ctx = captureContext
        val projection = mediaProjection
        if (ctx == null || projection == null) {
            Log.w(TAG, "MediaProjection 없음 — 화면 캡처 불가 (감지는 Stub으로 동작)")
            return
        }

        screenCaptureManager = ScreenCaptureManager(
            context = ctx,
            mediaProjection = projection,
            detectors = listOf(detector)
        )
        screenCaptureManager?.startCapture()
    }

    override fun stopDetection() {
        Log.d(TAG, "감지 중단")

        // ── 화면 캡처 중단 ──
        screenCaptureManager?.stopCapture()
        screenCaptureManager = null

        detector.stopDetection()
    }

    override fun setMediaProjection(context: Context, mediaProjection: MediaProjection) {
        this.captureContext = context
        this.mediaProjection = mediaProjection
        Log.d(TAG, "MediaProjection 설정 완료")
    }

    override fun releaseMediaProjection() {
        screenCaptureManager?.stopCapture()
        screenCaptureManager = null
        mediaProjection?.stop()
        mediaProjection = null
        captureContext = null
        Log.d(TAG, "MediaProjection 해제 완료")
    }

    companion object {
        private const val TAG = "DeepfakeDetectionRepo"
    }
}

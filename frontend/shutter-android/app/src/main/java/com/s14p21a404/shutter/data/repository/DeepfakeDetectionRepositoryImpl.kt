package com.s14p21a404.shutter.data.repository

import android.util.Log
import com.s14p21a404.shutter.data.detector.DeepfakeDetector
import com.s14p21a404.shutter.data.detector.real.RealDeepfakeDetector
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
 */
class DeepfakeDetectionRepositoryImpl(
    private val detector: DeepfakeDetector
) : DeepfakeDetectionRepository {

    private val _results = MutableSharedFlow<DeepfakeDetectionResult>(
        replay = 0,
        extraBufferCapacity = 1
    )

    override fun observeDetectionResults(): Flow<DeepfakeDetectionResult> = _results.asSharedFlow()

    override fun startDetection() {
        Log.d(TAG, "감지 시작")
        detector.startDetection { result ->
            Log.d(TAG, "📊 감지 결과: $result")
            _results.tryEmit(result)
        }
    }

    override fun stopDetection() {
        Log.d(TAG, "감지 중단")
        detector.stopDetection()
    }

    override fun feedFrame(buffer: ByteArray, width: Int, height: Int, rowStride: Int) {
        (detector as? RealDeepfakeDetector)?.feedFrame(buffer, width, height, rowStride)
    }

    companion object {
        private const val TAG = "DeepfakeDetectionRepo"
    }
}


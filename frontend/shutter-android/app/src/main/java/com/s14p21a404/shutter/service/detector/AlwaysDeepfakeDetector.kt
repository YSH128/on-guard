package com.s14p21a404.shutter.service.detector

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 항상 딥페이크를 반환하는 Stub 구현체
 *
 * 용도: 영상통화 감지 → 1초 후 경고 오버레이가 뜨는지 기본 동작 확인
 */
class AlwaysDeepfakeDetector : DeepfakeDetector {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    override fun startDetection(onResult: (DeepfakeDetectionResult) -> Unit) {
        if (isRunning) return
        isRunning = true
        Log.d(TAG, "AlwaysDeepfakeDetector 시작 — 1초 후 딥페이크 결과 전달")

        handler.postDelayed({
            if (isRunning) {
                val result = DeepfakeDetectionResult(
                    detected = true,
                    roi = floatArrayOf(0.3f, 0.22f, 0.45f, 0.567f)
                )
                Log.d(TAG, "🚨 딥페이크 결과 전달: $result")
                onResult(result)
            }
        }, 1000L)
    }

    override fun stopDetection() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "AlwaysDeepfakeDetector 중단")
    }

    companion object {
        private const val TAG = "AlwaysDeepfakeDetector"
    }
}


package com.s14p21a404.shutter.service.detector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * ADB 명령으로 딥페이크 감지 결과를 수동 주입하는 Stub 구현체
 *
 * 사용법 (PC 터미널에서):
 *
 *   # 딥페이크 감지 시뮬레이션 (detected=1, roi 포함)
 *   adb shell am broadcast -a com.s14p21a404.shutter.FAKE_DETECTION --ei detected 1 --ef roi_min_x 0.3 --ef roi_min_y 0.22 --ef roi_max_x 0.45 --ef roi_max_y 0.567
 *
 *   # 안전 판정 시뮬레이션 (detected=0)
 *   adb shell am broadcast -a com.s14p21a404.shutter.FAKE_DETECTION --ei detected 0
 *
 * 용도: 영상통화 중 원하는 타이밍에 수동으로 결과를 주입하여 경고 오버레이 테스트.
 *       데모·발표에 최적.
 */
class BroadcastTriggerDetector(private val context: Context) : DeepfakeDetector {

    private var receiver: BroadcastReceiver? = null
    private var onResultCallback: ((DeepfakeDetectionResult) -> Unit)? = null

    override fun startDetection(onResult: (DeepfakeDetectionResult) -> Unit) {
        if (receiver != null) {
            Log.d(TAG, "이미 리스닝 중")
            return
        }

        onResultCallback = onResult

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val detected = intent.getIntExtra("detected", 0) == 1

                // roi는 detected=1일 때만 파싱
                val roi = if (detected && intent.hasExtra("roi_min_x")) {
                    floatArrayOf(
                        intent.getFloatExtra("roi_min_x", 0f),
                        intent.getFloatExtra("roi_min_y", 0f),
                        intent.getFloatExtra("roi_max_x", 0f),
                        intent.getFloatExtra("roi_max_y", 0f)
                    )
                } else null

                val result = DeepfakeDetectionResult(
                    detected = detected,
                    roi = roi
                )
                Log.d(TAG, "📡 ADB 브로드캐스트 수신: $result")
                onResult(result)
            }
        }

        val filter = IntentFilter(ACTION_FAKE_DETECTION)
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        Log.d(TAG, "BroadcastTriggerDetector 시작 — ADB 브로드캐스트 대기 중")
        Log.d(TAG, "  딥페이크 트리거: adb shell am broadcast -a $ACTION_FAKE_DETECTION --ei detected 1 --ef roi_min_x 0.3 --ef roi_min_y 0.22 --ef roi_max_x 0.45 --ef roi_max_y 0.567")
        Log.d(TAG, "  안전 트리거:     adb shell am broadcast -a $ACTION_FAKE_DETECTION --ei detected 0")
    }

    override fun stopDetection() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
                Log.d(TAG, "BroadcastTriggerDetector 중단")
            } catch (e: Exception) {
                Log.e(TAG, "리시버 해제 실패", e)
            }
        }
        receiver = null
        onResultCallback = null
    }

    companion object {
        private const val TAG = "BroadcastTriggerDetect"
        const val ACTION_FAKE_DETECTION = "com.s14p21a404.shutter.FAKE_DETECTION"
    }
}


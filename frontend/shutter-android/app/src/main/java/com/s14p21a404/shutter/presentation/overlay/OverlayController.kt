package com.s14p21a404.shutter.presentation.overlay

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.s14p21a404.shutter.domain.model.DeepfakeDetectionResult

/**
 * 오버레이 표시/숨김 제어
 *
 * 감지 결과를 받아 WarningOverlayManager에 위임한다.
 * Service에서 직접 오버레이를 다루지 않고 이 컨트롤러를 통해 제어한다.
 */
class OverlayController(
    private val context: Context,
    private val overlayManager: WarningOverlayManager
) {
    /**
     * 감지 결과에 따라 오버레이를 표시하거나 숨긴다.
     */
    fun onDetectionResult(result: DeepfakeDetectionResult) {
        if (result.detected) {
            showWarning()
        } else {
            dismissWarning()
        }
    }

    fun showWarning() {
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "오버레이 권한 없음 — 경고 표시 불가")
            return
        }
        overlayManager.show()
    }

    fun dismissWarning() {
        overlayManager.dismiss()
    }

    fun isShowing(): Boolean = overlayManager.isShowing()

    companion object {
        private const val TAG = "OverlayController"
    }
}


package com.s14p21a404.shutter.presentation.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 포그라운드 앱 변경을 감지하는 AccessibilityService.
 *
 * TYPE_WINDOW_STATE_CHANGED 이벤트를 수신하여,
 * 현재 포그라운드 패키지명을 StateFlow로 노출한다.
 *
 * polling 없이 이벤트 기반으로 동작하므로 배터리 소모가 적다.
 */
class ForegroundAppMonitorService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AccessibilityService 연결됨")
        instance = this

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT
            notificationTimeout = 200
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            // Activity 전환만 추적 (팝업, 토스트 등 제외)
            val className = event.className?.toString() ?: return
            if (!className.contains("Activity", ignoreCase = true)
                && !className.startsWith("android.app")
                && !className.startsWith("com.")
            ) {
                return
            }

            if (pkg != _foregroundPackage.value) {
                Log.d(TAG, "포그라운드 앱 변경: ${_foregroundPackage.value} → $pkg")
                _foregroundPackage.value = pkg
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService 인터럽트")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AccessibilityService 종료")
        instance = null
    }

    companion object {
        private const val TAG = "ForegroundAppMonitor"

        /** 서비스 인스턴스 (활성 상태일 때만 non-null) */
        @Volatile
        var instance: ForegroundAppMonitorService? = null
            private set

        /** 현재 포그라운드 패키지명 */
        private val _foregroundPackage = MutableStateFlow("")
        val foregroundPackage: StateFlow<String> = _foregroundPackage.asStateFlow()

        /** 서비스가 활성 상태인지 확인 */
        fun isRunning(): Boolean = instance != null
    }
}


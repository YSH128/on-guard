package com.s14p21a404.shutter.presentation.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 200
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowContentChanged(event)
            }
        }
    }

    // ── 포그라운드 앱 추적 ──

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return

        // 시스템 UI(볼륨 패널, 알림바, 네비게이션 바 등)는 포그라운드 앱 변경으로 취급하지 않는다.
        // 볼륨 버튼, 알림 풀다운 등에 의해 영상통화 상태가 리셋되는 것을 방지.
        if (pkg in IGNORED_SYSTEM_PACKAGES) return

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

        // 포그라운드가 VoIP 앱이 아닌 다른 앱으로 변경되면 연결 상태 초기화
        if (pkg !in CALL_UI_PACKAGES) {
            if (_isCallConnected.value) {
                Log.d(TAG, "VoIP 앱 이탈 → 통화 연결 상태 초기화")
                _isCallConnected.value = false
            }
        }
    }

    // ── 통화 연결 상태 감지 (View hierarchy 텍스트 분석) ──

    /**
     * TYPE_WINDOW_CONTENT_CHANGED 이벤트 발생 시,
     * VoIP 영상통화 앱의 View hierarchy를 분석하여
     * 통화 시간(00:01 형태) 또는 연결 완료 텍스트를 감지한다.
     *
     * ★ 래치 방식: 한 번 통화 연결(isCallConnected=true)이 확인되면,
     *   통화 앱을 이탈하기 전까지 추가 View hierarchy 분석을 하지 않는다.
     *   → UI 업데이트 타이밍/애니메이션으로 인한 flickering 방지.
     *   → 리셋은 handleWindowStateChanged()에서 통화 앱 이탈 시에만 수행.
     */
    private fun handleWindowContentChanged(event: AccessibilityEvent) {

        // ★ 이미 연결 확인됨 → 더 이상 분석하지 않음 (래치 유지)
        if (_isCallConnected.value) return

        val pkg = event.packageName?.toString() ?: return

        // ① 패키지 필터: VoIP/통화 UI 패키지만 허용
        if (pkg !in CALL_UI_PACKAGES) return

        // ② 포그라운드 필터: 통화 UI 앱이 포그라운드일 때만 처리
        //    이벤트 패키지와 포그라운드가 정확히 같거나,
        //    둘 다 CALL_UI_PACKAGES인 경우 허용 (예: incallui가 포그라운드인데
        //    vtcallplugin이 콘텐츠 변경 이벤트를 보내는 경우)
        val currentFg = _foregroundPackage.value
        if (pkg != currentFg && currentFg !in CALL_UI_PACKAGES) return

        // ③ 쓰로틀링: 500ms 이내 중복 분석 방지
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTimestamp < ANALYSIS_THROTTLE_MS) return
        lastAnalysisTimestamp = now

        val rootNode = rootInActiveWindow ?: return
        try {
            if (findCallTimerInTree(rootNode, 0)) {
                Log.d(TAG, "📞 통화 연결 확인 (래치 ON) — pkg=$pkg")
                _isCallConnected.value = true
            }
        } finally {
            rootNode.recycle()
        }
    }

    // ── 통화 종료 (AccessibilityService로 종료 버튼 클릭) ──

    /** 통신사 전화 UI 패키지 (TelecomManager로 종료 가능) */
    private val CARRIER_CALL_PACKAGES = setOf(
        "com.samsung.android.incallui",
        "com.android.incallui",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.dialer",
        "com.samsung.android.vtcallplugin",
    )

    /**
     * 현재 영상통화 화면에서 통화를 종료한다.
     *
     * 전략:
     * 1. 통신사 전화 → TelecomManager.endCall() API 사용 (가장 확실)
     * 2. VoIP(카카오톡 등) → Accessibility로 종료 버튼 contentDescription 찾아 클릭
     * 3. fallback → Accessibility 클릭 재시도
     *
     * @return true=종료 성공, false=실패
     */
    @SuppressLint("MissingPermission")
    fun performEndCall(): Boolean {
        Log.d(TAG, "📞 performEndCall() 시작")

        val currentFg = _foregroundPackage.value
        Log.d(TAG, "📞 현재 포그라운드: $currentFg")

        // ── 전략 1: 통신사 전화 → TelecomManager.endCall() ──
        if (currentFg in CARRIER_CALL_PACKAGES) {
            Log.d(TAG, "📞 통신사 전화 감지 → TelecomManager.endCall() 시도")
            if (endCallViaTelecomManager()) {
                Log.d(TAG, "📞 TelecomManager.endCall() 성공")
                _isCallConnected.value = false
                return true
            }
            Log.w(TAG, "📞 TelecomManager.endCall() 실패 → Accessibility 클릭 fallback")
        }

        // ── 전략 2: Accessibility로 종료 버튼 찾아 클릭 (VoIP / fallback) ──
        return performEndCallViaAccessibility()
    }

    /**
     * TelecomManager.endCall() API로 통화를 종료한다.
     * Android 9(P)+ 에서 ANSWER_PHONE_CALLS 권한 필요.
     */
    @SuppressLint("MissingPermission")
    private fun endCallViaTelecomManager(): Boolean {
        return try {
            val telecomManager = getSystemService(TelecomManager::class.java)
            if (telecomManager == null) {
                Log.w(TAG, "❌ TelecomManager null")
                return false
            }
            @Suppress("DEPRECATION")
            val result = telecomManager.endCall()
            Log.d(TAG, "📞 TelecomManager.endCall() 반환: $result")
            result
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ ANSWER_PHONE_CALLS 권한 없음", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ TelecomManager.endCall() 예외", e)
            false
        }
    }

    /**
     * Accessibility windows API를 통해 종료 버튼을 찾아 클릭한다.
     * VoIP 앱(카카오톡 등) 전용.
     */
    private fun performEndCallViaAccessibility(): Boolean {
        // ① windows API로 모든 윈도우에서 통화 앱 윈도우의 root를 탐색
        val allWindows = try { windows } catch (e: Exception) { null }
        if (allWindows != null) {
            Log.d(TAG, "📞 windows 개수: ${allWindows.size}")
            for (window in allWindows) {
                val windowPkg = window.root?.packageName?.toString()
                Log.d(TAG, "📞 window: pkg=$windowPkg, type=${window.type}, layer=${window.layer}")

                if (windowPkg != null && windowPkg in CALL_UI_PACKAGES) {
                    val root = window.root ?: continue
                    try {
                        val found = findAndClickEndCallButton(root, 0)
                        if (found) {
                            Log.d(TAG, "📞 통화 종료 버튼 클릭 성공 (windows API, pkg=$windowPkg)")
                            _isCallConnected.value = false
                            return true
                        }
                    } finally {
                        root.recycle()
                    }
                }
            }
            Log.w(TAG, "❌ windows API에서 통화 종료 버튼을 찾지 못함")
        }

        // ② fallback: rootInActiveWindow 사용 (오버레이가 이미 닫힌 경우)
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            Log.d(TAG, "📞 fallback: rootInActiveWindow pkg=${rootNode.packageName}")
            try {
                val found = findAndClickEndCallButton(rootNode, 0)
                if (found) {
                    Log.d(TAG, "📞 통화 종료 버튼 클릭 성공 (rootInActiveWindow)")
                    _isCallConnected.value = false
                    return true
                }
            } finally {
                rootNode.recycle()
            }
        } else {
            Log.w(TAG, "❌ rootInActiveWindow도 null")
        }

        Log.w(TAG, "❌ 통화 종료 버튼을 찾지 못함")
        return false
    }

    /**
     * View 트리를 재귀 탐색하여 통화 종료 버튼(contentDescription 기반)을 찾아 클릭한다.
     */
    private fun findAndClickEndCallButton(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 15) return false

        val desc = node.contentDescription?.toString()
        if (desc != null && node.isClickable && isEndCallDescription(desc)) {
            Log.d(TAG, "🔍 종료 버튼 발견: desc=\"$desc\", class=${node.className}, depth=$depth")
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (findAndClickEndCallButton(child, depth + 1)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    /**
     * contentDescription이 통화 종료 버튼인지 판별한다.
     *
     * - 통신사(삼성 InCallUI): "통화 종료, 버튼"
     * - 카카오톡 페이스톡: "통화 종료"
     * - 영어 로케일 등 대비: "End call" 계열
     */
    private fun isEndCallDescription(desc: String): Boolean {
        val normalized = desc.replace(",", "").replace(" ", " ").trim()
        return END_CALL_DESCRIPTIONS.any { normalized.contains(it, ignoreCase = true) }
    }

    /**
     * View 트리에서 통화 타이머 텍스트(MM:SS 또는 H:MM:SS)를 탐색한다.
     * 찾는 즉시 true를 반환하고 탐색을 종료한다 (early return).
     */
    private fun findCallTimerInTree(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > MAX_TRAVERSE_DEPTH) return false

        // text 확인
        val text = node.text?.toString()
        if (text != null && CALL_TIMER_PATTERN.matches(text)) {
            Log.d(TAG, "⏱ 통화 시간 텍스트 발견: $text (depth=$depth)")
            return true
        }

        // contentDescription 확인
        val desc = node.contentDescription?.toString()
        if (desc != null && CALL_TIMER_PATTERN.matches(desc)) {
            Log.d(TAG, "⏱ 통화 시간 contentDesc 발견: $desc (depth=$depth)")
            return true
        }

        // 자식 노드 탐색
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (findCallTimerInTree(child, depth + 1)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }


    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService 인터럽트")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AccessibilityService 종료")
        instance = null
        _isCallConnected.value = false
    }

    /** View hierarchy 분석 쓰로틀링을 위한 마지막 분석 시각 */
    @Volatile
    private var lastAnalysisTimestamp: Long = 0L

    companion object {
        private const val TAG = "ForegroundAppMonitor"

        /** View hierarchy 분석 쓰로틀링 간격 (ms) */
        private const val ANALYSIS_THROTTLE_MS = 500L

        /** View 트리 최대 순회 깊이 (통화 UI 텍스트는 보통 3~5 depth) */
        private const val MAX_TRAVERSE_DEPTH = 6

        /** 서비스 인스턴스 (활성 상태일 때만 non-null) */
        @Volatile
        var instance: ForegroundAppMonitorService? = null
            private set

        /** 현재 포그라운드 패키지명 */
        private val _foregroundPackage = MutableStateFlow("")
        val foregroundPackage: StateFlow<String> = _foregroundPackage.asStateFlow()

        /**
         * VoIP 통화 연결 상태 (래치 방식).
         *
         * View hierarchy에서 통화 타이머 텍스트(00:01 형태)를 감지하면 true로 래치.
         * 한 번 true가 되면, 아래 조건에서만 false로 리셋된다:
         * - 통화 앱이 포그라운드에서 이탈 (handleWindowStateChanged)
         * - 통화 상태가 IDLE로 전환 (resetCallConnected 호출)
         * - 서비스 종료 (onDestroy)
         */
        private val _isCallConnected = MutableStateFlow(false)
        val isCallConnected: StateFlow<Boolean> = _isCallConnected.asStateFlow()

        /**
         * 통화 연결 래치를 해제한다.
         * 통화 종료(IDLE 전환) 등 외부에서 통화가 확실히 끝났을 때 호출한다.
         */
        fun resetCallConnected() {
            if (_isCallConnected.value) {
                Log.d(TAG, "📞 통화 연결 래치 리셋 (외부 호출)")
                _isCallConnected.value = false
            }
        }

        /** 서비스가 활성 상태인지 확인 */
        fun isRunning(): Boolean = instance != null

        /**
         * 통화 시간 형식 정규식.
         * "00:01", "12:34", "1:23:45" 등 MM:SS 또는 H:MM:SS 형태를 매칭한다.
         */
        private val CALL_TIMER_PATTERN = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")


        /**
         * 통화 UI 관련 패키지 목록.
         * - ForegroundAppMonitorService: 이 패키지일 때만 View hierarchy 분석
         * - VideoCallRepositoryImpl: 이 패키지가 포그라운드일 때 영상통화 판단
         */
        val CALL_UI_PACKAGES = setOf(
            // VoIP 영상통화 앱
            "com.kakao.talk",                        // 카카오톡 (페이스톡)
            "jp.naver.line.android",                  // 라인
            "com.whatsapp",                           // WhatsApp
            "org.telegram.messenger",                 // 텔레그램
            "com.facebook.orca",                      // 페이스북 메신저
            "com.google.android.apps.tachyon",        // Google Duo/Meet
            "com.google.android.apps.meetings",       // Google Meet
            "us.zoom.videomeetings",                  // Zoom
            "com.microsoft.teams",                    // Microsoft Teams
            "com.discord",                            // Discord
            "com.skype.raider",                       // Skype
            // 시스템 전화 UI
            "com.samsung.android.incallui",           // 삼성 전화 UI
            "com.android.incallui",                   // AOSP 전화 UI
            "com.google.android.dialer",              // Google 전화 앱
            "com.samsung.android.dialer",             // 삼성 전화 앱
            "com.android.dialer",                     // AOSP 전화 앱
            "com.samsung.android.vtcallplugin",       // 삼성 영상통화 플러그인
        )

        /**
         * 통화 종료 버튼의 contentDescription 매칭 키워드 목록.
         *
         * - 통신사(삼성 InCallUI): "통화 종료, 버튼" → 정규화 후 "통화 종료 버튼"
         * - 카카오톡 페이스톡: "통화 종료"
         * - 영어 로케일: "End call"
         */
        private val END_CALL_DESCRIPTIONS = listOf(
            "통화 종료",     // 한국어 (통신사 + 카카오톡 공통)
            "End call",     // 영어 로케일
            "end call",     // 소문자
            "Hang up",      // 일부 기기
        )

        /**
         * 포그라운드 앱 변경으로 취급하지 않는 시스템 패키지 목록.
         * 볼륨 패널, 알림바, 키보드 등 시스템 오버레이 UI가 떠도
         * 실제 앱 전환이 아니므로 영상통화 래치를 리셋하면 안 된다.
         */
        private val IGNORED_SYSTEM_PACKAGES = setOf(
            "com.android.systemui",                   // 볼륨 패널, 알림바, 네비게이션 바
            "com.samsung.android.systemui",           // 삼성 SystemUI
            "com.samsung.android.app.cocktailbarservice", // 삼성 엣지 패널
            "com.samsung.android.smartmirroring",     // 삼성 스마트미러링
            "com.android.inputmethod.latin",          // AOSP 키보드
            "com.samsung.android.honeyboard",         // 삼성 키보드
            "com.google.android.inputmethod.latin",   // Gboard
            "com.sec.android.inputmethod",            // 삼성 구 키보드
        )
    }
}


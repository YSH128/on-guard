package com.s14p21a404.shutter.presentation.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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
            val connected = analyzeCallConnectionState(rootNode)
            if (connected) {
                Log.d(TAG, "📞 통화 연결 확인 (래치 ON) — pkg=$pkg")
                _isCallConnected.value = true
            }
            // false는 무시: 래치 방식이므로, 리셋은 앱 이탈 시에만 수행
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * View hierarchy를 얕은 깊이(최대 6)로 순회하며 통화 연결 상태를 판단한다.
     *
     * 판단 우선순위:
     * 1. 통화 시간 형식 텍스트 (MM:SS 또는 H:MM:SS) → 즉시 연결 완료 확정
     * 2. 발신 중 / 연결 중 텍스트 발견 → 미연결
     * 3. 연결 완료 UI 텍스트만 있고 발신 중 텍스트 없음 → 연결 완료
     */
    private fun analyzeCallConnectionState(rootNode: AccessibilityNodeInfo): Boolean {
        // 1단계: 통화 타이머 찾기 (가장 확실한 신호 — 찾으면 즉시 반환)
        if (findCallTimerInTree(rootNode, 0)) {
            return true
        }

        // 2단계: 키워드 기반 판단 (타이머가 없는 경우 보조 수단)
        var foundConnectedText = false
        var foundDialingText = false

        scanKeywordsInTree(rootNode, 0) { type ->
            when (type) {
                KeywordType.CONNECTED -> foundConnectedText = true
                KeywordType.DIALING -> foundDialingText = true
            }
        }

        // 연결 완료 텍스트가 있고 발신 중 텍스트가 없으면 연결 완료
        return foundConnectedText && !foundDialingText
    }

    /**
     * View 트리에서 통화 타이머 텍스트를 탐색한다.
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

    /** 키워드 탐색 결과 분류 */
    private enum class KeywordType { CONNECTED, DIALING }

    /**
     * View 트리에서 연결 완료 / 발신 중 키워드를 탐색한다.
     * 깊이 제한 내에서 모든 텍스트 노드를 검사한다.
     */
    private fun scanKeywordsInTree(
        node: AccessibilityNodeInfo,
        depth: Int,
        onFound: (KeywordType) -> Unit
    ) {
        if (depth > MAX_TRAVERSE_DEPTH) return

        val text = node.text?.toString()
        if (text != null) {
            val lowerText = text.lowercase()
            if (CONNECTED_KEYWORDS.any { it in lowerText }) {
                Log.d(TAG, "✅ 연결 완료 텍스트 발견: $text (depth=$depth)")
                onFound(KeywordType.CONNECTED)
            }
            if (DIALING_KEYWORDS.any { it in lowerText }) {
                Log.d(TAG, "📲 발신 중 텍스트 발견: $text (depth=$depth)")
                onFound(KeywordType.DIALING)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                scanKeywordsInTree(child, depth + 1, onFound)
            } finally {
                child.recycle()
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
         * AccessibilityService의 View hierarchy 분석으로
         * 통화 시간 표시(00:01 형태) 또는 연결 완료 UI 텍스트를 감지하면 true로 래치.
         *
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

        // ── 통화 시간 텍스트 패턴 ──

        /**
         * 통화 시간 형식 정규식.
         * "00:01", "12:34", "1:23:45" 등 MM:SS 또는 H:MM:SS 형태를 매칭한다.
         */
        private val CALL_TIMER_PATTERN = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")

        // ── 연결 완료 판단 키워드 ──

        /**
         * 통화 연결 완료 시 화면에 나타나는 텍스트 키워드 (소문자).
         * 다양한 VoIP 앱(카카오톡, 라인, WhatsApp 등)의 연결 완료 UI를 커버한다.
         */
        private val CONNECTED_KEYWORDS = listOf(
            // 한국어
            "통화 중", "통화중", "연결됨", "연결 됨",
            "페이스톡", "영상통화 중", "영상통화중",
            // 영어 (WhatsApp, Telegram, Line 등)
            "connected", "ongoing", "in call", "video call",
            "call in progress", "call connected",
        )

        /**
         * 발신 중 / 연결 대기 상태 텍스트 키워드 (소문자).
         * 이 텍스트가 보이면 아직 연결되지 않은 것으로 판단한다.
         */
        private val DIALING_KEYWORDS = listOf(
            // 한국어
            "발신", "연결 중", "연결중", "호출 중", "호출중",
            "응답 대기", "응답 대기 중", "초대 중", "초대중",
            "전화 거는 중", "통화 연결 중",
            // 영어
            "calling", "dialing", "ringing", "connecting",
            "waiting", "inviting",
        )

        /**
         * 통화 UI 관련 패키지 목록.
         * 이 패키지가 포그라운드일 때만 View hierarchy 분석을 수행한다.
         */
        private val CALL_UI_PACKAGES = setOf(
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
    }
}


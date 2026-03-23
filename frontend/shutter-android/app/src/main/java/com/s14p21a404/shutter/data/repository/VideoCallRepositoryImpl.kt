package com.s14p21a404.shutter.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.s14p21a404.shutter.domain.repository.VideoCallRepository
import com.s14p21a404.shutter.presentation.service.ForegroundAppMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 영상통화 상태 Repository 구현체
 *
 * TelephonyCallback + CameraManager.AvailabilityCallback으로
 * 통화 중 + 카메라 사용 중 → 영상통화 상태를 판단한다.
 *
 * AccessibilityService로부터 포그라운드 패키지를 받아
 * 영상통화 앱 화면 위에 있는지 여부도 판단한다.
 */
class VideoCallRepositoryImpl(private val context: Context) : VideoCallRepository {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val _videoCallState = MutableStateFlow(false)

    @Volatile
    private var isCameraBusy: Boolean = false

    /**
     * 영상통화가 시작되었을 때 기록된 패키지명.
     * 이 패키지가 포그라운드이면 오버레이 표시, 아니면 숨김.
     */
    @Volatile
    private var videoCallPackage: String? = null

    // ── 통화 상태 콜백 (IDLE 감지 → 래치 리셋 전용) ──
    private val callStateCallback = object : TelephonyCallback(),
        TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            Log.d(TAG, "통화 상태 변경: $state")
            if (state == TelephonyManager.CALL_STATE_IDLE) {
                ForegroundAppMonitorService.resetCallConnected()
            }
        }
    }

    // ── 카메라 사용 상태 콜백 ──
    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            Log.d(TAG, "카메라 사용 가능: $cameraId")
            isCameraBusy = false
            // 카메라 해제 = 영상통화 종료 가능성 → 래치 리셋
            ForegroundAppMonitorService.resetCallConnected()
            evaluate()
        }

        override fun onCameraUnavailable(cameraId: String) {
            Log.d(TAG, "카메라 사용 중: $cameraId")
            isCameraBusy = true
            evaluate()
        }
    }

    // ── 포그라운드 앱 변경 관찰 (VoIP 영상통화 감지용) ──
    private var foregroundObserverJob: kotlinx.coroutines.Job? = null
    private val observerScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    override fun observeVideoCallState(): Flow<Boolean> = _videoCallState.asStateFlow()

    override fun observeForegroundIsVideoCallApp(): Flow<Boolean> {
        return ForegroundAppMonitorService.foregroundPackage.map { currentPkg ->
            val targetPkg = videoCallPackage
            if (targetPkg == null) {
                // 영상통화 패키지가 설정되지 않았으면 true (기본 표시)
                true
            } else {
                // 포그라운드 패키지가 영상통화 패키지와 같거나,
                // 시스템 전화 UI 패키지인 경우 true
                val isOnVideoCallScreen = currentPkg == targetPkg
                        || currentPkg in ALL_VIDEO_CALL_PACKAGES
                        || currentPkg.isEmpty()  // 아직 감지 전
                Log.d(TAG, "포그라운드=$currentPkg, 영상통화앱=$targetPkg → onScreen=$isOnVideoCallScreen")
                isOnVideoCallScreen
            }
        }
    }

    override fun observeForegroundIsFinancialApp(): Flow<Boolean> {
        return ForegroundAppMonitorService.foregroundPackage.map { currentPkg ->
            val isFinancial = currentPkg in FINANCIAL_APP_PACKAGES
            Log.d(TAG, "포그라운드=$currentPkg → 금융앱=$isFinancial")
            isFinancial
        }
    }

    override fun setVideoCallPackage(packageName: String?) {
        videoCallPackage = packageName
        Log.d(TAG, "영상통화 패키지 설정: $packageName")
    }

    override fun startMonitoring() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            telephonyManager.registerTelephonyCallback(context.mainExecutor, callStateCallback)
            Log.d(TAG, "TelephonyCallback 등록")
        } else {
            Log.w(TAG, "READ_PHONE_STATE 권한 없음")
        }

        cameraManager.registerAvailabilityCallback(cameraCallback, null)
        Log.d(TAG, "CameraCallback 등록")

        // VoIP 영상통화 앱 감지를 위한 포그라운드 앱 관찰 시작
        startForegroundObserver()
    }

    override fun stopMonitoring() {
        try {
            telephonyManager.unregisterTelephonyCallback(callStateCallback)
        } catch (e: Exception) {
            Log.e(TAG, "TelephonyCallback 해제 실패", e)
        }
        try {
            cameraManager.unregisterAvailabilityCallback(cameraCallback)
        } catch (e: Exception) {
            Log.e(TAG, "CameraCallback 해제 실패", e)
        }
        stopForegroundObserver()
        _videoCallState.value = false
        videoCallPackage = null
    }

    private fun evaluate() {
        val currentFg = ForegroundAppMonitorService.foregroundPackage.value
        val isCallTimerVisible = ForegroundAppMonitorService.isCallConnected.value

        // 영상통화 판단: 카메라 사용 + 통화 UI 앱 포그라운드 + 타이머 텍스트 감지
        // 통신사/VoIP 구분 없이 동일한 조건 사용.
        // - 카메라: CameraManager.AvailabilityCallback
        // - 통화 UI 앱: CALL_UI_PACKAGES에 포함된 앱이 포그라운드
        // - 타이머: AccessibilityService가 View hierarchy에서 MM:SS 텍스트 감지 (래치)
        val isOnCallScreen = currentFg in ALL_VIDEO_CALL_PACKAGES
        val isVideoCall = isCameraBusy && isOnCallScreen && isCallTimerVisible
        _videoCallState.value = isVideoCall

        // 영상통화 감지 시 현재 포그라운드 앱을 영상통화 패키지로 기록
        if (isVideoCall && videoCallPackage == null) {
            if (currentFg.isNotEmpty()) {
                videoCallPackage = currentFg
                Log.d(TAG, "영상통화 시작 시 포그라운드 패키지 자동 기록: $currentFg")
            }
        } else if (!isVideoCall) {
            videoCallPackage = null
        }

        Log.d(TAG, "영상통화 판단: camera=$isCameraBusy, fg=$currentFg, " +
                "onCallScreen=$isOnCallScreen, timerVisible=$isCallTimerVisible → videoCall=$isVideoCall")
    }

    // ── VoIP 영상통화 앱 포그라운드 관찰 ──

    /**
     * 포그라운드 앱 변경 및 통화 연결 상태 변경을 관찰하여,
     * VoIP 영상통화 앱이 포그라운드에 오거나 벗어날 때,
     * 또는 통화가 실제 연결/해제될 때 evaluate()를 재호출한다.
     */
    private fun startForegroundObserver() {
        if (foregroundObserverJob?.isActive == true) return
        foregroundObserverJob = observerScope.launch {
            // 포그라운드 앱 변경 관찰
            // (StateFlow는 자체적으로 distinctUntilChanged를 보장하므로 중복 emit 없음)
            launch {
                ForegroundAppMonitorService.foregroundPackage.collect { pkg ->
                    Log.d(TAG, "VoIP 감지용 포그라운드 변경: $pkg")
                    evaluate()
                }
            }
            // AccessibilityService의 통화 연결 상태 관찰
            // (StateFlow — 값이 실제로 바뀔 때만 collect 콜백 호출)
            launch {
                ForegroundAppMonitorService.isCallConnected.collect { connected ->
                    Log.d(TAG, "VoIP 감지용 통화 연결 상태 변경: $connected")
                    evaluate()
                }
            }
        }
        Log.d(TAG, "VoIP 포그라운드 + 통화 연결 관찰 시작")
    }

    private fun stopForegroundObserver() {
        foregroundObserverJob?.cancel()
        foregroundObserverJob = null
        Log.d(TAG, "VoIP 포그라운드 관찰 중단")
    }

    companion object {
        private const val TAG = "VideoCallRepo"

        /**
         * 영상통화 관련 패키지 목록 (통신사 + VoIP 통합).
         * 이 패키지가 포그라운드 + 카메라 사용 + 타이머 감지 시 영상통화로 판단한다.
         * 오버레이 표시 여부 판단에도 사용된다.
         */
        private val ALL_VIDEO_CALL_PACKAGES = setOf(
            // ── VoIP 영상통화 앱 ──
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
            // ── 시스템 전화 UI ──
            "com.samsung.android.incallui",           // 삼성 전화 UI
            "com.android.incallui",                   // AOSP 전화 UI
            "com.google.android.dialer",              // Google 전화 앱
            "com.samsung.android.dialer",             // 삼성 전화 앱
            "com.android.dialer",                     // AOSP 전화 앱
            "com.samsung.android.vtcallplugin",       // 삼성 영상통화 플러그인
        )

        /**
         * 금융 앱 패키지 목록.
         * 딥페이크 통화 감지 중 이 앱이 포그라운드이면
         * 금융 앱 접근 제한 오버레이를 표시한다.
         */
        private val FINANCIAL_APP_PACKAGES = setOf(
            // ── 은행 ──
            "com.kbstar.kbbank",                     // KB국민은행
            "com.shinhan.sbanking",                  // 신한은행(SOL)
            "com.wooribank.smart.npib",              // 우리은행(우리WON뱅킹)
            "com.kebhana.hanapush",                  // 하나은행(하나원큐)
            "nh.smart.banking",                      // NH농협은행
            "com.ibk.neobanking",                    // IBK기업은행(i-ONE뱅크)
            "com.kdb.talk",                          // KDB산업은행
            "com.epost.psf.sdsi",                    // 우체국뱅킹
            "com.sc.danb",                           // SC제일은행
            "com.citi.android.citibank.kr",          // 한국씨티은행
            "com.bnk.busanbank",                     // BNK부산은행
            "com.bnk.cbank",                         // BNK경남은행
            "com.knb.psb",                           // DGB대구은행
            "com.jeonbukbank.jbm",                   // JB전북은행
            "kr.co.kwangju.bank",                    // 광주은행
            "com.jeju.jbank",                        // 제주은행
            "com.suhyup.mbanking",                   // Sh수협은행
            // ── 인터넷/디지털 뱅크 ──
            "com.kakaobank.channel",                 // 카카오뱅크
            "com.kbanknow",                          // 케이뱅크(K뱅크)
            "viva.republica.toss",                   // 토스
            // ── 증권 ──
            "com.kiwoom.hero",                       // 키움증권(영웅문)
            "com.kiwoom.herots",                     // 키움증권(영웅문S)
            "com.samsung.android.samsungpop",        // 삼성증권(mPOP)
            "com.mirae.mstock",                      // 미래에셋증권(M-STOCK)
            "com.daishin.cybos",                     // 대신증권
            "com.nhqv.namuh",                        // NH투자증권(나무)
            "com.shinhan.smartstock",                // 신한투자증권
            "com.kbsec.mstocku",                     // KB증권(M-able)
            "com.hanwha.investment",                 // 한화투자증권
            "com.hana.fnc.securities",               // 하나증권
            // ── 보험 ──
            "com.samsung.life.samsunglife",          // 삼성생명
            "kr.co.hanwhalife",                      // 한화생명
            "com.kyobo.kcpi",                        // 교보생명
            "com.samsung.android.samsungfire",       // 삼성화재
            "com.dbins.smart",                       // DB손해보험
            "com.kbinsure.smart",                    // KB손해보험
            // ── 카드 ──
            "com.shinhancard.smartshinhan",          // 신한카드
            "com.samsung.android.samsungcard",       // 삼성카드
            "com.kbcard.cxh.appcard",                // KB국민카드
            "com.hyundaicard.appcard",               // 현대카드
            "com.lottecard.app",                     // 롯데카드
            "com.wooricard.smartapp",                // 우리카드
            "com.hanacard.smart",                    // 하나카드
            "kr.co.bccard.bcsmartpay",               // BC카드
            // ── 간편결제/핀테크 ──
            "com.nhn.android.search",                // 네이버페이(네이버앱 내)
            "com.kakaopay.app",                      // 카카오페이
            "com.nhnent.payapp",                     // 페이앱
        )
    }
}




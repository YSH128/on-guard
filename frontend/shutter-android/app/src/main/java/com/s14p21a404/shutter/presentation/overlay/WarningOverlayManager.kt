package com.s14p21a404.shutter.presentation.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.s14p21a404.shutter.domain.model.DeepfakeDetectionResult
import com.s14p21a404.shutter.domain.repository.VideoCallRepository

/**
 * 딥페이크 감지 경고 오버레이 매니저.
 *
 * 3가지 독립 오버레이를 관리한다:
 * 1) **Scanning Pill** — "Shutter 감지 중•••" 스캐닝 상태 pill 배지 (터치 통과)
 * 2) **ROI Bounding Box** — 감지된 얼굴 ROI에 빨간 border + 스캔 라인 + confidence 배지 (터치 통과)
 * 3) **Warning Bottom Sheet** — 흰색 바텀시트 모달 (터치 가능)
 *    - 인앱: 지도 + 국기 + 감지유형 + 전화종료 버튼
 *    - 외부앱: AI 위조 감지 타이틀 + 전화종료 버튼만
 */
class WarningOverlayManager(
    private val context: Context,
    private val videoCallRepository: VideoCallRepository? = null
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    // ── 오버레이 뷰 참조 ──
    private var scanningPillView: ScanningPillView? = null
    private var roiBoundingBoxView: RoiBoundingBoxView? = null
    private var bottomSheetView: WarningBottomSheetView? = null


    // 푸쉬 알림 pill 뷰 참조
    private val notificationPillViews = mutableListOf<NotificationPillView>()

    /** 오버레이 활성화 여부 — false이면 show 호출을 무시한다. */
    @Volatile
    private var enabled = false

    /** 현재 감지 결과 (복원용) */
    private var lastResult: DeepfakeDetectionResult? = null

    /**
     * 현재 세션에서 바텀시트가 이미 한 번 표시되었는지 여부.
     * true이면 이후 감지 결과에서 바텀시트를 다시 표시하지 않고, ROI box만 업데이트한다.
     */
    @Volatile
    private var bottomSheetShownForSession = false

    /** 수신된 상대방 국가명 (바텀시트에 표시) */
    @Volatile
    private var receivedCountryName: String? = null

    /** 수신된 상대방 위도/경도 (지도 핀 위치에 사용) */
    @Volatile
    private var receivedLat: Double? = null
    @Volatile
    private var receivedLng: Double? = null

    /** 콜백: 전화 종료 */
    var onEndCall: (() -> Unit)? = null

    /** 콜백: 재탐지 */
    var onRescan: (() -> Unit)? = null

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    /**
     * 감지 결과에 따라 오버레이를 표시하거나 숨긴다.
     *
     * - 최초 detected=true: 스캐닝 pill 제거 → ROI box + dim + 바텀시트 표시
     * - 이미 바텀시트가 한 번 표시된 후: ROI box 위치만 업데이트 (바텀시트 재표시 안 함)
     * - detected=false: ROI box만 제거 (바텀시트는 사용자가 닫을 때까지 유지)
     *
     * @param result     딥페이크 감지 결과
     * @param roiBitmap  ROI 영역을 crop한 Bitmap — 현재 미사용
     */
    fun onDetectionResult(result: DeepfakeDetectionResult, roiBitmap: Bitmap? = null) {
        lastResult = result
        if (result.detected) {
            // 스캐닝 pill 제거
            dismissScanningPill()

            if (bottomSheetShownForSession) {
                // 이미 바텀시트가 표시된 적 있음 → ROI box 위치만 갱신
                updateRoiBoxOnly(result)
            } else {
                // 최초 감지 → ROI box + dim + 바텀시트 모두 표시
                showDetectionOverlays(result)
                bottomSheetShownForSession = true
            }
        } else {
            // detected=false → ROI box만 업데이트 (없으면 제거)
            updateRoiBoxOnly(result)
        }
    }

    fun showWarning() {
        if (!enabled) {
            Log.d(TAG, "오버레이 비활성 상태 — 표시 무시")
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "오버레이 권한 없음 — 경고 표시 불가")
            return
        }
        lastResult?.let { showDetectionOverlays(it) }
    }

    fun dismissWarning() {
        dismissAll()
    }


    /**
     * 오버레이 표시 허용 여부를 설정한다.
     * 영상통화 시작 시 true, 종료 시 false로 설정하여
     * 종료 직후 잔존 감지 결과에 의한 재표시를 방지한다.
     */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /** 하위 호환용 show() — 기존 코드가 호출하는 경우 대비 */
    fun show() {
        lastResult?.let { showDetectionOverlays(it) } ?: showScanningPill()
    }

    fun dismiss() {
        dismissAll()
    }

    fun isShowing(): Boolean =
        scanningPillView != null || roiBoundingBoxView != null || bottomSheetView != null || notificationPillViews.isNotEmpty()

    /** 기존 API 호환용. 새 UI에서는 face preview를 바텀시트 내에 포함하지 않으므로 no-op. */
    fun updateFacePreview(bitmap: Bitmap?) {
        // 새 디자인에서는 face preview 미사용 (ROI box로 대체)
    }

    // ─────────────────────────────────────────────
    // 1) Scanning Pill
    // ─────────────────────────────────────────────

    /**
     * "Shutter 감지 중" pill을 화면 좌상단에 표시한다.
     */
    fun showScanningPill() {
        if (!enabled) return
        if (!Settings.canDrawOverlays(context)) return
        if (scanningPillView != null) return // 이미 표시 중

        val pill = ScanningPillView(context)
        addOverlayView(
            view = pill,
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.END,
            touchable = false, // 터치 통과
            xOffset = dpToPx(24),
            yOffset = dpToPx(80) // 상태바 + 통화 헤더 아래
        )
        pill.startAnimation()
        scanningPillView = pill
        Log.d(TAG, "스캐닝 pill 표시")
    }

    fun dismissScanningPill() {
        scanningPillView?.let {
            it.stopAnimation()
            removeOverlayView(it)
        }
        scanningPillView = null
    }

    // ─────────────────────────────────────────────
    // 2) ROI Bounding Box + 3) Bottom Sheet
    // ─────────────────────────────────────────────

    private fun showDetectionOverlays(result: DeepfakeDetectionResult) {
        if (!enabled) return
        if (!Settings.canDrawOverlays(context)) return

        // 이미 표시 중이면 ROI만 업데이트
        if (roiBoundingBoxView != null && bottomSheetView != null) {
            if (result.hasRoi) {
                roiBoundingBoxView?.setRoi(
                    result.roiMinX, result.roiMinY,
                    result.roiMaxX, result.roiMaxY,
                    result.fakeProb * 100f
                )
            }
            return
        }

        // ── ROI Bounding Box ──
        if (result.hasRoi) {
            val roiView = RoiBoundingBoxView(context)
            addOverlayView(
                view = roiView,
                width = WindowManager.LayoutParams.MATCH_PARENT,
                height = WindowManager.LayoutParams.MATCH_PARENT,
                gravity = Gravity.TOP or Gravity.START,
                touchable = false // 터치 통과
            )
            roiView.setRoi(
                result.roiMinX, result.roiMinY,
                result.roiMaxX, result.roiMaxY,
                result.fakeProb * 100f
            )
            roiView.startAnimations()
            roiBoundingBoxView = roiView
            Log.d(TAG, "ROI 바운딩 박스 표시: roi=${result.roi?.contentToString()}")
        }


        // ── Warning Bottom Sheet (500ms 지연 후 표시) ──
        handler.postDelayed({
            if (!enabled) return@postDelayed
            val isInApp = videoCallRepository?.isInAppCallActive() ?: false
            val sheet = WarningBottomSheetView(
                context = context,
                isInApp = isInApp,
                onEndCall = {
                    Log.d(TAG, "전화 종료 버튼 클릭")
                    onEndCall?.invoke()
                },
                onRescan = {
                    Log.d(TAG, "재탐지 버튼 클릭")
                    onRescan?.invoke()
                },
                onClose = {
                    Log.d(TAG, "닫기 버튼 클릭 → 바텀시트+dim만 닫기, ROI box는 유지")
                    dismissBottomSheetAndDim()
                }
            )
            addOverlayView(
                view = sheet,
                width = WindowManager.LayoutParams.MATCH_PARENT,
                height = WindowManager.LayoutParams.MATCH_PARENT,
                gravity = Gravity.BOTTOM or Gravity.START,
                touchable = true
            )
            sheet.startEntranceAnimation()
            // 이미 수신된 국가명/좌표가 있으면 바텀시트에 반영
            receivedCountryName?.let { country -> sheet.updateCountry(country) }
            val lat = receivedLat
            val lng = receivedLng
            if (lat != null && lng != null) sheet.updatePinPosition(lat, lng)
            bottomSheetView = sheet
            Log.d(TAG, "바텀시트 표시")

            // ── 푸쉬 알림 pills (바텀시트 등장 후 표시) ──
            showNotificationPills()
        }, 500)
    }

    // ─────────────────────────────────────────────
    // 4) Push Notification Pills
    // ─────────────────────────────────────────────

    /**
     * 레퍼런스 기반 푸쉬 알림 pill 순차 표시:
     * 1) "보호자 카톡 알림 완료" (0ms 딜레이)
     * 2) "GPS 위치 공유 완료" (500ms 딜레이)
     * 3) "금융앱 접근 차단 활성화" (1000ms 딜레이)
     *
     * 각 pill은 우측 상단에서 슬라이드 인 → 3.5초 유지 → 페이드 아웃 후 자동 제거.
     */
    private fun showNotificationPills() {
        if (!enabled) return
        if (!Settings.canDrawOverlays(context)) return

        val types = listOf(
            NotificationPillView.Type.KAKAO,
            NotificationPillView.Type.GPS,
            NotificationPillView.Type.FINANCE
        )

        types.forEachIndexed { index, type ->
            handler.postDelayed({
                if (!enabled) return@postDelayed
                val pillView = NotificationPillView(context, type)

                // 각 pill은 이전 pill 아래에 배치 (겹치지 않도록 yOffset 조절)
                addOverlayView(
                    view = pillView,
                    width = WindowManager.LayoutParams.WRAP_CONTENT,
                    height = WindowManager.LayoutParams.WRAP_CONTENT,
                    gravity = Gravity.TOP or Gravity.END,
                    touchable = false,
                    xOffset = dpToPx(12),
                    yOffset = dpToPx(80) + dpToPx(index * 44) // 각 pill 사이 간격
                )
                notificationPillViews.add(pillView)

                pillView.startSlideInFadeOut {
                    // 애니메이션 종료 후 자동 제거
                    removeOverlayView(pillView)
                    notificationPillViews.remove(pillView)
                }
                Log.d(TAG, "알림 pill 표시: $type")
            }, (index * 500).toLong())
        }
    }

    private fun dismissNotificationPills() {
        notificationPillViews.forEach { removeOverlayView(it) }
        notificationPillViews.clear()
    }

    /**
     * ROI bounding box만 업데이트한다.
     * 바텀시트가 이미 표시된 뒤에 감지 결과가 갱신될 때 호출.
     * ROI가 없는 결과(detected=false)이면 ROI box를 제거한다.
     */
    private fun updateRoiBoxOnly(result: DeepfakeDetectionResult) {
        if (!enabled) return

        if (result.hasRoi) {
            if (roiBoundingBoxView != null) {
                // 기존 ROI box 위치만 갱신
                roiBoundingBoxView?.setRoi(
                    result.roiMinX, result.roiMinY,
                    result.roiMaxX, result.roiMaxY,
                    result.fakeProb * 100f
                )
            } else if (Settings.canDrawOverlays(context)) {
                // ROI box가 없으면 새로 생성 (바텀시트 닫힌 후 재표시)
                val roiView = RoiBoundingBoxView(context)
                addOverlayView(
                    view = roiView,
                    width = WindowManager.LayoutParams.MATCH_PARENT,
                    height = WindowManager.LayoutParams.MATCH_PARENT,
                    gravity = Gravity.TOP or Gravity.START,
                    touchable = false
                )
                roiView.setRoi(
                    result.roiMinX, result.roiMinY,
                    result.roiMaxX, result.roiMaxY,
                    result.fakeProb * 100f
                )
                roiView.startAnimations()
                roiBoundingBoxView = roiView
                Log.d(TAG, "ROI 바운딩 박스 재생성: roi=${result.roi?.contentToString()}")
            }
        } else {
            // ROI 없는 결과 → ROI box 제거
            roiBoundingBoxView?.let {
                it.stopAnimations()
                removeOverlayView(it)
            }
            roiBoundingBoxView = null
        }
    }

    /**
     * 바텀시트만 닫는다. ROI bounding box는 유지.
     * 사용자가 바텀시트의 X 버튼을 누르면 호출된다.
     */
    private fun dismissBottomSheetAndDim() {
        bottomSheetView?.let {
            it.stopAnimations()
            removeOverlayView(it)
        }
        bottomSheetView = null
    }

    private fun dismissDetectionOverlays() {
        roiBoundingBoxView?.let {
            it.stopAnimations()
            removeOverlayView(it)
        }
        roiBoundingBoxView = null


        bottomSheetView?.let {
            it.stopAnimations()
            removeOverlayView(it)
        }
        bottomSheetView = null
    }

    fun dismissAll() {
        dismissScanningPill()
        dismissDetectionOverlays()
        dismissNotificationPills()
        handler.removeCallbacksAndMessages(null)
        lastResult = null
        bottomSheetShownForSession = false
        receivedCountryName = null
        receivedLat = null
        receivedLng = null
    }

    /**
     * 상대방 위치(국가명 + 위경도)를 업데이트한다.
     * 바텀시트가 이미 표시 중이면 즉시 반영하고,
     * 아직 표시되지 않았으면 저장해두었다가 바텀시트 생성 시 반영한다.
     */
    fun updateLocation(country: String, lat: Double? = null, lng: Double? = null) {
        receivedCountryName = country
        receivedLat = lat
        receivedLng = lng
        handler.post {
            bottomSheetView?.updateCountry(country)
            if (lat != null && lng != null) {
                bottomSheetView?.updatePinPosition(lat, lng)
            }
        }
        Log.d(TAG, "위치 업데이트: $country lat=$lat lng=$lng")
    }

    // ─────────────────────────────────────────────
    // Window 헬퍼
    // ─────────────────────────────────────────────

    private fun addOverlayView(
        view: View,
        width: Int,
        height: Int,
        gravity: Int,
        touchable: Boolean,
        xOffset: Int = 0,
        yOffset: Int = 0
    ) {
        val flags = if (touchable) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        val params = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = xOffset
            this.y = yOffset
        }

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "overlay add error", e)
        }
    }

    private fun removeOverlayView(view: View) {
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "overlay remove error", e)
        }
    }

    // ─────────────────────────────────────────────
    // Utils
    // ─────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()

    companion object {
        private const val TAG = "WarningOverlay"
    }
}

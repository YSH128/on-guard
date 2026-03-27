package com.s14p21a404.shutter.presentation.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.s14p21a404.shutter.presentation.service.ForegroundAppMonitorService
import kotlin.math.roundToInt

/**
 * 금융 앱 접근 제한 오버레이.
 *
 * 딥페이크 의심 통화 중 사용자가 금융 앱을 열면
 * 전체 화면을 덮어 접근을 차단한다.
 *
 * WarningBottomSheetView와 동일한 화이트 색상 테마를 사용하되,
 * 가운데에서 팝업으로 나타난다.
 *
 * ── isInApp = true (자체앱 통화) ──
 * - '금융앱 접근 제한' 타이틀
 * - 세계 지도 + 위치 핀(⚠ + pulse) + 국가명 + 국기
 * - 대화 감지 유형 ("계좌 이체 요구" + $ 아이콘)
 * - 통화종료 버튼 없음
 *
 * ── isInApp = false (외부앱: 페이스톡/통신사 전화) ──
 * - 금지 아이콘(pulse) + '금융앱 접근 제한' 타이틀만 표시
 */
class FinancialBlockOverlayManager(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    /** 관리 중인 모든 윈도우 */
    private val overlayViews = mutableListOf<View>()

    /** 오버레이 활성화 여부 — false이면 show() 호출을 무시한다. */
    @Volatile
    private var enabled = false

    /** pulse 애니메이터 목록 (dismiss 시 정리) */
    private val pulseAnimators = mutableListOf<AnimatorSet>()

    // 인앱 전용 뷰 참조 (국가명/국기 업데이트용)
    private var countryNameTv: TextView? = null
    private var flagImageView: ImageView? = null
    private var pinContainerRef: FrameLayout? = null
    private var labelContainer: LinearLayout? = null
    private var mapContainer: FrameLayout? = null

    // ── 캐시: show() 이전에 수신된 데이터를 보관하여 뷰 생성 시 반영 ──
    private var cachedCountryName: String = "위치 확인 중..."
    private var cachedCountryCode: String? = null
    private var cachedLat: Double? = null
    private var cachedLng: Double? = null

    /** 홈/메뉴 버튼 감지용 BroadcastReceiver */
    private var closeSystemDialogsReceiver: BroadcastReceiver? = null

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /**
     * 금융 앱 접근 제한 오버레이를 표시한다.
     * @param isInApp true=자체앱 통화 (지도+국기+감지유형), false=외부앱 (금지아이콘+타이틀)
     */
    fun show(isInApp: Boolean) {
        if (!enabled) {
            Log.d(TAG, "금융 차단 오버레이 비활성 상태 — 표시 무시")
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "오버레이 권한 없음 — 금융 차단 오버레이 표시 불가")
            return
        }
        if (overlayViews.isNotEmpty()) {
            Log.d(TAG, "금융 차단 오버레이 already showing")
            return
        }

        // 전체 화면을 덮는 단일 윈도우 (터치 차단)
        addOverlay(
            view = buildFullScreenOverlay(isInApp),
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            gravity = Gravity.CENTER,
            touchable = true
        )

        Log.d(TAG, "금융 차단 오버레이 표시")

        // 홈버튼/메뉴(최근앱)버튼 감지 → 300ms 후 금융앱 여부 재확인
        registerSystemButtonReceiver()

        // show 이전에 수신된 국기/위경도 데이터를 뷰에 반영 (뷰가 레이아웃된 후 적용)
        if (isInApp) {
            handler.postDelayed({ applyCachedData() }, 200)
        }
    }

    fun dismiss() {
        Log.d(TAG, "🔒 [FinancialBlockOverlay] dismiss() 호출됨. 현재 isShowing()=${isShowing()}")
        handler.removeCallbacksAndMessages(null)
        pulseAnimators.forEach { it.cancel() }
        pulseAnimators.clear()
        countryNameTv = null
        flagImageView = null
        pinContainerRef = null
        labelContainer = null
        mapContainer = null

        unregisterSystemButtonReceiver()

        overlayViews.forEach { view ->
            try {
                windowManager.removeView(view)
                Log.d(TAG, "↳ 오버레이 뷰 제거 성공")
            } catch (e: Exception) {
                Log.e(TAG, "overlay remove error", e)
            }
        }
        overlayViews.clear()
        Log.d(TAG, "금융 차단 오버레이 해제 처리 완료")
    }

    fun isShowing(): Boolean = overlayViews.isNotEmpty()

    // ─────────────────────────────────────────────
    // 위치 데이터 캐시 (인앱 전용)
    // ─────────────────────────────────────────────

    /** 국가명 + 국기코드를 캐싱한다. 오버레이가 표시 중이면 즉시 반영. */
    fun updateCountry(country: String) {
        cachedCountryName = country
        cachedCountryCode = countryNameToCode(country)
        if (isShowing()) handler.post { applyCachedData() }
    }

    /** 핀 위경도를 캐싱한다. 오버레이가 표시 중이면 즉시 반영. */
    fun updatePinPosition(lat: Double, lng: Double) {
        cachedLat = lat
        cachedLng = lng
        if (isShowing()) handler.post { applyCachedData() }
    }

    // ─────────────────────────────────────────────
    // 캐시 → 뷰 반영
    // ─────────────────────────────────────────────

    /**
     * 캐싱된 국가명·국기·위경도를 뷰에 일괄 반영한다.
     */
    private fun applyCachedData() {
        // 국가명
        countryNameTv?.text = cachedCountryName

        // 국기
        val code = cachedCountryCode
        if (code != null) {
            flagImageView?.let { iv ->
                iv.visibility = View.VISIBLE
                Glide.with(iv.context)
                    .load("https://flagcdn.com/w40/${code}.png")
                    .transform(RoundedCorners(dpToPx(2)))
                    .into(iv)
            }
        }

        // 핀 위치
        val lat = cachedLat
        val lng = cachedLng
        if (lat != null && lng != null) {
            val mc = mapContainer ?: return
            mc.post {
                val mapW = mc.width.toFloat()
                val mapH = mc.height.toFloat()
                if (mapW <= 0 || mapH <= 0) return@post

                val xRatio = ((lng + 180.0) / 360.0).toFloat().coerceIn(0f, 1f)
                val yRatio = ((90.0 - lat) / 180.0).toFloat().coerceIn(0f, 1f)

                val pinSize = dpToPx(80)
                val pinX = (mapW * xRatio - pinSize / 2f).toInt().coerceAtLeast(0)
                val pinY = (mapH * yRatio - pinSize / 2f).toInt().coerceAtLeast(0)

                pinContainerRef?.let { pin ->
                    (pin.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                        lp.leftMargin = pinX
                        lp.topMargin = pinY
                        pin.layoutParams = lp
                    }
                }
                labelContainer?.let { label ->
                    (label.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                        lp.leftMargin = (pinX - dpToPx(10)).coerceAtLeast(0)
                        lp.topMargin = (pinY + pinSize - dpToPx(6)).coerceAtLeast(0)
                        label.layoutParams = lp
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Window 헬퍼
    // ─────────────────────────────────────────────

    private fun addOverlay(
        view: View,
        width: Int,
        height: Int,
        gravity: Int,
        touchable: Boolean,
        yOffset: Int = 0
    ) {
        val flags = if (touchable) {
            // 뒤로가기 키 이벤트를 받기 위해 FLAG_NOT_FOCUSABLE 제거
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
            this.y = yOffset
        }

        try {
            windowManager.addView(view, params)
            overlayViews.add(view)
        } catch (e: Exception) {
            Log.e(TAG, "overlay add error", e)
        }
    }

    // ─────────────────────────────────────────────
    // Full Screen Overlay (dim + white card, center popup)
    // ─────────────────────────────────────────────

    private fun buildFullScreenOverlay(isInApp: Boolean): View {
        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_BACK) {
                    Log.d(TAG, "🔙 뒤로가기 버튼 감지 → 오버레이 즉시 해제")
                    this@FinancialBlockOverlayManager.dismiss()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusableInTouchMode = true
            requestFocus()
            setOnTouchListener { _, _ -> true }  // 터치 차단
        }

        // dim 배경
        val dimView = View(context).apply {
            setBackgroundColor(Color.parseColor("#B3000000"))
        }
        root.addView(dimView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // white card (center popup with scale animation)
        val cardView = buildWhiteCard(isInApp)
        cardView.scaleX = 0.8f
        cardView.scaleY = 0.8f
        cardView.alpha = 0f

        root.addView(cardView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        // 등장 애니메이션 (가운데에서 팝업)
        handler.postDelayed({
            cardView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(OvershootInterpolator(0.8f))
                .start()
        }, 50)

        // pulse 시작
        handler.postDelayed({
            pulseAnimators.forEach { it.start() }
        }, 300)

        return root
    }

    // ─────────────────────────────────────────────
    // White Card (WarningBottomSheetView 색상 테마)
    // ─────────────────────────────────────────────

    private fun buildWhiteCard(isInApp: Boolean): View {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth * 0.85f).roundToInt()
        val radius = dpToPx(24).toFloat()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = radius
            }
            elevation = dpToPx(8).toFloat()
        }

        val wrapper = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        wrapper.addView(card, FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })

        if (isInApp) {
            buildInAppContent(card)
        } else {
            buildExternalContent(card)
        }

        return wrapper
    }

    // ─────────────────────────────────────────────
    // 인앱 전용 컨텐츠 (지도 + 핀 + 국가 + 감지유형)
    // ─────────────────────────────────────────────

    private fun buildInAppContent(parent: LinearLayout) {
        // 타이틀: "금융앱 접근 제한"
        parent.addView(TextView(context).apply {
            text = "금융앱 접근 제한"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.parseColor("#111827"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(16) })

        // 세계 지도 + 핀
        buildMapSection(parent)

        // 대화 감지 유형
        buildDetectionTypeSection(parent)

        // 설명 텍스트
        parent.addView(View(context).apply {
            setBackgroundColor(Color.parseColor("#E5E7EB"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
        ).apply { bottomMargin = dpToPx(16); topMargin = dpToPx(8) })

        parent.addView(TextView(context).apply {
            text = "딥페이크 의심 전화와 통화 중입니다.\n통화 상대와의 거래를 권장하지 않습니다."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#6B7280"))
            gravity = Gravity.CENTER
            setLineSpacing(dpToPx(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(8) })

        parent.addView(TextView(context).apply {
            text = "통화를 종료해주세요"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#EF4444"))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun buildMapSection(parent: LinearLayout) {
        mapContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        // 지도 View
        val worldMap = ImageView(context).apply {
            setImageResource(context.resources.getIdentifier("map", "drawable", context.packageName))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        mapContainer!!.addView(worldMap, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dpToPx(160)
        ))

        // 핀 컨테이너
        val pinContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        pinContainerRef = pinContainer

        // pulse circles
        for (i in 0..1) {
            val pulseCircle = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#80EF4444"))
                    shape = GradientDrawable.OVAL
                }
                alpha = 0f
            }
            val pulseSize = dpToPx(32)
            pinContainer.addView(pulseCircle, FrameLayout.LayoutParams(pulseSize, pulseSize).apply {
                gravity = Gravity.CENTER
            })

            val scaleX = ObjectAnimator.ofFloat(pulseCircle, "scaleX", 1f, 2.5f).apply {
                duration = if (i == 0) 1500L else 2000L
                repeatCount = ValueAnimator.INFINITE
                startDelay = (i * 300).toLong()
            }
            val scaleY = ObjectAnimator.ofFloat(pulseCircle, "scaleY", 1f, 2.5f).apply {
                duration = if (i == 0) 1500L else 2000L
                repeatCount = ValueAnimator.INFINITE
                startDelay = (i * 300).toLong()
            }
            val alphaAnim = ObjectAnimator.ofFloat(pulseCircle, "alpha", 0.8f, 0f).apply {
                duration = if (i == 0) 1500L else 2000L
                repeatCount = ValueAnimator.INFINITE
                startDelay = (i * 300).toLong()
            }
            val set = AnimatorSet().apply { playTogether(scaleX, scaleY, alphaAnim) }
            pulseAnimators.add(set)
        }

        // 경고 아이콘 원
        val alertCircle = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FEE2E2"))
                shape = GradientDrawable.OVAL
                setStroke(dpToPx(2), Color.parseColor("#FECACA"))
            }
            elevation = dpToPx(2).toFloat()
        }
        val alertIcon = TextView(context).apply {
            text = "⚠"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#EF4444"))
            gravity = Gravity.CENTER
        }
        alertCircle.addView(alertIcon, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val circleSize = dpToPx(36)
        pinContainer.addView(alertCircle, FrameLayout.LayoutParams(circleSize, circleSize).apply {
            gravity = Gravity.CENTER
        })

        val pinWrapSize = dpToPx(80)
        val mapHeight = dpToPx(160)
        mapContainer!!.addView(pinContainer, FrameLayout.LayoutParams(pinWrapSize, pinWrapSize).apply {
            leftMargin = dpToPx(80)
            topMargin = (mapHeight * 0.35f).toInt()
        })

        // 국가명 라벨
        labelContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dpToPx(6).toFloat()
            }
            elevation = dpToPx(2).toFloat()
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
        }

        countryNameTv = TextView(context).apply {
            text = cachedCountryName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#374151"))
        }
        labelContainer!!.addView(countryNameTv)

        flagImageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }
        val flagSize = dpToPx(18)
        labelContainer!!.addView(flagImageView, LinearLayout.LayoutParams(flagSize, (flagSize * 0.7f).toInt()).apply {
            marginStart = dpToPx(4)
            gravity = Gravity.CENTER_VERTICAL
        })

        mapContainer!!.addView(labelContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dpToPx(70)
            topMargin = (mapHeight * 0.35f).toInt() + pinWrapSize - dpToPx(6)
        })

        parent.addView(mapContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(8) })
    }

    private fun buildDetectionTypeSection(parent: LinearLayout) {
        val detectionTypeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val typeLabelTv = TextView(context).apply {
            text = "계좌 이체 요구"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#374151"))
        }
        detectionTypeRow.addView(typeLabelTv)

        val dollarBg = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F3F4F6"))
                shape = GradientDrawable.OVAL
            }
        }
        val dollarTv = TextView(context).apply {
            text = "$"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#6B7280"))
            gravity = Gravity.CENTER
        }
        dollarBg.addView(dollarTv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val dollarSize = dpToPx(32)
        detectionTypeRow.addView(dollarBg, LinearLayout.LayoutParams(dollarSize, dollarSize).apply {
            leftMargin = dpToPx(8)
        })

        parent.addView(detectionTypeRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dpToPx(8)
        })
    }

    // ─────────────────────────────────────────────
    // 외부앱 컨텐츠 (금지 아이콘 + 타이틀만)
    // ─────────────────────────────────────────────

    private fun buildExternalContent(parent: LinearLayout) {
        // 타이틀 행: 금지 아이콘(pulse) + "금융앱 접근 제한"
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }

        // 금지 아이콘 컨테이너 (pulse 원 + 🚫 아이콘)
        val iconWrap = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        // pulse circles
        for (i in 0..1) {
            val pulseCircle = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#80EF4444"))
                    shape = GradientDrawable.OVAL
                }
                alpha = 0f
            }
            val pulseSize = dpToPx(32)
            iconWrap.addView(pulseCircle, FrameLayout.LayoutParams(pulseSize, pulseSize).apply {
                gravity = Gravity.CENTER
            })

            val scaleX = ObjectAnimator.ofFloat(pulseCircle, "scaleX", 1f, 2.5f).apply {
                duration = if (i == 0) 1500L else 2000L
                repeatCount = ValueAnimator.INFINITE
                startDelay = (i * 300).toLong()
            }
            val scaleY = ObjectAnimator.ofFloat(pulseCircle, "scaleY", 1f, 2.5f).apply {
                duration = if (i == 0) 1500L else 2000L
                repeatCount = ValueAnimator.INFINITE
                startDelay = (i * 300).toLong()
            }
            val alphaAnim = ObjectAnimator.ofFloat(pulseCircle, "alpha", 0.7f, 0f).apply {
                duration = if (i == 0) 1500L else 2000L
                repeatCount = ValueAnimator.INFINITE
                startDelay = (i * 300).toLong()
            }
            val set = AnimatorSet().apply { playTogether(scaleX, scaleY, alphaAnim) }
            pulseAnimators.add(set)
        }

        // 금지 아이콘 원
        val alertCircle = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FEE2E2"))
                shape = GradientDrawable.OVAL
                setStroke(dpToPx(2), Color.parseColor("#FECACA"))
            }
            elevation = dpToPx(2).toFloat()
        }
        val alertIcon = TextView(context).apply {
            text = "🚫"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#EF4444"))
            gravity = Gravity.CENTER
        }
        alertCircle.addView(alertIcon, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val circleSize = dpToPx(32)
        iconWrap.addView(alertCircle, FrameLayout.LayoutParams(circleSize, circleSize).apply {
            gravity = Gravity.CENTER
        })

        val iconWrapSize = dpToPx(56)
        titleRow.addView(iconWrap, LinearLayout.LayoutParams(iconWrapSize, iconWrapSize).apply {
            marginEnd = dpToPx(6)
        })

        // "금융앱 접근 제한" 텍스트
        val tv = TextView(context).apply {
            text = "금융앱 접근 제한"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.parseColor("#111827"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        titleRow.addView(tv)

        parent.addView(titleRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dpToPx(20)
        })

        // 구분선
        parent.addView(View(context).apply {
            setBackgroundColor(Color.parseColor("#E5E7EB"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
        ).apply { bottomMargin = dpToPx(16) })

        // 설명 텍스트
        parent.addView(TextView(context).apply {
            text = "딥페이크 의심 전화와 통화 중입니다.\n통화 상대와의 거래를 권장하지 않습니다."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#6B7280"))
            gravity = Gravity.CENTER
            setLineSpacing(dpToPx(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dpToPx(8)
        })

        parent.addView(TextView(context).apply {
            text = "통화를 종료해주세요"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#EF4444"))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL })
    }

    // ─────────────────────────────────────────────
    // 국가명 → ISO 코드 변환
    // ─────────────────────────────────────────────

    private fun countryNameToCode(name: String): String? {
        return when (name.trim()) {
            "대한민국", "한국" -> "kr"
            "캄보디아" -> "kh"
            "미국" -> "us"
            "중국" -> "cn"
            "일본" -> "jp"
            "베트남" -> "vn"
            "태국" -> "th"
            "필리핀" -> "ph"
            "미얀마" -> "mm"
            "라오스" -> "la"
            "말레이시아" -> "my"
            "싱가포르" -> "sg"
            "인도네시아" -> "id"
            "인도" -> "in"
            "러시아" -> "ru"
            "영국" -> "gb"
            "독일" -> "de"
            "프랑스" -> "fr"
            "호주" -> "au"
            "캐나다" -> "ca"
            "브라질" -> "br"
            "나이지리아" -> "ng"
            "가나" -> "gh"
            "케냐" -> "ke"
            "북한" -> "kp"
            "대만", "타이완" -> "tw"
            "홍콩" -> "hk"
            "멕시코" -> "mx"
            "아르헨티나" -> "ar"
            "이탈리아" -> "it"
            "스페인" -> "es"
            "네덜란드" -> "nl"
            "스위스" -> "ch"
            "터키", "튀르키예" -> "tr"
            "사우디아라비아" -> "sa"
            "이란" -> "ir"
            "이라크" -> "iq"
            "이집트" -> "eg"
            "남아프리카공화국", "남아공" -> "za"
            else -> null
        }
    }

    // ─────────────────────────────────────────────
    // 시스템 버튼 감지 (홈/뒤로가기/메뉴)
    // ─────────────────────────────────────────────

    /**
     * 홈 버튼 / 최근 앱(메뉴) 버튼 감지를 위한 BroadcastReceiver를 등록한다.
     * 버튼 감지 시 즉시 dismiss하지 않고, 300ms 후 포그라운드 앱이
     * 금융 앱인지 재확인한 뒤 금융 앱이 아닐 때만 dismiss한다.
     */
    private fun registerSystemButtonReceiver() {
        if (closeSystemDialogsReceiver != null) return

        closeSystemDialogsReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                    Log.d(TAG, "🏠 홈/최근앱 버튼 감지 → 오버레이 즉시 해제")
                    dismiss()
                }
            }
        }
        try {
            context.registerReceiver(
                closeSystemDialogsReceiver,
                IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                Context.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            try {
                context.registerReceiver(
                    closeSystemDialogsReceiver,
                    IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                )
            } catch (e2: Exception) {
                Log.e(TAG, "리시버 등록 실패", e2)
            }
        }
    }

    private fun unregisterSystemButtonReceiver() {
        closeSystemDialogsReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        closeSystemDialogsReceiver = null
    }

    /**
     * 300ms 딜레이 후 현재 포그라운드 앱이 금융 앱이 아니면 오버레이를 해제한다.
     *
     * 홈/뒤로가기/메뉴 버튼을 누르면, 실제 포그라운드 앱이 전환되기까지
     * 약 100~300ms 정도 걸린다. 따라서 즉시 확인하면 아직 금융 앱이
     * 포그라운드일 수 있으므로, 짧은 딜레이 후 재확인한다.
     *
     * - 금융 앱 아님 → dismiss (빠른 반응)
     * - 여전히 금융 앱 → 유지 (기존 observeForegroundIsFinancialApp에 위임)
     */
    private fun dismissIfNotFinancialAppDelayed() {
        handler.postDelayed({
            if (!isShowing()) return@postDelayed

            val currentPkg = ForegroundAppMonitorService.foregroundPackage.value
            val isStillFinancial = currentPkg in FINANCIAL_APP_PACKAGES
            Log.d(TAG, "⏱ 지연 확인: 현재 포그라운드=$currentPkg, 금융앱=$isStillFinancial")

            if (!isStillFinancial) {
                Log.d(TAG, "✅ 금융 앱 이탈 확인 → 오버레이 즉시 해제")
                dismiss()
            } else {
                Log.d(TAG, "↳ 아직 금융 앱 포그라운드 — 오버레이 유지")
            }
        }, BUTTON_DISMISS_DELAY_MS)
    }

    // ─────────────────────────────────────────────
    // Utils
    // ─────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()

    companion object {
        private const val TAG = "FinancialBlockOverlay"

        /** 시스템 버튼 감지 후 포그라운드 앱 재확인까지 대기 시간 */
        private const val BUTTON_DISMISS_DELAY_MS = 50L

        /**
         * 금융 앱 패키지 목록 (VideoCallRepositoryImpl과 동일).
         * dismissIfNotFinancialAppDelayed()에서 직접 참조.
         */
        private val FINANCIAL_APP_PACKAGES = setOf(
            // ── 은행 ──
            "com.kbstar.kbbank",
            "com.shinhan.sbanking",
            "com.wooribank.smart.npib",
            "com.kebhana.hanapush",
            "nh.smart.banking",
            "com.ibk.neobanking",
            "com.kdb.talk",
            "com.epost.psf.sdsi",
            "com.sc.danb",
            "com.citi.android.citibank.kr",
            "com.bnk.busanbank",
            "com.bnk.cbank",
            "com.knb.psb",
            "com.jeonbukbank.jbm",
            "kr.co.kwangju.bank",
            "com.jeju.jbank",
            "com.suhyup.mbanking",
            // ── 인터넷/디지털 뱅크 ──
            "com.kakaobank.channel",
            "com.kbanknow",
            "viva.republica.toss",
            // ── 증권 ──
            "com.kiwoom.hero",
            "com.kiwoom.herots",
            "com.samsung.android.samsungpop",
            "com.mirae.mstock",
            "com.daishin.cybos",
            "com.nhqv.namuh",
            "com.shinhan.smartstock",
            "com.kbsec.mstocku",
            "com.hanwha.investment",
            "com.hana.fnc.securities",
            // ── 보험 ──
            "com.samsung.life.samsunglife",
            "kr.co.hanwhalife",
            "com.kyobo.kcpi",
            "com.samsung.android.samsungfire",
            "com.dbins.smart",
            "com.kbinsure.smart",
            // ── 카드 ──
            "com.shinhancard.smartshinhan",
            "com.samsung.android.samsungcard",
            "com.kbcard.cxh.appcard",
            "com.hyundaicard.appcard",
            "com.lottecard.app",
            "com.wooricard.smartapp",
            "com.hanacard.smart",
            "kr.co.bccard.bcsmartpay",
            // ── 간편결제/핀테크 ──
            "com.nhn.android.search",
            "com.kakaopay.app",
            "com.nhnent.payapp",
        )
    }
}

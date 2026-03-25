package com.s14p21a404.shutter.presentation.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 하단에서 슬라이드업 되는 화이트 바텀시트 감지 결과 모달.
 *
 * 레퍼런스: ai-detection-modal (page.tsx)
 *
 * ── isInApp = true (자체앱 통화) ──
 * - 흰색 rounded-top-3xl 바텀시트 (bounce slide-up)
 * - "AI 위조 감지" 타이틀 (센터, bold)
 * - 세계 지도 + 위치 핀(⚠ + pulse) + 국가명 + 국기
 * - 대화 감지 유형 ("계좌 이체 요구" + $ 아이콘)
 * - "전화 종료" 빨간 버튼
 *
 * ── isInApp = false (외부앱: 페이스톡/통신사 전화) ──
 * - "AI 위조 감지" 타이틀 + "전화 종료" 버튼만 표시
 */
class WarningBottomSheetView(
    context: Context,
    private val isInApp: Boolean = false,
    private val onEndCall: (() -> Unit)? = null,
    private val onRescan: (() -> Unit)? = null,
    private val onClose: (() -> Unit)? = null,
    private val analysisResult: String? = null
) : FrameLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private val pulseAnimators = mutableListOf<AnimatorSet>()

    // 애니메이션 대상 뷰들
    private val sheetContainer: LinearLayout
    private val sheetFrame: FrameLayout
    private val titleContainer: View // 타이틀 (외부앱: 아이콘+텍스트 row / 인앱: 텍스트만)
    private var mapContainer: FrameLayout? = null
    private var detectionTypeRow: LinearLayout? = null
    private var countryNameTv: TextView? = null
    private var labelContainer: LinearLayout? = null
    private lateinit var subtitleContainer: LinearLayout
    private val buttonsContainer: LinearLayout

    /** 현재 표시 중인 국가명 */
    private var currentCountryName: String = "위치 확인 중..."

    init {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false

        // ── 바텀시트 컨테이너 ──
        sheetContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false

            val radius = dpToPx(24).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
        }

        // ── 타이틀 영역 ──
        if (isInApp) {
            // 인앱: "AI 위조 감지" 텍스트만
            val tv = TextView(context).apply {
                text = "AI 위조 감지"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(Color.parseColor("#111827"))
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.CENTER
                alpha = 0f
                translationY = dpToPx(24).toFloat()
            }
            titleContainer = tv
            sheetContainer.addView(tv, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(16)
            })
        } else {
            // 외부앱: 위험 아이콘(pulse) + "AI 위조 감지" 텍스트 행
            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
                alpha = 0f
                translationY = dpToPx(24).toFloat()
            }

            // 위험 아이콘 컨테이너 (pulse 원 + ⚠ 아이콘)
            val iconWrap = FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
            }

            // pulse 원 2개
            for (i in 0..1) {
                val pulseCircle = View(context).apply {
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#80EF4444")) // red-500/50%
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

            // 경고 아이콘 원
            val alertCircle = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FEE2E2")) // red-100
                    shape = GradientDrawable.OVAL
                    setStroke(dpToPx(2), Color.parseColor("#FECACA")) // red-200
                }
                elevation = dpToPx(2).toFloat()
            }
            val alertIcon = TextView(context).apply {
                text = "⚠"
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

            // iconWrap 크기를 pulse 최대 크기보다 크게 설정하여 overflow 허용
            val iconWrapSize = dpToPx(56)
            titleRow.addView(iconWrap, LinearLayout.LayoutParams(iconWrapSize, iconWrapSize).apply {
                marginEnd = dpToPx(6)
            })

            // "AI 위조 감지" 텍스트
            val tv = TextView(context).apply {
                text = "AI 위조 감지"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(Color.parseColor("#111827"))
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }
            titleRow.addView(tv)

            titleContainer = titleRow
            sheetContainer.addView(titleRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(24)
            })
        }

        // ── 인앱 전용: 세계 지도 + 핀 ──
        if (isInApp) {
            buildMapSection()
            buildDetectionTypeSection()
        }

        // GMS 분석 결과 추가
        if (!analysisResult.isNullOrBlank()) {
            subtitleContainer.addView(TextView(context).apply {
                text = "#$analysisResult"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.parseColor("#9CA3AF")) // gray-400
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(8) })
        }

        sheetContainer.addView(subtitleContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dpToPx(24)
        })

        // ── 버튼 ──
        buttonsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            alpha = 0f
            translationY = dpToPx(16).toFloat()
        }

        // "전화 종료" 버튼 (red)
        val endCallBtn = TextView(context).apply {
            text = "전화 종료"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            val r = dpToPx(12).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EF4444"))
                cornerRadius = r
            }
            setPadding(0, dpToPx(14), 0, dpToPx(14))
            setOnClickListener { onEndCall?.invoke() }
        }
        buttonsContainer.addView(endCallBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        sheetContainer.addView(buttonsContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // sheetFrame 전체에 slide-up 애니메이션
        sheetFrame = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            translationY = dpToPx(400).toFloat()
            alpha = 0f
        }
        sheetFrame.addView(sheetContainer, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ))

        addView(sheetFrame, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM })
    }

    // ─────────────────────────────────────────────
    // 인앱 전용 UI 빌드
    // ─────────────────────────────────────────────

    /**
     * 세계 지도 + 경고 핀 + 국가명/국기
     */
    private fun buildMapSection() {
        mapContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            alpha = 0f
            translationY = dpToPx(16).toFloat()
        }

        // 지도 View (Canvas로 대륙 그리기)
        val worldMap = WorldMapView(context)
        mapContainer!!.addView(worldMap, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dpToPx(160)
        ))

        // 핀 컨테이너 (지도 위 동남아/캄보디아 부근)
        val pinContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        // pulse circles (2개)
        for (i in 0..1) {
            val pulseCircle = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#80EF4444")) // red-500/50%
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
                setColor(Color.parseColor("#FEE2E2")) // red-100
                shape = GradientDrawable.OVAL
                setStroke(dpToPx(2), Color.parseColor("#FECACA")) // red-200
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

        // 핀 배치: 지도 상의 left ~28%, top ~50% (동남아 위치)
        val pinWrapSize = dpToPx(80)
        val mapHeight = dpToPx(160)
        mapContainer!!.addView(pinContainer, FrameLayout.LayoutParams(pinWrapSize, pinWrapSize).apply {
            leftMargin = dpToPx(80)
            topMargin = (mapHeight * 0.35f).toInt()
        })

        // 국가명 라벨 (핀 아래)
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
            text = currentCountryName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#374151"))
        }
        labelContainer!!.addView(countryNameTv)

        mapContainer!!.addView(labelContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dpToPx(70)
            topMargin = (mapHeight * 0.35f).toInt() + pinWrapSize - dpToPx(6)
        })

        sheetContainer.addView(mapContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dpToPx(8)
        })
    }

    /**
     * 대화 감지 유형 ("계좌 이체 요구" + $ 아이콘)
     */
    private fun buildDetectionTypeSection() {
        detectionTypeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = dpToPx(16).toFloat()
        }

        val typeLabelTv = TextView(context).apply {
            text = "계좌 이체 요구"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#374151"))
        }
        detectionTypeRow!!.addView(typeLabelTv)

        // $ 아이콘 (원형 회색 배경)
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
        val dollarLp = LinearLayout.LayoutParams(dollarSize, dollarSize).apply {
            leftMargin = dpToPx(8)
        }
        detectionTypeRow!!.addView(dollarBg, dollarLp)

        sheetContainer.addView(detectionTypeRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dpToPx(24)
        })
    }

    // ─────────────────────────────────────────────
    // 국가명 업데이트
    // ─────────────────────────────────────────────

    /**
     * 국가명을 업데이트한다. FCM 등으로 위치 데이터가 수신되면 호출.
     */
    fun updateCountry(country: String) {
        currentCountryName = country
        handler.post {
            countryNameTv?.text = country
        }
    }

    // ─────────────────────────────────────────────
    // 애니메이션
    // ─────────────────────────────────────────────

    fun startEntranceAnimation() {
        // 1) 바텀시트 slide-up with bounce
        handler.postDelayed({
            sheetFrame.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(600)
                .setInterpolator(OvershootInterpolator(0.8f))
                .start()
        }, 50)

        // 2) 타이틀 fade-in (+ 외부앱: pulse 시작)
        handler.postDelayed({
            titleContainer.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(OvershootInterpolator(1.0f))
                .start()
            if (!isInApp) {
                // 외부앱: 타이틀 안의 pulse 애니메이션 시작
                pulseAnimators.forEach { it.start() }
            }
        }, 250)

        if (isInApp) {
            // 3) 지도 fade-in + pulse
            handler.postDelayed({
                mapContainer?.animate()
                    ?.translationY(0f)
                    ?.alpha(1f)
                    ?.setDuration(500)
                    ?.setInterpolator(OvershootInterpolator(1.0f))
                    ?.start()
                pulseAnimators.forEach { it.start() }
            }, 400)

            // 4) 감지 유형 fade-in
            handler.postDelayed({
                detectionTypeRow?.animate()
                    ?.translationY(0f)
                    ?.alpha(1f)
                    ?.setDuration(500)
                    ?.setInterpolator(OvershootInterpolator(1.0f))
                    ?.start()
            }, 550)

            // 5) 버튼 fade-in
            handler.postDelayed({
                buttonsContainer.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(500)
                    .setInterpolator(OvershootInterpolator(1.0f))
                    .start()
            }, 700)
        } else {
            // 외부앱: 바로 버튼 fade-in
            handler.postDelayed({
                buttonsContainer.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(500)
                    .setInterpolator(OvershootInterpolator(1.0f))
                    .start()
            }, 400)
        }
    }

    fun stopAnimations() {
        handler.removeCallbacksAndMessages(null)
        pulseAnimators.forEach { it.cancel() }
    }

    fun setAnalysisResult(result: String) {
        if (result.isNotBlank()) {
            // 기존 GMS 결과 제거 (있으면)
            for (i in subtitleContainer.childCount - 1 downTo 0) {
                val child = subtitleContainer.getChildAt(i)
                if (child.tag == "gms_result") {
                    subtitleContainer.removeViewAt(i)
                }
            }
            
            // 새로운 GMS 결과 추가
            subtitleContainer.addView(TextView(context).apply {
                text = "감지된 의도: $result"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.parseColor("#9CA3AF")) // gray-400
                gravity = Gravity.CENTER
                tag = "gms_result"
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(8) })
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimations()
    }

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()

    // ─────────────────────────────────────────────
    // 내부 뷰: 세계 지도
    // ─────────────────────────────────────────────

    /**
     * 레퍼런스의 WorldMap SVG를 Canvas path로 그리는 커스텀 View.
     * viewBox="0 0 400 200" 기준 7개 대륙.
     */
    private class WorldMapView(context: Context) : View(context) {
        private val mapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E5E7EB") // gray-200
            style = Paint.Style.FILL
        }

        private val paths = mutableListOf<Path>()

        init {
            paths.add(parseSvgPath("M50,40 Q80,30 100,45 L120,35 Q140,40 130,60 L110,80 Q90,90 70,85 L50,70 Q40,55 50,40"))
            paths.add(parseSvgPath("M80,95 Q100,90 110,100 L115,130 Q110,150 95,160 L85,155 Q75,140 80,120 L75,105 Q75,95 80,95"))
            paths.add(parseSvgPath("M175,35 Q190,30 200,35 L210,40 Q220,35 215,50 L205,55 Q195,60 185,55 L175,45 Q170,40 175,35"))
            paths.add(parseSvgPath("M175,65 Q195,60 210,70 L215,100 Q210,130 195,140 L180,135 Q165,120 170,95 L175,65"))
            paths.add(parseSvgPath("M220,30 Q260,25 300,35 L340,40 Q360,50 350,70 L320,85 Q280,95 250,85 L230,70 Q210,55 220,30"))
            paths.add(parseSvgPath("M310,120 Q340,115 355,130 L350,150 Q335,160 315,155 L305,140 Q300,125 310,120"))
            paths.add(parseSvgPath("M280,95 Q300,90 320,95 L330,105 Q325,115 305,115 L290,110 Q275,105 280,95"))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            val sx = w / 400f
            val sy = h / 200f

            canvas.save()
            canvas.scale(sx, sy)
            for (p in paths) {
                canvas.drawPath(p, mapPaint)
            }
            canvas.restore()
        }

        private fun parseSvgPath(svg: String): Path {
            val path = Path()
            val regex = Regex("([MLQZ])([^MLQZ]*)", RegexOption.IGNORE_CASE)
            for (match in regex.findAll(svg)) {
                val cmd = match.groupValues[1]
                val coords = match.groupValues[2].trim()
                    .split("[,\\s]+".toRegex())
                    .filter { it.isNotEmpty() }
                    .map { it.toFloat() }

                when (cmd.uppercase()) {
                    "M" -> if (coords.size >= 2) path.moveTo(coords[0], coords[1])
                    "L" -> if (coords.size >= 2) path.lineTo(coords[0], coords[1])
                    "Q" -> if (coords.size >= 4) path.quadTo(coords[0], coords[1], coords[2], coords[3])
                    "Z" -> path.close()
                }
            }
            return path
        }
    }

    // ─────────────────────────────────────────────
    // 내부 뷰: 캄보디아 국기
    // ─────────────────────────────────────────────

    /**
     * 캄보디아 국기: 파랑/빨강/파랑 3줄 + 중앙 흰색 앙코르와트 실루엣(간략)
     */
    private class CambodiaFlagView(context: Context) : View(context) {
        private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#032EA1"); style = Paint.Style.FILL
        }
        private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E00025"); style = Paint.Style.FILL
        }
        private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.FILL
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D1D5DB"); style = Paint.Style.STROKE; strokeWidth = 1f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            canvas.drawRect(0f, 0f, w, h * 0.28f, bluePaint)
            canvas.drawRect(0f, h * 0.28f, w, h * 0.72f, redPaint)
            canvas.drawRect(0f, h * 0.72f, w, h, bluePaint)

            // 간략 앙코르와트 (흰 삼각형)
            val cx = w / 2f; val cy = h / 2f
            val triW = w * 0.2f; val triH = h * 0.25f
            val triPath = Path().apply {
                moveTo(cx, cy - triH)
                lineTo(cx - triW, cy + triH * 0.5f)
                lineTo(cx + triW, cy + triH * 0.5f)
                close()
            }
            canvas.drawPath(triPath, whitePaint)
            canvas.drawRoundRect(0f, 0f, w, h, 2f, 2f, borderPaint)
        }
    }
}

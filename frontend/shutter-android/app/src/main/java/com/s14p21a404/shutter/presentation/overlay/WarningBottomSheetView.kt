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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

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
    private var pinContainerRef: FrameLayout? = null
    private var detectionTypeRow: LinearLayout? = null
    private var countryNameTv: TextView? = null
    private var flagImageView: ImageView? = null
    private var labelContainer: LinearLayout? = null
    private var typeLabelTv: TextView? = null
    private val buttonsContainer: LinearLayout

    /** 중복된 GMS 결과를 합치거나 필터링하기 위한 내부 Set */
    private val gmsResultSet = mutableSetOf<String>()

    /** 현재 표시 중인 국가명 */
    private var currentCountryName: String = "위치 확인 중..."

    init {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false

        if (!analysisResult.isNullOrBlank()) {
            val tags = analysisResult.split(" ").map { it.trim() }.filter { it.isNotBlank() }
            for (tag in tags) {
                if (tag != "#일상대화" && tag != "#일상 대화") {
                    gmsResultSet.remove("#일상대화")
                    gmsResultSet.remove("#일상 대화")
                    gmsResultSet.add(tag)
                } else if (gmsResultSet.isEmpty()) {
                    gmsResultSet.add(tag)
                }
            }
        }

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
            // 인앱: "AI 위조 감지" 텍스트 + X 닫기 버튼
            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                alpha = 0f
                translationY = dpToPx(24).toFloat()
            }

            // 왼쪽 spacer (X 버튼과 균형 맞추기 위해)
            val spacer = View(context)
            titleRow.addView(spacer, LinearLayout.LayoutParams(dpToPx(26), dpToPx(26)))

            val tv = TextView(context).apply {
                text = "AI 위조 감지"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(Color.parseColor("#111827"))
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            titleRow.addView(tv, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ))

            // X 닫기 버튼 (재탐지)
            val closeBtn = TextView(context).apply {
                text = "✕"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.parseColor("#9CA3AF"))
                gravity = Gravity.CENTER
                val btnBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#F3F4F6"))
                    shape = GradientDrawable.OVAL
                }
                background = btnBg
                setOnClickListener { onRescan?.invoke() }
            }
            titleRow.addView(closeBtn, LinearLayout.LayoutParams(dpToPx(26), dpToPx(26)))

            titleContainer = titleRow
            sheetContainer.addView(titleRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
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

            // 왼쪽 spacer (X 버튼과 균형 맞추되 아이콘 폭 감안해 좁게)
            val spacer = View(context)
            titleRow.addView(spacer, LinearLayout.LayoutParams(dpToPx(10), dpToPx(26)))

            // 가운데 영역: 아이콘 + 텍스트를 감싸는 컨테이너
            val centerContent = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
            }
            centerContent.addView(iconWrap, LinearLayout.LayoutParams(iconWrapSize, iconWrapSize).apply {
                marginEnd = dpToPx(6)
            })

            // "AI 위조 감지" 텍스트
            val tv = TextView(context).apply {
                text = "AI 위조 감지"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(Color.parseColor("#111827"))
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }
            centerContent.addView(tv)

            titleRow.addView(centerContent, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { gravity = Gravity.CENTER_VERTICAL })

            // X 닫기 버튼 (재탐지)
            val closeBtn = TextView(context).apply {
                text = "✕"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.parseColor("#9CA3AF"))
                gravity = Gravity.CENTER
                val btnBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#F3F4F6"))
                    shape = GradientDrawable.OVAL
                }
                background = btnBg
                setOnClickListener { onRescan?.invoke() }
            }
            titleRow.addView(closeBtn, LinearLayout.LayoutParams(dpToPx(26), dpToPx(26)))

            titleContainer = titleRow
            sheetContainer.addView(titleRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
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

        // 지도 View (res/drawable/map.png 이미지 사용)
        val worldMap = ImageView(context).apply {
            setImageResource(context.resources.getIdentifier("map", "drawable", context.packageName))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        mapContainer!!.addView(worldMap, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dpToPx(160)
        ))

        // 핀 컨테이너 (좌표 수신 전: 동남아 기본 위치 → 수신 후: 실제 좌표로 이동)
        val pinContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        pinContainerRef = pinContainer

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
            text = "위치 확인 중..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#374151"))
        }
        labelContainer!!.addView(countryNameTv)

        // 국기 이미지 — 국가명 오른쪽 (flagcdn CDN + Glide)
        flagImageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE // 국가코드 수신 전까지 숨김
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

        typeLabelTv = TextView(context).apply {
            text = if (gmsResultSet.isNotEmpty()) {
                gmsResultSet.joinToString(" ")
            } else {
                "#AI위조영상통화"
            }
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
     * 국가명을 그대로 표시하고, 내부에서 국가코드로 변환하여 국기 이미지를 로드한다.
     */
    fun updateCountry(country: String) {
        currentCountryName = country
        handler.post {
            countryNameTv?.text = country

            // 국가명 → 소문자 ISO 코드 변환 후 flagcdn CDN으로 국기 로드
            val code = countryNameToCode(country)
            if (code != null) {
                flagImageView?.let { iv ->
                    iv.visibility = View.VISIBLE
                    val flagUrl = "https://flagcdn.com/w40/${code}.png"
                    Glide.with(iv.context)
                        .load(flagUrl)
                        .transform(RoundedCorners(dpToPx(2)))
                        .into(iv)
                }
            }
        }
    }

    /**
     * lat/lng 좌표를 지도 이미지 위 픽셀 위치로 변환하여 핀을 이동한다.
     * 등장방형(equirectangular) 투영 기준으로 계산하며,
     * mapContainer 실제 크기를 post{} 로 읽어 적용한다.
     */
    fun updatePinPosition(lat: Double, lng: Double) {
        handler.post {
            val mc = mapContainer ?: return@post
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

    /** 한국어 국가명 → ISO 3166-1 alpha-2 소문자 코드 매핑 (국기 로딩용) */
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
            // 여러 해시태그가 띄어쓰기로 분리되어 옴
            val tags = result.split(" ").map { it.trim() }.filter { it.isNotBlank() }
            
            for (tag in tags) {
                if (tag != "#일상대화" && tag != "#일상 대화") {
                    // 위험 태그가 들어왔으므로 기존의 "일상대화"는 즉시 삭제
                    gmsResultSet.remove("#일상 대화")
                    gmsResultSet.remove("#일상대화")
                    gmsResultSet.add(tag)
                } else if (gmsResultSet.isEmpty()) {
                    // 들어온 태그가 "일상대화"뿐이고, 아직 위험 태그가 하나도 없을 때만 추가
                    gmsResultSet.add(tag)
                }
            }

            typeLabelTv?.text = gmsResultSet.joinToString(" ")
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


}

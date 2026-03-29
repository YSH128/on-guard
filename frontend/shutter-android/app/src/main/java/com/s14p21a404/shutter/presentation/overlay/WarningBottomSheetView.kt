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
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
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
 * 단일 BottomSheet 안에서 모든 단계를 순차 진행하며,
 * 각 단계마다 풍부한 콘텐츠 애니메이션을 표시한다.
 *
 * ── 단계 흐름 ──
 * Stage 1: 지도 + "상대방 위치 추적 중…" 깜빡이는 dot
 * Stage 2: 카카오톡 메시지 버블 + 전송 dots → 전송 완료
 * Stage 3: 미니맵 + 핀 드롭 spring + pulse rings → GPS 공유 완료
 * Stage 4: 금융앱 4개 그리드 + 잠금 오버레이 → 차단 활성화
 * Stage 5: "모든 보호 조치 완료" merge + 체크 아이콘
 */
class WarningBottomSheetView(
    context: Context,
    private val isInApp: Boolean = false,
    private val onEndCall: (() -> Unit)? = null,
    private val onRescan: (() -> Unit)? = null,
    private val onClose: (() -> Unit)? = null,
    private val analysisResult: String? = null,
    private val isRestoredFromCache: Boolean = false
) : FrameLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private val pulseAnimators = mutableListOf<AnimatorSet>()

    // ── 레이아웃 뷰 ──
    private val sheetContainer: LinearLayout
    private val sheetFrame: FrameLayout

    // 타이틀
    private val titleContainer: LinearLayout
    private lateinit var statusTextView: TextView

    // 완료 pill 영역
    private val completedPillContainer: LinearLayout
    private var pillScrollView: android.widget.HorizontalScrollView? = null

    // 지도 (Stage 1)
    private var mapContainer: FrameLayout? = null
    private var pinContainerRef: FrameLayout? = null
    private var pinCircleRef: FrameLayout? = null
    private var pinIconTv: TextView? = null
    private var countryNameTv: TextView? = null
    private var flagImageView: ImageView? = null
    private var labelContainer: LinearLayout? = null
    private var locationTrackingRow: LinearLayout? = null


    // 단계별 콘텐츠 영역 (Stage 2~4가 교대로 표시)
    private val stageContentContainer: FrameLayout

    // 보호 조치 영역 (pill + stage content wrapper)
    private val protectionContainer: LinearLayout

    // 버튼
    private val buttonsContainer: LinearLayout

    // 최종 merge pill
    private var finalMergePill: LinearLayout? = null

    /** 중복된 GMS 결과를 합치거나 필터링하기 위한 내부 Set */
    private val gmsResultSet = mutableSetOf<String>()

    /** 현재 표시 중인 국가명 */
    private var currentCountryName: String = "위치 확인 중..."

    /** 지도 UI 빌드 전에 범죄자 위치 데이터가 먼저 도착한 경우 임시 보관 */
    private var bufferedCriminalCountry: String? = null
    private var bufferedCriminalLat: Double? = null
    private var bufferedCriminalLng: Double? = null


    // 카드 pulse 애니메이터
    private val miscAnimators = mutableListOf<ObjectAnimator>()

    // pill 설정
    private data class PillConfig(val label: String, val color: String)
    private val pillConfigs = listOf(
        PillConfig("AI 위조 감지", "#EF4444"),
        PillConfig("보호자 알림 완료", "#6AAFED"),
        PillConfig("내 위치 공유 완료", "#79D888"),
        PillConfig("금융 차단 활성화", "#EB5545")
    )

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
            gravity = Gravity.START
            clipChildren = false
            clipToPadding = false
            val radius = dpToPx(24).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
            setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(24))
        }

        // ── 드래그 핸들 ──
        val handleContainer = LinearLayout(context).apply { gravity = Gravity.CENTER_HORIZONTAL }
        handleContainer.addView(View(context).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#D1D5DB")); cornerRadius = dpToPx(2).toFloat() }
        }, LinearLayout.LayoutParams(dpToPx(40), dpToPx(4)))
        sheetContainer.addView(handleContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(10) })

        // ══════════════════════════════════════
        // 1. 타이틀 영역
        // ══════════════════════════════════════
        titleContainer = buildTitleSection()
        sheetContainer.addView(titleContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(10) })


        // ══════════════════════════════════════
        // 2. 완료 pill 영역
        // ══════════════════════════════════════
        completedPillContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val hScroll = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false; visibility = View.GONE
            addView(completedPillContainer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
        pillScrollView = hScroll
        sheetContainer.addView(hScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(10) })

        // ══════════════════════════════════════
        // 3. 단계별 콘텐츠 영역
        // ══════════════════════════════════════
        protectionContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }

        // 단계별 콘텐츠 프레임 (Stage 1~4 콘텐츠가 교대로 들어감)
        stageContentContainer = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
        protectionContainer.addView(stageContentContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        sheetContainer.addView(protectionContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(12) })

        // ══════════════════════════════════════
        // 4. 버튼 영역 (마지막 단계 전까지 숨김)
        // ══════════════════════════════════════
        buttonsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            alpha = 0f; translationY = dpToPx(16).toFloat()
        }
        buttonsContainer.addView(View(context).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#F3F4F6")) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)).apply { bottomMargin = dpToPx(16) })
        buttonsContainer.addView(TextView(context).apply {
            text = "전화 종료"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(Color.parseColor("#EF4444")); cornerRadius = dpToPx(16).toFloat() }
            setPadding(0, dpToPx(16), 0, dpToPx(16)); elevation = dpToPx(4).toFloat()
            setOnClickListener { onEndCall?.invoke() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        sheetContainer.addView(buttonsContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // ── sheetFrame ──
        sheetFrame = FrameLayout(context).apply {
            clipChildren = false; clipToPadding = false
            translationY = dpToPx(200).toFloat(); alpha = 0f
        }
        sheetFrame.addView(sheetContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(sheetFrame, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM })

        if (isRestoredFromCache) { showFinalStateImmediately() }
    }

    // ─────────────────────────────────────────────
    // 타이틀 빌드
    // ─────────────────────────────────────────────

    private fun buildTitleSection(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            alpha = 0f; translationY = dpToPx(16).toFloat()
        }

        // 위험 아이콘 (pulse + ⚠)
        val iconWrap = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
        for (i in 0..1) {
            val pc = View(context).apply {
                background = GradientDrawable().apply { setColor(Color.parseColor("#80EF4444")); shape = GradientDrawable.OVAL }; alpha = 0f
            }
            iconWrap.addView(pc, FrameLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply { gravity = Gravity.CENTER })
            pulseAnimators.add(AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(pc, "scaleX", 1f, 2.2f).apply { duration = if (i == 0) 1500L else 2000L; repeatCount = ValueAnimator.INFINITE; startDelay = (i * 300).toLong() },
                    ObjectAnimator.ofFloat(pc, "scaleY", 1f, 2.2f).apply { duration = if (i == 0) 1500L else 2000L; repeatCount = ValueAnimator.INFINITE; startDelay = (i * 300).toLong() },
                    ObjectAnimator.ofFloat(pc, "alpha", 0.7f, 0f).apply { duration = if (i == 0) 1500L else 2000L; repeatCount = ValueAnimator.INFINITE; startDelay = (i * 300).toLong() }
                )
            })
        }
        val circle = FrameLayout(context).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#FEE2E2")); shape = GradientDrawable.OVAL; setStroke(dpToPx(1), Color.parseColor("#FECACA")) }
            elevation = dpToPx(1).toFloat()
        }
        circle.addView(TextView(context).apply { text = "⚠"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); setTextColor(Color.parseColor("#EF4444")); gravity = Gravity.CENTER },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        iconWrap.addView(circle, FrameLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply { gravity = Gravity.CENTER })
        row.addView(iconWrap, LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)).apply { marginEnd = dpToPx(10) })

        // 텍스트
        val textArea = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        textArea.addView(TextView(context).apply {
            text = "AI 위조 감지"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setTextColor(Color.parseColor("#111827"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })
        statusTextView = TextView(context).apply {
            text = if (isRestoredFromCache) "보호 조치 완료" else "보호 조치 진행 중..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(if (isRestoredFromCache) Color.parseColor("#059669") else Color.parseColor("#6B7280"))
        }
        textArea.addView(statusTextView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpToPx(2) })
        row.addView(textArea, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // X 닫기
        row.addView(TextView(context).apply {
            text = "✕"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(Color.parseColor("#9CA3AF")); gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(Color.parseColor("#F3F4F6")); shape = GradientDrawable.OVAL }
            setOnClickListener { onRescan?.invoke() }
        }, LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)))

        return row
    }

    // ─────────────────────────────────────────────
    // 지도 섹션 (Stage 1)
    // ─────────────────────────────────────────────

    /** 하단 추적 상태 텍스트 참조 (상대방 위치 추적 중... → 상대방 위치 추적 완료) */
    private var trackingStatusTv: TextView? = null
    private var trackingRedDot: View? = null
    private var trackingPulseAnim: ObjectAnimator? = null
    private var locationResolved = false
    @Volatile
    private var locationDataReceived = false
    /** 지도가 화면에 완전히 표시되었는지 여부 */
    private var mapFullyShown = false
    /** Stage 2 이후 진행을 지연/재스케줄하기 위한 참조 */
    private var nextStagesRunnable: Runnable? = null

    private fun buildMapContent(): View {
        android.util.Log.d("WarningSheet", "▶▶▶ buildMapContent() 호출 — 지도 UI 빌드 시작")
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            alpha = 0f; translationY = dpToPx(20).toFloat()
        }

        mapContainer = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
        android.util.Log.d("WarningSheet", "  mapContainer 생성 완료: $mapContainer")

        // 지도 이미지 추가
        val mapResId = context.resources.getIdentifier("map", "drawable", context.packageName)
        android.util.Log.d("WarningSheet", "  지도 리소스 ID: $mapResId (0이면 리소스 없음)")
        val mapImageView = ImageView(context).apply {
            if (mapResId != 0) {
                setImageResource(mapResId)
            } else {
                // 지도 리소스가 없으면 회색 배경으로 대체
                setBackgroundColor(Color.parseColor("#E5E7EB"))
                android.util.Log.w("WarningSheet", "  ⚠️ 지도 리소스(map.drawable)를 찾을 수 없음! 회색 배경으로 대체")
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        mapContainer!!.addView(mapImageView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(140)))

        // 핀 (가운데 고정, 드롭 애니메이션 없음)
        val pinContainer = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
        pinContainerRef = pinContainer
        // pulse rings
        for (i in 0..1) {
            val pc = View(context).apply {
                background = GradientDrawable().apply { setColor(Color.parseColor("#80EF4444")); shape = GradientDrawable.OVAL }; alpha = 0f
            }
            pinContainer.addView(pc, FrameLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply { gravity = Gravity.CENTER })
            pulseAnimators.add(AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(pc, "scaleX", 1f, 2.5f).apply { duration = if (i == 0) 1500L else 2000L; repeatCount = ValueAnimator.INFINITE; startDelay = (i * 300).toLong() },
                    ObjectAnimator.ofFloat(pc, "scaleY", 1f, 2.5f).apply { duration = if (i == 0) 1500L else 2000L; repeatCount = ValueAnimator.INFINITE; startDelay = (i * 300).toLong() },
                    ObjectAnimator.ofFloat(pc, "alpha", 0.8f, 0f).apply { duration = if (i == 0) 1500L else 2000L; repeatCount = ValueAnimator.INFINITE; startDelay = (i * 300).toLong() }
                )
            })
        }
        val pinCircle = FrameLayout(context).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#FEE2E2")); shape = GradientDrawable.OVAL; setStroke(dpToPx(2), Color.parseColor("#FECACA")) }
            elevation = dpToPx(6).toFloat()
            translationZ = dpToPx(8).toFloat()
        }
        pinCircleRef = pinCircle
        val pinIcon = TextView(context).apply { text = "⚠"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setTextColor(Color.parseColor("#EF4444")); gravity = Gravity.CENTER }
        pinIconTv = pinIcon
        pinCircle.addView(pinIcon,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        pinContainer.addView(pinCircle, FrameLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply { gravity = Gravity.CENTER })

        // 핀을 지도 정 가운데에 배치 — elevation 설정하여 지도 이미지 위에 확실히 보이도록
        pinContainer.elevation = dpToPx(6).toFloat()
        pinContainer.translationZ = dpToPx(8).toFloat()
        mapContainer!!.addView(pinContainer, FrameLayout.LayoutParams(dpToPx(80), dpToPx(80)).apply { gravity = Gravity.CENTER })

        // 국가명 라벨 (핀 아래, 가운데 정렬)
        labelContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dpToPx(6).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#F3F4F6")) // 더 연한 테두리
            }
            elevation = dpToPx(1).toFloat()
            translationZ = dpToPx(1).toFloat()
            setPadding(dpToPx(8), dpToPx(5), dpToPx(8), dpToPx(5))
        }
        countryNameTv = TextView(context).apply {
            text = "위치 확인 중..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#1F2937"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        labelContainer!!.addView(countryNameTv)
        flagImageView = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; visibility = View.GONE }
        labelContainer!!.addView(flagImageView, LinearLayout.LayoutParams(dpToPx(20), dpToPx(14)).apply { marginStart = dpToPx(6); gravity = Gravity.CENTER_VERTICAL })
        // 라벨은 핀 하단에서 일정 거리 아래에 고정 (추적 완료 시와 동일한 간격)
        // 핀 중앙 배치 시: 지도높이 160dp, 핀 80dp → 핀 bottom = 120dp, 라벨 top = 110dp
        mapContainer!!.addView(labelContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dpToPx(110) })

        root.addView(mapContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(160)
        ))

        // 하단 상태 row: 빨간점 깜빡임 + "상대방 위치 추적 중..."
        locationTrackingRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, dpToPx(10), 0, dpToPx(4))
        }
        trackingRedDot = View(context).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#EF4444")); shape = GradientDrawable.OVAL }
        }
        locationTrackingRow!!.addView(trackingRedDot, LinearLayout.LayoutParams(dpToPx(6), dpToPx(6)).apply { marginEnd = dpToPx(6); gravity = Gravity.CENTER_VERTICAL })
        trackingStatusTv = TextView(context).apply {
            text = "상대방 위치 추적 중..."; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(Color.parseColor("#6B7280"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        locationTrackingRow!!.addView(trackingStatusTv)
        trackingPulseAnim = ObjectAnimator.ofFloat(locationTrackingRow, "alpha", 0.5f, 1f).apply {
            duration = 1200; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
        }
        miscAnimators.add(trackingPulseAnim!!)

        root.addView(locationTrackingRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))


        return root
    }

    /** 외부에서 위치 데이터가 연결됐을 때 하단 상태를 "상대방 위치 추적 완료"로 전환 */
    private fun markLocationResolved() {
        android.util.Log.d("WarningSheet", "▶▶▶ markLocationResolved() 호출 — locationResolved=$locationResolved, locationDataReceived=$locationDataReceived")
        if (locationResolved) {
            android.util.Log.d("WarningSheet", "  이미 resolved 상태 → 스킵")
            return
        }
        locationResolved = true
        locationDataReceived = true
        android.util.Log.d("WarningSheet", "  → locationResolved=true, locationDataReceived=true 로 설정")
        handler.post {
            android.util.Log.d("WarningSheet", "  handler.post 내부 — 추적 상태 UI 업데이트")
            // 깜빡임 멈추고 완전 불투명
            trackingPulseAnim?.cancel()
            locationTrackingRow?.alpha = 1f
            // 빨간점 → 녹색점
            trackingRedDot?.background = GradientDrawable().apply { setColor(Color.parseColor("#22C55E")); shape = GradientDrawable.OVAL }
            // 텍스트 바로 변경 (애니메이션 없이)
            trackingStatusTv?.text = "상대방 위치 추적 완료"
            trackingStatusTv?.setTextColor(Color.parseColor("#16A34A"))
        }
    }

    /** 서버에서 위치를 못 받았을 때 폴백 UI 표시 */
    private fun markLocationUnavailable() {
        if (locationResolved || locationDataReceived) return
        locationResolved = true
        handler.post {
            // 깜빡임 멈추고 완전 불투명
            trackingPulseAnim?.cancel()
            locationTrackingRow?.alpha = 1f
            // 빨간점 → 회색점
            trackingRedDot?.background = GradientDrawable().apply { setColor(Color.parseColor("#9CA3AF")); shape = GradientDrawable.OVAL }
            // "위치 추적 중..." → "위치 정보 없음"
            trackingStatusTv?.animate()?.alpha(0f)?.setDuration(150)?.withEndAction {
                trackingStatusTv?.text = "위치 정보 없음"
                trackingStatusTv?.setTextColor(Color.parseColor("#9CA3AF"))
                trackingStatusTv?.animate()?.alpha(1f)?.setDuration(200)?.start()
            }?.start()
            // "위치 확인 중..." → "위치 확인 불가"
            countryNameTv?.animate()?.alpha(0f)?.setDuration(150)?.withEndAction {
                countryNameTv?.text = "위치 확인 불가"
                countryNameTv?.setTextColor(Color.parseColor("#9CA3AF"))
                countryNameTv?.animate()?.alpha(1f)?.setDuration(200)?.start()
            }?.start()
            // 국기 placeholder: 🏳
            flagImageView?.visibility = View.GONE
            labelContainer?.let { lc ->
                val placeholder = TextView(context).apply {
                    text = "🏳"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                }
                lc.addView(placeholder, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dpToPx(4); gravity = Gravity.CENTER_VERTICAL })
            }
            // 위험 아이콘 → 회색 "?" placeholder
            pinCircleRef?.background = GradientDrawable().apply {
                setColor(Color.parseColor("#F3F4F6")); shape = GradientDrawable.OVAL
                setStroke(dpToPx(2), Color.parseColor("#D1D5DB"))
            }
            pinIconTv?.text = "?"
            pinIconTv?.setTextColor(Color.parseColor("#9CA3AF"))

            // 폴백 UI 2초 보여주고 다음 단계 진행
            scheduleProceedAfterLocation(3000L)
        }
    }

    /** 지도 빌드 후 타임아웃을 걸어 서버 응답이 없으면 폴백 처리 */
    private fun scheduleLocationTimeout() {
        android.util.Log.d("WarningSheet", "▶▶▶ scheduleLocationTimeout() — 10초 타임아웃 시작")
        handler.postDelayed({
            android.util.Log.d("WarningSheet", "  [타임아웃 만료] locationDataReceived=$locationDataReceived")
            if (!locationDataReceived) {
                android.util.Log.d("WarningSheet", "  → 위치 데이터 미수신 → markLocationUnavailable() 호출")
                markLocationUnavailable()
            } else {
                android.util.Log.d("WarningSheet", "  → 이미 위치 데이터 수신됨 → 무시")
            }
        }, 10_000L)
    }

    /** 위치 UI를 충분히 보여준 뒤 다음 단계로 넘어가도록 지연을 재조정 */
    private fun scheduleProceedAfterLocation(delayMs: Long = 2000L) {
        val caller = Thread.currentThread().stackTrace.getOrNull(3)?.let { "${it.methodName}:${it.lineNumber}" } ?: "unknown"
        android.util.Log.d("WarningSheet", "══════════════════════════════════════════════════════════")
        android.util.Log.d("WarningSheet", "▶▶▶ scheduleProceedAfterLocation(delayMs=$delayMs) — Stage 2로 전환 예약")
        android.util.Log.d("WarningSheet", "  호출 위치: $caller")
        android.util.Log.d("WarningSheet", "  현재 상태: mapFullyShown=$mapFullyShown, nextStagesStarted=$nextStagesStarted, locationResolved=$locationResolved")
        android.util.Log.d("WarningSheet", "  지도 UI 상태: mapContainer=${mapContainer != null}, pinContainerRef=${pinContainerRef != null}, labelContainer=${labelContainer != null}")
        android.util.Log.d("WarningSheet", "  위치 데이터: countryNameTv?.text=${countryNameTv?.text}, flagImageView?.visibility=${flagImageView?.visibility}")
        nextStagesRunnable?.let {
            handler.removeCallbacks(it)
            android.util.Log.d("WarningSheet", "  기존 nextStagesRunnable 제거")
        }
        val runnable = Runnable {
            android.util.Log.d("WarningSheet", "══════════════════════════════════════════════════════════")
            android.util.Log.d("WarningSheet", "  ★★★ [${delayMs}ms 지연 후 실행] proceedToNextStages() 호출 ★★★")
            android.util.Log.d("WarningSheet", "  지도 UI가 곧 사라집니다! mapContainer=${mapContainer != null}")
            proceedToNextStages()
        }
        nextStagesRunnable = runnable
        handler.postDelayed(runnable, delayMs)
        android.util.Log.d("WarningSheet", "  ${delayMs}ms 후 Stage 2 전환 예약 완료")
    }

    // ─────────────────────────────────────────────
    // Stage 2: 카카오톡 메시지 UI
    // ─────────────────────────────────────────────

    private fun buildKakaoTalkContent(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dpToPx(16).toFloat() }
            clipToOutline = true
            elevation = dpToPx(4).toFloat()
            alpha = 0f; translationY = dpToPx(20).toFloat()
        }

        // ── 카카오톡 헤더 (흰 배경 + 하단 그림자) ──
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            elevation = dpToPx(1).toFloat()
        }
        // 노란 TALK pill
        val talkPill = TextView(context).apply {
            text = "TALK"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f); setTextColor(Color.parseColor("#3C1E1E"))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            background = GradientDrawable().apply { setColor(Color.parseColor("#FEE500")); cornerRadius = dpToPx(10).toFloat() }
            setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3)); gravity = Gravity.CENTER
        }
        header.addView(talkPill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dpToPx(8) })
        header.addView(TextView(context).apply {
            text = "카카오톡"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(Color.parseColor("#111827"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })
        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ── 채팅 영역 (#B2C7D9 연한 하늘색 배경) ──
        val chatArea = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#B2C7D9"))
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
        }

        // 수신자 info (파란 아바타 + "보호자에게")
        val recipientRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val avatar = FrameLayout(context).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#5AC8FA")); shape = GradientDrawable.OVAL }
        }
        // 사람 아이콘 (텍스트 이모지 대용)
        avatar.addView(TextView(context).apply {
            text = "👤"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        recipientRow.addView(avatar, LinearLayout.LayoutParams(dpToPx(34), dpToPx(34)).apply { marginEnd = dpToPx(8) })
        recipientRow.addView(TextView(context).apply {
            text = "보호자에게"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(Color.parseColor("#3C1E1E"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })
        chatArea.addView(recipientRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(10) })

        // 메시지 버블 (왼쪽 정렬, ml-11에 해당 = 아바타 폭 만큼 indent)
        val bubbleContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val bubble = TextView(context).apply {
            text = "[ OnGuard: 피싱 통화 중 ]\n\n즉시 연락하여 통화를 종료하도록 안내해 주세요.\n앱에서 현재 위치를 확인하세요."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f); setTextColor(Color.parseColor("#3C1E1E"))
            setLineSpacing(dpToPx(2).toFloat(), 1f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FEE500"))
                val r = dpToPx(14).toFloat(); val s = dpToPx(4).toFloat()
                cornerRadii = floatArrayOf(s, s, r, r, r, r, r, r) // top-left: small (꼬리)
            }
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
            elevation = dpToPx(1).toFloat()
            alpha = 0f; scaleX = 0.9f; scaleY = 0.9f; translationY = dpToPx(10).toFloat()
        }
        bubbleContainer.addView(bubble, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dpToPx(42); marginEnd = dpToPx(20) })
        chatArea.addView(bubbleContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(8) })

        // 전송 상태 row (왼쪽 정렬, 아바타 indent 맞춤)
        val statusRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val dot1 = makeDot(); val dot2 = makeDot(); val dot3 = makeDot()
        statusRow.addView(dot1, LinearLayout.LayoutParams(dpToPx(5), dpToPx(5)).apply { marginEnd = dpToPx(3) })
        statusRow.addView(dot2, LinearLayout.LayoutParams(dpToPx(5), dpToPx(5)).apply { marginEnd = dpToPx(3) })
        statusRow.addView(dot3, LinearLayout.LayoutParams(dpToPx(5), dpToPx(5)).apply { marginEnd = dpToPx(4) })
        val sendingTv = TextView(context).apply {
            text = "전송 중..."; setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); setTextColor(Color.parseColor("#3C1E1E"))
            alpha = 0.7f
        }
        statusRow.addView(sendingTv)

        // 완료 row (초기 숨김)
        val sentRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            alpha = 0f; visibility = View.GONE
        }
        val sentCheckBg = FrameLayout(context).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#15803D")); shape = GradientDrawable.OVAL }
        }
        sentCheckBg.addView(TextView(context).apply { text = "✓"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f); setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        sentRow.addView(sentCheckBg, LinearLayout.LayoutParams(dpToPx(14), dpToPx(14)).apply { marginEnd = dpToPx(4) })
        sentRow.addView(TextView(context).apply {
            text = "전송 완료"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); setTextColor(Color.parseColor("#15803D"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })

        val statusWrapper = FrameLayout(context)
        statusWrapper.addView(statusRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        statusWrapper.addView(sentRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        chatArea.addView(statusWrapper, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dpToPx(42) })

        root.addView(chatArea, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 애니메이션 시퀀스
        root.post {
            // 버블 slide-in
            handler.postDelayed({
                bubble.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(400).setInterpolator(DecelerateInterpolator()).start()
            }, 300)

            // dots 깜빡임
            animateDot(dot1, 0L); animateDot(dot2, 200L); animateDot(dot3, 400L)

            // 1초 후: 전송 중 → 전송 완료
            handler.postDelayed({
                statusRow.animate().alpha(0f).setDuration(200).withEndAction {
                    statusRow.visibility = View.GONE
                    sentRow.visibility = View.VISIBLE
                    sentRow.alpha = 0f; sentRow.scaleX = 0.8f; sentRow.scaleY = 0.8f
                    sentRow.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.2f)).start()
                }.start()
            }, 2000)
        }

        return root
    }

    private fun makeDot(): View = View(context).apply {
        background = GradientDrawable().apply { setColor(Color.parseColor("#9CA3AF")); shape = GradientDrawable.OVAL }
    }

    private fun animateDot(dot: View, delay: Long) {
        val anim = ObjectAnimator.ofFloat(dot, "alpha", 0.3f, 1f).apply {
            duration = 500; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
            startDelay = delay; interpolator = AccelerateDecelerateInterpolator()
        }
        miscAnimators.add(anim); anim.start()
    }

    // ─────────────────────────────────────────────
    // Stage 3: GPS 위치 공유 UI
    // ─────────────────────────────────────────────

    private fun buildGPSContent(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; alpha = 0f; translationY = dpToPx(20).toFloat()
        }

        // 미니맵 (그리드 배경 + 핀)
        val miniMap = FrameLayout(context).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#F3F4F6")); cornerRadius = dpToPx(16).toFloat() }
            clipChildren = false; clipToPadding = false
        }

        // 그리드 패턴 (Canvas로 그린 View)
        val gridView = object : View(context) {
            private val gridPaint = Paint().apply { color = Color.parseColor("#30000000"); strokeWidth = 1f; style = Paint.Style.STROKE }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val step = dpToPx(20).toFloat()
                var x = 0f; while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint); x += step }
                var y = 0f; while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, gridPaint); y += step }
            }
        }
        miniMap.addView(gridView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(140)))

        // 중앙 핀 (spring drop 애니메이션)
        val pinFrame = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }

        // Pulse rings
        for (i in 0..1) {
            val ring = View(context).apply {
                background = GradientDrawable().apply { setColor(Color.parseColor("#50EF4444")); shape = GradientDrawable.OVAL }; alpha = 0f
            }
            pinFrame.addView(ring, FrameLayout.LayoutParams(dpToPx(48), dpToPx(48)).apply { gravity = Gravity.CENTER })
            val ringAnim = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(ring, "scaleX", 1f, 2.2f).apply { duration = 1500; repeatCount = ValueAnimator.INFINITE; startDelay = (i * 300).toLong() },
                    ObjectAnimator.ofFloat(ring, "scaleY", 1f, 2.2f).apply { duration = 1500; repeatCount = ValueAnimator.INFINITE; startDelay = (i * 300).toLong() },
                    ObjectAnimator.ofFloat(ring, "alpha", 0.6f, 0f).apply { duration = 1500; repeatCount = ValueAnimator.INFINITE; startDelay = (i * 300).toLong() }
                )
            }
            pulseAnimators.add(ringAnim)
        }

        // 핀 아이콘
        val pinIcon = FrameLayout(context).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#EF4444")); shape = GradientDrawable.OVAL }
            elevation = dpToPx(4).toFloat()
            translationY = dpToPx(-30).toFloat(); alpha = 0f // 초기: 위에서 떨어짐
        }
        pinIcon.addView(ImageView(context).apply {
            setImageResource(context.resources.getIdentifier("location", "drawable", context.packageName))
            scaleType = ImageView.ScaleType.CENTER_INSIDE; val p = dpToPx(6); setPadding(p, p, p, p)
            setColorFilter(Color.WHITE)
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        pinFrame.addView(pinIcon, FrameLayout.LayoutParams(dpToPx(42), dpToPx(42)).apply { gravity = Gravity.CENTER })

        miniMap.addView(pinFrame, FrameLayout.LayoutParams(dpToPx(80), dpToPx(80)).apply { gravity = Gravity.CENTER })
        root.addView(miniMap, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(140)))

        // 상태 텍스트
        val gpsStatusRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, dpToPx(12), 0, dpToPx(4))
        }
        val gpsTrackingIcon = ImageView(context).apply {
            setImageResource(context.resources.getIdentifier("location", "drawable", context.packageName))
            scaleType = ImageView.ScaleType.CENTER_INSIDE; setColorFilter(Color.parseColor("#6B7280"))
        }
        gpsStatusRow.addView(gpsTrackingIcon, LinearLayout.LayoutParams(dpToPx(16), dpToPx(16)).apply { marginEnd = dpToPx(6); gravity = Gravity.CENTER_VERTICAL })
        val gpsStatusTv = TextView(context).apply {
            text = "내 위치 공유 중..."; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(Color.parseColor("#6B7280"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        gpsStatusRow.addView(gpsStatusTv)

        // 완료 row (초기 숨김)
        val gpsCompleteRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, dpToPx(12), 0, dpToPx(4)); visibility = View.GONE; alpha = 0f
        }
        val gpsCheckBg = FrameLayout(context).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#22C55E")); shape = GradientDrawable.OVAL }
        }
        gpsCheckBg.addView(TextView(context).apply { text = "✓"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f); setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        gpsCompleteRow.addView(gpsCheckBg, LinearLayout.LayoutParams(dpToPx(18), dpToPx(18)).apply { marginEnd = dpToPx(6); gravity = Gravity.CENTER_VERTICAL })
        gpsCompleteRow.addView(TextView(context).apply {
            text = "내 위치 공유 완료"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(Color.parseColor("#16A34A"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })

        val statusWrapper = FrameLayout(context)
        statusWrapper.addView(gpsStatusRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        statusWrapper.addView(gpsCompleteRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addView(statusWrapper)

        // 애니메이션 시퀀스
        root.post {
            // 상태텍스트 깜빡임
            val gpsPulse = ObjectAnimator.ofFloat(gpsStatusRow, "alpha", 0.5f, 1f).apply {
                duration = 800; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
            }
            miscAnimators.add(gpsPulse); gpsPulse.start()

            // 핀 드롭 (spring)
            handler.postDelayed({
                pinIcon.animate().translationY(0f).alpha(1f).setDuration(600).setInterpolator(BounceInterpolator()).start()
                // pulse rings 시작
                pulseAnimators.takeLast(2).forEach { it.start() }
            }, 300)

            // 1.4초 후 완료 전환
            handler.postDelayed({
                gpsPulse.cancel()
                gpsStatusRow.animate().alpha(0f).setDuration(200).withEndAction {
                    gpsStatusRow.visibility = View.GONE
                    gpsCompleteRow.visibility = View.VISIBLE
                    gpsCompleteRow.animate().alpha(1f).setDuration(300).setInterpolator(DecelerateInterpolator()).start()
                }.start()
            }, 1400)
        }

        return root
    }

    // ─────────────────────────────────────────────
    // Stage 4: 금융앱 차단 UI
    // ─────────────────────────────────────────────

    private data class FinApp(val name: String, val color: String)
    private val finApps = listOf(
        FinApp("은행", "#2563EB"), FinApp("증권", "#059669"), FinApp("카드", "#DC2626"), FinApp("페이", "#7C3AED")
    )

    private fun buildFinancialBlockContent(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; alpha = 0f; translationY = dpToPx(20).toFloat()
        }

        // 4개 앱 그리드 (horizontal)
        val gridRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val appViews = mutableListOf<Pair<FrameLayout, FrameLayout>>() // (appTile, lockOverlay)

        finApps.forEachIndexed { idx, app ->
            val appWrapper = FrameLayout(context)

            // 앱 타일
            val appTile = FrameLayout(context).apply {
                background = GradientDrawable().apply { setColor(Color.parseColor(app.color)); cornerRadius = dpToPx(14).toFloat() }
            }
            appTile.addView(TextView(context).apply {
                text = app.name; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(Color.WHITE); gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            appWrapper.addView(appTile, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

            // 잠금 오버레이 (초기 숨김)
            val lockOverlay = FrameLayout(context).apply {
                background = GradientDrawable().apply { setColor(Color.parseColor("#99000000")); cornerRadius = dpToPx(14).toFloat() }
                alpha = 0f; scaleX = 0.5f; scaleY = 0.5f; visibility = View.GONE
            }
            lockOverlay.addView(ImageView(context).apply {
                setImageResource(context.resources.getIdentifier("no", "drawable", context.packageName))
                scaleType = ImageView.ScaleType.CENTER_INSIDE; val p = dpToPx(8); setPadding(p, p, p, p)
                setColorFilter(Color.WHITE)
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            appWrapper.addView(lockOverlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

            appViews.add(appTile to lockOverlay)
            val tileSize = dpToPx(60)
            gridRow.addView(appWrapper, LinearLayout.LayoutParams(tileSize, tileSize).apply {
                if (idx < finApps.size - 1) marginEnd = dpToPx(10)
            })
        }
        root.addView(gridRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dpToPx(12) })

        // 상태 텍스트
        val finStatusRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        val lockIcon = ImageView(context).apply {
            setImageResource(context.resources.getIdentifier("no", "drawable", context.packageName))
            scaleType = ImageView.ScaleType.CENTER_INSIDE; setColorFilter(Color.parseColor("#6B7280"))
        }
        finStatusRow.addView(lockIcon, LinearLayout.LayoutParams(dpToPx(16), dpToPx(16)).apply { marginEnd = dpToPx(6); gravity = Gravity.CENTER_VERTICAL })
        finStatusRow.addView(TextView(context).apply {
            text = "금융 앱 차단 중..."; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(Color.parseColor("#6B7280"))
        })

        // 완료 pill (초기 숨김)
        val finCompletePill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(Color.parseColor("#1AEF4444")); cornerRadius = dpToPx(20).toFloat() }
            setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
            visibility = View.GONE; alpha = 0f
        }
        val finLockIcon = ImageView(context).apply {
            setImageResource(context.resources.getIdentifier("no", "drawable", context.packageName))
            scaleType = ImageView.ScaleType.CENTER_INSIDE; setColorFilter(Color.parseColor("#EF4444"))
        }
        finCompletePill.addView(finLockIcon, LinearLayout.LayoutParams(dpToPx(16), dpToPx(16)).apply { marginEnd = dpToPx(6); gravity = Gravity.CENTER_VERTICAL })
        finCompletePill.addView(TextView(context).apply {
            text = "금융 앱 접근 차단 활성화"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(Color.parseColor("#EF4444"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })

        val statusWrapper = FrameLayout(context)
        statusWrapper.addView(finStatusRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        statusWrapper.addView(finCompletePill, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        root.addView(statusWrapper, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 애니메이션 시퀀스
        root.post {
            val finPulse = ObjectAnimator.ofFloat(finStatusRow, "alpha", 0.5f, 1f).apply {
                duration = 800; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
            }
            miscAnimators.add(finPulse); finPulse.start()

            // 0.9초 후 잠금 오버레이 순차 표시
            handler.postDelayed({
                appViews.forEachIndexed { idx, (tile, lock) ->
                    handler.postDelayed({
                        // 타일 dim
                        tile.animate().alpha(0.3f).scaleX(0.95f).scaleY(0.95f).setDuration(300).start()
                        // 잠금 오버레이 등장
                        lock.visibility = View.VISIBLE
                        lock.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.0f)).start()
                    }, (idx * 150).toLong())
                }

                // 모든 잠금 후 텍스트 전환
                handler.postDelayed({
                    finPulse.cancel()
                    finStatusRow.animate().alpha(0f).setDuration(200).withEndAction {
                        finStatusRow.visibility = View.GONE
                        finCompletePill.visibility = View.VISIBLE
                        finCompletePill.alpha = 0f; finCompletePill.scaleX = 0.8f; finCompletePill.scaleY = 0.8f
                        finCompletePill.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.0f)).start()
                    }.start()
                }, (finApps.size * 150 + 500).toLong())
            }, 900)
        }

        return root
    }

    // ─────────────────────────────────────────────
    // 완료 pill 추가
    // ─────────────────────────────────────────────

    private fun addCompletedPill(label: String, color: String) {
        pillScrollView?.visibility = View.VISIBLE
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#F3F4F6")); cornerRadius = dpToPx(12).toFloat() }
            setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5)); alpha = 0f; scaleX = 0.8f; scaleY = 0.8f
        }
        val cb = FrameLayout(context).apply { background = GradientDrawable().apply { setColor(Color.parseColor(color)); shape = GradientDrawable.OVAL } }
        cb.addView(TextView(context).apply { text = "✓"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f); setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        pill.addView(cb, LinearLayout.LayoutParams(dpToPx(14), dpToPx(14)).apply { marginEnd = dpToPx(4) })
        pill.addView(TextView(context).apply { text = label; setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); setTextColor(Color.parseColor("#6B7280")); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
        completedPillContainer.addView(pill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dpToPx(6) })
        pill.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.2f)).start()
    }

    // ─────────────────────────────────────────────
    // 단계 전환 (이전 콘텐츠 fade-out → 새 콘텐츠 fade-in)
    // ─────────────────────────────────────────────

    private fun transitionStageContent(newContent: View, onShown: (() -> Unit)? = null) {
        android.util.Log.d("WarningSheet", "▶▶▶ transitionStageContent() — Stage 전환 시작")
        val oldCount = stageContentContainer.childCount
        android.util.Log.d("WarningSheet", "  oldCount=$oldCount, newContent=$newContent")
        if (oldCount > 0) {
            val old = stageContentContainer.getChildAt(0)
            android.util.Log.d("WarningSheet", "  이전 콘텐츠 fade-out 시작: $old")
            old.animate().alpha(0f).translationY(dpToPx(-20).toFloat()).scaleX(0.95f).scaleY(0.95f).setDuration(250).withEndAction {
                android.util.Log.d("WarningSheet", "  이전 콘텐츠 제거 완료 → addNewStageContent 호출")
                stageContentContainer.removeView(old)
                addNewStageContent(newContent, onShown)
            }.start()
        } else {
            addNewStageContent(newContent, onShown)
        }
    }

    private fun addNewStageContent(content: View, onShown: (() -> Unit)?) {
        android.util.Log.d("WarningSheet", "▶▶▶ addNewStageContent() — 새 콘텐츠 fade-in 시작: $content")
        content.alpha = 0f; content.translationY = dpToPx(20).toFloat()
        stageContentContainer.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        content.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .withLayer()
            .withEndAction {
                android.util.Log.d("WarningSheet", "  fade-in 완료 → onShown 콜백 호출 (onShown=${onShown != null})")
                onShown?.invoke()
            }
            .start()
    }

    // ─────────────────────────────────────────────
    // 최종 merge
    // ─────────────────────────────────────────────

    private fun showFinalMerge() {
        statusTextView.text = "보호 조치 완료"
        statusTextView.setTextColor(Color.parseColor("#059669"))

        handler.postDelayed({
            for (i in 0 until completedPillContainer.childCount) {
                completedPillContainer.getChildAt(i).animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(300).start()
            }
            handler.postDelayed({
                completedPillContainer.removeAllViews()
                addFinalMergePill()

                // Stage 5 완료 아이콘
                transitionStageContent(buildCompletionView())

                handler.postDelayed({
                    buttonsContainer.visibility = View.VISIBLE
                    buttonsContainer.animate().translationY(0f).alpha(1f).setDuration(400).setInterpolator(DecelerateInterpolator()).start()
                }, 400)
            }, 350)
        }, 100)
    }

    private fun addFinalMergePill() {
        finalMergePill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#F0FDF4")); cornerRadius = dpToPx(14).toFloat(); setStroke(dpToPx(1), Color.parseColor("#BBF7D0")) }
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6)); alpha = 0f; scaleX = 0.8f; scaleY = 0.8f
        }
        val cb = FrameLayout(context).apply { background = GradientDrawable().apply { setColor(Color.parseColor("#22C55E")); shape = GradientDrawable.OVAL } }
        cb.addView(TextView(context).apply { text = "✓"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        finalMergePill!!.addView(cb, LinearLayout.LayoutParams(dpToPx(16), dpToPx(16)).apply { marginEnd = dpToPx(6) })
        finalMergePill!!.addView(TextView(context).apply { text = "모든 보호 조치 완료"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(Color.parseColor("#16A34A")); typeface = Typeface.create("sans-serif-medium", Typeface.BOLD) })
        completedPillContainer.addView(finalMergePill)
        finalMergePill!!.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(400).setInterpolator(OvershootInterpolator(1.0f)).start()
    }

    private fun buildCompletionView(): View {
        val area = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val ic = FrameLayout(context).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#F0FDF4")); shape = GradientDrawable.OVAL }
            scaleX = 0f; scaleY = 0f
        }
        ic.addView(TextView(context).apply { text = "✓"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f); setTextColor(Color.parseColor("#22C55E")); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        area.addView(ic, LinearLayout.LayoutParams(dpToPx(56), dpToPx(56)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dpToPx(10) })
        area.addView(TextView(context).apply {
            text = "모든 보호 조치가 자동으로\n완료되었습니다"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(Color.parseColor("#6B7280"))
            gravity = Gravity.CENTER; setLineSpacing(dpToPx(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dpToPx(8) })

        // spring scale-in for check icon
        area.post {
            ic.animate().scaleX(1f).scaleY(1f).setDuration(500).setStartDelay(200).setInterpolator(OvershootInterpolator(1.5f)).start()
        }
        return area
    }

    // ─────────────────────────────────────────────
    // 복귀 시 최종 상태 즉시 표시
    // ─────────────────────────────────────────────

    private fun showFinalStateImmediately() {
        sheetFrame.translationY = 0f; sheetFrame.alpha = 1f
        titleContainer.translationY = 0f; titleContainer.alpha = 1f

        pillScrollView?.visibility = View.VISIBLE
        finalMergePill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#F0FDF4")); cornerRadius = dpToPx(14).toFloat(); setStroke(dpToPx(1), Color.parseColor("#BBF7D0")) }
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
        }
        val cb = FrameLayout(context).apply { background = GradientDrawable().apply { setColor(Color.parseColor("#22C55E")); shape = GradientDrawable.OVAL } }
        cb.addView(TextView(context).apply { text = "✓"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        finalMergePill!!.addView(cb, LinearLayout.LayoutParams(dpToPx(16), dpToPx(16)).apply { marginEnd = dpToPx(6) })
        finalMergePill!!.addView(TextView(context).apply { text = "모든 보호 조치 완료"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(Color.parseColor("#16A34A")); typeface = Typeface.create("sans-serif-medium", Typeface.BOLD) })
        completedPillContainer.addView(finalMergePill)

        // 완료 아이콘 (즉시)
        val area = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val ic = FrameLayout(context).apply { background = GradientDrawable().apply { setColor(Color.parseColor("#F0FDF4")); shape = GradientDrawable.OVAL } }
        ic.addView(TextView(context).apply { text = "✓"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f); setTextColor(Color.parseColor("#22C55E")); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        area.addView(ic, LinearLayout.LayoutParams(dpToPx(56), dpToPx(56)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dpToPx(10) })
        area.addView(TextView(context).apply {
            text = "모든 보호 조치가 자동으로\n완료되었습니다"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(Color.parseColor("#6B7280"))
            gravity = Gravity.CENTER; setLineSpacing(dpToPx(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        stageContentContainer.addView(area, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        buttonsContainer.visibility = View.VISIBLE
        buttonsContainer.alpha = 1f; buttonsContainer.translationY = 0f
        pulseAnimators.forEach { it.start() }
    }

    // ─────────────────────────────────────────────
    // 국가명 업데이트
    // ─────────────────────────────────────────────

    fun updateCountry(country: String) {
        val timestamp = System.currentTimeMillis()
        android.util.Log.d("WarningSheet", "══════════════════════════════════════════════════════════")
        android.util.Log.d("WarningSheet", "▶▶▶ updateCountry() 호출됨 [$timestamp] — country=$country")
        android.util.Log.d("WarningSheet", "  현재 상태: mapFullyShown=$mapFullyShown, nextStagesStarted=$nextStagesStarted, locationResolved=$locationResolved")
        android.util.Log.d("WarningSheet", "  mapContainer=${mapContainer != null}, countryNameTv=${countryNameTv != null}, flagImageView=${flagImageView != null}")
        android.util.Log.d("WarningSheet", "  labelContainer=${labelContainer != null}, pinContainerRef=${pinContainerRef != null}")
        currentCountryName = country
        locationDataReceived = true
        handler.post {
            android.util.Log.d("WarningSheet", "  [handler.post 내부 ${System.currentTimeMillis()}] mapFullyShown=$mapFullyShown, nextStagesStarted=$nextStagesStarted")
            // 이미 Stage 전환이 시작됐으면 무시 (지도가 곧 사라짐)
            if (nextStagesStarted) {
                android.util.Log.d("WarningSheet", "  ⚠️ nextStagesStarted=true → Stage 전환 진행 중 → 스킵")
                return@post
            }
            // 지도가 아직 완전히 표시되지 않았으면 버퍼에 저장
            if (!mapFullyShown) {
                bufferedCriminalCountry = country
                android.util.Log.d("WarningSheet", "  → 버퍼에 저장: $country (mapFullyShown=false)")
                return@post
            }
            // 위치 추적 느낌을 주기 위해 1초 대기 후 표시
            android.util.Log.d("WarningSheet", "  → 1초 대기 후 applyCountryToMap() 호출 예약")
            handler.postDelayed({
                if (nextStagesStarted) return@postDelayed
                android.util.Log.d("WarningSheet", "  → [1초 후] applyCountryToMap() 호출")
                applyCountryToMap(country)
            }, 1000)
        }
    }

    /** 지도 위에 국가명/국기/아이콘을 실제로 반영하는 내부 함수 */
    private fun applyCountryToMap(country: String) {
        android.util.Log.d("WarningSheet", "══════════════════════════════════════════════════════════")
        android.util.Log.d("WarningSheet", "▶▶▶ applyCountryToMap() 호출 — country=$country")
        android.util.Log.d("WarningSheet", "  현재 상태: nextStagesStarted=$nextStagesStarted, mapFullyShown=$mapFullyShown")

        // Stage 전환이 이미 시작됐으면 지도가 곧 사라지므로 스킵
        if (nextStagesStarted) {
            android.util.Log.d("WarningSheet", "  ⚠️ nextStagesStarted=true → 이미 Stage 전환 중 → 스킵")
            return
        }

        val mc = mapContainer
        android.util.Log.d("WarningSheet", "  mapContainer=$mc")
        android.util.Log.d("WarningSheet", "    width=${mc?.width}, height=${mc?.height}, alpha=${mc?.alpha}")
        android.util.Log.d("WarningSheet", "    visibility=${mc?.visibility}, isAttachedToWindow=${mc?.isAttachedToWindow}, parent=${mc?.parent}")

        // mapContainer가 null이거나 뷰 계층에서 detach 됐으면 스킵
        if (mc == null || !mc.isAttachedToWindow) {
            android.util.Log.d("WarningSheet", "  ⚠️ mapContainer=null 또는 detached → 스킵")
            return
        }

        android.util.Log.d("WarningSheet", "  ✓ 조건 충족 — UI 업데이트 시작")

        // mc.post 대신 즉시 실행 — 이미 메인 스레드(handler.post)에서 호출됨
        // 국가명 텍스트 즉시 설정
        countryNameTv?.let { tv ->
            tv.text = country
            tv.setTextColor(Color.parseColor("#1F2937")) // 진한 회색
            tv.alpha = 1f; tv.scaleX = 1f; tv.scaleY = 1f; tv.visibility = View.VISIBLE
            android.util.Log.d("WarningSheet", "countryNameTv 설정 완료: text=${tv.text}, parent=${tv.parent}, parentAlpha=${(tv.parent as? View)?.alpha}")
        }

        // 국기 로드
        val code = countryNameToCode(country)
        android.util.Log.d("WarningSheet", "국가 코드: $code")
        if (code != null) {
            flagImageView?.let { iv ->
                iv.visibility = View.VISIBLE; iv.alpha = 1f; iv.scaleX = 1f; iv.scaleY = 1f
                val url = "https://flagcdn.com/w80/${code}.png"
                android.util.Log.d("WarningSheet", "국기 URL: $url, ivParent=${iv.parent}")
                try {
                    Glide.with(context.applicationContext)
                        .load(url)
                        .transform(RoundedCorners(dpToPx(2)))
                        .into(iv)
                } catch (e: Exception) {
                    android.util.Log.e("WarningSheet", "Glide 에러", e)
                }
            }
        }

        // 라벨 컨테이너 확실히 보이게 + 맨 위로
        labelContainer?.let { lc ->
            lc.alpha = 1f; lc.visibility = View.VISIBLE
            lc.elevation = dpToPx(1).toFloat()
            lc.translationZ = dpToPx(1).toFloat()
            lc.bringToFront()
            android.util.Log.d("WarningSheet", "labelContainer: vis=${lc.visibility}, alpha=${lc.alpha}, w=${lc.width}, h=${lc.height}, x=${lc.x}, y=${lc.y}, elevation=${lc.elevation}")
        }

        // 핀 아이콘을 위험(⚠) 상태로 유지 + z-depth 보정
        pinContainerRef?.let { pin ->
            pin.translationZ = dpToPx(2).toFloat()
            pin.bringToFront()
        }
        pinCircleRef?.let { pc ->
            pc.background = GradientDrawable().apply {
                setColor(Color.parseColor("#FEE2E2"))
                shape = GradientDrawable.OVAL
                setStroke(dpToPx(2), Color.parseColor("#FECACA"))
            }
            pc.elevation = dpToPx(4).toFloat()
            pc.translationZ = dpToPx(6).toFloat()
            pc.bringToFront()
        }
        pinIconTv?.text = "⚠"
        pinIconTv?.setTextColor(Color.parseColor("#EF4444"))

        mc.requestLayout()
        mc.invalidate()

        android.util.Log.d("WarningSheet", "  ✓ 지도 UI 업데이트 완료!")
        android.util.Log.d("WarningSheet", "    countryNameTv: text='${countryNameTv?.text}', visibility=${countryNameTv?.visibility}, alpha=${countryNameTv?.alpha}")
        android.util.Log.d("WarningSheet", "    flagImageView: visibility=${flagImageView?.visibility}, alpha=${flagImageView?.alpha}")
        android.util.Log.d("WarningSheet", "    labelContainer: visibility=${labelContainer?.visibility}, alpha=${labelContainer?.alpha}, isAttached=${labelContainer?.isAttachedToWindow}")
        android.util.Log.d("WarningSheet", "    pinIconTv: text='${pinIconTv?.text}'")

        // 버퍼된 좌표가 있으면 핀 위치도 함께 적용
        val bLat = bufferedCriminalLat
        val bLng = bufferedCriminalLng
        if (bLat != null && bLng != null) {
            android.util.Log.d("WarningSheet", "  → 버퍼된 좌표 ($bLat, $bLng) 핀 위치 적용")
            bufferedCriminalLat = null
            bufferedCriminalLng = null
            applyPinPosition(bLat, bLng)
        }

        android.util.Log.d("WarningSheet", "  → markLocationResolved() 호출")
        markLocationResolved()
        // 위치 확인 후 2초 동안 지도+국가명+국기 보여주고 나머지 단계 진행
        android.util.Log.d("WarningSheet", "  → scheduleProceedAfterLocation(2000L) 호출 (지도 2초 후 Stage 2 전환)")
        scheduleProceedAfterLocation(2000L)
    }

    fun updatePinPosition(lat: Double, lng: Double) {
        // 버퍼에 저장 - applyCountryToMap에서 함께 적용됨
        bufferedCriminalLat = lat
        bufferedCriminalLng = lng
        android.util.Log.d("WarningSheet", "updatePinPosition: lat=$lat, lng=$lng → 버퍼에 저장")
    }

    private fun applyPinPosition(lat: Double, lng: Double) {
        val mc = mapContainer ?: return
        mc.post {
            val mapW = mc.width.toFloat(); val mapH = mc.height.toFloat()
            if (mapW <= 0 || mapH <= 0) return@post

            // 핀은 위경도 기반으로 위치 계산
            val xRatio = ((lng + 180.0) / 360.0).toFloat().coerceIn(0f, 1f)
            val yRatio = ((90.0 - lat) / 180.0).toFloat().coerceIn(0f, 1f)
            val pinSize = dpToPx(80)
            val pinX = (mapW * xRatio - pinSize / 2f).toInt().coerceIn(0, (mapW - pinSize).toInt())
            val pinY = (mapH * yRatio - pinSize / 2f).toInt().coerceIn(0, (mapH - pinSize).toInt())

            pinContainerRef?.let { pin ->
                (pin.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                    lp.leftMargin = pinX
                    lp.topMargin = pinY
                    lp.gravity = Gravity.NO_GRAVITY
                    pin.layoutParams = lp
                }
            }

            // 라벨은 핀 바로 아래에 위치 (핀을 따라감)
            labelContainer?.let { label ->
                val labelW = label.width.takeIf { it > 0 } ?: dpToPx(100) // 추정 너비
                val pinCenterX = pinX + pinSize / 2
                // 라벨 중앙이 핀 중앙에 오도록, 화면 밖으로 나가지 않게 제한
                val labelX = (pinCenterX - labelW / 2).coerceIn(dpToPx(8), (mapW - labelW - dpToPx(8)).toInt())
                // 핀 하단에서 8dp 더 아래 (pinSize - 10dp 대신 pinSize - 5dp로)
                val labelY = (pinY + pinSize - dpToPx(10)).coerceIn(0, (mapH - dpToPx(40)).toInt())

                (label.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                    lp.leftMargin = labelX
                    lp.topMargin = labelY
                    lp.gravity = Gravity.NO_GRAVITY
                    label.layoutParams = lp
                }
            }

            android.util.Log.d("WarningSheet", "applyPinPosition: pinX=$pinX, pinY=$pinY, mapW=$mapW, mapH=$mapH")
        }
    }

    private fun countryNameToCode(name: String): String? = when (name.trim()) {
        "대한민국", "한국" -> "kr"; "캄보디아" -> "kh"; "미국" -> "us"; "중국" -> "cn"
        "일본" -> "jp"; "베트남" -> "vn"; "태국" -> "th"; "필리핀" -> "ph"
        "미얀마" -> "mm"; "라오스" -> "la"; "말레이시아" -> "my"; "싱가포르" -> "sg"
        "인도네시아" -> "id"; "인도" -> "in"; "러시아" -> "ru"; "영국" -> "gb"
        "독일" -> "de"; "프랑스" -> "fr"; "호주" -> "au"; "캐나다" -> "ca"
        "브라질" -> "br"; "나이지리아" -> "ng"; "가나" -> "gh"; "케냐" -> "ke"
        "북한" -> "kp"; "대만", "타이완" -> "tw"; "홍콩" -> "hk"; "멕시코" -> "mx"
        "아르헨티나" -> "ar"; "이탈리아" -> "it"; "스페인" -> "es"; "네덜란드" -> "nl"
        "스위스" -> "ch"; "터키", "튀르키예" -> "tr"; "사우디아라비아" -> "sa"
        "이란" -> "ir"; "이라크" -> "iq"; "이집트" -> "eg"
        "남아프리카공화국", "남아공" -> "za"; else -> null
    }

    // ─────────────────────────────────────────────
    // 메인 애니메이션 시퀀스
    // ─────────────────────────────────────────────

    /** 위치 결정 후 나머지 단계가 이미 시작되었는지 여부 */
    private var nextStagesStarted = false

    fun startEntranceAnimation() {
        if (isRestoredFromCache) {
            // 복원 시 부드러운 슬라이드업 애니메이션
            android.util.Log.d("WarningSheet", "▶▶▶ 복원 애니메이션 시작 (isRestoredFromCache=true)")
            sheetFrame.translationY = dpToPx(80).toFloat()
            sheetFrame.alpha = 0f
            // 레이아웃 완료 후 애니메이션 시작
            sheetFrame.post {
                sheetFrame.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(350)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(2f))
                    .withLayer()
                    .start()
            }
            return
        }
        android.util.Log.d("WarningSheet", "══════════════════════════════════════════════════════════")
        android.util.Log.d("WarningSheet", "▶▶▶ startEntranceAnimation() 호출 — isInApp=$isInApp")
        android.util.Log.d("WarningSheet", "  bufferedCriminalCountry=$bufferedCriminalCountry, mapFullyShown=$mapFullyShown")

        // 시작 상태 확실히 설정
        sheetFrame.translationY = dpToPx(200).toFloat()
        sheetFrame.alpha = 0f
        titleContainer.translationY = dpToPx(16).toFloat()
        titleContainer.alpha = 0f

        // 레이아웃 완료 후 애니메이션 시작 (뚝끊김 방지)
        sheetFrame.post {
            // 바텀시트 slide-up
            android.util.Log.d("WarningSheet", "  [post] 바텀시트 slide-up 시작")
            sheetFrame.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(450)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.8f))
                .withLayer()
                .start()
        }

        // 타이틀 + pulse - 부드러운 감속 애니메이션
        handler.postDelayed({
            android.util.Log.d("WarningSheet", "  [200ms] 타이틀 애니메이션 + pulse 시작")
            titleContainer.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                .withLayer()
                .start()
            pulseAnimators.forEach { it.start() }
        }, 200)

        // ─── Stage 1: 지도 + 위치 추적 ───
        if (isInApp) {
            handler.postDelayed({
                android.util.Log.d("WarningSheet", "  [500ms] Stage 1 지도 빌드 시작")
                val mapView = buildMapContent()
                android.util.Log.d("WarningSheet", "  mapView 생성 완료: $mapView")
                transitionStageContent(mapView) {
                    android.util.Log.d("WarningSheet", "  ★★★ transitionStageContent onShown 콜백 진입 ★★★")
                    android.util.Log.d("WarningSheet", "    bufferedCriminalCountry=$bufferedCriminalCountry")
                    miscAnimators.forEach { it.start() }
                    pulseAnimators.forEach { if (!it.isStarted) it.start() }
                    // 지도가 완전히 표시됨 — 즉시 플래그 설정 및 버퍼 적용
                    mapFullyShown = true
                    android.util.Log.d("WarningSheet", "    → mapFullyShown=true 설정 완료")
                    val bc = bufferedCriminalCountry
                    if (bc != null) {
                        bufferedCriminalCountry = null
                        // 위치 추적 느낌을 주기 위해 1초 대기 후 표시
                        android.util.Log.d("WarningSheet", "    → 버퍼된 국가 '$bc' 1초 대기 후 적용")
                        handler.postDelayed({
                            if (nextStagesStarted) return@postDelayed
                            applyCountryToMap(bc)
                        }, 1000)
                    } else {
                        android.util.Log.d("WarningSheet", "    → 버퍼된 국가 없음")
                    }
                }
                // 위치 데이터 타임아웃 (10초)
                scheduleLocationTimeout()
            }, 500)
        } else {
            android.util.Log.d("WarningSheet", "  인앱 아님 → 바로 proceedToNextStages() 호출")
            proceedToNextStages()
        }
    }

    /**
     * 위치 확인 완료(성공/실패) 후 Stage 2~5를 순차 시작한다.
     * 중복 호출 방지.
     */
    private fun proceedToNextStages() {
        android.util.Log.d("WarningSheet", "══════════════════════════════════════════════════════════")
        android.util.Log.d("WarningSheet", "▶▶▶ proceedToNextStages() 호출 — nextStagesStarted=$nextStagesStarted")
        if (nextStagesStarted) {
            android.util.Log.d("WarningSheet", "  이미 시작됨 → 중복 호출 방지")
            return
        }
        nextStagesStarted = true
        android.util.Log.d("WarningSheet", "  → nextStagesStarted=true 설정 → Stage 2~5 순차 진행 시작")

        // 위치 결과를 보여준 뒤 Stage 2로 전환
        val baseDelay = if (isInApp) 500L else 0L
        android.util.Log.d("WarningSheet", "  baseDelay=${baseDelay}ms")

        // ─── Stage 2: 카카오톡 메시지 ───
        handler.postDelayed({
            android.util.Log.d("WarningSheet", "══════════════════════════════════════════════════════════")
            android.util.Log.d("WarningSheet", "  ★★★ [${baseDelay + 280}ms] Stage 2 카카오톡 전환 ★★★")
            android.util.Log.d("WarningSheet", "  ⚠️ 지도 UI가 이제 사라집니다!")
            android.util.Log.d("WarningSheet", "    현재 상태: mapContainer=${mapContainer != null}, labelContainer=${labelContainer != null}")
            android.util.Log.d("WarningSheet", "    countryNameTv?.text='${countryNameTv?.text}'")
            // Stage 2로 전환하면 지도 UI 참조는 더 이상 유효하지 않음
            mapContainer = null
            pinContainerRef = null
            pinCircleRef = null
            pinIconTv = null
            countryNameTv = null
            flagImageView = null
            labelContainer = null
            locationTrackingRow = null
            mapFullyShown = false
            android.util.Log.d("WarningSheet", "    → 지도 UI 참조 무효화 완료")
            transitionStageContent(buildKakaoTalkContent())
        }, baseDelay + 280)

        // pill: "보호자 알림 완료" + Stage 3 전환
        val stage3Start = baseDelay + 3200L
        handler.postDelayed({
            android.util.Log.d("WarningSheet", "  [${stage3Start}ms] pill: 보호자 알림 완료")
            addCompletedPill(pillConfigs[1].label, pillConfigs[1].color)
        }, stage3Start)

        // // ─── Stage 3: GPS 공유 ───
        handler.postDelayed({
            transitionStageContent(buildGPSContent())
        }, stage3Start + 350)

        // pill: "GPS 공유 완료" + Stage 4 전환
        val stage4Start = stage3Start + 2500L
        handler.postDelayed({
            addCompletedPill(pillConfigs[2].label, pillConfigs[2].color)
        }, stage4Start)

        // ─── Stage 4: 금융앱 차단 ───
        handler.postDelayed({
            transitionStageContent(buildFinancialBlockContent())
        }, stage4Start + 350)

        // pill: "금융 차단 활성화" + Stage 5 (최종)
        val stage5Start = stage4Start + 3200L
        handler.postDelayed({
            addCompletedPill(pillConfigs[3].label, pillConfigs[3].color)
        }, stage5Start)

        handler.postDelayed({
            showFinalMerge()
        }, stage5Start + 500)
    }

    fun stopAnimations() {
        handler.removeCallbacksAndMessages(null)
        pulseAnimators.forEach { it.cancel() }
        miscAnimators.forEach { it.cancel() }; miscAnimators.clear()
    }

    fun setAnalysisResult(result: String) {
        if (result.isNotBlank()) {
            val tags = result.split(" ").map { it.trim() }.filter { it.isNotBlank() }
            for (tag in tags) {
                if (tag != "#일상대화" && tag != "#일상 대화") { gmsResultSet.remove("#일상 대화"); gmsResultSet.remove("#일상대화"); gmsResultSet.add(tag) }
                else if (gmsResultSet.isEmpty()) { gmsResultSet.add(tag) }
            }
        }
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); stopAnimations() }

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
}

package com.s14p21a404.shutter.presentation.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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
 * 레퍼런스: ai-detection-modal.tsx
 * - 흰색 rounded-top-3xl 바텀시트 (bounce slide-up)
 * - X 닫기 버튼
 * - 경고 아이콘 + pulse wave 애니메이션
 * - "AI 위조 감지" 타이틀
 * - 서브타이틀 2줄
 * - "전화 종료" / "재탐지 하기" 버튼
 * - 각 요소 staggered 등장 애니메이션
 */
class WarningBottomSheetView(
    context: Context,
    private val onEndCall: (() -> Unit)? = null,
    private val onRescan: (() -> Unit)? = null,
    private val onClose: (() -> Unit)? = null
) : FrameLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private val pulseAnimators = mutableListOf<AnimatorSet>()

    // 애니메이션 대상 뷰들
    private val sheetContainer: LinearLayout
    private val sheetFrame: FrameLayout
    private val titleRow: LinearLayout
    private val subtitleContainer: LinearLayout
    private val buttonsContainer: LinearLayout

    init {
        // 전체 wrap — 하단 정렬용
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


        // ── 타이틀 행 (아이콘 + 텍스트) ──
        titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            alpha = 0f
            translationY = dpToPx(24).toFloat()
        }

        // pulse wave 경고 아이콘 컨테이너
        // pulse가 scale 2.5x(=100dp)까지 확장되므로 충분한 크기 확보
        val iconWrapperSize = dpToPx(100)
        val iconWrapper = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(iconWrapperSize, iconWrapperSize).apply {
                // 시각적으로 아이콘~텍스트 간격 보정 (실제 아이콘은 가운데 40dp)
                setMargins(dpToPx(-30), dpToPx(-30), dpToPx(-18), dpToPx(-30))
            }
        }

        // pulse wave circles (3개)
        for (i in 0..2) {
            val pulseCircle = View(context).apply {
                val pulseBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#FECACA")) // red-200
                    shape = GradientDrawable.OVAL
                }
                background = pulseBg
                alpha = 0f
            }
            val pulseSize = dpToPx(40)
            iconWrapper.addView(pulseCircle, LayoutParams(pulseSize, pulseSize).apply {
                gravity = Gravity.CENTER
            })

            // pulse wave 애니메이션
            val scaleX = ObjectAnimator.ofFloat(pulseCircle, "scaleX", 1f, 2.5f).apply {
                duration = 2000; repeatCount = ValueAnimator.INFINITE; startDelay = (i * 500).toLong()
            }
            val scaleY = ObjectAnimator.ofFloat(pulseCircle, "scaleY", 1f, 2.5f).apply {
                duration = 2000; repeatCount = ValueAnimator.INFINITE; startDelay = (i * 500).toLong()
            }
            val alphaAnim = ObjectAnimator.ofFloat(pulseCircle, "alpha", 0.6f, 0f).apply {
                duration = 2000; repeatCount = ValueAnimator.INFINITE; startDelay = (i * 500).toLong()
            }
            val set = AnimatorSet().apply { playTogether(scaleX, scaleY, alphaAnim) }
            pulseAnimators.add(set)
        }

        // 중앙 아이콘 (경고 삼각형)
        val iconCircle = FrameLayout(context).apply {
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#FEF2F2")) // red-50
                shape = GradientDrawable.OVAL
            }
            background = bg
            translationZ = dpToPx(2).toFloat()
        }
        val warningIcon = TextView(context).apply {
            text = "⚠"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#EF4444")) // red-500
            gravity = Gravity.CENTER
        }
        iconCircle.addView(warningIcon, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val iconCircleSize = dpToPx(40)
        iconWrapper.addView(iconCircle, LayoutParams(iconCircleSize, iconCircleSize).apply {
            gravity = Gravity.CENTER
        })

        titleRow.addView(iconWrapper)

        // 타이틀 텍스트
        val titleTv = TextView(context).apply {
            text = "AI 위조 감지"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.parseColor("#111827")) // gray-900
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        titleRow.addView(titleTv)

        sheetContainer.addView(titleRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dpToPx(16)
        })

        // ── 서브타이틀 ──
        subtitleContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            alpha = 0f
            translationY = dpToPx(16).toFloat()
        }

        subtitleContainer.addView(TextView(context).apply {
            text = "딥페이크 의심 통화가 감지되었습니다."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#6B7280")) // gray-500
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(4) })

        // "금융 거래에 주의하세요." (금융 거래 = red, 나머지 = gray)
        val subtitleRow2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        subtitleRow2.addView(TextView(context).apply {
            text = "금융 거래"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#EF4444")) // red-500
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        subtitleRow2.addView(TextView(context).apply {
            text = "에 주의하세요."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#6B7280")) // gray-500
        })
        subtitleContainer.addView(subtitleRow2)

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
            val radius = dpToPx(12).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EF4444")) // red-500
                cornerRadius = radius
            }
            setPadding(0, dpToPx(14), 0, dpToPx(14))

            setOnClickListener { onEndCall?.invoke() }
        }
        buttonsContainer.addView(endCallBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(12) })

        // "재탐지 하기" 버튼 (gray)
        val rescanBtn = TextView(context).apply {
            text = "재탐지 하기"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#374151")) // gray-700
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            val radius = dpToPx(12).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F3F4F6")) // gray-100
                cornerRadius = radius
            }
            setPadding(0, dpToPx(14), 0, dpToPx(14))

            setOnClickListener { onRescan?.invoke() }
        }
        buttonsContainer.addView(rescanBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        sheetContainer.addView(buttonsContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // sheetContainer(LinearLayout) 전체를 FrameLayout으로 감싸고,
        // X 버튼을 그 위에 absolute top-right로 배치.
        // sheetFrame 전체에 slide-up 애니메이션을 적용하여 X 버튼도 함께 움직인다.
        sheetFrame = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            // 초기 상태: 화면 아래로 숨김
            translationY = dpToPx(400).toFloat()
            alpha = 0f
        }
        sheetFrame.addView(sheetContainer, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ))

        // wrapper를 FrameLayout 하단에 배치
        addView(sheetFrame, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM })
    }

    /**
     * staggered 등장 애니메이션을 시작한다.
     */
    fun startEntranceAnimation() {
        // 1) 바텀시트 slide-up with bounce (sheetFrame 전체 — X 버튼 포함)
        handler.postDelayed({
            sheetFrame.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(600)
                .setInterpolator(OvershootInterpolator(0.8f))
                .start()
        }, 50)

        // 2) 타이틀 행 fade-in
        handler.postDelayed({
            titleRow.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(OvershootInterpolator(1.0f))
                .start()

            // pulse wave 시작
            pulseAnimators.forEach { it.start() }
        }, 250)

        // 3) 서브타이틀 fade-in
        handler.postDelayed({
            subtitleContainer.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(OvershootInterpolator(1.0f))
                .start()
        }, 400)

        // 4) 버튼 fade-in
        handler.postDelayed({
            buttonsContainer.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(OvershootInterpolator(1.0f))
                .start()
        }, 550)
    }

    fun stopAnimations() {
        handler.removeCallbacksAndMessages(null)
        pulseAnimators.forEach { it.cancel() }
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


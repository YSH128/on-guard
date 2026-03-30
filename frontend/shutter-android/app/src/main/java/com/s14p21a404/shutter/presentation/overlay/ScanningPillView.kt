package com.s14p21a404.shutter.presentation.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 레퍼런스 기반 스캐닝 인디케이터.
 *
 * 흰색(80%) 반투명 + backdrop-blur + rounded-2xl 카드:
 * ┌────────────────────────┐
 * │  [로고]  |||||||||||   │  ← 로고 아이콘 + 음파 애니메이션
 * │  ⟳ 대화 내용 분석      │  ← 스피너 + 텍스트
 * └────────────────────────┘
 */
class ScanningPillView(context: Context) : LinearLayout(context) {

    private val waveView: SoundWaveView

    init {
        orientation = VERTICAL
        gravity = Gravity.START

        // 카드 배경: white/80% + rounded 16dp + 그림자
        val cardBg = GradientDrawable().apply {
            setColor(Color.parseColor("#CCF0F0F0")) // white/80%
            cornerRadius = dpToPx(16).toFloat()
            setStroke(dpToPx(1), Color.parseColor("#1AF3F4F6")) // border-gray-100 / ~10%
        }
        background = cardBg
        elevation = dpToPx(4).toFloat()

        val hPad = dpToPx(16)
        val vPad = dpToPx(12)
        setPadding(hPad, vPad, hPad, vPad)

        // ── Row 1: 로고 + 음파 애니메이션 ──
        val row1 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // 로고 아이콘 (res/drawable/logo.png)
        val logoView = ImageView(context).apply {
            setImageResource(context.resources.getIdentifier("logo", "drawable", context.packageName))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val logoSize = dpToPx(24)
        row1.addView(logoView, LayoutParams(logoSize, logoSize).apply {
            marginEnd = dpToPx(8)
        })

        // 음파 애니메이션 (가로 넓게)
        waveView = SoundWaveView(context)
        row1.addView(waveView, LayoutParams(dpToPx(110), dpToPx(24)))

        addView(row1, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dpToPx(8)
        })

        // ── Row 2: 스피너 + "대화 내용 분석" ──
        val row2 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // 스피너 (회전하는 원형 뷰)
        val spinnerView = SpinnerView(context)
        val spinnerSize = dpToPx(14)
        row2.addView(spinnerView, LayoutParams(spinnerSize, spinnerSize).apply {
            marginEnd = dpToPx(8)
        })

        // "대화 내용 분석" 텍스트
        val labelTv = TextView(context).apply {
            text = "대화 내용 분석"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#4B5563")) // gray-600
        }
        row2.addView(labelTv)

        addView(row2, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun startAnimation() {
        waveView.startAnimation()
        // SpinnerView는 자동 시작
    }

    fun stopAnimation() {
        waveView.stopAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()


    // ─────────────────────────────────────────────
    // 내부: 음파 애니메이션
    // ─────────────────────────────────────────────

    /**
     * Home 페이지 시그니처 음파 애니메이션과 동일한 디자인.
     * 28개 세로 바, blue(#6CAEEA)/pink(#D03770) 교차 색상,
     * sin(phase + i*0.45) 패턴, 1400ms 주기.
     */
    private class SoundWaveView(context: Context) : View(context) {
        // Home 페이지와 동일한 바 스케일 (28개)
        private val barScales = floatArrayOf(
            0.55f, 0.9f, 0.45f, 1.0f, 0.6f, 0.8f, 0.4f, 0.95f, 0.65f, 0.5f,
            0.85f, 0.4f, 1.0f, 0.55f, 0.75f, 0.45f, 0.9f, 0.6f, 0.5f, 0.85f,
            0.7f, 0.4f, 1.0f, 0.5f, 0.8f, 0.6f, 0.9f, 0.45f
        )
        private val barCount = barScales.size

        // Home 페이지와 동일한 색상 패턴 (7개 순환)
        private val barColors = intArrayOf(
            Color.parseColor("#6CAEEA"),
            Color.parseColor("#D03770"),
            Color.parseColor("#6CAEEA"),
            Color.parseColor("#D03770"),
            Color.parseColor("#6CAEEA"),
            Color.parseColor("#D03770"),
            Color.parseColor("#6CAEEA")
        )

        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private var phase = 0f
        private var animator: ValueAnimator? = null

        fun startAnimation() {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
                duration = 1400
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    phase = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        fun stopAnimation() {
            animator?.cancel()
            animator = null
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stopAnimation()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            // Home 페이지와 동일: barWidth ≈ 4dp 비율, gap ≈ 3dp 비율
            // 전체 폭에 맞게 스케일링
            val totalUnits = barCount * 4f + (barCount - 1) * 3f // 4dp bar + 3dp gap 비율
            val unit = w / totalUnits
            val barWidth = unit * 4f
            val gap = unit * 3f
            val radius = barWidth / 2f

            for (i in 0 until barCount) {
                val x = i * (barWidth + gap)
                // Home 페이지와 동일한 높이 계산: sin(phase + i * 0.45) 패턴
                val heightFraction = ((Math.sin((phase + i * 0.45f).toDouble()).toFloat() + 1f) / 2f) * barScales[i] * 0.8f + 0.1f
                val barH = h * heightFraction
                val top = (h - barH) / 2f  // 수직 중앙 정렬

                barPaint.shader = null
                barPaint.color = barColors[i % barColors.size]

                canvas.drawRoundRect(x, top, x + barWidth, top + barH, radius, radius, barPaint)
            }
        }
    }

    // ─────────────────────────────────────────────
    // 내부: 스피너 (회전하는 원호)
    // ─────────────────────────────────────────────

    private class SpinnerView(context: Context) : View(context) {
        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#60A5FA") // blue-400
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(context, 2).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        private val arcRect = RectF()
        private var rotationAngle = 0f

        private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val inset = arcPaint.strokeWidth
            arcRect.set(inset, inset, w.toFloat() - inset, h.toFloat() - inset)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.save()
            canvas.rotate(rotationAngle, width / 2f, height / 2f)
            // 3/4 원호 (270도) — 위쪽이 투명 (border-t-transparent)
            canvas.drawArc(arcRect, 0f, 270f, false, arcPaint)
            canvas.restore()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            animator.cancel()
        }

        companion object {
            private fun dpToPx(context: Context, dp: Int): Int = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
                context.resources.displayMetrics
            ).toInt()
        }
    }
}

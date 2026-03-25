package com.s14p21a404.shutter.presentation.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * ROI 기반 바운딩 박스 + 스캔 라인 + confidence 배지를 그리는 오버레이 뷰.
 *
 * ROI는 화면 기준 상대좌표 [minX, minY, maxX, maxY] (0.0~1.0) 로 설정한다.
 * - 노란색 외곽 border (yellow-400, #FACC15)
 * - 빨간색 내부 border (red-400/40%, #F87171 / 66)
 * - 연빨간색 fill (red-500/15%, #EF4444 / 26)
 * - 수직으로 왕복하는 빨간 스캔 라인 (glow 포함)
 * - 상단에 confidence 배지
 */
class RoiBoundingBoxView(context: Context) : View(context) {

    // ── ROI 데이터 ──
    private var roiMinX = 0f
    private var roiMinY = 0f
    private var roiMaxX = 0f
    private var roiMaxY = 0f
    private var confidence = 0f

    // ── 스캔 라인 위치 (0~1) ──
    private var scanProgress = 0f

    // ── pop-in 애니메이션 ──
    private var popScale = 0f
    private var popAlpha = 0f

    // ── Paint 객체들 ──
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#26EF4444") // red-500 / 15%
        style = Paint.Style.FILL
    }

    private val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444") // red-500 (레퍼런스: border-3 border-red-500)
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3).toFloat()
    }

    private val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2).toFloat()
    }

    private val scanGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(6).toFloat()
        maskFilter = BlurMaskFilter(dpToPx(4).toFloat(), BlurMaskFilter.Blur.NORMAL)
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444") // red-500
        style = Paint.Style.FILL
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dpToPx(11).toFloat()
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f).toFloat()
        strokeCap = Paint.Cap.ROUND
    }

    // ── Animator ──
    private val scanAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            scanProgress = it.animatedValue as Float
            invalidate()
        }
    }

    private val popAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 500
        interpolator = android.view.animation.OvershootInterpolator(1.2f)
        addUpdateListener {
            val v = it.animatedValue as Float
            popScale = v
            popAlpha = v
            invalidate()
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // BlurMaskFilter requires software layer
    }

    fun setRoi(minX: Float, minY: Float, maxX: Float, maxY: Float, conf: Float) {
        roiMinX = minX
        roiMinY = minY
        roiMaxX = maxX
        roiMaxY = maxY
        confidence = conf
        invalidate()
    }

    fun startAnimations() {
        if (!scanAnimator.isRunning) scanAnimator.start()
        popAnimator.start()
    }

    fun stopAnimations() {
        scanAnimator.cancel()
        popAnimator.cancel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimations()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        if (popScale <= 0f) return

        val left = w * roiMinX
        val top = h * roiMinY
        val right = w * roiMaxX
        val bottom = h * roiMaxY

        val boxW = right - left
        val boxH = bottom - top
        val cx = left + boxW / 2f
        val cy = top + boxH / 2f

        canvas.save()

        // pop-in scale from center of box
        canvas.scale(popScale, popScale, cx, cy)
        val alpha = (popAlpha * 255).toInt().coerceIn(0, 255)

        val outerRadius = dpToPx(8).toFloat()

        // ① 연빨간 fill (레퍼런스: bg-red-500/20)
        fillPaint.alpha = alpha * 0x33 / 255  // ~20%
        canvas.drawRoundRect(left, top, right, bottom, outerRadius, outerRadius, fillPaint)

        // ② 스캔 라인 (box 안쪽에서 위→아래 이동)
        val scanY = top + boxH * scanProgress
        scanLinePaint.shader = LinearGradient(
            left, scanY, right, scanY,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#EF4444"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        scanLinePaint.alpha = alpha
        canvas.drawLine(left, scanY, right, scanY, scanLinePaint)

        // glow
        scanGlowPaint.shader = LinearGradient(
            left, scanY, right, scanY,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#66EF4444"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        scanGlowPaint.alpha = alpha
        canvas.drawLine(left, scanY, right, scanY, scanGlowPaint)

        // ③ 외곽 빨간 border (레퍼런스: border-3 border-red-500 rounded-lg)
        outerBorderPaint.alpha = alpha
        canvas.drawRoundRect(left, top, right, bottom, outerRadius, outerRadius, outerBorderPaint)


        // ⑤ Confidence 배지 (box 상단 위)
        val badgeText = String.format("%.1f%%", confidence)
        val badgeH = dpToPx(22).toFloat()
        val badgePadH = dpToPx(8).toFloat()
        val iconSize = dpToPx(10).toFloat()
        val iconGap = dpToPx(4).toFloat()

        val textWidth = badgeTextPaint.measureText(badgeText)
        val totalBadgeW = badgePadH * 2 + iconSize + iconGap + textWidth
        val badgeRadius = badgeH / 2f

        val badgeCx = cx
        val badgeTop = top - dpToPx(28).toFloat()
        val badgeLeft = badgeCx - totalBadgeW / 2f
        val badgeRight = badgeCx + totalBadgeW / 2f
        val badgeBottom = badgeTop + badgeH

        badgePaint.alpha = alpha
        canvas.drawRoundRect(badgeLeft, badgeTop, badgeRight, badgeBottom, badgeRadius, badgeRadius, badgePaint)

        // ⚠ AlertTriangle icon (레퍼런스: AlertTriangle 아이콘)
        val iconCx = badgeLeft + badgePadH + iconSize / 2f
        val iconCy = badgeTop + badgeH / 2f
        iconPaint.alpha = alpha
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = dpToPx(1.5f).toFloat()
        // 삼각형
        val triPath = Path().apply {
            moveTo(iconCx, iconCy - iconSize * 0.4f)
            lineTo(iconCx - iconSize * 0.38f, iconCy + iconSize * 0.3f)
            lineTo(iconCx + iconSize * 0.38f, iconCy + iconSize * 0.3f)
            close()
        }
        canvas.drawPath(triPath, iconPaint)
        // 느낌표 (세로선 + 점)
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(iconCx, iconCy - iconSize * 0.15f, iconCx, iconCy + iconSize * 0.08f, iconPaint)
        canvas.drawPoint(iconCx, iconCy + iconSize * 0.2f, iconPaint)

        // badge text
        badgeTextPaint.alpha = alpha
        val textX = badgeLeft + badgePadH + iconSize + iconGap + textWidth / 2f
        val textY = badgeTop + badgeH / 2f - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
        canvas.drawText(badgeText, textX, textY, badgeTextPaint)

        canvas.restore()
    }

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()

    private fun dpToPx(dp: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp,
        context.resources.displayMetrics
    ).toInt()
}


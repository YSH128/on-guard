package com.s14p21a404.shutter.service

import android.content.Context
import android.graphics.*
import android.view.View

/**
 * 전체를 dimColor로 덮되, 지정 위치에 원형 hole을 뚫는 시각 전용 View.
 * 터치 처리는 하지 않음 (FLAG_NOT_TOUCHABLE 윈도우에서 사용).
 */
class HoledOverlayView(
    context: Context,
    private val holeCenterXRatio: Float = 0.5f,
    private val holeCenterYRatio: Float = 0.55f,
    private val holeRadiusRatio: Float = 0.1f,
    private val dimColor: Int = 0xCC000000.toInt(),
    private val fadeWidth: Float = 30f
) : View(context) {

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fadeRectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }
    private val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    private val bottomFadeHeight = 120f

    private var cachedWidth = 0f
    private var cachedHeight = 0f
    private var fadeShader: LinearGradient? = null
    private var holeShader: RadialGradient? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cachedWidth = w.toFloat()
        cachedHeight = h.toFloat()
        rebuildShaders()
    }

    private fun rebuildShaders() {
        val w = cachedWidth
        val h = cachedHeight
        if (w <= 0 || h <= 0) return

        val fadeTop = h - bottomFadeHeight
        fadeShader = LinearGradient(
            0f, fadeTop, 0f, h,
            dimColor, Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )

        val holeCx = w * holeCenterXRatio
        val holeCy = h * holeCenterYRatio
        val holeRadius = w * holeRadiusRatio

        holeShader = RadialGradient(
            holeCx, holeCy,
            holeRadius + fadeWidth,
            intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
            floatArrayOf(0f, holeRadius / (holeRadius + fadeWidth), 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = cachedWidth
        val h = cachedHeight
        if (w <= 0 || h <= 0) return

        dimPaint.color = dimColor
        canvas.drawRect(0f, 0f, w, h, dimPaint)

        val fadeTop = h - bottomFadeHeight
        fadeRectPaint.shader = fadeShader
        canvas.drawRect(0f, fadeTop, w, h, fadeRectPaint)

        val holeCx = w * holeCenterXRatio
        val holeCy = h * holeCenterYRatio
        val holeRadius = w * holeRadiusRatio
        fadePaint.shader = holeShader
        canvas.drawCircle(holeCx, holeCy, holeRadius + fadeWidth, fadePaint)
    }
}

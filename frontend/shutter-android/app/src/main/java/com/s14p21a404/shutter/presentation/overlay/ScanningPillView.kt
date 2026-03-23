package com.s14p21a404.shutter.presentation.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * "Shutter 감지 중•••" 스캐닝 상태 표시 Pill 뷰.
 * 500ms 주기로 점(•) 개수가 1→2→3 순환한다.
 */
class ScanningPillView(context: Context) : LinearLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private var dotCount = 1
    private val dotTextView: TextView

    private val dotRunnable = object : Runnable {
        override fun run() {
            dotCount = if (dotCount >= 3) 1 else dotCount + 1
            dotTextView.text = "•".repeat(dotCount)
            handler.postDelayed(this, 500)
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        // pill 배경: 검정 60% + rounded-full
        val pillBg = GradientDrawable().apply {
            setColor(Color.parseColor("#99000000")) // black/60%
            cornerRadius = dpToPx(20).toFloat()
        }
        background = pillBg

        val hPad = dpToPx(20)
        val vPad = dpToPx(10)
        setPadding(hPad, vPad, hPad, vPad)

        // "Shutter 감지 중" 텍스트
        val labelTv = TextView(context).apply {
            text = "Shutter 감지 중"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.parseColor("#4ADE80")) // green
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        addView(labelTv)

        // 점 애니메이션 텍스트 (고정 너비)
        dotTextView = TextView(context).apply {
            text = "•"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.parseColor("#4ADE80"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            width = dpToPx(24) // 고정 너비로 레이아웃 흔들림 방지
        }
        addView(dotTextView)
    }

    fun startAnimation() {
        handler.removeCallbacks(dotRunnable)
        dotCount = 1
        dotTextView.text = "•"
        handler.postDelayed(dotRunnable, 500)
    }

    fun stopAnimation() {
        handler.removeCallbacks(dotRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}


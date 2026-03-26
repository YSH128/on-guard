package com.s14p21a404.shutter.presentation.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 레퍼런스 기반 푸쉬 알림 pill.
 *
 * 우측 상단에서 슬라이드 인 → 유지 → 페이드 아웃 후 자동 제거.
 *
 * 종류:
 * - KAKAO: #6AAFED 원 + kakao_talk_logo.png + "보호자 카톡 알림 완료"
 * - GPS:   #79D888 원 + location.png       + "GPS 위치 공유 완료"
 * - FINANCE:#EB5545 원 + no.png            + "금융앱 접근 차단 활성화"
 */
class NotificationPillView(
    context: Context,
    private val type: Type
) : FrameLayout(context) {

    enum class Type {
        KAKAO, GPS, FINANCE
    }

    private var animatorSet: AnimatorSet? = null

    init {
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            // 배경: white/85% + rounded-full + shadow
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D9FFFFFF")) // white/85%
                cornerRadius = dpToPx(20).toFloat()
            }
            elevation = dpToPx(3).toFloat()
            setPadding(dpToPx(12), dpToPx(8), dpToPx(16), dpToPx(8))
        }

        // 아이콘 원형 배경
        val bgColor = when (type) {
            Type.KAKAO -> "#6AAFED"
            Type.GPS -> "#79D888"
            Type.FINANCE -> "#EB5545"
        }
        val iconCircle = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                shape = GradientDrawable.OVAL
            }
        }

        // 아이콘 PNG 이미지
        val iconDrawableRes = when (type) {
            Type.KAKAO -> "kakao_talk_logo"
            Type.GPS -> "location"
            Type.FINANCE -> "no"
        }
        val iconImg = ImageView(context).apply {
            setImageResource(context.resources.getIdentifier(iconDrawableRes, "drawable", context.packageName))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dpToPx(5)
            setPadding(pad, pad, pad, pad)
        }
        iconCircle.addView(iconImg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val iconSize = dpToPx(26)
        pill.addView(iconCircle, LinearLayout.LayoutParams(iconSize, iconSize).apply {
            marginEnd = dpToPx(8)
        })

        // 텍스트
        val label = when (type) {
            Type.KAKAO -> "보호자 카톡 알림 완료"
            Type.GPS -> "GPS 위치 공유 완료"
            Type.FINANCE -> "금융앱 접근 차단 활성화"
        }
        val labelTv = TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#1F2937")) // gray-800
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        pill.addView(labelTv)

        addView(pill, LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
        ))

        // 초기 상태: 오른쪽 밖에 숨김
        translationX = dpToPx(300).toFloat()
        alpha = 0f
    }

    /**
     * 슬라이드 인 → 2.5초 유지 → 부드럽게 우측으로 슬라이드 아웃.
     * 모든 애니메이션을 단일 AnimatorSet으로 관리하여 깜빡임 방지.
     */
    fun startSlideInFadeOut(onFinished: () -> Unit) {
        // 입장: 슬라이드 인 + 페이드 인
        val slideIn = ObjectAnimator.ofFloat(this, "translationX", dpToPx(300).toFloat(), 0f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
        }
        val fadeIn = ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
            duration = 300
        }

        // 퇴장: 우측으로 슬라이드 아웃 + 페이드 아웃 (깜빡임 없이 자연스럽게)
        val slideOut = ObjectAnimator.ofFloat(this, "translationX", 0f, dpToPx(300).toFloat()).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
            startDelay = 2500 // 2.5초 유지 후
        }
        val fadeOut = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f).apply {
            duration = 400
            startDelay = 2500
        }

        animatorSet = AnimatorSet().apply {
            playTogether(slideIn, fadeIn, slideOut, fadeOut)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // 완전히 숨긴 상태에서 콜백 호출 → WindowManager에서 제거
                    visibility = GONE
                    onFinished()
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animatorSet?.cancel()
        animatorSet = null
    }

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}

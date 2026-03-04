package com.s14p21a404.shutter.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 영상통화 감지 경고 오버레이 매니저
 *
 * WindowManager.addView()를 사용하여 TYPE_APPLICATION_OVERLAY로
 * 영상통화 화면 **중앙**에 경고 카드를 표시한다.
 *
 * WRAP_CONTENT + FLAG_NOT_TOUCH_MODAL 조합으로
 * 카드 바깥 영역(영상통화 앱의 통화종료 버튼 등)은 그대로 터치 가능.
 * 통화 종료 시 Service의 evaluate()에서 자동 dismiss.
 */
class WarningOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    /**
     * 오버레이 경고 카드 표시
     */
    fun show() {
        if (overlayView != null) {
            Log.d(TAG, "오버레이 이미 표시 중")
            return
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_NOT_FOCUSABLE: 포커스를 가져가지 않음
            // FLAG_NOT_TOUCH_MODAL: 카드 바깥 터치를 아래 앱으로 전달
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val view = buildCardView()
        overlayView = view

        try {
            windowManager.addView(view, params)
            Log.d(TAG, "오버레이 카드 표시 완료")
        } catch (e: Exception) {
            Log.e(TAG, "오버레이 표시 실패", e)
            overlayView = null
        }
    }

    /**
     * 오버레이 경고 카드 제거
     */
    fun dismiss() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
                Log.d(TAG, "오버레이 제거 완료")
            } catch (e: Exception) {
                Log.e(TAG, "오버레이 제거 실패", e)
            }
            overlayView = null
        }
    }

    /**
     * 오버레이가 현재 표시 중인지
     */
    fun isShowing(): Boolean = overlayView != null

    // ── 뷰 구성 ──

    private fun buildCardView(): View {
        // 카드 컨테이너
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(28), dpToPx(28), dpToPx(28), dpToPx(28))

            // 카드 배경: 둥근 모서리 + 연한 빨강 + 빨간 테두리
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFFFEBEE"))
                cornerRadius = dpToPx(20).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#FFEF5350"))
            }
            elevation = dpToPx(12).toFloat()
        }

        // 카드 최대 너비 제한을 위한 wrapper
        val screenWidth = context.resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth * 0.85).toInt()

        // ⚠️ 아이콘
        val iconText = TextView(context).apply {
            text = "⚠️"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            gravity = Gravity.CENTER
        }
        card.addView(iconText, wrapParams().apply {
            bottomMargin = dpToPx(12)
        })

        // 제목
        val titleText = TextView(context).apply {
            text = "영상통화 감지"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.parseColor("#FFD32F2F"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        card.addView(titleText, wrapParams().apply {
            bottomMargin = dpToPx(16)
        })

        // 설명
        val descText = TextView(context).apply {
            text = "영상통화가 감지되었습니다.\n딥페이크 분석을 시작합니다."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.parseColor("#FF424242"))
            gravity = Gravity.CENTER
            setLineSpacing(dpToPx(4).toFloat(), 1f)
        }
        card.addView(descText, wrapParams().apply {
            bottomMargin = dpToPx(8)
        })

        // 주의 문구
        val cautionText = TextView(context).apply {
            text = "주의: 상대방 영상이 조작되었을 수 있습니다."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#FFE53935"))
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        card.addView(cautionText, wrapParams().apply {
            bottomMargin = dpToPx(16)
        })

        // 안내 문구 (통화 종료 시 자동으로 닫힘)
        val hintText = TextView(context).apply {
            text = "통화 종료 시 자동으로 닫힙니다."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#FF9E9E9E"))
            gravity = Gravity.CENTER
        }
        card.addView(hintText, wrapParams())

        // 카드 너비 고정 wrapper
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val cardParams = LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
        wrapper.addView(card, cardParams)

        return wrapper
    }

    // ── 유틸리티 ──

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    private fun wrapParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    companion object {
        private const val TAG = "WarningOverlayManager"
    }
}

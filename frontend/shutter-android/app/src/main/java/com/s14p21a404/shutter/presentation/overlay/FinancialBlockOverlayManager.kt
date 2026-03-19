package com.s14p21a404.shutter.presentation.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import kotlin.math.roundToInt

/**
 * 금융 앱 접근 제한 오버레이.
 *
 * 딥페이크 의심 통화 중 사용자가 금융 앱을 열면
 * 전체 화면을 덮어 접근을 차단한다.
 *
 * 기존 WarningOverlayManager와 동일한 레이아웃 시스템(glass card)을 사용하되,
 * 배경을 회색 계열 그라데이션으로 변경하고, 하단 hole 없이 전체를 차단한다.
 */
class FinancialBlockOverlayManager(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** 관리 중인 모든 윈도우 */
    private val overlayViews = mutableListOf<View>()

    /** 오버레이 활성화 여부 — false이면 show() 호출을 무시한다. */
    @Volatile
    private var enabled = false

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    /**
     * 오버레이 표시 허용 여부를 설정한다.
     * 딥페이크 감지 시 true, 통화 종료 시 false.
     */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun show() {
        if (!enabled) {
            Log.d(TAG, "금융 차단 오버레이 비활성 상태 — 표시 무시")
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "오버레이 권한 없음 — 금융 차단 오버레이 표시 불가")
            return
        }
        if (overlayViews.isNotEmpty()) {
            Log.d(TAG, "금융 차단 오버레이 already showing")
            return
        }


        // 전체 화면을 덮는 단일 윈도우 (터치 차단)
        addOverlay(
            view = buildFullScreenOverlay(),
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            gravity = Gravity.CENTER,
            touchable = true
        )

        Log.d(TAG, "금융 차단 오버레이 표시")
    }

    fun dismiss() {
        overlayViews.forEach { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "overlay remove error", e)
            }
        }
        overlayViews.clear()
        Log.d(TAG, "금융 차단 오버레이 해제")
    }

    fun isShowing(): Boolean = overlayViews.isNotEmpty()

    // ─────────────────────────────────────────────
    // Window 헬퍼
    // ─────────────────────────────────────────────

    private fun addOverlay(
        view: View,
        width: Int,
        height: Int,
        gravity: Int,
        touchable: Boolean,
        yOffset: Int = 0
    ) {
        val flags = if (touchable) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        val params = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.y = yOffset
        }

        try {
            windowManager.addView(view, params)
            overlayViews.add(view)
        } catch (e: Exception) {
            Log.e(TAG, "overlay add error", e)
        }
    }

    // ─────────────────────────────────────────────
    // Full Screen Overlay (dim + card)
    // ─────────────────────────────────────────────

    private fun buildFullScreenOverlay(): View {
        val root = FrameLayout(context).apply {
            setOnTouchListener { _, _ -> true }  // 터치 차단
        }

        // dim 배경 (회색 계열)
        val dimView = View(context).apply {
            setBackgroundColor(Color.parseColor("#CC1A1A2E"))
        }
        root.addView(dimView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // glass card
        root.addView(buildGlassCard(), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        return root
    }

    // ─────────────────────────────────────────────
    // Glass Card (회색 계열 테마)
    // ─────────────────────────────────────────────

    private fun buildGlassCard(): View {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth * 0.72f).roundToInt()
        val borderWidth = dpToPx(2)
        val radius = dpToPx(24).toFloat()

        val borderFrame = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#AAFFFFFF"),
                    Color.parseColor("#446B7B8D"),
                    Color.parseColor("#11FFFFFF")
                )
            ).apply { cornerRadius = radius }
            elevation = dpToPx(6).toFloat()
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(22), dpToPx(18), dpToPx(22), dpToPx(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#CC4A5568"),   // 회색 상단
                    Color.parseColor("#33333333"),   // 회색 중간
                    Color.parseColor("#22FFFFFF")    // 밝은 하단
                )
            ).apply { cornerRadius = radius }
        }

        borderFrame.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(borderWidth, borderWidth, borderWidth, borderWidth) })

        val wrapper = LinearLayout(context).apply { gravity = Gravity.CENTER }
        wrapper.addView(borderFrame, LinearLayout.LayoutParams(
            cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        buildCardContent(card)
        return wrapper
    }

    // ─────────────────────────────────────────────
    // Card Content
    // ─────────────────────────────────────────────

    private fun buildCardContent(parent: LinearLayout) {

        // icon: rounded square with warning triangle
        val iconBorderSize = dpToPx(60)
        val iconInnerSize = dpToPx(56)
        val iconRadius = dpToPx(16).toFloat()

        val iconRing = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#CCFFFFFF"),
                    Color.parseColor("#446B7B8D"),
                    Color.parseColor("#22FFFFFF")
                )
            ).apply { cornerRadius = iconRadius }
        }

        val iconContainer = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#66FFFFFF"),
                    Color.parseColor("#33FFFFFF"),
                    Color.parseColor("#22FFFFFF")
                )
            ).apply { cornerRadius = iconRadius }
        }

        val icon = TextView(context).apply {
            text = "⚠"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        iconContainer.addView(icon, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))
        iconRing.addView(iconContainer, FrameLayout.LayoutParams(
            iconInnerSize, iconInnerSize, Gravity.CENTER
        ))
        parent.addView(iconRing, LinearLayout.LayoutParams(
            iconBorderSize, iconBorderSize
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(16); bottomMargin = dpToPx(12)
        })

        // title
        parent.addView(TextView(context).apply {
            text = "금융 앱 접근 제한"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
        }, wrapParams().apply { bottomMargin = dpToPx(16) })

        // divider
        parent.addView(View(context).apply {
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(2)
        ).apply { bottomMargin = dpToPx(20) })

        // description text (primary)
        parent.addView(TextView(context).apply {
            text = "딥페이크 의심 전화와 통화 중입니다."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#FF8A80"))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }, wrapParams().apply { bottomMargin = dpToPx(8) })

        // description text (secondary)
        parent.addView(TextView(context).apply {
            text = "통화 상대와의 거래를 권장하지 않습니다."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#B3FFFFFF"))
            gravity = Gravity.CENTER
        }, wrapParams().apply { bottomMargin = dpToPx(24) })

        // guide text
        parent.addView(TextView(context).apply {
            text = "통화를 종료해주세요 ▼"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#80FFFFFF"))
            gravity = Gravity.CENTER
        }, wrapParams().apply { bottomMargin = dpToPx(16) })
    }

    // ─────────────────────────────────────────────
    // Utils
    // ─────────────────────────────────────────────

    private fun wrapParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()

    companion object {
        private const val TAG = "FinancialBlockOverlay"
    }
}


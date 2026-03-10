package com.s14p21a404.shutter.presentation.overlay

import android.content.Context
import android.graphics.Bitmap
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
import com.s14p21a404.shutter.domain.model.DeepfakeDetectionResult
import kotlin.math.roundToInt

class WarningOverlayManager(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** 관리 중인 모든 윈도우 */
    private val overlayViews = mutableListOf<View>()

    private var facePreviewImageView: ImageView? = null

    /** 상단 오버레이 높이 비율 */
    private val topRatio = 0.78f

    /** 하단 hole 위치/크기 (하단 윈도우 내 비율) */
    private val holeCenterXRatio = 0.5f
    private val holeCenterYRatio = 0.55f
    private val holeRadiusRatio = 0.1f

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    /**
     * 감지 결과에 따라 오버레이를 표시하거나 숨긴다.
     */
    fun onDetectionResult(result: DeepfakeDetectionResult) {
        if (result.detected) {
            showWarning()
        } else {
            dismissWarning()
        }
    }

    fun showWarning() {
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "오버레이 권한 없음 — 경고 표시 불가")
            return
        }
        show()
    }

    fun dismissWarning() {
        dismiss()
    }

    fun show() {
        if (overlayViews.isNotEmpty()) {
            Log.d(TAG, "overlay already showing")
            return
        }

        val dm = context.resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val topHeight = (screenH * topRatio).roundToInt()
        val bottomHeight = screenH - topHeight

        // hole의 절대 좌표 (하단 윈도우 내)
        val holeCx = (screenW * holeCenterXRatio).roundToInt()
        val holeCy = (bottomHeight * holeCenterYRatio).roundToInt()
        val holeR = (screenW * holeRadiusRatio).roundToInt()

        // ────────────────────────────────────
        // 1) 상단 윈도우: 카드 + dim (터치 차단)
        // ────────────────────────────────────
        addOverlay(
            view = buildTopOverlay(),
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = topHeight,
            gravity = Gravity.TOP,
            touchable = true
        )

        // ────────────────────────────────────
        // 2) 하단 시각 레이어: hole 뚫린 dim (터치 통과)
        // ────────────────────────────────────
        addOverlay(
            view = HoledOverlayView(
                context = context,
                holeCenterXRatio = holeCenterXRatio,
                holeCenterYRatio = holeCenterYRatio,
                holeRadiusRatio = holeRadiusRatio,
                dimColor = Color.parseColor("#CC000000"),
                fadeWidth = dpToPx(20).toFloat()
            ),
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = bottomHeight,
            gravity = Gravity.BOTTOM,
            touchable = false   // FLAG_NOT_TOUCHABLE — 시각 전용
        )

        // ────────────────────────────────────
        // 3) 터치 차단 윈도우: hole 밖 영역 (투명, 터치만 차단)
        // ────────────────────────────────────
        val holeTop = holeCy - holeR
        val holeBottom = holeCy + holeR
        val holeLeft = holeCx - holeR
        val holeRight = holeCx + holeR
        val holeDiameter = holeR * 2

        // 위쪽 strip
        if (holeTop > 0) {
            addOverlay(
                view = transparentBlockView(),
                width = screenW,
                height = holeTop,
                gravity = Gravity.BOTTOM or Gravity.START,
                touchable = true,
                yOffset = bottomHeight - holeTop
            )
        }

        // 좌측 strip
        if (holeLeft > 0) {
            addOverlay(
                view = transparentBlockView(),
                width = holeLeft,
                height = holeDiameter,
                gravity = Gravity.BOTTOM or Gravity.START,
                touchable = true,
                yOffset = bottomHeight - holeBottom
            )
        }

        // 우측 strip
        val rightWidth = screenW - holeRight
        if (rightWidth > 0) {
            addOverlay(
                view = transparentBlockView(),
                width = rightWidth,
                height = holeDiameter,
                gravity = Gravity.BOTTOM or Gravity.END,
                touchable = true,
                yOffset = bottomHeight - holeBottom
            )
        }

        // 아래쪽 strip
        val belowHeight = bottomHeight - holeBottom
        if (belowHeight > 0) {
            addOverlay(
                view = transparentBlockView(),
                width = screenW,
                height = belowHeight,
                gravity = Gravity.BOTTOM or Gravity.START,
                touchable = true,
                yOffset = 0
            )
        }
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
        facePreviewImageView = null
    }

    fun isShowing(): Boolean = overlayViews.isNotEmpty()

    fun updateFacePreview(bitmap: Bitmap?) {
        facePreviewImageView?.post {
            if (bitmap == null) {
                facePreviewImageView?.visibility = View.GONE
                return@post
            }
            facePreviewImageView?.setImageBitmap(bitmap)
            facePreviewImageView?.visibility = View.VISIBLE
        }
    }

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

    /** 투명하지만 터치를 차단하는 빈 View */
    private fun transparentBlockView(): View {
        return View(context).apply {
            setOnTouchListener { _, _ -> true }
        }
    }

    // ─────────────────────────────────────────────
    // Top Overlay (카드 + dim 배경)
    // ─────────────────────────────────────────────

    private fun buildTopOverlay(): View {

        val fadeHeight = dpToPx(48)

        val root = FrameLayout(context).apply {
            setOnTouchListener { _, _ -> true }
        }

        // dim 배경
        val dimView = View(context).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
        }
        root.addView(dimView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { bottomMargin = fadeHeight })

        // 하단 fade
        val fadeView = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#CC000000"), Color.TRANSPARENT)
            )
        }
        root.addView(fadeView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, fadeHeight, Gravity.BOTTOM
        ))

        // glass card
        root.addView(buildGlassCard(), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        return root
    }

    // ─────────────────────────────────────────────
    // Glass Card
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
                    Color.parseColor("#44A67C5B"),
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
                    Color.parseColor("#CCA67C5B"),
                    Color.parseColor("#33444444"),
                    Color.parseColor("#22FFFFFF")
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

        // icon
        val iconBorderSize = dpToPx(60)
        val iconInnerSize = dpToPx(56)

        val iconRing = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#CCFFFFFF"),
                    Color.parseColor("#44A67C5B"),
                    Color.parseColor("#22FFFFFF")
                )
            ).apply { shape = GradientDrawable.OVAL }
        }

        val iconContainer = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#66FFFFFF"),
                    Color.parseColor("#33FFFFFF"),
                    Color.parseColor("#22FFFFFF")
                )
            ).apply { shape = GradientDrawable.OVAL }
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
            text = "AI 위조 감지"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }, wrapParams().apply { bottomMargin = dpToPx(16) })

        // divider
        parent.addView(View(context).apply {
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(2)
        ).apply { bottomMargin = dpToPx(20) })

        // preview container
        val previewBox = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#AAAAAA"))
                setStroke(dpToPx(1), Color.parseColor("#26FFFFFF"))
            }
        }

        facePreviewImageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }

        previewBox.addView(facePreviewImageView, FrameLayout.LayoutParams(
            dpToPx(72), dpToPx(72), Gravity.CENTER
        ))
        parent.addView(previewBox, LinearLayout.LayoutParams(
            dpToPx(180), dpToPx(180)
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dpToPx(24)
        })

        // warning text
        parent.addView(TextView(context).apply {
            text = "딥페이크 의심 영상이 감지되었습니다"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#FF8A80"))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }, wrapParams().apply { bottomMargin = dpToPx(4) })

        // guide text
        parent.addView(TextView(context).apply {
            text = "통화를 종료해주세요 ▼"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#B3FFFFFF"))
            gravity = Gravity.CENTER
        }, wrapParams().apply { bottomMargin = dpToPx(24) })
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
        private const val TAG = "WarningOverlay"
    }
}


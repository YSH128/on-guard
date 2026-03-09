package com.s14p21a404.shutter.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import kotlin.math.roundToInt

class WarningOverlayManager(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: View? = null
    private var facePreviewImageView: ImageView? = null

    /** 화면 상단 overlay 비율 */
    private val overlayRatio = 0.78

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    fun show() {

        if (overlayView != null) {
            Log.d(TAG, "overlay already showing")
            return
        }

        val screenHeight = context.resources.displayMetrics.heightPixels
        val overlayHeight = (screenHeight * overlayRatio).roundToInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
        }

        val view = buildOverlayView()
        overlayView = view

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "overlay add error", e)
            overlayView = null
        }
    }

    fun dismiss() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "overlay remove error", e)
            }
        }
        overlayView = null
        facePreviewImageView = null
    }

    fun isShowing(): Boolean = overlayView != null

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
    // Overlay Layout
    // ─────────────────────────────────────────────

    private fun buildOverlayView(): View {

        val fadeHeight = dpToPx(48)

        val root = FrameLayout(context).apply {
            setOnTouchListener { _, _ -> true }
        }

        // 검정 배경 (하단 fade 영역만큼 짧게)
        val dimView = View(context).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
        }

        root.addView(
            dimView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                bottomMargin = fadeHeight
            }
        )

        // 하단 fade out (좁은 폭, 검정 → 투명)
        val fadeView = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#CC000000"),
                    Color.TRANSPARENT
                )
            )
        }

        root.addView(
            fadeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                fadeHeight,
                Gravity.BOTTOM
            )
        )

        // glass card — 중앙 배치 (fade 영향 없음)
        val card = buildGlassCard()

        root.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        )

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

        // 바깥쪽: 그라데이션 테두리 역할
        val borderFrame = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#AAFFFFFF"),   // 위쪽 밝은 테두리
                    Color.parseColor("#44A67C5B"),   // 중간 brown 테두리
                    Color.parseColor("#11FFFFFF")    // 아래쪽 연한 테두리
                )
            ).apply {
                cornerRadius = radius
            }
            elevation = dpToPx(6).toFloat()
        }

        // 안쪽: 실제 카드 콘텐츠
        val card = LinearLayout(context).apply {

            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL

            setPadding(
                dpToPx(22),
                dpToPx(18),
                dpToPx(22),
                dpToPx(18)
            )

            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#CCA67C5B"),  // 위쪽 brown glass
                    Color.parseColor("#33444444"),   // 중간 무채색 전환
                    Color.parseColor("#22FFFFFF")   // 아래쪽 무채색 glass
                )
            ).apply {
                cornerRadius = radius
            }
        }

        borderFrame.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(borderWidth, borderWidth, borderWidth, borderWidth)
            }
        )

        val wrapper = LinearLayout(context).apply {
            gravity = Gravity.CENTER
        }

        wrapper.addView(
            borderFrame,
            LinearLayout.LayoutParams(
                cardWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

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

        // 바깥쪽: 그라데이션 링
        val iconRing = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#CCFFFFFF"),   // 위쪽 밝은 링
                    Color.parseColor("#44A67C5B"),   // 중간 brown 링
                    Color.parseColor("#22FFFFFF")    // 아래쪽 연한 링
                )
            ).apply {
                shape = GradientDrawable.OVAL
            }
        }

        // 안쪽: 아이콘 배경
        val iconContainer = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#66FFFFFF"),
                    Color.parseColor("#33FFFFFF"),
                    Color.parseColor("#22FFFFFF")
                )
            ).apply {
                shape = GradientDrawable.OVAL
            }
        }

        val icon = TextView(context).apply {
            text = "⚠"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        iconContainer.addView(
            icon,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        iconRing.addView(
            iconContainer,
            FrameLayout.LayoutParams(
                iconInnerSize,
                iconInnerSize,
                Gravity.CENTER
            )
        )

        parent.addView(
            iconRing,
            LinearLayout.LayoutParams(
                iconBorderSize,
                iconBorderSize
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dpToPx(16)
                bottomMargin = dpToPx(12)
            }
        )

        // title
        parent.addView(TextView(context).apply {

            text = "AI 위조 감지"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.WHITE)

            typeface = Typeface.create(
                "sans-serif-medium",
                Typeface.BOLD
            )

        }, wrapParams().apply {
            bottomMargin = dpToPx(16)
        })

        // divider
        parent.addView(View(context).apply {

            setBackgroundColor(Color.parseColor("#33FFFFFF"))

        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(2)
        ).apply {
            bottomMargin = dpToPx(20)
        })


        // preview container
        val previewBox = FrameLayout(context).apply {

            background = GradientDrawable().apply {

                setColor(Color.parseColor("#AAAAAA"))

                setStroke(
                    dpToPx(1),
                    Color.parseColor("#26FFFFFF")
                )
            }
        }

        facePreviewImageView = ImageView(context).apply {

            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }

        previewBox.addView(
            facePreviewImageView,
            FrameLayout.LayoutParams(
                dpToPx(72),
                dpToPx(72),
                Gravity.CENTER
            )
        )

        parent.addView(
            previewBox,
            LinearLayout.LayoutParams(
                dpToPx(180),
                dpToPx(180)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(24)
            }
        )

        // warning text
        parent.addView(TextView(context).apply {

            text = "딥페이크 의심 영상이 감지되었습니다"

            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)

            setTextColor(Color.parseColor("#FF8A80"))

            gravity = Gravity.CENTER

            typeface = Typeface.DEFAULT_BOLD

        }, wrapParams().apply {
            bottomMargin = dpToPx(4)
        })

        // guide text
        parent.addView(TextView(context).apply {

            text = "통화를 종료해주세요 ▼"

            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)

            setTextColor(Color.parseColor("#B3FFFFFF"))

            gravity = Gravity.CENTER

        }, wrapParams().apply {
            bottomMargin = dpToPx(24)
        })
    }

    // ─────────────────────────────────────────────
    // Utils
    // ─────────────────────────────────────────────

    private fun wrapParams(): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun dpToPx(dp: Int): Int {

        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    companion object {
        private const val TAG = "WarningOverlay"
    }
}
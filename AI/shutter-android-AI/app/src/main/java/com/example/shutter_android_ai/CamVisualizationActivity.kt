package com.example.shutter_android_ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class CamVisualizationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cam_visualization)

        val ivCamResult    = findViewById<ImageView>(R.id.ivCamResult)
        val tvBboxInfo     = findViewById<TextView>(R.id.tvBboxInfo)
        val btnBack        = findViewById<Button>(R.id.btnBack)
        val seekbarOpacity = findViewById<SeekBar>(R.id.seekbarOpacity)

        btnBack.setOnClickListener { finish() }

        val results    = MainActivity.lastResults
        val baseBitmap = MainActivity.lastBitmap

        // 최소 9개: fakeProb, realProb, loadTime, inferTime + cam×N + minx,miny,maxx,maxy,bbox_score
        if (results.size < 9 || baseBitmap == null) {
            tvBboxInfo.text = "결과 없음: 먼저 페이스 감지를 실행하세요"
            return
        }

        // 결과 파싱
        val fakeProb = results[0]
        val realProb = results[1]
        val camSize  = results.size - 9          // 전체 - (앞 4 + 뒤 5)
        val camData  = results.sliceArray(4 until 4 + camSize)

        val bboxOff   = 4 + camSize
        val minx      = results[bboxOff]
        val miny      = results[bboxOff + 1]
        val maxx      = results[bboxOff + 2]
        val maxy      = results[bboxOff + 3]
        val bboxScore = results.getOrElse(bboxOff + 4) { -1f }

        val camSide = sqrt(camSize.toDouble()).toInt()
        tvBboxInfo.text = "[${if (fakeProb > realProb) "FAKE" else "REAL"}]  " +
            "Fake: ${"%.1f%%".format(fakeProb * 100)}  Real: ${"%.1f%%".format(realProb * 100)}\n" +
            "ROI: (${"%.0f".format(minx)}, ${"%.0f".format(miny)}) ~ " +
            "(${"%.0f".format(maxx)}, ${"%.0f".format(maxy)})" +
            if (bboxScore >= 0f) "  score=${"%.3f".format(bboxScore)}" else ""

        // 초기 렌더링 (SeekBar 기본값 50%)
        renderOverlay(ivCamResult, baseBitmap, camData, camSide, minx, miny, maxx, maxy, 128)

        seekbarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val alpha = progress * 255 / 100
                renderOverlay(ivCamResult, baseBitmap, camData, camSide, minx, miny, maxx, maxy, alpha)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    /**
     * baseBitmap 위에 CAM 히트맵(alpha 적용) + ROI bbox 를 합성하여 ImageView에 설정
     *
     * @param alpha  CAM 히트맵 투명도 [0=완전 투명 ~ 255=불투명]
     */
    private fun renderOverlay(
        iv: ImageView,
        baseBitmap: Bitmap,
        camData: FloatArray,
        camSide: Int,
        minx: Float, miny: Float, maxx: Float, maxy: Float,
        alpha: Int
    ) {
        val displaySize = 300  // dp와 무관하게 pixel 기준으로 합성

        // 1. 베이스 이미지를 displaySize×displaySize 로 스케일
        val result = Bitmap.createScaledBitmap(baseBitmap, displaySize, displaySize, true)
            .copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // 2. CAM 히트맵 오버레이
        if (camSide > 0 && camSide * camSide == camData.size) {
            val heatmap = createJetHeatmap(camData, camSide, displaySize, alpha)
            canvas.drawBitmap(heatmap, 0f, 0f, null)
        }

        // 3. ROI 바운딩박스 (모델 좌표 0~224 → 디스플레이 스케일 변환)
        val hasValidBbox = minx >= 0f && miny >= 0f && maxx > minx && maxy > miny
        if (hasValidBbox) {
            val scaleX = displaySize / 224f
            val scaleY = displaySize / 224f
            val bboxPaint = Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = 4f
                isAntiAlias = true
            }
            canvas.drawRect(
                minx * scaleX, miny * scaleY,
                maxx * scaleX, maxy * scaleY,
                bboxPaint
            )
            // 코너 강조 (ROI 더 잘 보이게)
            val cornerLen = min(maxx - minx, maxy - miny) * scaleX * 0.15f
            val cornerPaint = Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            val l = minx * scaleX; val t = miny * scaleY
            val r = maxx * scaleX; val b = maxy * scaleY
            // 좌상단
            canvas.drawLine(l, t, l + cornerLen, t, cornerPaint)
            canvas.drawLine(l, t, l, t + cornerLen, cornerPaint)
            // 우상단
            canvas.drawLine(r, t, r - cornerLen, t, cornerPaint)
            canvas.drawLine(r, t, r, t + cornerLen, cornerPaint)
            // 좌하단
            canvas.drawLine(l, b, l + cornerLen, b, cornerPaint)
            canvas.drawLine(l, b, l, b - cornerLen, cornerPaint)
            // 우하단
            canvas.drawLine(r, b, r - cornerLen, b, cornerPaint)
            canvas.drawLine(r, b, r, b - cornerLen, cornerPaint)
        }

        iv.setImageBitmap(result)
    }

    /**
     * CAM 값 배열 → JET 컬러맵 히트맵 Bitmap
     *
     * @param camData  CAM 값 (side×side)
     * @param side     CAM 그리드 한 변 크기 (보통 7)
     * @param size     출력 Bitmap 크기 (정사각형)
     * @param alpha    픽셀 알파값 [0~255]
     */
    private fun createJetHeatmap(camData: FloatArray, side: Int, size: Int, alpha: Int): Bitmap {
        // 정규화
        val minVal = camData.min() ?: 0f
        val maxVal = camData.max() ?: 1f
        val range  = if (abs(maxVal - minVal) > 1e-6f) maxVal - minVal else 1f

        val small = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        for (r in 0 until side) {
            for (c in 0 until side) {
                val t = (camData[r * side + c] - minVal) / range   // 0.0 ~ 1.0
                small.setPixel(c, r, jetColor(t, alpha))
            }
        }

        // 가장 가까운 픽셀 보간 없이 부드럽게 업스케일
        return Bitmap.createScaledBitmap(small, size, size, true)
    }

    /**
     * JET 컬러맵: 0(파랑) → 0.5(초록) → 1(빨강)
     * OpenCV의 COLORMAP_JET 와 동일한 근사 공식 사용
     */
    private fun jetColor(t: Float, alpha: Int): Int {
        val r = clamp01(1.5f - abs(4f * t - 3f))
        val g = clamp01(1.5f - abs(4f * t - 2f))
        val b = clamp01(1.5f - abs(4f * t - 1f))
        return Color.argb(alpha, (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }

    private fun clamp01(v: Float) = v.coerceIn(0f, 1f)
}

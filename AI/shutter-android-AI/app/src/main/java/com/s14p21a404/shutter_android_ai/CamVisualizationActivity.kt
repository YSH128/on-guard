package com.s14p21a404.shutter_android_ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.s14p21a404.shutter_android_ai.databinding.ActivityCamVisualizationBinding
import kotlin.math.sqrt

class CamVisualizationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCamVisualizationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCamVisualizationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val result = MainActivity.lastDetectionResult
        if (result != null && result.size >= 9) {
            renderResult(result)
        } else {
            binding.tvBboxInfo.text = "표시할 결과가 없습니다"
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    // ──────────────────────────────────────────────────────────
    // 결과 파싱 및 렌더링
    // ──────────────────────────────────────────────────────────

    private fun renderResult(result: FloatArray) {
        val fakeProb  = result[0]
        val realProb  = result[1]
        val camSize   = result.size - 9
        val camData   = result.copyOfRange(4, 4 + camSize)
        val bboxOffset = 4 + camSize
        val minx       = result[bboxOffset]
        val miny       = result[bboxOffset + 1]
        val maxx       = result[bboxOffset + 2]
        val maxy       = result[bboxOffset + 3]
        val bboxScore  = result[bboxOffset + 4]

        val bitmap = renderCamHeatmap(camData, minx, miny, maxx, maxy)
        binding.ivCamResult.setImageBitmap(bitmap)

        binding.tvBboxInfo.text = buildString {
            appendLine("Fake: ${"%.1f".format(fakeProb * 100)}%   Real: ${"%.1f".format(realProb * 100)}%")
            if (minx >= 0f) {
                appendLine("BBox: (${minx.toInt()}, ${miny.toInt()}) ~ (${maxx.toInt()}, ${maxy.toInt()})")
                appendLine("BBox Score: ${"%.4f".format(bboxScore)}")
            } else {
                appendLine("BBox: 감지 안됨")
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    // CAM 히트맵 렌더링 (224×224)
    // ──────────────────────────────────────────────────────────

    private fun renderCamHeatmap(
        camData: FloatArray,
        bboxMinx: Float, bboxMiny: Float,
        bboxMaxx: Float, bboxMaxy: Float
    ): Bitmap {
        val outputSize = 224
        val camDim = sqrt(camData.size.toDouble()).toInt() // 7

        // CAM 정규화 (0~1)
        val minVal = camData.minOrNull() ?: 0f
        val maxVal = camData.maxOrNull() ?: 1f
        val range  = (maxVal - minVal).coerceAtLeast(1e-6f)

        val bitmap = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 배경 검정
        canvas.drawColor(Color.BLACK)

        // CAM 셀 색칠 (jet colormap, 반투명)
        val cellSize = outputSize.toFloat() / camDim
        val cellPaint = Paint().apply { style = Paint.Style.FILL }

        for (r in 0 until camDim) {
            for (c in 0 until camDim) {
                val normalized = (camData[r * camDim + c] - minVal) / range
                val color = jetColormap(normalized)
                cellPaint.color = Color.argb(200, Color.red(color), Color.green(color), Color.blue(color))
                canvas.drawRect(
                    c * cellSize, r * cellSize,
                    (c + 1) * cellSize, (r + 1) * cellSize,
                    cellPaint
                )
            }
        }

        // Bounding Box (빨간 테두리)
        if (bboxMinx >= 0f) {
            val bboxPaint = Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawRect(bboxMinx, bboxMiny, bboxMaxx, bboxMaxy, bboxPaint)

            // 꼭짓점 마커
            val dotPaint = Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.FILL
            }
            canvas.drawCircle(bboxMinx, bboxMiny, 6f, dotPaint)
            canvas.drawCircle(bboxMaxx, bboxMaxy, 6f, dotPaint)
        }

        return bitmap
    }

    // ──────────────────────────────────────────────────────────
    // Jet Colormap (blue→cyan→green→yellow→red)
    // ──────────────────────────────────────────────────────────

    private fun jetColormap(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        val r = (1.5f - Math.abs(v * 4f - 3f)).coerceIn(0f, 1f)
        val g = (1.5f - Math.abs(v * 4f - 2f)).coerceIn(0f, 1f)
        val b = (1.5f - Math.abs(v * 4f - 1f)).coerceIn(0f, 1f)
        return Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }
}

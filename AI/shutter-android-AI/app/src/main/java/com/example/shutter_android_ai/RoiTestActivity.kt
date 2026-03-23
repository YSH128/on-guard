package com.example.shutter_android_ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * ROI / CAM 출력 검증 Activity
 *
 * fakeVideo.mp4 에서 랜덤 프레임을 추출 → JNI 추론 → 시각화
 *
 * 화면 구성:
 *   - ImageView: 원본 프레임 + CAM 히트맵(JET) + ROI bbox (빨간 사각형 + 노란 코너)
 *   - SeekBar: CAM 히트맵 투명도 실시간 조절
 *   - TextView: fakeProb / realProb / ROI 좌표 / bbox_score / CAM 통계 / 성능
 *
 * 반환 배열 형식 (native-lib.cpp 기준):
 *   [0]        fakeProb
 *   [1]        realProb
 *   [2]        faceLoadTime (ms)
 *   [3]        inferTime (ms)
 *   [4..4+N-1] cam × N  (보통 N=49, 7×7)
 *   [4+N]      minx
 *   [4+N+1]    miny
 *   [4+N+2]    maxx
 *   [4+N+3]    maxy
 *   [4+N+4]    bbox_score
 */
class RoiTestActivity : AppCompatActivity() {

    private lateinit var ivFrame: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvInfo: TextView
    private lateinit var seekbarOpacity: SeekBar
    private lateinit var btnRefresh: Button
    private lateinit var btnBack: Button

    // SeekBar 변경 시 재렌더를 위한 현재 상태 저장
    private var currentBitmap: Bitmap? = null
    private var currentCamData: FloatArray = floatArrayOf()
    private var currentCamSide: Int = 0
    private var currentMinx: Float = -1f
    private var currentMiny: Float = -1f
    private var currentMaxx: Float = -1f
    private var currentMaxy: Float = -1f
    private var currentFakeProb: Float = 0f
    private var currentBboxScore: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_roi_test)

        ivFrame        = findViewById(R.id.ivRoiFrame)
        tvStatus       = findViewById(R.id.tvRoiStatus)
        tvInfo         = findViewById(R.id.tvRoiInfo)
        seekbarOpacity = findViewById(R.id.seekbarRoiOpacity)
        btnRefresh     = findViewById(R.id.btnRoiRefresh)
        btnBack        = findViewById(R.id.btnRoiBack)

        btnBack.setOnClickListener { finish() }

        btnRefresh.setOnClickListener {
            btnRefresh.isEnabled = false
            runInferenceOnRandomFrame()
        }

        seekbarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (currentBitmap != null && currentCamSide > 0) {
                    renderOverlay(progress * 255 / 100)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // 진입 즉시 자동 실행
        runInferenceOnRandomFrame()
    }

    // ─────────────────────────────────────────────────────────────────────
    // 추론 실행
    // ─────────────────────────────────────────────────────────────────────

    private fun runInferenceOnRandomFrame() {
        tvStatus.text = "랜덤 프레임 추출 및 추론 중..."
        tvInfo.text   = ""

        thread {
            try {
                val infer = MainActivity.nativeInfer
                if (infer == null) {
                    runOnUiThread {
                        tvStatus.text = "에러: 모델이 로드되지 않았습니다. 뒤로가서 '모델 로드' 버튼을 먼저 누르세요."
                        btnRefresh.isEnabled = true
                    }
                    return@thread
                }

                val videoPath = getAssetFilePath("fakeVideo.mp4")
                val frameResult = extractRandomFrame(videoPath)
                if (frameResult == null) {
                    runOnUiThread {
                        tvStatus.text = "에러: 프레임 추출 실패 (fakeVideo.mp4 확인 필요)"
                        btnRefresh.isEnabled = true
                    }
                    return@thread
                }
                val (bitmap, frameTimeMs, totalDurationMs) = frameResult
                currentBitmap = bitmap

                val rgbaBytes = bitmapToRgba(bitmap)
                val results   = infer(rgbaBytes, bitmap.width, bitmap.height, bitmap.width * 4)

                if (results.isEmpty() || results[0] < -0.5f) {
                    val code = results.getOrElse(0) { -99f }
                    runOnUiThread {
                        tvStatus.text = "추론 실패 (에러 코드: $code)"
                        btnRefresh.isEnabled = true
                    }
                    return@thread
                }

                // 결과 파싱
                val fakeProb  = results[0]
                val realProb  = results[1]
                val loadTime  = results[2]
                val inferTime = results[3]
                val camSize   = results.size - 9
                val camData   = results.sliceArray(4 until 4 + camSize)
                val bboxOff   = 4 + camSize
                val minx      = results.getOrElse(bboxOff)     { -1f }
                val miny      = results.getOrElse(bboxOff + 1) { -1f }
                val maxx      = results.getOrElse(bboxOff + 2) { -1f }
                val maxy      = results.getOrElse(bboxOff + 3) { -1f }
                val bboxScore = results.getOrElse(bboxOff + 4) { -1f }
                val camSide   = sqrt(camSize.toDouble()).toInt()

                currentCamData  = camData
                currentCamSide  = camSide
                currentMinx     = minx
                currentMiny     = miny
                currentMaxx     = maxx
                currentMaxy     = maxy
                currentFakeProb = fakeProb
                currentBboxScore = bboxScore

                runOnUiThread {
                    renderOverlay(seekbarOpacity.progress * 255 / 100)

                    val verdict   = if (fakeProb > realProb) "FAKE" else "REAL"
                    val bboxValid = minx >= 0f && miny >= 0f && maxx > minx && maxy > miny

                    tvInfo.text = buildString {
                        appendLine("═══════════════════════════")
                        appendLine("  판정: $verdict")
                        appendLine("═══════════════════════════")
                        appendLine()
                        appendLine("[ 확률 ]")
                        appendLine("  FakeProb  : ${"%.4f".format(fakeProb)}  (${"%.1f%%".format(fakeProb * 100)})")
                        appendLine("  RealProb  : ${"%.4f".format(realProb)}  (${"%.1f%%".format(realProb * 100)})")
                        appendLine()
                        appendLine("[ ROI 좌표  (모델 기준 0~224) ]")
                        if (bboxValid) {
                            appendLine("  minx   : ${"%.1f".format(minx)}")
                            appendLine("  miny   : ${"%.1f".format(miny)}")
                            appendLine("  maxx   : ${"%.1f".format(maxx)}")
                            appendLine("  maxy   : ${"%.1f".format(maxy)}")
                            appendLine("  width  : ${"%.1f".format(maxx - minx)}")
                            appendLine("  height : ${"%.1f".format(maxy - miny)}")
                        } else {
                            appendLine("  (유효하지 않은 ROI)")
                        }
                        appendLine()
                        appendLine("[ bbox_score ]")
                        appendLine("  score  : ${"%.6f".format(bboxScore)}")
                        appendLine()
                        appendLine("[ CAM ]")
                        appendLine("  원소 수 : $camSize  (${camSide}×${camSide})")
                        if (camData.isNotEmpty()) {
                            appendLine("  min    : ${"%.4f".format(camData.min())}")
                            appendLine("  max    : ${"%.4f".format(camData.max())}")
                            appendLine("  mean   : ${"%.4f".format(camData.average().toFloat())}")
                        }
                        appendLine()
                        appendLine("[ 성능 ]")
                        appendLine("  추론 시간      : ${"%.2f ms".format(inferTime)}")
                        appendLine("  모델 로드 시간 : ${"%.2f ms".format(loadTime)}")
                        appendLine()
                        appendLine("[ 프레임 정보 ]")
                        appendLine("  타임스탬프 : ${frameTimeMs} ms / ${totalDurationMs} ms")
                        appendLine("  해상도     : ${bitmap.width}×${bitmap.height}")
                        appendLine()
                        appendLine("[ raw 배열 크기 ]")
                        appendLine("  results.size = ${results.size}")
                        appendLine("  = 4 헤더 + $camSize CAM + 5 bbox")
                    }

                    tvStatus.text = "완료  (프레임 @${frameTimeMs}ms)"
                    btnRefresh.isEnabled = true
                }

            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "에러: ${e.message}"
                    tvInfo.text   = e.stackTraceToString()
                    btnRefresh.isEnabled = true
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 랜덤 프레임 추출
    // ─────────────────────────────────────────────────────────────────────

    private fun extractRandomFrame(videoPath: String): Triple<Bitmap, Long, Long>? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 1000L
            val randomMs   = Random.nextLong(durationMs.coerceAtLeast(1L))
            val bitmap     = retriever.getFrameAtTime(
                randomMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) Triple(bitmap, randomMs, durationMs) else null
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 오버레이 렌더링  (CAM 히트맵 + ROI bbox)
    // ─────────────────────────────────────────────────────────────────────

    private fun renderOverlay(alpha: Int) {
        val base = currentBitmap ?: return
        val displaySize = 320

        val result = Bitmap.createScaledBitmap(base, displaySize, displaySize, true)
            .copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // CAM 히트맵
        if (currentCamSide > 0 && currentCamSide * currentCamSide == currentCamData.size) {
            canvas.drawBitmap(
                createJetHeatmap(currentCamData, currentCamSide, displaySize, alpha),
                0f, 0f, null)
        }

        // ROI 바운딩박스  (0~224 → 0~320 스케일)
        val validBbox = currentMinx >= 0f && currentMiny >= 0f &&
                        currentMaxx > currentMinx && currentMaxy > currentMiny
        if (validBbox) {
            val sx = displaySize / 224f
            val sy = displaySize / 224f

            canvas.drawRect(
                currentMinx * sx, currentMiny * sy,
                currentMaxx * sx, currentMaxy * sy,
                Paint().apply {
                    color = Color.RED
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                    isAntiAlias = true
                })

            // 코너 강조
            val cl  = min(currentMaxx - currentMinx, currentMaxy - currentMiny) * sx * 0.15f
            val cp  = Paint().apply {
                color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 6f }
            val l = currentMinx * sx; val t = currentMiny * sy
            val r = currentMaxx * sx; val b = currentMaxy * sy
            canvas.drawLine(l, t, l + cl, t, cp); canvas.drawLine(l, t, l, t + cl, cp)
            canvas.drawLine(r, t, r - cl, t, cp); canvas.drawLine(r, t, r, t + cl, cp)
            canvas.drawLine(l, b, l + cl, b, cp); canvas.drawLine(l, b, l, b - cl, cp)
            canvas.drawLine(r, b, r - cl, b, cp); canvas.drawLine(r, b, r, b - cl, cp)
        }

        // 판정 텍스트 오버레이
        val isFake   = currentFakeProb > (1f - currentFakeProb)
        val verdict  = if (isFake) "FAKE" else "REAL"
        val verdictColor = if (isFake) Color.RED else Color.GREEN

        // 반투명 배경 패널 (좌상단)
        val panelPaint = Paint().apply {
            color = Color.argb(160, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, displaySize.toFloat(), 72f, panelPaint)

        // 판정 레이블 (FAKE / REAL)
        val labelPaint = Paint().apply {
            color = verdictColor
            textSize = 48f
            isFakeBoldText = true
            isAntiAlias = true
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }
        canvas.drawText(verdict, 12f, 54f, labelPaint)

        // fakeProb 수치
        val probPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isAntiAlias = true
        }
        canvas.drawText(
            "fake ${"%.1f%%".format(currentFakeProb * 100)}",
            140f, 54f, probPaint
        )

        // bbox_score (ROI 좌상단 근처, bbox가 유효할 때만)
        val validBboxForText = currentMinx >= 0f && currentMiny >= 0f &&
                               currentMaxx > currentMinx && currentMaxy > currentMiny
        if (validBboxForText && currentBboxScore > 0f) {
            val sx = displaySize / 224f
            val sy = displaySize / 224f
            val scorePaint = Paint().apply {
                color = Color.YELLOW
                textSize = 24f
                isAntiAlias = true
                setShadowLayer(3f, 1f, 1f, Color.BLACK)
            }
            canvas.drawText(
                "score ${"%.3f".format(currentBboxScore)}",
                currentMinx * sx, (currentMiny * sy - 6f).coerceAtLeast(24f),
                scorePaint
            )
        }

        ivFrame.setImageBitmap(result)
    }

    // ─────────────────────────────────────────────────────────────────────
    // JET 컬러맵 히트맵 생성
    // ─────────────────────────────────────────────────────────────────────

    private fun createJetHeatmap(camData: FloatArray, side: Int, size: Int, alpha: Int): Bitmap {
        val minVal = camData.min() ?: 0f
        val maxVal = camData.max() ?: 1f
        val range  = if (abs(maxVal - minVal) > 1e-6f) maxVal - minVal else 1f

        val small = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        for (r in 0 until side)
            for (c in 0 until side) {
                val t = (camData[r * side + c] - minVal) / range
                small.setPixel(c, r, jetColor(t, alpha))
            }
        return Bitmap.createScaledBitmap(small, size, size, true)
    }

    private fun jetColor(t: Float, alpha: Int): Int {
        val r = (1.5f - abs(4f * t - 3f)).coerceIn(0f, 1f)
        val g = (1.5f - abs(4f * t - 2f)).coerceIn(0f, 1f)
        val b = (1.5f - abs(4f * t - 1f)).coerceIn(0f, 1f)
        return Color.argb(alpha, (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }

    // ─────────────────────────────────────────────────────────────────────
    // 유틸
    // ─────────────────────────────────────────────────────────────────────

    private fun bitmapToRgba(bitmap: Bitmap): ByteArray {
        val rgba   = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val buffer = ByteBuffer.allocate(rgba.byteCount)
        rgba.copyPixelsToBuffer(buffer)
        return buffer.array()
    }

    private fun getAssetFilePath(assetName: String): String {
        val file = File(filesDir, assetName)
        if (file.exists() && file.length() > 0) return file.absolutePath
        assets.open(assetName).use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        return file.absolutePath
    }
}

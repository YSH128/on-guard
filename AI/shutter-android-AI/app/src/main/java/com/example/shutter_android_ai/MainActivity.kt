package com.example.shutter_android_ai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.shutter_android_ai.network.RetrofitClient
import com.example.shutter_android_ai.network.TelemetryRequest
import com.example.shutter_android_ai.telemetry.DeviceStatusProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    // native-lib.cpp JNI 시그니처와 정확히 일치
    // Java_com_example_shutter_1android_1ai_MainActivity_loadModels(faceModelPath)
    private external fun loadModels(faceModelPath: String): FloatArray

    // Java_com_example_shutter_1android_1ai_MainActivity_detectDeepfakeVisual(buffer, width, height, stride)
    // 반환: [fakeProb, realProb, faceLoadTime, inferTime, cam×N, minx, miny, maxx, maxy, bbox_score]
    private external fun detectDeepfakeVisual(
        buffer: ByteArray,
        width: Int,
        height: Int,
        stride: Int
    ): FloatArray

    companion object {
        var lastResults: FloatArray = floatArrayOf()
        var lastBitmap: Bitmap? = null

        init { System.loadLibrary("shutter_android_ai") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnLoadModels   = findViewById<Button>(R.id.btnLoadModels)
        val tvLoadStatus    = findViewById<TextView>(R.id.tvLoadStatus)
        val btnVisualDetect = findViewById<Button>(R.id.btnVisualDetect)
        val tvVisualResult  = findViewById<TextView>(R.id.tvVisualResult)
        val tvOpenCVStatus  = findViewById<TextView>(R.id.tvOpenCVStatus)
        val btnCamVisualize = findViewById<Button>(R.id.btnCamVisualize)
        val btnAudioDetect  = findViewById<Button>(R.id.btnAudioDetect)
        val tvAudioResult   = findViewById<TextView>(R.id.tvAudioResult)

        // 오디오 감지는 native-lib.cpp에 미구현
        btnAudioDetect.isEnabled = false
        tvAudioResult.text = "오디오 감지: 현재 미지원 (native 미구현)"

        if (OpenCVLoader.initDebug()) {
            tvOpenCVStatus.text = "OpenCV 로드 성공 (v${org.opencv.core.Core.VERSION})"
        } else {
            tvOpenCVStatus.text = "OpenCV 로드 실패"
        }

        btnLoadModels.setOnClickListener {
            tvLoadStatus.text = "모델 로드 중..."
            thread {
                try {
                    val facePath = getAssetFilePath("face_deepfake_model.pt")
                    val startCpu = DeviceStatusProvider.getThreadCpuTimeMs()
                    val results  = loadModels(facePath)
                    val cpuTime  = DeviceStatusProvider.getThreadCpuTimeMs() - startCpu

                    val success      = results.getOrElse(0) { 0f } > 0f
                    val faceLoadTime = results.getOrElse(1) { 0f }

                    sendTelemetryData(
                        modelType = "FACE",
                        isSuccess = success,
                        failureType = if (success) null else "MODEL_LOAD_ERROR",
                        inferenceLatencyMs = 0L,
                        riskScore = 0f,
                        modelLoadLatencyMs = faceLoadTime.toLong(),
                        threadCpuTimeMs = cpuTime
                    )

                    runOnUiThread {
                        tvLoadStatus.text = if (success)
                            "모델 로드 성공!\n페이스 모델: ${"%.2f ms".format(faceLoadTime)}"
                        else "모델 로드 실패!"
                    }
                } catch (e: Exception) {
                    runOnUiThread { tvLoadStatus.text = "에러: ${e.message}" }
                }
            }
        }

        btnVisualDetect.setOnClickListener {
            tvVisualResult.text = "페이스 분석 중..."
            thread {
                try {
                    val videoPath = getAssetFilePath("fakeVideo.mp4")

                    // 비디오에서 첫 번째 프레임 추출
                    val bitmap = extractFirstFrame(videoPath)
                        ?: throw Exception("프레임 추출 실패 (비디오 파일 확인 필요)")
                    lastBitmap = bitmap

                    // Bitmap → RGBA ByteArray (native-lib.cpp는 CV_8UC4 형식 기대)
                    val rgbaBytes = bitmapToRgba(bitmap)
                    val stride = bitmap.width * 4

                    val startCpu = DeviceStatusProvider.getThreadCpuTimeMs()
                    val results  = detectDeepfakeVisual(rgbaBytes, bitmap.width, bitmap.height, stride)
                    val cpuTime  = DeviceStatusProvider.getThreadCpuTimeMs() - startCpu

                    lastResults = results

                    // 오류 코드 확인 (native에서 음수 반환 시 실패)
                    if (results.isEmpty() || results[0] < -0.5f) {
                        val code = results.getOrElse(0) { -99f }
                        runOnUiThread { tvVisualResult.text = "추론 실패 (코드: $code)" }
                        return@thread
                    }

                    // 결과 파싱: [fakeProb(0), realProb(1), loadTime(2), inferTime(3),
                    //             cam×N (4..4+N-1), minx, miny, maxx, maxy, bbox_score]
                    val fakeProb  = results[0]
                    val realProb  = results[1]
                    val loadTime  = results[2]
                    val inferTime = results[3]
                    val camSize   = results.size - 9
                    val bboxOff   = 4 + camSize
                    val minx      = results.getOrElse(bboxOff)     { -1f }
                    val miny      = results.getOrElse(bboxOff + 1) { -1f }
                    val maxx      = results.getOrElse(bboxOff + 2) { -1f }
                    val maxy      = results.getOrElse(bboxOff + 3) { -1f }

                    val isSuccess = fakeProb >= 0f
                    sendTelemetryData(
                        modelType = "FACE",
                        isSuccess = isSuccess,
                        failureType = if (isSuccess) null else "INFERENCE_ERROR",
                        inferenceLatencyMs = inferTime.toLong(),
                        riskScore = fakeProb,
                        modelLoadLatencyMs = loadTime.toLong(),
                        threadCpuTimeMs = cpuTime
                    )

                    val currentTemp  = DeviceStatusProvider.getDeviceTemperature(this@MainActivity)
                    val pssMemory    = DeviceStatusProvider.getAppPssMemory()
                    val thermalStat  = DeviceStatusProvider.getThermalThrottlingStatus(this@MainActivity)
                    val batteryCurr  = DeviceStatusProvider.getBatteryCurrentNow(this@MainActivity)

                    runOnUiThread {
                        tvVisualResult.text = """
                            [페이스 추론 성공 - ${if (fakeProb > realProb) "FAKE" else "REAL"}]
                            Fake: ${"%.2f%%".format(fakeProb * 100)}
                            Real: ${"%.2f%%".format(realProb * 100)}
                            Load: ${"%.2f ms".format(loadTime)}
                            Infer: ${"%.2f ms".format(inferTime)}
                            CAM size: $camSize (${Math.sqrt(camSize.toDouble()).toInt()}×${Math.sqrt(camSize.toDouble()).toInt()})
                            ROI: (${"%.0f".format(minx)},${"%.0f".format(miny)})~(${"%.0f".format(maxx)},${"%.0f".format(maxy)})

                            [하드웨어]
                            Temp: ${"%.1f".format(currentTemp)}°C  RAM: $pssMemory KB
                            CPU Time: $cpuTime ms  Current: ${"%.1f mA".format(batteryCurr)}
                            Thermal: $thermalStat
                        """.trimIndent()
                    }
                } catch (e: Exception) {
                    runOnUiThread { tvVisualResult.text = "페이스 에러: ${e.message}" }
                }
            }
        }

        btnCamVisualize.setOnClickListener {
            if (lastResults.size >= 9 && lastBitmap != null) {
                startActivity(Intent(this, CamVisualizationActivity::class.java))
            } else {
                Toast.makeText(this, "먼저 페이스 감지를 실행하세요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 비디오 파일에서 첫 번째 프레임을 Bitmap으로 추출 */
    private fun extractFirstFrame(videoPath: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /** Bitmap → RGBA ByteArray (native CV_8UC4와 동일 포맷) */
    private fun bitmapToRgba(bitmap: Bitmap): ByteArray {
        val rgba = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val buffer = ByteBuffer.allocate(rgba.byteCount)
        rgba.copyPixelsToBuffer(buffer)
        return buffer.array()
    }

    private fun sendTelemetryData(
        modelType: String,
        isSuccess: Boolean,
        failureType: String?,
        inferenceLatencyMs: Long,
        riskScore: Float,
        modelLoadLatencyMs: Long,
        threadCpuTimeMs: Long
    ) {
        val currentTemp   = DeviceStatusProvider.getDeviceTemperature(this)
        val pssMemory     = DeviceStatusProvider.getAppPssMemory()
        val thermalStatus = DeviceStatusProvider.getThermalThrottlingStatus(this)
        val batteryCurr   = DeviceStatusProvider.getBatteryCurrentNow(this)
        val sdf           = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

        val request = TelemetryRequest(
            user_id = 1,
            model_type = modelType,
            model_version = "v1.0.0",
            inference_success = isSuccess,
            failure_type = failureType,
            inference_latency_ms = inferenceLatencyMs,
            risk_score = riskScore,
            timestamp = sdf.format(Date()),
            device_hash = DeviceStatusProvider.getDeviceModelName(),
            session_id = UUID.randomUUID().toString(),
            device_temperature = currentTemp,
            app_pss_memory = pssMemory,
            thermal_throttling_status = thermalStatus,
            thread_cpu_time = threadCpuTimeMs,
            battery_current_now = batteryCurr,
            model_load_latency_ms = modelLoadLatencyMs
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.api.sendTelemetry(request)
            } catch (_: Exception) {}
        }
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

package com.example.shutter_android_ai

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat

import android.widget.Button
import android.widget.TextView

import android.os.Bundle
import org.opencv.android.OpenCVLoader

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder

import androidx.appcompat.app.AppCompatActivity

import android.content.Intent
import android.widget.Toast
import kotlin.concurrent.thread

import com.example.shutter_android_ai.telemetry.DeviceStatusProvider

import androidx.lifecycle.lifecycleScope
import com.example.shutter_android_ai.network.RetrofitClient
import com.example.shutter_android_ai.network.TelemetryRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // C++ JNI 함수 선언
    private external fun loadModels(audioModelPath: String, faceModelPath: String): FloatArray
    private external fun detectDeepfakeAudio(audioData: FloatArray): FloatArray
    private external fun detectDeepfakeVisual(videoPath: String): FloatArray

    private val deviceModel by lazy { DeviceStatusProvider.getDeviceModelName() }

    companion object {
        // 시각화 액티비티에서 참조하는 마지막 추론 결과
        var lastVideoPath: String = ""
        var lastResults: FloatArray = floatArrayOf()

        init {
            System.loadLibrary("shutter_android_ai")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 뷰 찾기 로직
        val btnLoadModels = findViewById<Button>(R.id.btnLoadModels)
        val tvLoadStatus = findViewById<TextView>(R.id.tvLoadStatus)
        val btnAudioDetect = findViewById<Button>(R.id.btnAudioDetect)
        val tvAudioResult = findViewById<TextView>(R.id.tvAudioResult)
        val btnVisualDetect = findViewById<Button>(R.id.btnVisualDetect)
        val tvVisualResult = findViewById<TextView>(R.id.tvVisualResult)
        val tvOpenCVStatus = findViewById<TextView>(R.id.tvOpenCVStatus)

        // OpenCV 설치 및 버전 확인
        if (OpenCVLoader.initDebug()) {
            val version = org.opencv.core.Core.VERSION
            tvOpenCVStatus.text = "OpenCV 로드 성공 (v$version)"
        } else {
            tvOpenCVStatus.text = "OpenCV 로드 실패"
        }

        btnLoadModels.setOnClickListener {
            tvLoadStatus.text = "모델 로드 중..."
            thread {
                try {
                    val audioPath = getAssetFilePath(this@MainActivity, "voice_deepfake_model.pt")
                    val facePath = getAssetFilePath(this@MainActivity, "face_deepfake_model.pt")
                    
                    val startCpuTimeMs = DeviceStatusProvider.getThreadCpuTimeMs()
                    
                    val results = loadModels(audioPath, facePath)
                    
                    val endCpuTimeMs = DeviceStatusProvider.getThreadCpuTimeMs()
                    val loadThreadCpuTime = endCpuTimeMs - startCpuTimeMs
                    
                    val success = results[0] > 0.0f
                    val audioLoadTime = results[1]
                    val faceLoadTime = results[2]
                    
                    // Send telemetry for Audio Load
                    sendTelemetryData(
                        modelType = "DEEPVOICE",
                        isSuccess = success,
                        failureType = if (success) null else "MODEL_LOAD_ERROR",
                        inferenceLatencyMs = 0L,
                        riskScore = 0.0f,
                        modelLoadLatencyMs = audioLoadTime.toLong(),
                        threadCpuTimeMs = loadThreadCpuTime / 2
                    )

                    // Send telemetry for Face Load
                    sendTelemetryData(
                        modelType = "FACE",
                        isSuccess = success,
                        failureType = if (success) null else "MODEL_LOAD_ERROR",
                        inferenceLatencyMs = 0L,
                        riskScore = 0.0f,
                        modelLoadLatencyMs = faceLoadTime.toLong(),
                        threadCpuTimeMs = loadThreadCpuTime / 2
                    )
                    
                    runOnUiThread {
                        if (success) {
                            tvLoadStatus.text = "모델 로드 성공!\n오디오 모델: ${String.format("%.2f", audioLoadTime)} ms\n페이스 모델: ${String.format("%.2f", faceLoadTime)} ms"
                        } else {
                            tvLoadStatus.text = "모델 로드 실패!"
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tvLoadStatus.text = "에러: ${e.message}"
                    }
                }
            }
        }

        btnAudioDetect.setOnClickListener {
            try {
                val audioPath = getAssetFilePath(this@MainActivity, "test.flac")
                request_deepfake_detection("DEEPVOICE", audioPath, tvAudioResult)
            } catch (e: Exception) {
                tvAudioResult.text = "에러: test.flac 파일을 assets 폴더에서 찾을 수 없습니다."
            }
        }

        btnVisualDetect.setOnClickListener {
            try {
                val videoPath = getAssetFilePath(this@MainActivity, "fakeVideo.mp4")
                request_deepfake_detection("FACE", videoPath, tvVisualResult)
            } catch (e: Exception) {
                tvVisualResult.text = "에러: fakeVideo.mp4 파일을 assets 폴더에서 찾을 수 없습니다."
            }
        }

        val btnCamVisualize = findViewById<Button>(R.id.btnCamVisualize)
        btnCamVisualize.setOnClickListener {
            if (lastResults.size >= 58) {
                startActivity(Intent(this@MainActivity, CamVisualizationActivity::class.java))
            } else {
                Toast.makeText(this@MainActivity, "먼저 페이스 감지를 실행하세요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun request_deepfake_detection(type: String, filePath: String, resultTextView: TextView) {
        val typeName = if (type == "DEEPVOICE") "오디오" else "페이스"
        resultTextView.text = "$typeName 분석 중..."

        thread {
            try {
                val startCpuTimeMs = DeviceStatusProvider.getThreadCpuTimeMs()
                val results: FloatArray
                var isSuccess = false
                var score = 0.0f
                var loadTime = 0.0f
                var inferenceTime = 0.0f
                var isFake = false
                var status = ""
                var faceFakeProb = 0.0f
                var faceRealProb = 0.0f

                if (type == "DEEPVOICE") {
                    val audioData = decodeAudioFromFile(filePath, 64000)
                    results = detectDeepfakeAudio(audioData)
                    score = results[0]
                    loadTime = results[1]
                    inferenceTime = results[2]
                    isSuccess = score > -1.0f
                } else if (type == "FACE") {
                    results = detectDeepfakeVisual(filePath)
                    // 시각화 액티비티용 결과 저장
                    lastVideoPath = filePath
                    lastResults = results
                    score = results[0]
                    loadTime = results[2]
                    inferenceTime = results[3]
                    isSuccess = score > -1.0f
                    if (isSuccess) {
                        isFake = results[0] > results[1]
                        status = if (isFake) "FAKE" else "REAL"
                        faceFakeProb = results[0]
                        faceRealProb = results[1]
                    }
                }

                val endCpuTimeMs = DeviceStatusProvider.getThreadCpuTimeMs()
                val inferenceThreadCpuTime = endCpuTimeMs - startCpuTimeMs

                sendTelemetryData(
                    modelType = type,
                    isSuccess = isSuccess,
                    failureType = if (isSuccess) null else "INFERENCE_ERROR",
                    inferenceLatencyMs = inferenceTime.toLong(),
                    riskScore = score,
                    modelLoadLatencyMs = loadTime.toLong(),
                    threadCpuTimeMs = inferenceThreadCpuTime
                )

                val currentTemp = DeviceStatusProvider.getDeviceTemperature(this@MainActivity)
                val pssMemoryKb = DeviceStatusProvider.getAppPssMemory()
                val thermalStatus = DeviceStatusProvider.getThermalThrottlingStatus(this@MainActivity)
                val batteryCurrentmA = DeviceStatusProvider.getBatteryCurrentNow(this@MainActivity)

                runOnUiThread {
                    if (!isSuccess) {
                        resultTextView.text = "에러: 모델 로드 또는 추론 실패"
                    } else {
                        val header = if (type == "FACE") "[$typeName 추론 성공 - $status]\nFake: ${String.format("%.2f%%", faceFakeProb * 100)}\nReal: ${String.format("%.2f%%", faceRealProb * 100)}" 
                                     else "[$typeName 추론 성공]\nScore: ${String.format("%.4f", score)}"

                        val resultText = """
                            $header
                            Load: ${String.format("%.2f", loadTime)} ms
                            Infer: ${String.format("%.2f", inferenceTime)} ms

                            [하드웨어 모니터링]
                            Temp: ${String.format("%.2f", currentTemp)}°C
                            RAM (PSS): $pssMemoryKb KB
                            CPU Time: $inferenceThreadCpuTime ms
                            Current: ${String.format("%.2f", batteryCurrentmA)} mA
                            Thermal Status: $thermalStatus
                        """.trimIndent()
                        
                        resultTextView.text = resultText
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    resultTextView.text = "$typeName 에러: ${e.message}"
                }
            }
        }
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
        val currentTemp = DeviceStatusProvider.getDeviceTemperature(this@MainActivity)
        val pssMemoryKb = DeviceStatusProvider.getAppPssMemory()
        val thermalStatus = DeviceStatusProvider.getThermalThrottlingStatus(this@MainActivity)
        val batteryCurrentmA = DeviceStatusProvider.getBatteryCurrentNow(this@MainActivity)
        
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val currentTimeStr = sdf.format(Date())

        val request = TelemetryRequest(
            user_id = 1,
            model_type = modelType,
            model_version = "v1.0.0",
            inference_success = isSuccess,
            failure_type = failureType,
            inference_latency_ms = inferenceLatencyMs,
            risk_score = riskScore,
            timestamp = currentTimeStr,
            device_hash = deviceModel,
            session_id = UUID.randomUUID().toString(),
            device_temperature = currentTemp,
            app_pss_memory = pssMemoryKb,
            thermal_throttling_status = thermalStatus,
            thread_cpu_time = threadCpuTimeMs,
            battery_current_now = batteryCurrentmA,
            model_load_latency_ms = modelLoadLatencyMs
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.api.sendTelemetry(request)
                if (response.isSuccessful) {
                    android.util.Log.d("Telemetry", "데이터 전송 성공 ($modelType)")
                } else {
                    android.util.Log.e("Telemetry", "데이터 전송 실패: ${response.code()} ($modelType)")
                }
            } catch (e: Exception) {
                android.util.Log.e("Telemetry", "네트워크 에러: ${e.message} ($modelType)")
            }
        }
    }

    // 오디오 디코딩 함수: Context 대신 로컬 파일 경로(filePath)를 직접 받습니다.
    private fun decodeAudioFromFile(filePath: String, targetLength: Int): FloatArray {
        val floatList = mutableListOf<Float>()
        val extractor = MediaExtractor()

        // assets.openFd 대신 일반 파일 경로를 사용하여 소스를 설정합니다. (압축 문제 해결)
        extractor.setDataSource(filePath)

        var format: MediaFormat? = null
        var mime: String? = null
        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val trackMime = trackFormat.getString(MediaFormat.KEY_MIME)
            if (trackMime?.startsWith("audio/") == true) {
                format = trackFormat
                mime = trackMime
                extractor.selectTrack(i)
                break
            }
        }

        if (format == null || mime == null) throw IllegalArgumentException("오디오 트랙을 찾을 수 없습니다.")

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val info = MediaCodec.BufferInfo()
        var isEOS = false

        while (true) {
            if (!isEOS) {
                val inIndex = codec.dequeueInputBuffer(10000)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)
                    val sampleSize = extractor.readSampleData(buffer!!, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(info, 10000)
            if (outIndex >= 0) {
                val buffer = codec.getOutputBuffer(outIndex)
                if (buffer != null && info.size > 0) {
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    buffer.order(ByteOrder.LITTLE_ENDIAN)
                    val shortBuffer = buffer.asShortBuffer()
                    
                    while (shortBuffer.hasRemaining()) {
                        floatList.add(shortBuffer.get().toFloat() / 32768.0f)
                    }
                }
                codec.releaseOutputBuffer(outIndex, false)
            } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (isEOS) break
            }
            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
        }

        codec.stop()
        codec.release()
        extractor.release()

        val result = FloatArray(targetLength)
        val copyLength = minOf(floatList.size, targetLength)
        for (i in 0 until copyLength) {
            result[i] = floatList[i]
        }
        return result
    }

    // 에셋 복사 함수
    private fun getAssetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) return file.absolutePath

        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return file.absolutePath
    }
}
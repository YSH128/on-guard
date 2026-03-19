package com.s14p21a404.shutter.data.detector.bridge

import android.content.Context
import android.util.Log
import android.widget.TextView
import com.s14p21a404.shutter.data.detector.DeepfakeSettings
import com.s14p21a404.shutter.telemetry.DeviceStatusProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * JNI 브릿지 (Kotlin 쪽) — NDK 네이티브 함수 request_deepfake_detection 호출
 * 
 * ┌────────────────────────────────────────────────────────┐
 * │  담당 경계                                              │
 * │  • 이 파일 (NativeDeepfakeBridge.kt)  → 앱(Kotlin) 담당 │
 * │  • shutter-native SO + deepfake_jni.cpp → NDK 담당      │
 * └────────────────────────────────────────────────────────┘
 *
 * NDK 담당자와 합의한 JNI 함수 시그니처:
 *   request_deepfake_detection(type: String, buffer: ByteArray, resultTextView: TextView)
 *
 * - type: 감지 유형 ("FACE")
 * - buffer: RGBA 프레임 byte[] (ScreenCaptureManager에서 캡처한 그대로)
 * - resultTextView: 결과를 표시할 TextView (현재 더미 — 향후 NDK 쪽에서 활용 예정)
 */
class NativeDeepfakeBridge(private val context: Context) {

    @Volatile
    var isQnnInitialized = false

    @Volatile
    var isQnnCompiled = false

    private var initJobThread: Thread? = null

    private external fun initQnn(cacheDir: String): Boolean
    private external fun buildModelCache(assetsPath: String): Boolean
    private external fun loadModel(assetsPath: String): Boolean
    private external fun releaseQnn()
    private external fun detectDeepfakeVisual(buffer: ByteArray, width: Int, height: Int, stride: Int): FloatArray

    fun compileQnnCache() {
        if (isQnnCompiled || isQnnInitialized) return
        if (initJobThread?.isAlive == true) return
        initJobThread = thread {
            try {
                val facePath = getAssetFilePath(context, "face_deepfake.bin")
                val filesDir = context.filesDir.absolutePath
                val cacheDir = context.cacheDir.absolutePath
                
                Log.d("NativeDeepfakeBridge", "QNN 시스템 초기화 시작...")
                val initSuccess = initQnn(cacheDir)
                if (initSuccess) {
                    val keepInVram = DeepfakeSettings.keepModelInVram.value

                    if (keepInVram) {
                        Log.d("NativeDeepfakeBridge", "옵션: 모델 VRAM 상시 할당 활성화. (loadModel 즉시 호출)")
                        val startCpuTimeMs = DeviceStatusProvider.getThreadCpuTimeMs()
                        val loadSuccess = loadModel(filesDir)
                        val loadThreadCpuTime = DeviceStatusProvider.getThreadCpuTimeMs() - startCpuTimeMs
                        
                        if (loadSuccess) {
                            isQnnCompiled = true
                            isQnnInitialized = true // 메모리에 올렸으므로 초기화 완료
                            Log.d("NativeDeepfakeBridge", "QNN 모델 초기 적재 완료 (CPU: ${loadThreadCpuTime}ms)")
                        } else {
                            Log.e("NativeDeepfakeBridge", "QNN 초기 적재 실패!")
                        }
                    } else {
                        Log.d("NativeDeepfakeBridge", "옵션: 모델 VRAM 해제 활성화. (buildModelCache 호출)")
                        val startCpuTimeMs = DeviceStatusProvider.getThreadCpuTimeMs()
                        val buildSuccess = buildModelCache(filesDir)
                        val compileThreadCpuTime = DeviceStatusProvider.getThreadCpuTimeMs() - startCpuTimeMs
                        
                        if (buildSuccess) {
                            isQnnCompiled = true
                            Log.d("NativeDeepfakeBridge", "QNN 모델 컴파일 및 VRAM 해제 성공 (CPU: ${compileThreadCpuTime}ms)")
                        } else {
                            Log.e("NativeDeepfakeBridge", "QNN 모델 컴파일 실패!")
                        }
                    }
                } else {
                    Log.e("NativeDeepfakeBridge", "QNN 시스템 초기화 실패!")
                }
            } catch (e: Exception) {
                Log.e("NativeDeepfakeBridge", "QNN 컴파일 에러: ${e.message}")
            }
        }
    }

    fun loadModelIntoMemory(onComplete: () -> Unit = {}) {
        if (isQnnInitialized) {
            onComplete()
            return
        }
        thread {
            // 사전 컴파일 및 적재 스레드가 돌고 있다면, C++ Mutex Lock에 막혀 19초 중복 호출되는 것을 방지하기 위해 대기
            initJobThread?.join()

            // 대기 후 이미 적재가 끝났다면 즉시 리턴
            if (isQnnInitialized) {
                onComplete()
                return@thread
            }

            try {
                val filesDir = context.filesDir.absolutePath
                val cacheDir = context.cacheDir.absolutePath
                
                // 만일을 대비한 fallback
                initQnn(cacheDir)

                val startCpuTimeMs = DeviceStatusProvider.getThreadCpuTimeMs()
                val loadSuccess = loadModel(filesDir)
                val loadThreadCpuTime = DeviceStatusProvider.getThreadCpuTimeMs() - startCpuTimeMs
                
                if (loadSuccess) {
                    isQnnInitialized = true
                    Log.d("NativeDeepfakeBridge", "QNN 모델 캐시 읽기 및 메모리 적재 완료 (CPU: ${loadThreadCpuTime}ms)")
                } else {
                    Log.e("NativeDeepfakeBridge", "QNN 모델 메모리 로드 실패!")
                }
                onComplete()
            } catch (e: Exception) {
                Log.e("NativeDeepfakeBridge", "QNN 로드 상태 에러: ${e.message}")
                onComplete()
            }
        }
    }

    private fun getAssetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) return file.absolutePath
        context.assets.open(assetName).use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        return file.absolutePath
    }

    /**
     * NDK AI 추론 요청
     *
     * @param type           감지 유형 ("FACE" = 영상 딥페이크)
     * @param buffer         RGBA 프레임 byte[] (캡처된 프레임 버퍼 그대로)
     * @param resultTextView 결과 표시용 TextView (현재 더미)
     */
    fun request_deepfake_detection(
        type: String,
        buffer: ByteArray,
        width: Int,
        height: Int,
        stride: Int,
        resultTextView: TextView
    ) {
        if (type != "FACE") return
        
        if (!isQnnInitialized) {
            val jsonString = org.json.JSONObject().apply {
                put("detected", 0)
                put("error", "AI 모델 빌드 및 최적화 중입니다...")
            }.toString()
            resultTextView.text = jsonString
            return
        }

        val typeName = "페이스"
        
        try {
            val startCpuTimeMs = DeviceStatusProvider.getThreadCpuTimeMs()
            
            // 추론 요청
            val results = detectDeepfakeVisual(buffer, width, height, stride)
            
            var score = 0.0f
            var loadTime = 0.0f
            var inferenceTime = 0.0f
            var isFake = false
            var status = ""
            var faceFakeProb = 0.0f
            var faceRealProb = 0.0f
            var roi = floatArrayOf(0f, 0f, 0f, 0f)

            val avgFake = results[0]
            val avgReal = results[1]
            loadTime = results[2]
            inferenceTime = results[3]
            val isSuccess = avgFake > -1.0f

            if (results.size >= 10) {
                roi = floatArrayOf(results[6], results[7], results[8], results[9])
            }

            if (isSuccess) {
                isFake = avgFake > avgReal
                status = if (isFake) "FAKE" else "REAL"
                faceFakeProb = avgFake
                faceRealProb = avgReal
                score = avgFake
            }

            val endCpuTimeMs = DeviceStatusProvider.getThreadCpuTimeMs()
            val inferenceThreadCpuTime = endCpuTimeMs - startCpuTimeMs

            val currentTemp = DeviceStatusProvider.getDeviceTemperature(context)
            val pssMemoryKb = DeviceStatusProvider.getAppPssMemory()
            val thermalStatus = DeviceStatusProvider.getThermalThrottlingStatus(context)
            val batteryCurrentmA = DeviceStatusProvider.getBatteryCurrentNow(context)

            if (!isSuccess) {
                val jsonString = org.json.JSONObject().apply {
                    put("detected", 0)
                    put("error", "INFERENCE_ERROR")
                }.toString()
                resultTextView.text = jsonString
            } else {
                val header = "[$typeName 추론 성공 - $status]\nFake: ${String.format("%.2f%%", faceFakeProb * 100)}\nReal: ${String.format("%.2f%%", faceRealProb * 100)}" 
                
                val resultText = """
                    $header
                    ROI: [${roi[0]}, ${roi[1]}, ${roi[2]}, ${roi[3]}]
                    Load: ${String.format("%.2f", loadTime)} ms
                    Infer: ${String.format("%.2f", inferenceTime)} ms

                    [하드웨어 모니터링]
                    Temp: ${String.format("%.2f", currentTemp)}°C
                    RAM (PSS): $pssMemoryKb KB
                    CPU Time: $inferenceThreadCpuTime ms
                    Current: ${String.format("%.2f", batteryCurrentmA)} mA
                    Thermal Status: $thermalStatus
                """.trimIndent()
                
                val jsonString = org.json.JSONObject().apply {
                    put("detected", if (isFake) 1 else 0)
                    if (results.size >= 10) {
                        put("roi", org.json.JSONArray().apply {
                            put(roi[0])
                            put(roi[1])
                            put(roi[2])
                            put(roi[3])
                        })
                    }
                    put("debug_info", resultText)
                }.toString()
                
                resultTextView.text = jsonString
            }
        } catch (e: Exception) {
            val errorType = when {
                e is java.util.concurrent.TimeoutException ||
                e.message?.lowercase()?.contains("timeout") == true -> "TIMEOUT"
                else -> "INFERENCE_ERROR"
            }
            val jsonString = org.json.JSONObject().apply {
                put("detected", 0)
                put("error", errorType)
            }.toString()
            resultTextView.text = jsonString
        }
    }

    companion object {
        private var loaded = false

        /**
         * 네이티브 라이브러리 로드.
         *
         * @return true: 로드 성공, false: 로드 실패 (NDK 미빌드)
         */
        fun loadLibrary(): Boolean {
            if (loaded) return true
            return try {
                System.loadLibrary("shutter-native")
                loaded = true
                true
            } catch (e: UnsatisfiedLinkError) {
                false
            }
        }
    }
}


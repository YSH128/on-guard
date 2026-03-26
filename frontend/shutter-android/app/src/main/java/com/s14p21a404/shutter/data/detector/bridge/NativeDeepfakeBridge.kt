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
 * │  담당 경계                                               │
 * │  • 이 파일 (NativeDeepfakeBridge.kt)  → 앱(Kotlin) 담당  │
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

    @Volatile
    var isQnnSupported = true // 기본 지원가정. 초기화 실패시 false.

    @Volatile
    var isOnnxInitialized = false
    private val onnxLock = Any()

    val isModelReady: Boolean
        get() = isQnnInitialized || isOnnxInitialized

    private var initJobThread: Thread? = null

    private external fun initQnn(cacheDir: String, nativeLibDir: String): Boolean
    private external fun buildModelCache(assetsPath: String): Boolean
    private external fun loadModel(assetsPath: String): Boolean
    private external fun releaseQnn()
    private external fun detectDeepfakeVisual(buffer: ByteArray, width: Int, height: Int, stride: Int): FloatArray
    private external fun initOnnx(modelPath: String): Boolean
    private external fun releaseOnnx()
    private external fun detectDeepfakeOnnx(buffer: ByteArray, width: Int, height: Int, stride: Int): FloatArray

    private fun initOnnxFallback() {
        synchronized(onnxLock) {
            if (isOnnxInitialized) return
            try {
                Log.d("NativeDeepfakeBridge", "ONNX C++ 네이티브 초기화 시작...")
                val modelPath = getAssetFilePath(context, "ds_model.onnx")
                val ok = initOnnx(modelPath)
                if (ok) {
                    isOnnxInitialized = true
                    Log.d("NativeDeepfakeBridge", "ONNX C++ 세션 초기화 완료 (워밍업 포함)")
                } else {
                    Log.e("NativeDeepfakeBridge", "ONNX C++ 세션 초기화 실패")
                }
            } catch (e: Exception) {
                Log.e("NativeDeepfakeBridge", "ONNX 초기화 에러: ${e.message}")
            }
        }
    }

    fun compileQnnCache() {
        if (isQnnCompiled || isQnnInitialized || isOnnxInitialized) return
        if (initJobThread?.isAlive == true) return
        // TODO: QNN 재컴파일 후 USE_QNN = true 로 변경
        //  현재 libface_deepfake.so 는 구버전 ONNX(output 1개: "output")로 변환된 파일이므로
        //  최신 모델(face_deepfake.onnx, logits+cam 2출력)과 tensor 불일치 발생.
        //  재컴파일 절차: AI/face_deepfake_model_preprocessing/convert_model_to_qnn.py 참고
        if (!USE_QNN) {
            isQnnSupported = false
            initJobThread = thread { initOnnxFallback() }
            return
        }
        initJobThread = thread {
            try {
                val facePath = getAssetFilePath(context, "face_deepfake.bin")
                val filesDir = context.filesDir.absolutePath
                val binFile = File(filesDir, "face_deepfake.bin")
                if (!binFile.exists()) {
                    Log.e("NativeDeepfakeBridge", "QNN 캐시 파일 없음! C++ 빌드/로드 병목을 우회하고 즉시 ONNX 폴백 시도...")
                    isQnnSupported = false
                    initOnnxFallback()
                    return@thread
                }
                val cacheDir = context.cacheDir.absolutePath
                val nativeLibDir = context.applicationInfo.nativeLibraryDir
                
                Log.d("NativeDeepfakeBridge", "QNN 시스템 초기화 시작...")
                val initSuccess = initQnn(cacheDir, nativeLibDir)
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
                            Log.e("NativeDeepfakeBridge", "QNN 초기 적재 실패! ONNX 폴백 시도...")
                            isQnnSupported = false
                            initOnnxFallback()
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
                            Log.e("NativeDeepfakeBridge", "QNN 모델 컴파일 실패! ONNX 폴백 시도...")
                            isQnnSupported = false
                            initOnnxFallback()
                        }
                    }
                } else {
                    isQnnSupported = false
                    Log.e("NativeDeepfakeBridge", "QNN 시스템 초기화 실패! (비 퀄컴칩 지원안됨)")
                    initOnnxFallback()
                }
            } catch (e: Exception) {
                Log.e("NativeDeepfakeBridge", "QNN 컴파일 에러: ${e.message}")
            }
        }
    }

    fun loadModelIntoMemory(onComplete: () -> Unit = {}) {
        if (isQnnInitialized || isOnnxInitialized) {
            onComplete()
            return
        }
        thread {
            // 사전 컴파일 및 적재 스레드가 돌고 있다면, C++ Mutex Lock에 막혀 19초 중복 호출되는 것을 방지하기 위해 대기
            initJobThread?.join()

            // 대기 후 이미 적재가 끝났다면 즉시 리턴
            if (isQnnInitialized || isOnnxInitialized) {
                onComplete()
                return@thread
            }

            // TODO: QNN 재컴파일 후 USE_QNN = true 로 변경 (compileQnnCache 주석 참고)
            if (!USE_QNN) {
                isQnnSupported = false
                initOnnxFallback()
                onComplete()
                return@thread
            }

            try {
                val filesDir = context.filesDir.absolutePath
                val cacheDir = context.cacheDir.absolutePath
                val nativeLibDir = context.applicationInfo.nativeLibraryDir
                
                val qnnInitSuccess = initQnn(cacheDir, nativeLibDir)
                if (!qnnInitSuccess) {
                    isQnnSupported = false
                    Log.e("NativeDeepfakeBridge", "QNN 시스템 초기화 실패! (비 퀄컴칩 지원안됨)")
                    initOnnxFallback()
                    onComplete()
                    return@thread
                }

                val startCpuTimeMs = DeviceStatusProvider.getThreadCpuTimeMs()
                val loadSuccess = loadModel(filesDir)
                val loadThreadCpuTime = DeviceStatusProvider.getThreadCpuTimeMs() - startCpuTimeMs
                
                if (loadSuccess) {
                    isQnnInitialized = true
                    Log.d("NativeDeepfakeBridge", "QNN 모델 캐시 읽기 및 메모리 적재 완료 (CPU: ${loadThreadCpuTime}ms)")
                } else {
                    Log.e("NativeDeepfakeBridge", "QNN 모델 메모리 로드 실패! ONNX 폴백 시도...")
                    isQnnSupported = false
                    initOnnxFallback()
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
        stride: Int
    ): String {
        if (type != "FACE") return ""

        if (!isQnnSupported) {
            if (!isOnnxInitialized) {
                return org.json.JSONObject().apply {
                    put("detected", 0)
                    put("error", "ONNX 폴백 모델 로드 중...")
                }.toString()
            }

            try {
                val startCpuTimeMs = DeviceStatusProvider.getThreadCpuTimeMs()

                // C++ 에서 전처리 + 추론 + CAM ROI 추출까지 전부 수행
                // 반환: [fakeProb, realProb, 0.0, inferTime, minx, miny, maxx, maxy, bboxScore]
                val results = detectDeepfakeOnnx(buffer, width, height, stride)

                if (results.isEmpty() || results[0] < 0f) {
                    return org.json.JSONObject().apply {
                        put("detected", 0)
                        put("error", "INFERENCE_ERROR")
                    }.toString()
                }

                val fakeProb      = results[0]
                val realProb      = results[1]
                val inferenceTime = results[3]
                val isFake        = fakeProb > realProb
                val status        = if (isFake) "FAKE" else "REAL"

                var roi: FloatArray? = null
                var bboxScore = -1f
                if (results.size >= 9 && results[4] >= 0f) {
                    roi       = floatArrayOf(results[4], results[5], results[6], results[7])
                    bboxScore = results[8]
                }

                val endCpuTimeMs          = DeviceStatusProvider.getThreadCpuTimeMs()
                val inferenceThreadCpuTime = endCpuTimeMs - startCpuTimeMs
                val currentTemp           = DeviceStatusProvider.getDeviceTemperature(context)
                val pssMemoryKb           = DeviceStatusProvider.getAppPssMemory()
                val thermalStatus         = DeviceStatusProvider.getThermalThrottlingStatus(context)
                val batteryCurrentmA      = DeviceStatusProvider.getBatteryCurrentNow(context)

                val header = "[ONNX 폴백 추론 성공 - $status]\nFake: ${String.format("%.2f%%", fakeProb * 100)}\nReal: ${String.format("%.2f%%", realProb * 100)}"
                val resultText = """
                    $header
                    ROI: ${roi?.let { "[${it[0]}, ${it[1]}, ${it[2]}, ${it[3]}]" } ?: "[없음]"}
                    Infer: ${String.format("%.2f", inferenceTime)} ms

                    [하드웨어 모니터링]
                    Temp: ${String.format("%.2f", currentTemp)}°C
                    RAM (PSS): $pssMemoryKb KB
                    CPU Time: $inferenceThreadCpuTime ms
                    Current: ${String.format("%.2f", batteryCurrentmA)} mA
                    Thermal Status: $thermalStatus
                """.trimIndent()

                return org.json.JSONObject().apply {
                    put("detected", if (isFake) 1 else 0)
                    put("fakeProb", fakeProb)
                    roi?.let {
                        put("roi", org.json.JSONArray().apply {
                            put(it[0]); put(it[1]); put(it[2]); put(it[3])
                        })
                        put("bboxScore", bboxScore)
                    }
                    put("debug_info", resultText)
                }.toString()
            } catch (e: Exception) {
                Log.e("NativeDeepfakeBridge", "ONNX 추론 에러: ${e.message}")
                return org.json.JSONObject().apply {
                    put("detected", 0)
                    put("error", "INFERENCE_ERROR")
                }.toString()
            }
        }
        
        if (!isQnnInitialized) {
            return org.json.JSONObject().apply {
                put("detected", 0)
                put("error", "AI 모델 빌드 및 최적화 중입니다...")
            }.toString()
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
            var roi: FloatArray? = null
            var bboxScore = -1f

            val avgFake = results[0]
            val avgReal = results[1]
            loadTime = results[2]
            inferenceTime = results[3]
            val isSuccess = avgFake > -1.0f

            if (results.size >= 9 && results[4] >= 0f) {
                roi = floatArrayOf(results[4], results[5], results[6], results[7])
                bboxScore = results[8]
            }

            if (isSuccess) {
                isFake = avgFake > 0.1f// 원래 isFake = avgFake > avgReal
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
                return org.json.JSONObject().apply {
                    put("detected", 0)
                    put("error", "INFERENCE_ERROR")
                }.toString()
            } else {
                val header = "[$typeName 추론 성공 - $status]\nFake: ${String.format("%.2f%%", faceFakeProb * 100)}\nReal: ${String.format("%.2f%%", faceRealProb * 100)}" 
                
                val resultText = """
                    $header
                    ROI: ${roi?.let { "[${it[0]}, ${it[1]}, ${it[2]}, ${it[3]}]" } ?: "[없음]"}
                    Load: ${String.format("%.2f", loadTime)} ms
                    Infer: ${String.format("%.2f", inferenceTime)} ms

                    [하드웨어 모니터링]
                    Temp: ${String.format("%.2f", currentTemp)}°C
                    RAM (PSS): $pssMemoryKb KB
                    CPU Time: $inferenceThreadCpuTime ms
                    Current: ${String.format("%.2f", batteryCurrentmA)} mA
                    Thermal Status: $thermalStatus
                """.trimIndent()
                
                return org.json.JSONObject().apply {
                    put("detected", if (isFake) 1 else 0)
                    put("fakeProb", faceFakeProb)
                    roi?.let {
                        put("roi", org.json.JSONArray().apply {
                            put(it[0])
                            put(it[1])
                            put(it[2])
                            put(it[3])
                        })
                        put("bboxScore", bboxScore)
                    }
                    put("debug_info", resultText)
                }.toString()
            }
        } catch (e: Exception) {
            val errorType = when {
                e is java.util.concurrent.TimeoutException ||
                e.message?.lowercase()?.contains("timeout") == true -> "TIMEOUT"
                else -> "INFERENCE_ERROR"
            }
            return org.json.JSONObject().apply {
                put("detected", 0)
                put("error", errorType)
            }.toString()
        }
    }

    companion object {
        private var loaded = false

        // TODO: QNN 재컴파일 필요
        //  현재 libface_deepfake.so 는 구버전 ONNX(output 1개: "output")로 변환된 파일입니다.
        //  최신 모델(face_deepfake.onnx)은 출력이 2개(logits [1,2] + cam [7,7])로 변경되었으므로
        //  아래 절차로 libface_deepfake.so 를 재컴파일한 후 USE_QNN = true 로 되돌려야 합니다.
        //
        //  [재컴파일 절차 - Linux/Mac 환경 필요]
        //  1. Qualcomm AI Engine Direct SDK 설치 (qpm.qualcomm.com)
        //  2. AI/face_deepfake_model_preprocessing/convert_model_to_qnn.py 실행
        //     (best_model-v3.pt 필요, output_names=["logits","cam"] 으로 변환)
        //  3. 생성된 libface_deepfake.so →
        //     frontend/shutter-android/app/src/main/jniLibs/arm64-v8a/ 에 교체
        //  4. USE_QNN = true 로 변경
        //
        //  QNN 재컴파일 전까지는 ONNX(NNAPI → GPU/CPU 폴백) 경로를 사용합니다.
        private const val USE_QNN = false // TODO: QNN 재컴파일 완료 후 true로 변경

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


package com.s14p21a404.shutter_android_ai

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.s14p21a404.shutter_android_ai.databinding.ActivityMainBinding
import com.s14p21a404.shutter.data.detector.bridge.NativeDeepfakeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val bridge = NativeDeepfakeBridge()

    companion object {
        // CamVisualizationActivity에서 접근하기 위해 companion object에 보관
        var lastDetectionResult: FloatArray? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvOpenCVStatus.text = "OpenCV: 초기화됨 (native-lib)"

        binding.btnLoadModels.setOnClickListener { loadModels() }
        binding.btnVisualDetect.setOnClickListener { runVisualDetect() }
        binding.btnCamVisualize.setOnClickListener {
            if (lastDetectionResult != null) {
                startActivity(Intent(this, CamVisualizationActivity::class.java))
            } else {
                Toast.makeText(this, "먼저 딥페이크 감지를 실행하세요", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnAudioDetect.setOnClickListener {
            Toast.makeText(this, "오디오 딥페이크 감지는 미구현입니다", Toast.LENGTH_SHORT).show()
        }
    }

    // ──────────────────────────────────────────────────────────
    // 모델 로드
    // ──────────────────────────────────────────────────────────

    private fun loadModels() {
        lifecycleScope.launch {
            binding.tvLoadStatus.text = "모델 로딩 중... (수십 초 소요)"
            binding.btnLoadModels.isEnabled = false

            val result = withContext(Dispatchers.IO) {
                val faceModelFile = copyAssetToFile("fake_deepfake_model.pt")
                bridge.loadModels(faceModelFile.absolutePath)
            }

            binding.btnLoadModels.isEnabled = true
            if (result[0] == 1.0f) {
                binding.tvLoadStatus.text = "모델 로드 완료 (${result[1].toInt()} ms)"
            } else {
                binding.tvLoadStatus.text = "모델 로드 실패 — logcat 확인"
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    // 비주얼 딥페이크 감지
    // ──────────────────────────────────────────────────────────

    private fun runVisualDetect() {
        lifecycleScope.launch {
            binding.tvVisualResult.text = "분석 중..."
            binding.btnVisualDetect.isEnabled = false

            val result = withContext(Dispatchers.IO) {
                val frame = extractFrameFromVideo("fakeVideo.mp4")
                    ?: return@withContext null

                // Bitmap → RGBA ByteArray (OpenCV COLOR_RGBA2RGB 포맷에 맞게)
                val argbBitmap = frame.copy(Bitmap.Config.ARGB_8888, false)
                val pixels = IntArray(argbBitmap.width * argbBitmap.height)
                argbBitmap.getPixels(pixels, 0, argbBitmap.width, 0, 0, argbBitmap.width, argbBitmap.height)

                val byteArray = ByteArray(pixels.size * 4)
                for (i in pixels.indices) {
                    val p = pixels[i]
                    byteArray[i * 4]     = ((p shr 16) and 0xFF).toByte() // R
                    byteArray[i * 4 + 1] = ((p shr 8)  and 0xFF).toByte() // G
                    byteArray[i * 4 + 2] = (p           and 0xFF).toByte() // B
                    byteArray[i * 4 + 3] = ((p shr 24)  and 0xFF).toByte() // A
                }

                bridge.detectDeepfakeVisual(
                    byteArray,
                    argbBitmap.width,
                    argbBitmap.height,
                    argbBitmap.width * 4
                )
            }

            binding.btnVisualDetect.isEnabled = true

            if (result == null || result.size < 9) {
                val code = result?.get(0) ?: -99f
                binding.tvVisualResult.text = "감지 실패 (code=${code})\n모델을 먼저 로드하세요"
                return@launch
            }

            lastDetectionResult = result

            val fakeProb  = result[0]
            val realProb  = result[1]
            val loadTime  = result[2]
            val inferTime = result[3]
            val camSize   = result.size - 9
            val bboxOffset = 4 + camSize
            val minx = result[bboxOffset]
            val miny = result[bboxOffset + 1]
            val maxx = result[bboxOffset + 2]
            val maxy = result[bboxOffset + 3]

            binding.tvVisualResult.text = buildString {
                appendLine("Fake: ${"%.1f".format(fakeProb * 100)}%   Real: ${"%.1f".format(realProb * 100)}%")
                appendLine("로드: ${loadTime.toInt()} ms   추론: ${"%.1f".format(inferTime)} ms")
                appendLine("CAM size: ${camSize} (${Math.sqrt(camSize.toDouble()).toInt()}×${Math.sqrt(camSize.toDouble()).toInt()})")
                if (minx >= 0) {
                    appendLine("BBox: (${minx.toInt()}, ${miny.toInt()}) ~ (${maxx.toInt()}, ${maxy.toInt()})")
                } else {
                    appendLine("BBox: 감지 안됨")
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────

    /** assets 파일을 앱 내부 저장소로 복사 (이미 있으면 재사용) */
    private fun copyAssetToFile(assetName: String): File {
        val file = File(filesDir, assetName)
        if (!file.exists()) {
            assets.open(assetName).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file
    }

    /** fakeVideo.mp4 에서 1초 시점 프레임을 추출 */
    private fun extractFrameFromVideo(assetName: String): Bitmap? {
        val videoFile = File(cacheDir, assetName)
        if (!videoFile.exists()) {
            assets.open(assetName).use { input ->
                FileOutputStream(videoFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}

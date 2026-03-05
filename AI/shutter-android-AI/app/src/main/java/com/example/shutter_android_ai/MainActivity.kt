package com.example.shutter_android_ai

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.shutter_android_ai.R
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    // C++ JNI 함수 선언
    private external fun detectDeepfake(modelPath: String, audioData: FloatArray): FloatArray

    companion object {
        init {
            System.loadLibrary("shutter_android_ai")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnTestInference = findViewById<Button>(R.id.btnTestInference)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        btnTestInference.setOnClickListener {
            tvResult.text = "분석 중..."

            thread {
                try {
                    val modelPath = getAssetFilePath(this@MainActivity, "voice_deepfake_model.pt")
                    val audioPath = getAssetFilePath(this@MainActivity, "test.flac")
                    val audioData = decodeAudioFromFile(audioPath, 64000)

                    // 2. 결과를 배열로 받습니다.
                    val results: FloatArray = detectDeepfake(modelPath, audioData)

                    // 3. 배열 인덱스를 통해 안전하게 값을 가져옵니다.
                    val score = results[0]
                    val loadTime = results[1]
                    val inferenceTime = results[2]

                    runOnUiThread {
                        if (score <= -1.0f) { // -1.0 이하면 로드 실패로 간주
                            tvResult.text = "에러: 모델 로드 실패"
                        } else {
                            val resultText = """
                                [추론 성공]
                                Score: ${String.format("%.4f", score)}
                                Load: ${String.format("%.2f", loadTime)} ms
                                Infer: ${String.format("%.2f", inferenceTime)} ms
                            """.trimIndent()
                            tvResult.text = resultText
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tvResult.text = "에러 발생: ${e.message}"
                    }
                }
            }
        }
    }

    // 수정된 디코딩 함수: Context 대신 로컬 파일 경로(filePath)를 직접 받습니다.
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
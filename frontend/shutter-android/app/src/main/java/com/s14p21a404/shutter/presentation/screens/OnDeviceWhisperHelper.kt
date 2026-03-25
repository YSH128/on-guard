package com.s14p21a404.shutter.presentation.screens

import android.content.Context
import android.util.Log
import com.argmaxinc.whisperkit.ExperimentalWhisperKit
import com.argmaxinc.whisperkit.WhisperKit
import com.google.gson.JsonParser
import com.s14p21a404.shutter.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.URL
import javax.net.ssl.HttpsURLConnection

@OptIn(ExperimentalWhisperKit::class)
class OnDeviceWhisperHelper(private val context: Context) {

    private var whisperKit: WhisperKit? = null
    private var isInitialized = false
    
    var onResult: ((String) -> Unit)? = null
    var onAnalysisResult: ((String) -> Unit)? = null
    
    private var isProcessingChunk = false

    suspend fun initModel() = withContext(Dispatchers.IO) {
        try {
            Log.d("WhisperSTT", "Initializing WhisperKit...")
            
            whisperKit = WhisperKit.Builder()
                .setModel(WhisperKit.Builder.OPENAI_BASE)
                .setEncoderBackend(WhisperKit.Builder.CPU_ONLY)
                .setDecoderBackend(WhisperKit.Builder.CPU_ONLY)
                .setApplicationContext(context.applicationContext)
                .setCallback { what, result ->
                    when (what) {
                        WhisperKit.TextOutputCallback.MSG_INIT -> {
                            Log.d("WhisperSTT", "✅ WhisperKit model initialized successfully.")
                            isInitialized = true
                        }
                        WhisperKit.TextOutputCallback.MSG_TEXT_OUT -> {
                            // val rawText = result.text
                            // if (rawText.contains("<|translate|>")) {
                            //     Log.d("WhisperSTT", "⚠️ 번역 모드 감지 → 영어 결과 폐기: $rawText")
                            //     return@setCallback
                            // }
                            val rawText = result.segments
                                .joinToString(" ") { it.text }
                            
                            // [Music], (음악), ♪음악♪ 형태 등 Whisper의 대표적인 비음성 환각(Hallucination) 태그를 모두 제거
                            val cleanText = rawText
                                .replace(Regex("\\[.*?\\]|\\(.*?\\)|♪.*?♪|\\*.*?\\*"), "")
                                .trim()

                            // Log.d("WhisperSTT", "Transcribed (clean): $cleanText")
                            if (cleanText.isNotBlank()) {
                                onResult?.invoke(cleanText)
                                // GMS 분석 자동 실행
                                sttScope.launch {
                                    analyzeTextWithGMS(cleanText)
                                }
                            }
                        }
                        WhisperKit.TextOutputCallback.MSG_CLOSE -> {
                            Log.d("WhisperSTT", "WhisperKit cleanup complete.")
                        }
                    }
                }
                .build()

            whisperKit?.loadModel()?.collect { progress ->
                Log.d("WhisperSTT", "Model loading progress: ${progress.fractionCompleted}%")
            }
            
            whisperKit?.init(frequency = 16000, channels = 1, duration = 0)
            
        } catch (e: Exception) {
            Log.e("WhisperSTT", "❌ Error initializing WhisperKit: ${e.message}")
        }
    }

    private val sttScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun transcribe(audioChunk: ByteArray) {
        if (whisperKit == null || !isInitialized) {
            Log.w("WhisperSTT", "WhisperKit not initialized yet.")
            return
        }
        if (isProcessingChunk) {
            Log.w("WhisperSTT", "Already processing an audio chunk. Skipping.")
            return
        }
        
        sttScope.launch {
            try {
                isProcessingChunk = true
                
                Log.d("WhisperSTT", "🔄 JNI Native STT 추론 시작... 크기: ${audioChunk.size} bytes")
                
                val seconds = whisperKit?.transcribe(audioChunk) ?: 0
                
                Log.d("WhisperSTT", "✅ Inference completed. Processed $seconds seconds of audio.")
                
            } catch (e: Exception) {
                Log.e("WhisperSTT", "❌ Inference error: ${e.message}", e)
            } finally {
                isProcessingChunk = false
            }
        }
    }

    fun release() {
        try {
            isInitialized = false
            whisperKit?.deinitialize()
        } catch (e: Exception) {
            Log.e("WhisperSTT", "Error releasing resources", e)
        }
    }

    suspend fun analyzeTextWithGMS(text: String) = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            val errorMsg = "변환된 텍스트가 없어 분석할 수 없습니다."
            Log.w("GMSAPI", errorMsg)
            onAnalysisResult?.invoke(errorMsg)
            return@withContext errorMsg
        }

        val gmsApiToken = BuildConfig.GMS_API_TOKEN
        if (gmsApiToken.isBlank()) {
            val errorMsg = "[오류] GMS_API_TOKEN이 설정되지 않았습니다."
            Log.e("GMSAPI", errorMsg)
            onAnalysisResult?.invoke(errorMsg)
            return@withContext errorMsg
        }

        val url = "https://gms.ssafy.io/gmsapi/api.openai.com/v1/chat/completions"
        val systemPrompt = (
            "주어진 대화(STT 결과)의 내용을 분석하여 해당하는 의도를 모두 단답형으로 출력해주세요.\n" +
            "다음 옵션 중 해당하는 것을 모두 선택하세요: '계좌 이체 요구', '특정 장소 유인', '개인정보 요구', '일상 대화'\n" +
            "여러 개일 경우 쉼표(,)로 구분하고, 설명이나 부가적인 문구, 마침표 없이 선택된 문자열만 정확히 출력하세요."
        )

        return@withContext try {
            val requestBody = buildGMSRequestJson(systemPrompt, text)
            Log.d("GMSAPI", "🔄 GMS API 요청 시작... 텍스트: $text")

            val urlConnection = URL(url).openConnection() as HttpsURLConnection
            urlConnection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $gmsApiToken")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 30000
            }

            urlConnection.outputStream.bufferedWriter().use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = urlConnection.responseCode
            val responseBody = if (responseCode == HttpsURLConnection.HTTP_OK) {
                urlConnection.inputStream.bufferedReader().use { it.readText() }
            } else {
                urlConnection.errorStream.bufferedReader().use { it.readText() }
            }

            Log.d("GMSAPI", "Response Code: $responseCode, Body: $responseBody")

            if (responseCode == HttpsURLConnection.HTTP_OK) {
                val analysisResult = parseGMSResponse(responseBody)
                Log.d("GMSAPI", "✅ 분석 완료: $analysisResult")
                onAnalysisResult?.invoke(analysisResult)
                analysisResult
            } else {
                val errorMsg = "[오류] GMS API 응답 실패 (Code: $responseCode)"
                Log.e("GMSAPI", errorMsg)
                onAnalysisResult?.invoke(errorMsg)
                errorMsg
            }
        } catch (e: Exception) {
            val errorMsg = "[오류] GMS API 요청 실패: ${e.message}"
            Log.e("GMSAPI", errorMsg, e)
            onAnalysisResult?.invoke(errorMsg)
            errorMsg
        }
    }

    private fun buildGMSRequestJson(systemPrompt: String, userText: String): String {
        return """
            {
                "model": "gpt-5-mini",
                "messages": [
                    {
                        "role": "developer",
                        "content": ${escapeJsonString(systemPrompt)}
                    },
                    {
                        "role": "user",
                        "content": ${escapeJsonString(userText)}
                    }
                ]
            }
        """.trimIndent()
    }

    private fun escapeJsonString(str: String): String {
        return "\"${str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")}\""
    }

    private fun parseGMSResponse(jsonResponse: String): String {
        return try {
            val json = JsonParser.parseString(jsonResponse).asJsonObject
            val choices = json.getAsJsonArray("choices")
            val message = choices.get(0).asJsonObject.get("message").asJsonObject
            message.get("content").asString
        } catch (e: Exception) {
            Log.e("GMSAPI", "JSON 파싱 오류: ${e.message}")
            "분석 결과를 파싱할 수 없습니다."
        }
    }
}

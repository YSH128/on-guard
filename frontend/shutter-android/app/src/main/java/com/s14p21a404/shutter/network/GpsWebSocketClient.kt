package com.s14p21a404.shutter.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.s14p21a404.shutter.BuildConfig
import com.s14p21a404.shutter.data.token.TokenManager
import com.s14p21a404.shutter.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

// ── WebSocket 이벤트 ──────────────────────────────────────────────────────────

sealed class GpsWebSocketEvent {
    data class LocationUpdate(
        val wardId: Long,
        val lat: Double,
        val lng: Double,
        val updatedAt: String,
        val isSharing: Boolean,
    ) : GpsWebSocketEvent()

    data class LocationExpired(
        val wardId: Long,
    ) : GpsWebSocketEvent()

    object Connected : GpsWebSocketEvent()
    object Disconnected : GpsWebSocketEvent()
}

// ── 서버 메시지 DTO ───────────────────────────────────────────────────────────

private data class WsMessage(
    val type: String? = null,
    @SerializedName("ward_id") val wardId: Long? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("is_sharing") val isSharing: Boolean? = null,
)

// ── STOMP 유틸 ────────────────────────────────────────────────────────────────

private fun stompFrame(command: String, headers: Map<String, String> = emptyMap(), body: String = ""): String {
    val sb = StringBuilder()
    sb.append(command).append("\n")
    headers.forEach { (k, v) -> sb.append("$k:$v\n") }
    sb.append("\n")
    sb.append(body)
    sb.append("\u0000")
    return sb.toString()
}

/** STOMP 프레임 파싱: (command, headers, body) 반환 */
private fun parseStompFrame(text: String): Triple<String, Map<String, String>, String> {
    val raw = if (text.endsWith("\u0000")) text.dropLast(1) else text
    val headerBodySplit = raw.indexOf("\n\n")
    val headerPart = if (headerBodySplit >= 0) raw.substring(0, headerBodySplit) else raw
    val body = if (headerBodySplit >= 0) raw.substring(headerBodySplit + 2) else ""

    val lines = headerPart.split("\n")
    val command = lines.firstOrNull()?.trim() ?: ""
    val headers = lines.drop(1).mapNotNull { line ->
        val idx = line.indexOf(':')
        if (idx >= 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        else null
    }.toMap()

    return Triple(command, headers, body)
}

// ── 클라이언트 ────────────────────────────────────────────────────────────────

class GpsWebSocketClient(
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository,
) {

    private val gson = Gson()
    private val okHttpClient = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var pendingWardIds: List<Long> = emptyList()
    private var isConnected = false
    private var isConnecting = false
    private val subscribedWardIds = mutableSetOf<Long>()

    private val _events = MutableSharedFlow<GpsWebSocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GpsWebSocketEvent> = _events.asSharedFlow()

    fun connect(wardIds: List<Long>) {
        if (wardIds.isEmpty()) return

        // 이미 연결된 경우: 새로 추가된 wardId만 구독
        if (isConnected) {
            val newWardIds = wardIds.filter { it !in subscribedWardIds }
            if (newWardIds.isEmpty()) {
                Log.d(TAG, "이미 모두 구독 중 — 무시")
                return
            }
            newWardIds.forEachIndexed { index, wardId ->
                val subscribeFrame = stompFrame(
                    "SUBSCRIBE",
                    mapOf(
                        "id" to "sub-${subscribedWardIds.size + index}",
                        "destination" to "/topic/location/$wardId",
                    )
                )
                webSocket?.send(subscribeFrame)
                subscribedWardIds.add(wardId)
                Log.d(TAG, "추가 구독: /topic/location/$wardId")
            }
            return
        }

        // 연결 중(handshake 진행 중)이면 새 연결 시도 생략
        if (isConnecting) {
            Log.d(TAG, "이미 연결 중 — connect() 무시")
            return
        }

        val token = tokenManager.getAccessToken() ?: run {
            Log.w(TAG, "액세스 토큰 없음 — WebSocket 연결 취소")
            return
        }

        isConnecting = true
        pendingWardIds = wardIds

        val url = BuildConfig.SERVER_BASE_URL.trimEnd('/') + "/api/v1/ws/gps"
        val request = Request.Builder().url(url).build()

        Log.d(TAG, "STOMP WebSocket 연결 시도: $url")

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket 연결됨 — STOMP CONNECT 전송")
                // STOMP CONNECT (토큰은 STOMP 헤더에 포함)
                val connectFrame = stompFrame(
                    "CONNECT",
                    mapOf(
                        "accept-version" to "1.1,1.2",
                        "heart-beat" to "0,0",
                        "Authorization" to "Bearer $token",
                    )
                )
                webSocket.send(connectFrame)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val (command, _, body) = parseStompFrame(text)

                when (command) {
                    "CONNECTED" -> {
                        Log.d(TAG, "STOMP CONNECTED — 구독 시작")
                        isConnected = true
                        isConnecting = false
                        _events.tryEmit(GpsWebSocketEvent.Connected)
                        // 각 피보호자 위치 토픽 구독
                        pendingWardIds.forEachIndexed { index, wardId ->
                            val subscribeFrame = stompFrame(
                                "SUBSCRIBE",
                                mapOf(
                                    "id" to "sub-$index",
                                    "destination" to "/topic/location/$wardId",
                                )
                            )
                            webSocket.send(subscribeFrame)
                            subscribedWardIds.add(wardId)
                            Log.d(TAG, "구독: /topic/location/$wardId")
                        }
                    }

                    "MESSAGE" -> {
                        if (body.isBlank()) return
                        try {
                            val msg = gson.fromJson(body, WsMessage::class.java)
                            when (msg.type) {
                                "LOCATION_UPDATE" -> {
                                    if (msg.wardId != null && msg.lat != null && msg.lng != null
                                        && msg.updatedAt != null && msg.isSharing != null
                                    ) {
                                        _events.tryEmit(
                                            GpsWebSocketEvent.LocationUpdate(
                                                wardId = msg.wardId,
                                                lat = msg.lat,
                                                lng = msg.lng,
                                                updatedAt = msg.updatedAt,
                                                isSharing = msg.isSharing,
                                            )
                                        )
                                    }
                                }
                                "LOCATION_EXPIRED" -> {
                                    if (msg.wardId != null) {
                                        _events.tryEmit(GpsWebSocketEvent.LocationExpired(msg.wardId))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "MESSAGE 파싱 실패: $body", e)
                        }
                    }

                    "ERROR" -> {
                        Log.e(TAG, "STOMP ERROR: $body — 토큰 갱신 후 재연결 시도")
                        val currentWs = this@GpsWebSocketClient.webSocket
                        this@GpsWebSocketClient.webSocket = null
                        isConnected = false
                        isConnecting = false
                        subscribedWardIds.clear()
                        currentWs?.close(1000, "STOMP ERROR — reconnecting")
                        scope.launch {
                            authRepository.refreshToken()
                                .onSuccess {
                                    Log.d(TAG, "토큰 갱신 성공 — WebSocket 재연결")
                                    connect(pendingWardIds)
                                }
                                .onFailure { e ->
                                    Log.e(TAG, "토큰 갱신 실패 — 재연결 포기", e)
                                    _events.tryEmit(GpsWebSocketEvent.Disconnected)
                                }
                        }
                    }
                    else -> Log.d(TAG, "STOMP 프레임 수신: $command")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket 오류", t)
                if (this@GpsWebSocketClient.webSocket == webSocket) {
                    isConnected = false
                    isConnecting = false
                    subscribedWardIds.clear()
                    _events.tryEmit(GpsWebSocketEvent.Disconnected)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket 닫힘: $code $reason")
                if (this@GpsWebSocketClient.webSocket == webSocket) {
                    isConnected = false
                    isConnecting = false
                    subscribedWardIds.clear()
                    _events.tryEmit(GpsWebSocketEvent.Disconnected)
                }
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "정상 종료")
        webSocket = null
        isConnected = false
        isConnecting = false
        subscribedWardIds.clear()
    }

    companion object {
        private const val TAG = "GpsWebSocketClient"
    }
}

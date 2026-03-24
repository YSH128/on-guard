package com.s14p21a404.shutter.presentation.criminal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.FaceRetouchingNatural
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.agora.base.VideoFrame
import io.agora.rtc2.*
import io.agora.rtc2.video.IVideoFrameObserver
import io.agora.rtc2.video.VideoCanvas
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val AccentRed = Color(0xFFE53935)
private val OverlayBg = Color(0x99000000)

private const val AGORA_APP_ID = "6c6109e0b9644b599072a68b514d77a8"

/** ← 서버 포트 확정 후 변경 */
private const val WS_URL = "wss://j14a404.p.ssafy.io/ws/faceswap"

/** 30fps 기준 서버로 보낼 프레임 비율 (3 = 10fps) */
private const val SEND_EVERY_N = 3

@Composable
fun CriminalVideoCallScreen(
    channelId: String,
    faceSetupViewModel: FaceSetupViewModel,
    onCallEnd: () -> Unit,
) {
    val context = LocalContext.current
    val sourceFaceJpeg = faceSetupViewModel.sourceFaceJpeg

    var rtcEngine by remember { mutableStateOf<RtcEngine?>(null) }
    var remoteUid  by remember { mutableStateOf<Int?>(null) }
    var serverReady by remember { mutableStateOf(false) }

    val localView  = remember { mutableStateOf<SurfaceView?>(null) }
    val remoteView = remember { mutableStateOf<SurfaceView?>(null) }

    // 서버에서 받은 최신 스왑 프레임 (ARGB 바이트)
    val latestSwappedArgb = remember { mutableStateOf<ByteArray?>(null) }
    val latestFrameSize   = remember { mutableStateOf<Pair<Int,Int>?>(null) } // w,h
    val frameCounter      = remember { AtomicInteger(0) }
    val wsRef             = remember { mutableStateOf<WebSocket?>(null) }

    // remoteUid 변경 시 리모트 뷰 설정
    LaunchedEffect(remoteUid, rtcEngine) {
        val engine = rtcEngine ?: return@LaunchedEffect
        val uid    = remoteUid ?: return@LaunchedEffect
        val view   = remoteView.value ?: return@LaunchedEffect
        engine.setupRemoteVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }

    // rtcEngine 준비 후 로컬 뷰 설정
    LaunchedEffect(rtcEngine) {
        val engine = rtcEngine ?: return@LaunchedEffect
        val view   = localView.value ?: return@LaunchedEffect
        engine.setupLocalVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0))
        engine.startPreview()
    }

    // WebSocket 연결
    LaunchedEffect(Unit) {
        if (sourceFaceJpeg == null) return@LaunchedEffect

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder().url(WS_URL).build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket 연결됨 — 소스 얼굴 전송")
                val b64 = Base64.encodeToString(sourceFaceJpeg, Base64.NO_WRAP)
                ws.send("""{"type":"set_source","image":"$b64"}""")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val data = JSONObject(text)
                if (data.optString("type") == "ready") {
                    serverReady = true
                    Log.d(TAG, "서버 준비 완료")
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                // 서버에서 받은 스왑된 JPEG → ARGB 디코딩
                val arr = bytes.toByteArray()
                val bmp = BitmapFactory.decodeByteArray(arr, 0, arr.size) ?: return
                val argb = ByteArray(bmp.width * bmp.height * 4)
                bmp.copyPixelsToBuffer(ByteBuffer.wrap(argb))
                latestSwappedArgb.value = argb
                latestFrameSize.value   = bmp.width to bmp.height
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket 오류: ${t.message}")
            }
        })
        wsRef.value = ws
    }

    // Agora RTC 엔진 초기화
    LaunchedEffect(Unit) {
        val config = RtcEngineConfig().apply {
            mContext     = context
            mAppId       = AGORA_APP_ID
            mEventHandler = object : IRtcEngineEventHandler() {
                override fun onUserJoined(uid: Int, elapsed: Int) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post { remoteUid = uid }
                }
                override fun onUserOffline(uid: Int, reason: Int) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post { remoteUid = null }
                }
            }
        }

        val engine = RtcEngine.create(config)
        engine.enableVideo()
        engine.enableAudio()

        engine.registerVideoFrameObserver(object : IVideoFrameObserver {

            override fun getVideoFrameProcessMode(): Int = IVideoFrameObserver.PROCESS_MODE_READ_WRITE
            override fun getVideoFormatPreference(): Int = IVideoFrameObserver.VIDEO_PIXEL_I420
            override fun getRotationApplied():  Boolean = false
            override fun getMirrorApplied():    Boolean = false
            override fun getObservedFramePosition(): Int = IVideoFrameObserver.POSITION_POST_CAPTURER

            override fun onCaptureVideoFrame(sourceType: Int, videoFrame: VideoFrame): Boolean {
                val i420 = videoFrame.buffer.toI420() ?: return true
                val w = i420.width
                val h = i420.height

                // 1. 서버에서 받은 최신 프레임 → 버퍼에 덮어쓰기
                val swapped = latestSwappedArgb.value
                val size    = latestFrameSize.value
                if (swapped != null && size != null && size.first == w && size.second == h) {
                    argbToI420(
                        swapped, w, h,
                        i420.dataY, i420.strideY,
                        i420.dataU, i420.strideU,
                        i420.dataV, i420.strideV,
                    )
                }

                // 2. 현재 프레임을 서버로 전송 (N프레임마다 1회)
                val ws = wsRef.value
                if (serverReady && ws != null && frameCounter.incrementAndGet() % SEND_EVERY_N == 0) {
                    try {
                        val argb = i420ToArgb(
                            i420.dataY, i420.strideY,
                            i420.dataU, i420.strideU,
                            i420.dataV, i420.strideV,
                            w, h,
                        )
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(argb))

                        // 해상도 절반 축소 후 JPEG 전송 (대역폭 절약)
                        val half = Bitmap.createScaledBitmap(bmp, w / 2, h / 2, false)
                        val out  = ByteArrayOutputStream()
                        half.compress(Bitmap.CompressFormat.JPEG, 70, out)
                        ws.send(out.toByteArray().toByteString())
                    } catch (e: Exception) {
                        Log.e(TAG, "프레임 전송 오류", e)
                    }
                }

                i420.release()
                return true
            }

            override fun onPreEncodeVideoFrame(sourceType: Int, videoFrame: VideoFrame): Boolean = true
            override fun onMediaPlayerVideoFrame(videoFrame: VideoFrame, mediaPlayerId: Int): Boolean = false
            override fun onRenderVideoFrame(channelId: String, uid: Int, videoFrame: VideoFrame): Boolean = false
        })

        val options = ChannelMediaOptions().apply {
            channelProfile        = Constants.CHANNEL_PROFILE_COMMUNICATION
            clientRoleType        = Constants.CLIENT_ROLE_BROADCASTER
            publishCameraTrack    = true
            publishMicrophoneTrack = true
            autoSubscribeAudio    = true
            autoSubscribeVideo    = true
        }
        engine.joinChannel(null, channelId, 0, options)
        rtcEngine = engine
    }

    DisposableEffect(Unit) {
        onDispose {
            wsRef.value?.close(1000, null)
            rtcEngine?.apply {
                registerVideoFrameObserver(null)
                leaveChannel()
            }
            RtcEngine.destroy()
            rtcEngine = null
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    remoteView.value = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    localView.value = this
                }
            },
            modifier = Modifier
                .width(120.dp).height(160.dp)
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 100.dp),
        )

        // 상태 배지
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(50))
                .background(OverlayBg)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.FaceRetouchingNatural, contentDescription = null,
                tint = AccentRed, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    serverReady           -> "딥페이크 송출 중"
                    sourceFaceJpeg != null -> "서버 연결 중..."
                    else                  -> "소스 얼굴 없음"
                },
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
        }

        // 종료 버튼
        IconButton(
            onClick = { rtcEngine?.leaveChannel(); onCallEnd() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(AccentRed),
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = "통화 종료",
                tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

// ── I420 ↔ ARGB 변환 ─────────────────────────────────────────────────────────

private fun i420ToArgb(
    yBuf: ByteBuffer?, yStride: Int,
    uBuf: ByteBuffer?, uStride: Int,
    vBuf: ByteBuffer?, vStride: Int,
    width: Int, height: Int,
): ByteArray {
    val argb = ByteArray(width * height * 4)
    val yArr = yBuf?.let { b -> ByteArray(b.remaining()).also { b.duplicate().get(it) } } ?: return argb
    val uArr = uBuf?.let { b -> ByteArray(b.remaining()).also { b.duplicate().get(it) } } ?: return argb
    val vArr = vBuf?.let { b -> ByteArray(b.remaining()).also { b.duplicate().get(it) } } ?: return argb
    for (row in 0 until height) {
        for (col in 0 until width) {
            val y = yArr[row * yStride + col].toInt() and 0xFF
            val u = (uArr[(row / 2) * uStride + col / 2].toInt() and 0xFF) - 128
            val v = (vArr[(row / 2) * vStride + col / 2].toInt() and 0xFF) - 128
            val r = (y + 1.370705f * v).toInt().coerceIn(0, 255)
            val g = (y - 0.337633f * u - 0.698001f * v).toInt().coerceIn(0, 255)
            val b = (y + 1.732446f * u).toInt().coerceIn(0, 255)
            val idx = (row * width + col) * 4
            argb[idx] = r.toByte(); argb[idx+1] = g.toByte()
            argb[idx+2] = b.toByte(); argb[idx+3] = 0xFF.toByte()
        }
    }
    return argb
}

private fun argbToI420(
    argb: ByteArray, width: Int, height: Int,
    yBuf: ByteBuffer?, yStride: Int,
    uBuf: ByteBuffer?, uStride: Int,
    vBuf: ByteBuffer?, vStride: Int,
) {
    if (yBuf == null || uBuf == null || vBuf == null) return
    yBuf.rewind(); uBuf.rewind(); vBuf.rewind()
    for (row in 0 until height) {
        for (col in 0 until width) {
            val idx = (row * width + col) * 4
            val r = argb[idx].toInt() and 0xFF
            val g = argb[idx+1].toInt() and 0xFF
            val b = argb[idx+2].toInt() and 0xFF
            val y = (0.257f*r + 0.504f*g + 0.098f*b + 16).toInt().coerceIn(0, 255)
            yBuf.put(row * yStride + col, y.toByte())
            if (row % 2 == 0 && col % 2 == 0) {
                val u = (-0.148f*r - 0.291f*g + 0.439f*b + 128).toInt().coerceIn(0, 255)
                val v = ( 0.439f*r - 0.368f*g - 0.071f*b + 128).toInt().coerceIn(0, 255)
                uBuf.put((row/2) * uStride + col/2, u.toByte())
                vBuf.put((row/2) * vStride + col/2, v.toByte())
            }
        }
    }
}

private const val TAG = "CriminalVideoCall"
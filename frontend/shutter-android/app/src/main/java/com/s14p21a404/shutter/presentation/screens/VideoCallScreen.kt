package com.s14p21a404.shutter.presentation.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.agora.rtc2.*
import io.agora.rtc2.audio.AudioParams
import io.agora.rtc2.video.VideoCanvas
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

@Composable
fun VideoCallScreen(channelId: String) {
    val context = LocalContext.current
    val appId = "6c6109e0b9644b599072a68b514d77a8"

    var rtcEngine by remember { mutableStateOf<RtcEngine?>(null) }
    var remoteUid by remember { mutableStateOf<Int?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var outputStream by remember { mutableStateOf<FileOutputStream?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    // 로컬/리모트 SurfaceView를 remember로 보관
    val localView = remember { mutableStateOf<SurfaceView?>(null) }
    val remoteView = remember { mutableStateOf<SurfaceView?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val audioFrameObserver = remember {
        object : IAudioFrameObserver {
            override fun onPlaybackAudioFrame(
                channelId: String, type: Int, samplesPerChannel: Int,
                bytesPerSample: Int, channels: Int, samplesPerSec: Int,
                buffer: ByteBuffer, renderTimeMs: Long, avsync_type: Int
            ): Boolean {
                if (isRecording && outputStream != null) {
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)
                    outputStream?.write(data)
                }
                return true
            }
            override fun onRecordAudioFrame(channelId: String, type: Int, samplesPerChannel: Int, bytesPerSample: Int, channels: Int, samplesPerSec: Int, buffer: ByteBuffer, renderTimeMs: Long, avsync_type: Int): Boolean = true
            override fun onMixedAudioFrame(channelId: String, type: Int, samplesPerChannel: Int, bytesPerSample: Int, channels: Int, samplesPerSec: Int, buffer: ByteBuffer, renderTimeMs: Long, avsync_type: Int): Boolean = true
            override fun onEarMonitoringAudioFrame(type: Int, samplesPerChannel: Int, bytesPerSample: Int, channels: Int, samplesPerSec: Int, buffer: ByteBuffer, renderTimeMs: Long, avsync_type: Int): Boolean = true
            override fun onPlaybackAudioFrameBeforeMixing(channelId: String, userId: Int, type: Int, samplesPerChannel: Int, bytesPerSample: Int, channels: Int, samplesPerSec: Int, buffer: ByteBuffer, renderTimeMs: Long, avsync_type: Int): Boolean = true
            override fun getObservedAudioFramePosition(): Int = 0x000F
            override fun getRecordAudioParams(): AudioParams = AudioParams(44100, 1, 0, 1024)
            override fun getPlaybackAudioParams(): AudioParams = AudioParams(44100, 1, 0, 1024)
            override fun getMixedAudioParams(): AudioParams = AudioParams(44100, 1, 0, 1024)
            override fun getEarMonitoringAudioParams(): AudioParams = AudioParams(44100, 1, 0, 1024)
        }
    }

    // rtcEngine이 세팅되면 로컬 비디오 세팅
    LaunchedEffect(rtcEngine) {
        val engine = rtcEngine ?: return@LaunchedEffect
        val view = localView.value ?: return@LaunchedEffect
        engine.setupLocalVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0))
        engine.startPreview()
    }

    // remoteUid가 바뀌면 리모트 비디오 세팅
    LaunchedEffect(remoteUid, rtcEngine) {
        val engine = rtcEngine ?: return@LaunchedEffect
        val uid = remoteUid ?: return@LaunchedEffect
        val view = remoteView.value ?: return@LaunchedEffect
        android.util.Log.d("VIDEO", "setupRemoteVideo uid=$uid view=$view")
        engine.setupRemoteVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }

    LaunchedEffect(Unit) {
        val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        val needRequest = permissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needRequest) launcher.launch(permissions)

        val config = RtcEngineConfig().apply {
            mContext = context
            mAppId = appId
            mEventHandler = object : IRtcEngineEventHandler() {
                override fun onUserJoined(uid: Int, elapsed: Int) {
                    android.util.Log.d("Agora", "상대방 입장: $uid")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        remoteUid = uid
                    }
                }
                override fun onUserOffline(uid: Int, reason: Int) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        remoteUid = null
                    }
                }
                override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
                    android.util.Log.d("Agora", "상대방 영상 상태: uid=$uid state=$state")
                    if (state == 2) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            remoteUid = uid
                        }
                    }
                }
            }
        }

        val engine = RtcEngine.create(config)
        engine.enableVideo()
        engine.enableAudio()
        engine.muteLocalAudioStream(false)
        engine.adjustRecordingSignalVolume(300)
        engine.adjustPlaybackSignalVolume(200)
        engine.registerAudioFrameObserver(audioFrameObserver)

        val file = File(context.getExternalFilesDir(null), "remote_only.pcm")
        recordingFile = file
        outputStream = FileOutputStream(file)
        isRecording = true

        val options = ChannelMediaOptions().apply {
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            publishMicrophoneTrack = true
            autoSubscribeAudio = true
        }
        engine.joinChannel(null, channelId, 0, options)
        rtcEngine = engine  // 마지막에 세팅
    }

    DisposableEffect(Unit) {
        onDispose {
            isRecording = false
            outputStream?.flush()
            outputStream?.close()
            outputStream = null

            recordingFile?.let { pcmFile ->
                if (pcmFile.exists() && pcmFile.length() > 0) {
                    val wavFile = File(context.getExternalFilesDir(null), "remote_only.wav")
                    convertPcmToWav(pcmFile, wavFile, 44100, 1, 16)
                    pcmFile.delete()
                    sendWavToJetson(wavFile)
                }
            }

            rtcEngine?.leaveChannel()
            RtcEngine.destroy()
            rtcEngine = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 상대방 화면 (전체)
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    remoteView.value = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 내 화면 (우측 하단)
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    localView.value = this
                }
            },
            modifier = Modifier
                .width(120.dp)
                .height(160.dp)
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

private fun sendWavToJetson(wavFile: File) {
    Thread {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("ws://116.43.199.143:8000/ws/stt")
                .build()

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    android.util.Log.d("WS", "✅ WebSocket 연결됨")
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    android.util.Log.d("WS", "서버 메시지 수신: $text")
                    val audioBytes = wavFile.readBytes()
                    webSocket.send(ByteString.of(*audioBytes))
                    android.util.Log.d("WS", "✅ WAV 전송 완료: ${audioBytes.size} bytes")
                }
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    android.util.Log.d("WS", "서버 바이너리 수신: ${bytes.size} bytes")
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    android.util.Log.e("WS", "❌ WebSocket 오류: ${t.message}")
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    android.util.Log.d("WS", "WebSocket 닫힘: $reason")
                }
            }

            client.newWebSocket(request, listener)

        } catch (e: Exception) {
            android.util.Log.e("WS", "❌ 전송 실패: ${e.message}")
        }
    }.start()
}

private fun convertPcmToWav(pcmFile: File, wavFile: File, sampleRate: Int, channels: Int, bitDepth: Int) {
    val pcmData = pcmFile.readBytes()
    val totalDataLen = pcmData.size + 36
    val byteRate = sampleRate * channels * bitDepth / 8

    wavFile.outputStream().use { out ->
        out.write("RIFF".toByteArray())
        out.write(intToBytes(totalDataLen))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intToBytes(16))
        out.write(shortToBytes(1))
        out.write(shortToBytes(channels.toShort()))
        out.write(intToBytes(sampleRate))
        out.write(intToBytes(byteRate))
        out.write(shortToBytes((channels * bitDepth / 8).toShort()))
        out.write(shortToBytes(bitDepth.toShort()))
        out.write("data".toByteArray())
        out.write(intToBytes(pcmData.size))
        out.write(pcmData)
    }
}

private fun intToBytes(value: Int): ByteArray =
    byteArrayOf(
        (value and 0xFF).toByte(),
        (value shr 8 and 0xFF).toByte(),
        (value shr 16 and 0xFF).toByte(),
        (value shr 24 and 0xFF).toByte()
    )

private fun shortToBytes(value: Short): ByteArray =
    byteArrayOf(
        (value.toInt() and 0xFF).toByte(),
        (value.toInt() shr 8 and 0xFF).toByte()
    )
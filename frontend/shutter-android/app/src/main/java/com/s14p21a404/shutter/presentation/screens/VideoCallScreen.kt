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
        rtcEngine = engine

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
                }
            }

            rtcEngine?.leaveChannel()
            RtcEngine.destroy()
            rtcEngine = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        remoteUid?.let { uid ->
            key(uid) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).also { view ->
                            view.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            rtcEngine?.setupRemoteVideo(
                                VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, uid)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        key(rtcEngine) {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).also { view ->
                        rtcEngine?.setupLocalVideo(
                            VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0)
                        )
                        rtcEngine?.startPreview()
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
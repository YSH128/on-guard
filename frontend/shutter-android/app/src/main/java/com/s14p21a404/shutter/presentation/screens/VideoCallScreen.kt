package com.s14p21a404.shutter.presentation.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.s14p21a404.shutter.data.repository.VideoCallSessionStore
import com.s14p21a404.shutter.di.ServiceLocator
import com.s14p21a404.shutter.presentation.service.CriminalLocationBridge
import com.s14p21a404.shutter.presentation.service.VideoCallDetectService
import com.s14p21a404.shutter.push.IncomingCallEventBus
import kotlinx.coroutines.launch
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

// ── 색상 상수 ──
private val ControlBarBg = Color.Black
private val EndCallRed = Color(0xFFEF4444)
private val ControlBtnBg = Color(0xFF404040)
private val BounceDotColor = Color(0xFFFACC15)
private val AvatarBg = Color(0xFF6B7280)
private val AvatarBgLarge = Color(0xFF4B5563)
private val GradientTop = Color(0xFF525252)
private val GradientBottom = Color(0xFF262626)
private val RemoteGradientTop = Color(0xFF404040)
private val RemoteGradientBottom = Color(0xFF171717)

@Composable
fun VideoCallScreen(
    channelId: String,
    callerName: String = "",
    opponentKakaoId: Long = 0L,
    onCallEnded: () -> Unit = {},
) {
    val context = LocalContext.current
    val appId = com.s14p21a404.shutter.BuildConfig.AGORA_APP_ID

    // 토큰과 uid는 세션 저장소 + TokenManager에서 가져옴
    val serviceLocator = remember { ServiceLocator.getInstance(context) }
    val agoraToken = remember { VideoCallSessionStore.agoraToken }
    val agoraUid = remember { serviceLocator.tokenManager.getUserId().toInt() }

    val videoCallRepository = remember { serviceLocator.videoCallRepository }
    val criminalLocationApi = remember { serviceLocator.criminalLocationApi }
    val scope = rememberCoroutineScope()

    var rtcEngine by remember { mutableStateOf<RtcEngine?>(null) }
    var remoteUid by remember { mutableStateOf<Int?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var outputStream by remember { mutableStateOf<FileOutputStream?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    var isMuted by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var callEnded by remember { mutableStateOf(false) }
    var callDuration by remember { mutableIntStateOf(0) }

    val isEndingCall = remember { mutableStateOf(false) }

    // ── 범죄자 위치 추적 상태 ──
    var isRequestingLocation by remember { mutableStateOf(false) }
    val warningOverlayManager = remember {
        ServiceLocator.getInstance(context).warningOverlayManager
    }

    // criminal_location FCM 수신 콜백 등록 (위치 데이터는 오버레이 바텀시트에 표시)
    DisposableEffect(Unit) {
        CriminalLocationBridge.onLocationReceived = { country ->
            android.util.Log.d("CriminalLocation", "위치 수신됨 — country: $country")
            isRequestingLocation = false
            warningOverlayManager.updateCountry(country)
        }
        onDispose {
            CriminalLocationBridge.onLocationReceived = null
        }
    }

    /** leaveChannel + callEnded 처리를 한 곳에서 관리 */
    fun endCall() {
        if (isEndingCall.value) return
        isEndingCall.value = true
        rtcEngine?.leaveChannel()
        callEnded = true
        videoCallRepository.setInAppCallActive(false)
        VideoCallDetectService.onInAppCallEnd = null
        VideoCallDetectService.onInAppDeepfakeDetected = null
    }

    // CALL_REJECTED FCM 수신 시 통화 종료
    LaunchedEffect(Unit) {
        IncomingCallEventBus.callRejected.collect {
            android.util.Log.d("VideoCall", "CALL_REJECTED — 통화 종료")
            endCall()
        }
    }

    val isConnecting = remoteUid == null && !callEnded

    val localView = remember { mutableStateOf<SurfaceView?>(null) }
    val remoteView = remember { mutableStateOf<SurfaceView?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    // ── 딥페이크 감지 연동: 상대방 연결 시 인앱 통화 활성화 ──
    LaunchedEffect(remoteUid, callEnded) {
        if (remoteUid != null && !callEnded) {
            android.util.Log.d("VideoCallScreen", "🛡️ 인앱 영상통화 활성화 → 딥페이크 감지 시작")
            videoCallRepository.setInAppCallActive(true)
            VideoCallDetectService.onInAppCallEnd = { endCall() }
            // 딥페이크 감지 시 상대방 GPS 자동 요청
            VideoCallDetectService.onInAppDeepfakeDetected = {
                if (!isRequestingLocation && opponentKakaoId != 0L) {
                    isRequestingLocation = true
                    android.util.Log.d("CriminalLocation", "딥페이크 감지 → 위치 자동 요청 — opponentKakaoId: $opponentKakaoId")
                    scope.launch {
                        criminalLocationApi.requestLocation(opponentKakaoId)
                            .onSuccess { android.util.Log.d("CriminalLocation", "위치 요청 API 성공") }
                            .onFailure { e ->
                                android.util.Log.e("CriminalLocation", "위치 요청 API 실패: ${e.message}", e)
                                isRequestingLocation = false
                            }
                    }
                }
            }
        }
    }

    // ── 통화 시간 타이머 ──
    LaunchedEffect(isConnecting, callEnded) {
        if (!isConnecting && !callEnded) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                callDuration++
            }
        }
    }

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

    // ── 로컬 비디오 세팅 (rtcEngine 또는 localView 준비 시 재시도) ──
    val currentLocalView = localView.value
    LaunchedEffect(rtcEngine, currentLocalView) {
        val engine = rtcEngine ?: return@LaunchedEffect
        val view = currentLocalView ?: return@LaunchedEffect
        engine.setupLocalVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0))
        engine.startPreview()
    }

    DisposableEffect(currentLocalView, rtcEngine) {
        val view = currentLocalView
        val engine = rtcEngine
        if (view == null || engine == null) {
            onDispose { }
        } else {
            val callback = object : android.view.SurfaceHolder.Callback {
                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                    android.util.Log.d("VIDEO", "Local surface created → re-setup")
                    engine.setupLocalVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0))
                    engine.startPreview()
                }
                override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                    engine.setupLocalVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0))
                    engine.startPreview()
                }
                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {}
            }
            view.holder.addCallback(callback)
            onDispose { view.holder.removeCallback(callback) }
        }
    }

    // ── 리모트 비디오 세팅 (remoteUid 또는 remoteView 준비 시 재시도) ──
    val currentRemoteView = remoteView.value
    LaunchedEffect(remoteUid, rtcEngine, currentRemoteView) {
        val engine = rtcEngine ?: return@LaunchedEffect
        val uid = remoteUid ?: return@LaunchedEffect
        val view = currentRemoteView ?: return@LaunchedEffect
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
                override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
                    android.util.Log.d("Agora", "✅ 채널 입장 성공: channel=$channel uid=$uid")
                }
                override fun onError(err: Int) {
                    android.util.Log.e("Agora", "❌ 에러: err=$err")
                }
                override fun onConnectionStateChanged(state: Int, reason: Int) {
                    android.util.Log.d("Agora", "🔗 연결 상태: state=$state reason=$reason")
                }
                override fun onUserJoined(uid: Int, elapsed: Int) {
                    android.util.Log.d("Agora", "상대방 입장: $uid")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        remoteUid = uid
                    }
                }
                override fun onUserOffline(uid: Int, reason: Int) {
                    android.util.Log.d("Agora", "상대방 퇴장: uid=$uid reason=$reason")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        remoteUid = null
                        endCall()
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
            publishCameraTrack = true
            publishMicrophoneTrack = true
            autoSubscribeVideo = true
            autoSubscribeAudio = true
        }

        android.util.Log.d("Agora", "joinChannel channel=$channelId uid=$agoraUid token=${if (agoraToken != null) "ok" else "null"}")
        val joinResult = engine.joinChannel(agoraToken, channelId, agoraUid, options)
        android.util.Log.d("Agora", "joinChannel result=$joinResult")
        VideoCallSessionStore.clear()

        rtcEngine = engine
    }

    DisposableEffect(Unit) {
        onDispose {
            isRecording = false
            outputStream?.flush()
            outputStream?.close()
            outputStream = null

            videoCallRepository.setInAppCallActive(false)
            VideoCallDetectService.onInAppCallEnd = null

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


    // ── 통화 종료 화면 ──
    if (callEnded) {
        CallEndedScreen(
            callerName = callerName,
            callDuration = callDuration,
            onDismiss = onCallEnded
        )
        return
    }

    // ── 메인 통화 UI ──
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 비디오 영역
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // 리모트 비디오 (전체 화면) – 연결 후에만 보임
            androidx.compose.animation.AnimatedVisibility(
                visible = !isConnecting,
                enter = fadeIn(animationSpec = tween(700)),
                exit = fadeOut(animationSpec = tween(700)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(listOf(RemoteGradientTop, RemoteGradientBottom))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(128.dp)
                                .clip(CircleShape)
                                .background(AvatarBgLarge),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
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
                }
            }

            // ── 내 비디오: 연결 중 → 전체화면, 연결됨 → PiP ──
            val pipProgress by animateFloatAsState(
                targetValue = if (isConnecting) 0f else 1f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                label = "pipTransition"
            )

            val pipWidth = 128.dp
            val pipHeight = 176.dp
            val pipPaddingStart = 16.dp
            val pipPaddingBottom = 24.dp
            val cornerRadius by animateDpAsState(
                targetValue = if (isConnecting) 16.dp else 12.dp,
                animationSpec = tween(600, easing = FastOutSlowInEasing),
                label = "cornerRadius"
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val fullWidth = maxWidth
                val fullHeight = maxHeight

                val currentWidth = lerp(fullWidth, pipWidth, pipProgress)
                val currentHeight = lerp(fullHeight, pipHeight, pipProgress)
                val currentPadding = 12.dp * (1f - pipProgress)

                val alignX by animateFloatAsState(
                    targetValue = if (isConnecting) 0f else -1f,
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                    label = "alignX"
                )
                val alignY by animateFloatAsState(
                    targetValue = if (isConnecting) 0f else 1f,
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                    label = "alignY"
                )

                Box(
                    modifier = Modifier
                        .align(BiasAlignment(alignX, alignY))
                        .padding(
                            start = if (pipProgress > 0.5f) pipPaddingStart * pipProgress else currentPadding,
                            end = currentPadding * (1f - pipProgress),
                            top = currentPadding * (1f - pipProgress),
                            bottom = if (pipProgress > 0.5f) pipPaddingBottom * pipProgress else currentPadding
                        )
                        .size(width = currentWidth, height = currentHeight)
                        .clip(RoundedCornerShape(cornerRadius))
                        .zIndex(10f)
                ) {
                    val avatarSize by animateDpAsState(
                        targetValue = if (isConnecting) 112.dp else 56.dp,
                        animationSpec = tween(600, easing = FastOutSlowInEasing),
                        label = "avatarSize"
                    )
                    val iconSize by animateDpAsState(
                        targetValue = if (isConnecting) 56.dp else 28.dp,
                        animationSpec = tween(600, easing = FastOutSlowInEasing),
                        label = "iconSize"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(listOf(GradientTop, GradientBottom))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(avatarSize)
                                .clip(CircleShape)
                                .background(AvatarBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = Color(0xFFD1D5DB),
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setZOrderMediaOverlay(true)
                                localView.value = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    val overlayAlpha by animateFloatAsState(
                        targetValue = if (isConnecting) 0f else 1f,
                        animationSpec = tween(600),
                        label = "overlayAlpha"
                    )
                    if (overlayAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.7f * overlayAlpha)
                                        )
                                    )
                                )
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "나",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = overlayAlpha),
                            )
                        }
                    }
                }
            }

            // ── 연결 중 오버레이 ──
            androidx.compose.animation.AnimatedVisibility(
                visible = isConnecting,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(500)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 160.dp)
                    ) {
                        Text(
                            text = callerName.ifEmpty { "상대방" },
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "연결 중",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        BouncingDots()
                    }
                }
            }
        }

        // ── 상대 이름 & 통화 시간 (연결 후에만) ──
        AnimatedVisibility(
            visible = !isConnecting,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = callerName.ifEmpty { "상대방" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDuration(callDuration),
                    fontSize = 14.sp,
                    color = Color(0xFF9CA3AF),
                )
            }
        }

        // ── 하단 컨트롤 바 ──
        ControlBar(
            isMuted = isMuted,
            onToggleMute = {
                val newMuted = !isMuted
                isMuted = newMuted
                rtcEngine?.muteLocalAudioStream(newMuted)
            },
            onEndCall = ::endCall,
            onSwitchCamera = {
                isFrontCamera = !isFrontCamera
                rtcEngine?.switchCamera()
            },
        )
    }
}

@Composable
private fun BouncingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
    val delays = listOf(0, 150, 300)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        delays.forEach { delay ->
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -12f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 600
                        0f at 0
                        -12f at 200
                        0f at 400
                        0f at 600
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(delay)
                ),
                label = "dot_$delay"
            )
            Box(
                modifier = Modifier
                    .offset(y = offsetY.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(BounceDotColor)
            )
        }
    }
}

@Composable
private fun ControlBar(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onEndCall: () -> Unit,
    onSwitchCamera: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ControlBarBg)
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isMuted) Color.White else ControlBtnBg)
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = if (isMuted) "마이크 켜기" else "마이크 음소거",
                tint = if (isMuted) Color.Black else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        IconButton(
            onClick = onEndCall,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(EndCallRed)
        ) {
            Icon(
                imageVector = Icons.Filled.CallEnd,
                contentDescription = "통화 종료",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        IconButton(
            onClick = onSwitchCamera,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ControlBtnBg)
        ) {
            Icon(
                imageVector = Icons.Filled.Cameraswitch,
                contentDescription = "카메라 전환",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun CallEndedScreen(callerName: String, callDuration: Int, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF262626)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = callerName.ifEmpty { "상대방" }, fontSize = 20.sp, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "통화 종료", fontSize = 16.sp, color = Color(0xFF9CA3AF))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "통화 시간: ${formatDuration(callDuration)}",
                fontSize = 14.sp,
                color = Color(0xFF737373),
            )
        }
    }
}

// ── 유틸리티 ──

private fun lerp(start: Dp, stop: Dp, fraction: Float): Dp = start + (stop - start) * fraction

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
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

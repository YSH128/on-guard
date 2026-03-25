package com.s14p21a404.shutter.presentation.screens

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.s14p21a404.shutter.R
import kotlin.math.PI
import kotlin.math.sin

private val BgColor      = Color(0xFFF2F5F9)
private val TextPrimary  = Color(0xFF2A2A2A)
private val TextSecondary = Color(0xFF6B7280)

@Composable
fun HomeScreen(
    isServiceRunning: Boolean,
    onToggleService: () -> Unit,
    allPermissionsGranted: Boolean = true,
    overlayPermissionGranted: Boolean = true,
    accessibilityServiceEnabled: Boolean = true,
    onRequestPermissions: () -> Unit = {},
    onRequestOverlayPermission: () -> Unit = {},
    onRequestAccessibilityPermission: () -> Unit = {},
    onNavigateToVideoCall: (String, String, Long) -> Unit = { _, _, _ -> },
    homeViewModel: HomeViewModel = viewModel(),
) {
    val uiState by homeViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        homeViewModel.loadFriends()
        homeViewModel.loadMyProfile()
    }

    var showPermissionDialog    by remember { mutableStateOf(false) }
    var showOverlayDialog       by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }

    LaunchedEffect(allPermissionsGranted, overlayPermissionGranted, accessibilityServiceEnabled, isServiceRunning) {
        when {
            !allPermissionsGranted       -> showPermissionDialog = true
            !overlayPermissionGranted    -> showOverlayDialog = true
            !isServiceRunning            -> onToggleService()
            !accessibilityServiceEnabled -> showAccessibilityDialog = true
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("권한 허용 필요", fontWeight = FontWeight.Bold) },
            text  = { Text("서비스 실행에 필요한 권한을 허용해주세요.\n(전화 상태, 알림, 위치)") },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false; onRequestPermissions() }) {
                    Text("허용하기")
                }
            },
        )
    }
    if (showOverlayDialog && allPermissionsGranted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("오버레이 권한 필요", fontWeight = FontWeight.Bold) },
            text  = { Text("영상통화 위에 경고를 표시하려면\n'다른 앱 위에 표시' 권한이 필요합니다.\n설정 화면에서 직접 허용해주세요.") },
            confirmButton = {
                TextButton(onClick = { showOverlayDialog = false; onRequestOverlayPermission() }) {
                    Text("설정으로 이동")
                }
            },
        )
    }
    if (showAccessibilityDialog && allPermissionsGranted && overlayPermissionGranted && isServiceRunning) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("접근성 서비스 활성화 필요", fontWeight = FontWeight.Bold) },
            text  = { Text("영상통화 화면 감지를 위해\n접근성 서비스를 켜야 합니다.\n\n설정 화면에서 'Shutter'를 찾아\n토글을 켜주세요.") },
            confirmButton = {
                TextButton(onClick = { showAccessibilityDialog = false; onRequestAccessibilityPermission() }) {
                    Text("설정으로 이동")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        HeaderSection()

        // 친구 (N) 헤더
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ri_kakao_talk_fill),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "친구 (${uiState.friends.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF5B7FD6))
                }
            }
            uiState.error != null -> {
                Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                    Text(uiState.error!!, fontSize = 14.sp, color = TextSecondary)
                }
            }
            uiState.friends.isEmpty() -> {
                Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                    Text("카카오 친구가 없습니다.", fontSize = 14.sp, color = TextSecondary)
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.friends) { friend ->
                        FriendItem(
                            name = friend.name,
                            profileImageUrl = friend.profileImageUrl,
                            onVideoCall = {
                                val myName = homeViewModel.getMyName()
                                val channelId = listOf(myName, friend.id.toString())
                                    .sorted()
                                    .joinToString("_")
                                android.util.Log.d("Channel", "채널명: $channelId")
                                onNavigateToVideoCall(channelId, friend.name, friend.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    val context = LocalContext.current
    val gifImageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding()
            .padding(top = 28.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = R.drawable.logo_gif,
            contentDescription = "Shutter",
            imageLoader = gifImageLoader,
            modifier = Modifier.size(100.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(Modifier.height(20.dp))

        WaveformAnimation()
    }
}

// 각 바의 고정 높이 스케일 (웨이브 모양은 유지하되 막대마다 최대 높이가 다름)
private val barScales = floatArrayOf(
    0.55f, 0.9f, 0.45f, 1.0f, 0.6f, 0.8f, 0.4f, 0.95f, 0.65f, 0.5f,
    0.85f, 0.4f, 1.0f, 0.55f, 0.75f, 0.45f, 0.9f, 0.6f, 0.5f, 0.85f,
    0.7f, 0.4f, 1.0f, 0.5f, 0.8f, 0.6f, 0.9f, 0.45f
)

@Composable
private fun WaveformAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val barColors = listOf(
        Color(0xFF6CAEEA),
        Color(0xFFD03770),
        Color(0xFF6CAEEA),
        Color(0xFFD03770),
        Color(0xFF6CAEEA),
        Color(0xFFD03770),
        Color(0xFF6CAEEA),
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(36.dp)
    ) {
        repeat(28) { i ->
            val heightFraction = ((sin(phase + i * 0.45f).toFloat() + 1f) / 2f) * barScales[i] * 0.8f + 0.1f
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(heightFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColors[i % barColors.size])
            )
        }
    }
}

@Composable
private fun FriendItem(
    name: String,
    profileImageUrl: String? = null,
    onVideoCall: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFD6E0F5)),
            contentAlignment = Alignment.Center
        ) {
            if (!profileImageUrl.isNullOrEmpty()) {
                SubcomposeAsyncImage(
                    model = profileImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color(0xFF5B7FD6)
                        )
                    },
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF5B7FD6)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = name,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onVideoCall) {
            Icon(
                imageVector = Icons.Outlined.Videocam,
                contentDescription = "영상통화",
                tint = Color(0xFF5B7FD6),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

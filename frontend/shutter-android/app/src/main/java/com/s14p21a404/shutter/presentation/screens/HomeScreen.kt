package com.s14p21a404.shutter.presentation.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// ── 색상 상수 ──

private val HeaderGradientStart = Color(0xFF23368B)
private val HeaderGradientEnd   = Color(0xFF7297DB)
private val ShieldIconColor     = Color(0xFF5B7FD6)
private val StatusGreen         = Color(0xFF4ADE80)
private val BgColor             = Color(0xFFF2F5F9)
private val TextPrimary         = Color(0xFF2A2A2A)
private val TextSecondary       = Color(0xFF6B7280)

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
    onNavigateToVideoCall: (String, String) -> Unit = { _, _ -> },
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
        HeaderSection(isServiceRunning = isServiceRunning, onToggleService = onToggleService)

        Text(
            text = "연락처",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF3D527E))
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
                                onNavigateToVideoCall(channelId, friend.name)
                            }
                        )
                    }
                }
            }
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 프로필 아이콘
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8EBF5)),
            contentAlignment = Alignment.Center
        ) {
            if (profileImageUrl != null) {
                Image(
                    painter = rememberAsyncImagePainter(profileImageUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color(0xFF5B7FD6)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // 이름
        Text(
            text = name,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )

        // 영상통화 버튼
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

@Composable
private fun HeaderSection(isServiceRunning: Boolean, onToggleService: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.verticalGradient(listOf(HeaderGradientStart, HeaderGradientEnd)))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Shutter",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(Modifier.height(32.dp))

                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .shadow(16.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color.White, Color(0xFF999999)))),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color.White, Color.White, Color(0xFFC0C0C0)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = "보호 상태",
                            modifier = Modifier.size(64.dp),
                            tint = ShieldIconColor
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.55f, targetValue = 0.55f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes { durationMillis = 1800; 0.55f at 0; 0f at 900; 0.55f at 1800 },
                        repeatMode = RepeatMode.Restart
                    ), label = "pulseAlpha"
                )
                val pulseRadius by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes { durationMillis = 1800; 0f at 0; 5f at 900; 0f at 1800 },
                        repeatMode = RepeatMode.Restart
                    ), label = "pulseRadius"
                )

                Surface(
                    onClick = onToggleService,
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.2f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dotColor = if (isServiceRunning) StatusGreen else Color(0xFFEF4444)
                        Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                            if (isServiceRunning) {
                                Box(
                                    modifier = Modifier
                                        .size((8 + pulseRadius * 2).dp)
                                        .clip(CircleShape)
                                        .background(dotColor.copy(alpha = pulseAlpha))
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isServiceRunning) "실시간 AI 감지 중" else "실시간 감지 시작하기",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
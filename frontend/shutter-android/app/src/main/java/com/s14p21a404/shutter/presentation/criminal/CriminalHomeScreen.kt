package com.s14p21a404.shutter.presentation.criminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FaceRetouchingNatural
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val DarkBg       = Color(0xFF1A1A2E)
private val HeaderStart  = Color(0xFF560000)
private val HeaderEnd    = Color(0xFFB71C1C)
private val CardBg       = Color(0xFF16213E)
private val AccentRed    = Color(0xFFE53935)
private val TextWhite    = Color(0xFFE0E0E0)
private val TextGray     = Color(0xFF9E9E9E)
private val SuccessGreen = Color(0xFF4CAF50)

@Composable
fun CriminalHomeScreen(
    onNavigateToFaceSetup: () -> Unit,
    onNavigateToVideoCall: (channelId: String) -> Unit,
    faceSetupViewModel: FaceSetupViewModel,
    onLogout: () -> Unit,
    homeViewModel: CriminalHomeViewModel = viewModel(),
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val faceState by faceSetupViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        homeViewModel.loadData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
    ) {
        // 헤더
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Brush.verticalGradient(listOf(HeaderStart, HeaderEnd)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            ) {
                Text(
                    "⚠ DEMO — 범죄자 시뮬레이터",
                    fontSize = 14.sp,
                    color = Color(0xFFFFCDD2),
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "딥페이크 공격 시연",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "타겟의 영상통화에 합성 영상을 송출합니다.",
                    fontSize = 13.sp,
                    color = Color(0xFFEF9A9A),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 얼굴 설정 카드
        Surface(
            onClick = onNavigateToFaceSetup,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            color = CardBg,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (faceState.isReady) Color(0xFF1B3A2A) else Color(0xFF3A1A1A)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.FaceRetouchingNatural,
                        contentDescription = null,
                        tint = if (faceState.isReady) SuccessGreen else AccentRed,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "타겟 얼굴 설정",
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Text(
                        if (faceState.isReady) "설정 완료 — 합성 준비됨" else "사진을 선택해 합성 모델에 적용하세요",
                        color = if (faceState.isReady) SuccessGreen else TextGray,
                        fontSize = 12.sp,
                    )
                }
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 타겟 목록
        Text(
            "타겟 목록 (카카오 친구)",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextGray,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                    CircularProgressIndicator(color = AccentRed)
                }
            }
            uiState.error != null -> {
                Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                    Text(uiState.error!!, color = AccentRed, fontSize = 14.sp)
                }
            }
            uiState.friends.isEmpty() -> {
                Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                    Text("카카오 친구가 없습니다.", color = TextGray, fontSize = 14.sp)
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.friends) { friend ->
                        VictimItem(
                            name = friend.name,
                            faceReady = faceState.isReady,
                            onCall = {
                                val channelId = homeViewModel.buildChannelId(friend.id)
                                onNavigateToVideoCall(channelId)
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // 로그아웃 버튼
        TextButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text("로그아웃", color = TextGray, fontSize = 14.sp)
        }
    }
}

@Composable
private fun VictimItem(
    name: String,
    faceReady: Boolean,
    onCall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A1A1A)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = AccentRed, modifier = Modifier.size(22.dp))
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = name,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextWhite,
            modifier = Modifier.weight(1f),
        )

        IconButton(
            onClick = onCall,
            enabled = faceReady,
        ) {
            Icon(
                Icons.Outlined.Videocam,
                contentDescription = "영상통화",
                tint = if (faceReady) AccentRed else TextGray,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
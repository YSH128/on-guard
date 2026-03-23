package com.s14p21a404.shutter.presentation.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── 타임라인 데이터 모델 ──

private data class TimelineItem(
    val id: Int,
    val time: String,
    val badge: String,
    val badgeColor: Color,
    val type: String, // "message", "alert", "location"
    val personLabel: String? = null, // "to. 김철수" or "from. 정민수"
    val isLatest: Boolean = false,
    val title: String? = null,
    val description: String,
    val details: List<String>? = null,
    val footer: String? = null,
    val status: String? = null,
)

private val sampleTimeline = listOf(
    TimelineItem(
        id = 1,
        time = "14:34",
        badge = "자동 발송",
        badgeColor = Color(0xFF4A6CF7),
        type = "message",
        personLabel = "to. 김철수",
        isLatest = true,
        title = "[Shutter 긴급알림]",
        description = "김싸피님이 딥페이크 의심 통화 중입니다.",
        details = listOf(
            "• 통화 시작시각: 14:32",
            "• 발신자: 알 수 없음 (발신자 제한 번호)",
        ),
        footer = "즉시 연락해 주세요.",
        status = "문자 발송 완료",
    ),
    TimelineItem(
        id = 2,
        time = "14:33",
        badge = "위험 감지",
        badgeColor = Color(0xFFEF4444),
        type = "alert",
        isLatest = false,
        title = "높은 위험 감지",
        description = "AI 딥페이크 감지로 자동 알림이 발송되었습니다.",
    ),
    TimelineItem(
        id = 3,
        time = "14:33",
        badge = "위치 공유",
        badgeColor = Color(0xFF22C55E),
        type = "location",
        personLabel = "from. 정민수",
        isLatest = false,
        description = "AI 딥페이크 감지로 위치 정보를 공유합니다.",
    ),
)

// ── 색상 상수 ──

private val HeaderGradientStart = Color(0xFF23368B)
private val HeaderGradientEnd = Color(0xFF7297DB)
private val ShieldIconColor = Color(0xFF5B7FD6)
private val StatusGreen = Color(0xFF4ADE80)
private val BgColor = Color(0xFFF2F5F9)
private val CardBg = Color.White
private val TextPrimary = Color(0xFF2A2A2A)
private val TextSecondary = Color(0xFF6B7280)
private val TimelineLine = Color(0xFFE5E5E5)
private val DotActive = Color(0xFF3D527E)
private val DotInactive = Color(0xFFCED5E1)
private val ChipSelectedBg = Color(0xFF3D527E)
private val ChipSelectedText = Color.White
private val ChipUnselectedBg = Color.White
private val ChipUnselectedText = Color(0xFF9CA3AF)


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
    onNavigateToVideoCall: (String) -> Unit = {},  // ← 추가
    homeViewModel: HomeViewModel = viewModel(),
) {
    val scrollState = rememberScrollState()
    val homeUiState by homeViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        homeViewModel.loadWards()
    }

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }

    LaunchedEffect(allPermissionsGranted, overlayPermissionGranted, accessibilityServiceEnabled, isServiceRunning) {
        if (!allPermissionsGranted) {
            showPermissionDialog = true
        } else if (!overlayPermissionGranted) {
            showOverlayDialog = true
        } else if (!isServiceRunning) {
            onToggleService()
        } else if (!accessibilityServiceEnabled) {
            showAccessibilityDialog = true
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { /* 닫기 방지 */ },
            title = { Text("권한 허용 필요", fontWeight = FontWeight.Bold) },
            text = {
                Text("서비스 실행에 필요한 권한을 허용해주세요.\n(전화 상태, 알림, 위치)")
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    onRequestPermissions()
                }) {
                    Text("허용하기")
                }
            },
        )
    }

    if (showOverlayDialog && allPermissionsGranted) {
        AlertDialog(
            onDismissRequest = { /* 닫기 방지 */ },
            title = { Text("오버레이 권한 필요", fontWeight = FontWeight.Bold) },
            text = {
                Text("영상통화 위에 경고를 표시하려면\n'다른 앱 위에 표시' 권한이 필요합니다.\n설정 화면에서 직접 허용해주세요.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayDialog = false
                    onRequestOverlayPermission()
                }) {
                    Text("설정으로 이동")
                }
            },
        )
    }

    if (showAccessibilityDialog && allPermissionsGranted && overlayPermissionGranted && isServiceRunning) {
        AlertDialog(
            onDismissRequest = { /* 닫기 방지 */ },
            title = { Text("접근성 서비스 활성화 필요", fontWeight = FontWeight.Bold) },
            text = {
                Text("영상통화 화면 감지를 위해\n접근성 서비스를 켜야 합니다.\n\n설정 화면에서 'Shutter'를 찾아\n토글을 켜주세요.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showAccessibilityDialog = false
                    onRequestAccessibilityPermission()
                }) {
                    Text("설정으로 이동")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(scrollState)
    ) {
        // ── 상단 그라데이션 헤더 ──
        HeaderSection(isServiceRunning = isServiceRunning, onToggleService = onToggleService)

        // ── 영상통화 버튼 ──  ← 추가
        Button(
            onClick = { onNavigateToVideoCall("testchannel123") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("영상통화 시작")
        }

        // ── 알림 타임라인 ──
        TimelineSection(
            filterNames = homeUiState.filterNames,
            selectedFilter = homeUiState.selectedFilter,
            onSelectFilter = { homeViewModel.selectFilter(it) },
        )
    }
}

// ════════════════════════════════════════════════════════════
// 상단 헤더 (그라데이션 + 방패 아이콘 + 상태 뱃지)
// ════════════════════════════════════════════════════════════

@Composable
private fun HeaderSection(
    isServiceRunning: Boolean,
    onToggleService: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(HeaderGradientStart, HeaderGradientEnd)
                )
            )
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

                Spacer(modifier = Modifier.height(32.dp))

                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .shadow(16.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.White, Color(0xFF999999))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.White, Color.White, Color(0xFFC0C0C0))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = "보호 시작/중지",
                            modifier = Modifier.size(64.dp),
                            tint = ShieldIconColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val infiniteTransition = rememberInfiniteTransition(label = "homePulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.55f, targetValue = 0.55f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 1800
                            0.55f at 0; 0f at 900; 0.55f at 1800
                        }, repeatMode = RepeatMode.Restart
                    ), label = "homePulseAlpha"
                )
                val pulseRadius by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 1800
                            0f at 0; 5f at 900; 0f at 1800
                        }, repeatMode = RepeatMode.Restart
                    ), label = "homePulseRadius"
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
                        Box(
                            modifier = Modifier.size(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
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
                        Spacer(modifier = Modifier.width(8.dp))
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

// ════════════════════════════════════════════════════════════
// 알림 타임라인
// ════════════════════════════════════════════════════════════

@Composable
private fun TimelineSection(
    filterNames: List<String>,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "알림 타임라인",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filterNames) { name ->
                FilterChip(
                    text = name,
                    selected = selectedFilter == name,
                    onClick = { onSelectFilter(name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 필터링된 타임라인
        val filtered = if (selectedFilter == "전체") {
            sampleTimeline
        } else {
            sampleTimeline.filter { it.personLabel?.contains(selectedFilter) == true }
        }

        filtered.forEachIndexed { index, item ->
            TimelineRow(item = item, isLast = index == filtered.lastIndex)
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) ChipSelectedText else ChipUnselectedText,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) ChipSelectedBg else ChipUnselectedBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun TimelineRow(item: TimelineItem, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (item.isLatest) DotActive else DotInactive)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(TimelineLine)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.time,
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Badge(text = item.badge, color = item.badgeColor)
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (item.type) {
                "message" -> MessageCard(item)
                "alert" -> AlertCard(item)
                "location" -> LocationCard(item)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MessageCard(item: TimelineItem) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF5F5F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.personLabel ?: "",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "문자 내용:", fontSize = 14.sp, color = TextPrimary)

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF5F5F5))
                        .padding(12.dp)
                ) {
                    item.title?.let {
                        Text(text = it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(text = item.description, fontSize = 14.sp, color = TextPrimary)
                    item.details?.forEach { detail ->
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = detail, fontSize = 14.sp, color = TextSecondary)
                    }
                    item.footer?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, fontSize = 14.sp, color = TextPrimary)
                    }
                }

                item.status?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.MailOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = it, fontSize = 14.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

// ── 카드: 위험 감지 ──

@Composable
private fun AlertCard(item: TimelineItem) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(16.dp)) {

            Row {
                // 경고 아이콘
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "!",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    item.title?.let {
                        Text(
                            text = it,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(text = item.description, fontSize = 14.sp, color = TextPrimary)
                }
            }
        }
    }
}

// ── 카드: 위치 공유 ──

@Composable
private fun LocationCard(item: TimelineItem) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDCFCE7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF16A34A)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.personLabel ?: "",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = item.description, fontSize = 14.sp, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 2.dp)
    )
}
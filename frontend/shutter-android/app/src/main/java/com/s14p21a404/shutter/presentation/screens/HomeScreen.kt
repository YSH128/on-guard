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

// ── 타임라인 UI 모델 ──

private data class TimelineUiItem(
    val id: Int,
    val time: String,
    val badge: String,
    val badgeColor: Color,
    val type: String,           // "message" | "location"
    val personLabel: String? = null,
    val isLatest: Boolean = false,
    val title: String? = null,
    val description: String,
    val details: List<String>? = null,
    val footer: String? = null,
    val status: String? = null,
)

// ── API → UI 모델 변환 ──

private fun TimelineEntry.toUiItem(index: Int, isLatest: Boolean): TimelineUiItem {
    val time = item.timestamp.take(16).drop(11) // "HH:mm"
    return when (item.type) {
        "MESSAGE" -> TimelineUiItem(
            id = index,
            time = time,
            badge = item.tag,
            badgeColor = Color(0xFF4A6CF7),
            type = "message",
            personLabel = item.toName?.let { "to. $it" },
            isLatest = isLatest,
            title = "[SHUTTER 긴급알림]",
            description = "${wardName}님이 딥페이크 의심 통화 중입니다.",
            details = buildList {
                item.callStartedAt?.let {
                    add("• 통화 시작시각: ${it.take(16).drop(11)}")
                }
                item.caller?.let {
                    add("• 발신자: $it")
                }
            }.ifEmpty { null },
            footer = "즉시 연락해 주세요.",
            status = if (item.status == "SUCCESS") "문자 발송 완료" else "문자 발송 실패",
        )
        else -> TimelineUiItem( // LOCATION
            id = index,
            time = time,
            badge = item.tag,
            badgeColor = Color(0xFF22C55E),
            type = "location",
            personLabel = "from. $wardName",
            isLatest = isLatest,
            description = "AI 딥페이크 감지로 위치 정보를 공유합니다.",
        )
    }
}

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


/**
 * 홈 화면 — 참고 프로젝트 디자인 1:1 구현.
 * 첫 접속 시 권한이 없으면 권한 요청 다이얼로그를 표시한다.
 */
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
    homeViewModel: HomeViewModel = viewModel(),
) {
    val scrollState = rememberScrollState()
    val homeUiState by homeViewModel.uiState.collectAsState()

    // 화면 진입마다 피보호자 목록 새로고침
    LaunchedEffect(Unit) {
        homeViewModel.loadWards()
    }

    // 권한 요청 다이얼로그 표시 여부
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }

    // 순서: 런타임 권한 → 오버레이 → MediaProjection(서비스 시작) → 접근성
    LaunchedEffect(allPermissionsGranted, overlayPermissionGranted, accessibilityServiceEnabled, isServiceRunning) {
        if (!allPermissionsGranted) {
            showPermissionDialog = true
        } else if (!overlayPermissionGranted) {
            showOverlayDialog = true
        } else if (!isServiceRunning) {
            // 런타임 + 오버레이 충족 → MediaProjection 동의 (서비스 시작)
            onToggleService()
        } else if (!accessibilityServiceEnabled) {
            // 서비스 실행 후 접근성 미활성 → 접근성 다이얼로그
            showAccessibilityDialog = true
        }
    }

    // ── 런타임 권한 요청 다이얼로그 (전화 상태, 알림, 위치) ──
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

    // ── 오버레이 권한 요청 다이얼로그 ──
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

    // ── 접근성 서비스 활성화 요청 다이얼로그 ──
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

        // ── 알림 타임라인 ──
        TimelineSection(
            wardFilters = homeUiState.wardFilters,
            selectedFilter = homeUiState.selectedFilter,
            onSelectFilter = { homeViewModel.selectFilter(it) },
            timelineEntries = homeUiState.timelineEntries,
            isLoading = homeUiState.isLoading,
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
                // 로고
                Text(
                    text = "Shutter",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 방패 아이콘 원형 (표시 전용)
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
                    // 안쪽 원 (더 밝은 그라데이션)
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

                // 상태 뱃지 — 보호 시작/중지 기능
                // Pulse 애니메이션 (서비스 실행 중일 때만)
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
                        // 고정 크기 Box로 감싸서 레이아웃 흔들림 방지
                        Box(
                            modifier = Modifier.size(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isServiceRunning) {
                                // Pulse ring
                                Box(
                                    modifier = Modifier
                                        .size((8 + pulseRadius * 2).dp)
                                        .clip(CircleShape)
                                        .background(dotColor.copy(alpha = pulseAlpha))
                                )
                            }
                            // Core dot
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
    wardFilters: List<WardFilter>,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    timelineEntries: List<TimelineEntry>,
    isLoading: Boolean,
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

        // 피보호자 이름 필터 칩 가로 스크롤
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(wardFilters) { filter ->
                FilterChip(
                    text = filter.name,
                    selected = selectedFilter == filter.name,
                    onClick = { onSelectFilter(filter.name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ChipSelectedBg)
                }
            }
            timelineEntries.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "알림 내역이 없습니다.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                    )
                }
            }
            else -> {
                val uiItems = timelineEntries.mapIndexed { index, entry ->
                    entry.toUiItem(index, isLatest = index == 0)
                }
                uiItems.forEachIndexed { index, item ->
                    TimelineRow(item = item, isLast = index == uiItems.lastIndex)
                }
            }
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
private fun TimelineRow(item: TimelineUiItem, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // 타임라인 왼쪽 (점 + 선)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            // 점
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
            // 세로 선 — 점 하단부터 남은 공간 끝까지 (다음 점 상단과 이어짐)
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

        // 타임라인 오른쪽 (시간+뱃지 → 카드)
        Column(modifier = Modifier.weight(1f)) {
            // 시간 (왼쪽) + 뱃지 (오른쪽 끝)
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

            // 카드
            when (item.type) {
                "message" -> MessageCard(item)
                else -> LocationCard(item)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── 카드: 문자 발송 ──

@Composable
private fun MessageCard(item: TimelineUiItem) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(16.dp)) {

            Column {
                // 수신자
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

                // 문자 내용 박스
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

                // 상태
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

// ── 카드: 위치 공유 ──

@Composable
private fun LocationCard(item: TimelineUiItem) {
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

// ── 공통: 뱃지 ──

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
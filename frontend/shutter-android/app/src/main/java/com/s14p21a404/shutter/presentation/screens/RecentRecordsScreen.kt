package com.s14p21a404.shutter.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// ── UI 모델 ──

private data class TimelineUiItem(
    val id: Int,
    val time: String,
    val badge: String,
    val badgeColor: Color,
    val type: String,           // "message" | "alert" | "location"
    val personLabel: String? = null,
    val title: String? = null,
    val description: String,
    val details: List<String>? = null,
    val footer: String? = null,
    val status: String? = null,
)

// ── API → UI 변환 ──

private fun TimelineEntry.toUiItems(baseIndex: Int): List<TimelineUiItem> {
    val time = item.timestamp.take(16).drop(11)
    return when (item.type) {
        "MESSAGE" -> listOf(
            TimelineUiItem(
                id = baseIndex * 10,
                time = item.callStartedAt?.take(16)?.drop(11) ?: time,
                badge = "위험 감지",
                badgeColor = Color(0xFFEF4444),
                type = "alert",
                title = "높은 위험 감지",
                description = "AI 딥페이크 감지로 자동 알림이 발송되었습니다.",
            ),
            TimelineUiItem(
                id = baseIndex * 10 + 1,
                time = time,
                badge = "자동 발송",
                badgeColor = Color(0xFF4A6CF7),
                type = "message",
                personLabel = item.toName?.let { "to. $it" },
                title = "[SHUTTER 긴급알림]",
                description = "${wardName}님이 딥페이크 의심 통화 중입니다.",
                details = buildList {
                    item.callStartedAt?.let { add("• 통화 시작시각: ${it.take(16).drop(11)}") }
                    item.caller?.let { add("• 발신자: $it") }
                }.ifEmpty { null },
                footer = "즉시 연락해 주세요.",
                status = if (item.status == "SUCCESS") "문자 발송 완료" else "문자 발송 실패",
            ),
        )
        else -> listOf(
            TimelineUiItem(
                id = baseIndex * 10,
                time = time,
                badge = "위치 공유",
                badgeColor = Color(0xFF22C55E),
                type = "location",
                personLabel = "from. $wardName",
                description = "AI 딥페이크 의심 통화 중 입니다.\n${wardName}님의 위치를 확인해주세요.",
            )
        )
    }
}

// ── 색상 상수 ──

private val BgColor            = Color(0xFFF2F5F9)
private val CardBg             = Color.White
private val TextPrimary        = Color(0xFF2A2A2A)
private val TextSecondary      = Color(0xFF6B7280)
private val ChipSelectedBg     = Color(0xFF3D527E)
private val ChipSelectedText   = Color.White
private val ChipUnselectedBg   = Color.White
private val ChipUnselectedText = Color(0xFF9CA3AF)

// ════════════════════════════════════════════════════════════
// 화면
// ════════════════════════════════════════════════════════════

@Composable
fun RecentRecordsScreen(
    viewModel: RecentRecordsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.loadWards() }

    // 스크롤 50px 이상이면 collapsed
    val isCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 50
        }
    }

    // 헤더 패딩 애니메이션
    val topPadding by animateDpAsState(
        targetValue = if (isCollapsed) 16.dp else 36.dp,
        animationSpec = tween(300),
        label = "headerTop"
    )
    val bottomPadding by animateDpAsState(
        targetValue = if (isCollapsed) 12.dp else 24.dp,
        animationSpec = tween(300),
        label = "headerBottom"
    )

    // 제목 정렬 (0f = 왼쪽, 0.5f = 가운데)
    val titleBias by animateFloatAsState(
        targetValue = if (isCollapsed) -1f else 0f,
        animationSpec = tween(300),
        label = "titleBias"
    )
    val titleSize by animateFloatAsState(
        targetValue = if (isCollapsed) 17f else 22f,
        animationSpec = tween(300),
        label = "titleSize"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        // ── 헤더 (스크롤 감지) ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(top = topPadding, bottom = bottomPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentAlignment = if (isCollapsed) Alignment.CenterStart else Alignment.Center
            ) {
                Text(
                    text = "최근기록",
                    fontSize = titleSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
            }

            // 필터 칩은 collapsed 상태에서만 헤더 안에 붙음
            if (isCollapsed) {
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.wardFilters) { filter ->
                        FilterChip(
                            text = filter.name,
                            selected = uiState.selectedFilter == filter.name,
                            onClick = { viewModel.selectFilter(filter.name) }
                        )
                    }
                }
            }
        }

        // ── 필터 칩 (expanded 상태: 헤더 아래) ──
        if (!isCollapsed) {
            Spacer(Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.wardFilters) { filter ->
                    FilterChip(
                        text = filter.name,
                        selected = uiState.selectedFilter == filter.name,
                        onClick = { viewModel.selectFilter(filter.name) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        } else {
            Spacer(Modifier.height(12.dp))
        }

        // ── 콘텐츠 ──
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), Alignment.Center) {
                    CircularProgressIndicator(color = ChipSelectedBg)
                }
            }
            uiState.sessions.isEmpty() -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), Alignment.Center) {
                    Text("알림 내역이 없습니다.", fontSize = 14.sp, color = TextSecondary)
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.sessions,
                        key = { session -> session.sessionId }
                    ) { session ->
                        val isExpanded = session.sessionId in uiState.expandedSessions
                        SessionCard(
                            session = session,
                            isExpanded = isExpanded,
                            onToggle = { viewModel.toggleSession(session.sessionId) }
                        )
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// ── 필터 칩 ──

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
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

// ── 세션 카드 ──

@Composable
private fun SessionCard(
    session: SessionGroup,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevron"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 헤더 행
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 아바타
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD6E0F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF5B7FD6)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.wardName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${session.sessionDate} ${session.sessionTime}",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }

                // 펼침 화살표
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "접기" else "펼치기",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation)
                )
            }

            // 펼쳐진 콘텐츠
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                ) {
                    HorizontalDivider(color = Color(0xFFEEF0F4), thickness = 1.dp)
                    Spacer(Modifier.height(12.dp))

                    var itemIndex = 0
                    session.entries.forEach { entry ->
                        val uiItems = entry.toUiItems(itemIndex)
                        itemIndex += uiItems.size
                        uiItems.forEach { uiItem ->
                            EntryRow(uiItem)
                        }
                    }
                }
            }
        }
    }
}

// ── 항목 행 (뱃지 + 카드) ──

@Composable
private fun EntryRow(item: TimelineUiItem) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.time, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Badge(text = item.badge, color = item.badgeColor)
        }
        Spacer(Modifier.height(6.dp))
        when (item.type) {
            "message" -> MessageCard(item)
            "alert"   -> AlertCard(item)
            else      -> LocationCard(item)
        }
    }
}

// ── 카드: 자동 발송 ──

@Composable
private fun MessageCard(item: TimelineUiItem) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FF)),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8EBF5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Person, null, Modifier.size(14.dp), tint = TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.personLabel ?: "",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("문자 내용:", fontSize = 13.sp, color = TextPrimary)
            Spacer(Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEEF0F8))
                    .padding(10.dp)
            ) {
                item.title?.let {
                    Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(3.dp))
                }
                Text(item.description, fontSize = 13.sp, color = TextPrimary)
                item.details?.forEach { detail ->
                    Spacer(Modifier.height(2.dp))
                    Text(detail, fontSize = 13.sp, color = TextSecondary)
                }
                item.footer?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 13.sp, color = TextPrimary)
                }
            }

            item.status?.let {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MailOutline, null, Modifier.size(14.dp), tint = TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text(it, fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

// ── 카드: 위험 감지 ──

@Composable
private fun AlertCard(item: TimelineUiItem) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5F5)),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEE2E2)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFFEF4444)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column {
                item.title?.let {
                    Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444))
                    Spacer(Modifier.height(3.dp))
                }
                Text(item.description, fontSize = 13.sp, color = TextPrimary)
            }
        }
    }
}

// ── 카드: 위치 공유 ──

@Composable
private fun LocationCard(item: TimelineUiItem) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FFF4)),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.LocationOn, null, Modifier.size(14.dp), tint = Color(0xFF16A34A))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.personLabel ?: "",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(item.description, fontSize = 13.sp, color = TextPrimary)
        }
    }
}

// ── 뱃지 ──

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
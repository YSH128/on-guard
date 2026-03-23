package com.s14p21a404.shutter.presentation.screens

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
    val isLatest: Boolean = false,
    val title: String? = null,
    val description: String,
    val details: List<String>? = null,
    val footer: String? = null,
    val status: String? = null,
)

// ── API → UI 변환 (1 entry → 1~2 items) ──

private fun TimelineEntry.toUiItems(baseIndex: Int, isLatest: Boolean): List<TimelineUiItem> {
    val time = item.timestamp.take(16).drop(11) // "HH:mm"
    return when (item.type) {
        "MESSAGE" -> listOf(
            // 1) 위험 감지 카드
            TimelineUiItem(
                id = baseIndex * 10,
                time = item.callStartedAt?.take(16)?.drop(11) ?: time,
                badge = "위험 감지",
                badgeColor = Color(0xFFEF4444),
                type = "alert",
                isLatest = false,
                title = "높은 위험 감지",
                description = "AI 딥페이크 감지로 자동 알림이 발송되었습니다.",
            ),
            // 2) 자동 발송 카드
            TimelineUiItem(
                id = baseIndex * 10 + 1,
                time = time,
                badge = "자동 발송",
                badgeColor = Color(0xFF4A6CF7),
                type = "message",
                personLabel = item.toName?.let { "to. $it" },
                isLatest = isLatest,
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
        else -> listOf( // LOCATION
            TimelineUiItem(
                id = baseIndex * 10,
                time = time,
                badge = "위치 공유",
                badgeColor = Color(0xFF22C55E),
                type = "location",
                personLabel = "from. $wardName",
                isLatest = isLatest,
                description = "AI 딥페이크 의심 통화 중 입니다.\n${wardName}님의 위치를 확인해주세요.",
            )
        )
    }
}

// ── 색상 상수 ──

private val BgColor          = Color(0xFFF2F5F9)
private val CardBg           = Color.White
private val TextPrimary      = Color(0xFF2A2A2A)
private val TextSecondary    = Color(0xFF6B7280)
private val TimelineLine     = Color(0xFFE5E5E5)
private val DotActive        = Color(0xFF3D527E)
private val DotInactive      = Color(0xFFCED5E1)
private val ChipSelectedBg   = Color(0xFF3D527E)
private val ChipSelectedText = Color.White
private val ChipUnselectedBg = Color.White
private val ChipUnselectedText = Color(0xFF9CA3AF)

// ════════════════════════════════════════════════════════════
// 화면
// ════════════════════════════════════════════════════════════

@Composable
fun RecentRecordsScreen(
    viewModel: RecentRecordsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadWards() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        // ── 타이틀 ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "최근 기록",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        }

        // ── 필터 칩 ──
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

        // ── 타임라인 콘텐츠 ──
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), Alignment.Center) {
                    CircularProgressIndicator(color = ChipSelectedBg)
                }
            }
            uiState.timelineEntries.isEmpty() -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), Alignment.Center) {
                    Text("알림 내역이 없습니다.", fontSize = 14.sp, color = TextSecondary)
                }
            }
            else -> {
                // entry → UI item 변환 (MESSAGE는 2개 생성)
                val uiItems = uiState.timelineEntries
                    .flatMapIndexed { index, entry ->
                        entry.toUiItems(baseIndex = index, isLatest = index == 0)
                    }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    uiItems.forEachIndexed { index, item ->
                        TimelineRow(item = item, isLast = index == uiItems.lastIndex)
                    }
                    Spacer(Modifier.height(16.dp))
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

// ── 타임라인 행 ──

@Composable
private fun TimelineRow(item: TimelineUiItem, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // 왼쪽 점 + 선
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp)
        ) {
            Spacer(Modifier.height(6.dp))
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

        Spacer(Modifier.width(8.dp))

        // 오른쪽 시간+뱃지+카드
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = item.time, fontSize = 14.sp, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                Badge(text = item.badge, color = item.badgeColor)
            }

            Spacer(Modifier.height(8.dp))

            when (item.type) {
                "message"  -> MessageCard(item)
                "alert"    -> AlertCard(item)
                else       -> LocationCard(item)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── 카드: 자동 발송 ──

@Composable
private fun MessageCard(item: TimelineUiItem) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 수신자
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF5F5F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Person, null, Modifier.size(16.dp), tint = TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.personLabel ?: "",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("문자 내용:", fontSize = 14.sp, color = TextPrimary)
            Spacer(Modifier.height(8.dp))

            // 내용 박스
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF5F5F5))
                    .padding(12.dp)
            ) {
                item.title?.let {
                    Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                }
                Text(item.description, fontSize = 14.sp, color = TextPrimary)
                item.details?.forEach { detail ->
                    Spacer(Modifier.height(2.dp))
                    Text(detail, fontSize = 14.sp, color = TextSecondary)
                }
                item.footer?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 14.sp, color = TextPrimary)
                }
            }

            // 발송 상태
            item.status?.let {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MailOutline, null, Modifier.size(16.dp), tint = TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(it, fontSize = 14.sp, color = TextSecondary)
                }
            }
        }
    }
}

// ── 카드: 위험 감지 ──

@Composable
private fun AlertCard(item: TimelineUiItem) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEE2E2)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFFEF4444)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column {
                item.title?.let {
                    Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444))
                    Spacer(Modifier.height(4.dp))
                }
                Text(item.description, fontSize = 14.sp, color = TextPrimary)
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.LocationOn, null, Modifier.size(16.dp), tint = Color(0xFF16A34A))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.personLabel ?: "",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(item.description, fontSize = 14.sp, color = TextPrimary)
        }
    }
}

// ── 뱃지 ──

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 2.dp)
    )
}
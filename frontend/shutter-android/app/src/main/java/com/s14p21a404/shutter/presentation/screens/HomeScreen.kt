package com.s14p21a404.shutter.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

/**
 * 홈 화면 — 참고 프로젝트 디자인 1:1 구현
 */
@Composable
fun HomeScreen(
    isServiceRunning: Boolean,
    onToggleService: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(scrollState)
    ) {
        // ── 상단 그라데이션 헤더 ──
        HeaderSection(isServiceRunning = isServiceRunning, onToggleService = onToggleService)

        // ── 알림 타임라인 ──
        TimelineSection()
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
                Surface(
                    onClick = onToggleService,
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.2f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isServiceRunning) StatusGreen else Color(0xFFEF4444))
                        )
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
private fun TimelineSection() {
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

        Spacer(modifier = Modifier.height(16.dp))

        sampleTimeline.forEachIndexed { index, item ->
            TimelineRow(item = item, isLast = index == sampleTimeline.lastIndex)
        }
    }
}

@Composable
private fun TimelineRow(item: TimelineItem, isLast: Boolean) {
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
                "alert" -> AlertCard(item)
                "location" -> LocationCard(item)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── 카드: 문자 발송 ──

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


package com.s14p21a404.shutter.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.s14p21a404.shutter.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── 보호자 데이터 모델 (샘플) ──

private data class Guardian(
    val id: String,
    val name: String,
)

private val sampleGuardians = listOf(
    Guardian(id = "1", name = "김철수"),
    Guardian(id = "2", name = "정민수"),
    Guardian(id = "3", name = "김영희"),
)

// 위치가 활성화된 보호자 (첫 번째만 활성)
private val guardiansWithLocation = setOf("1")

// ── 색상 상수 ──

private val BgColor = Color(0xFFF2F5F9)
private val TitleColor = Color(0xFF4A6091)
private val SelectedBorderColor = Color(0xFF5B7FD6)
private val UnselectedAvatarBg = Color(0xFFD6DCE8)
private val SelectedAvatarBg = Color(0xFFF2F5F9)
private val SelectedIconColor = Color(0xFF5B7FD6)
private val UnselectedIconColor = Color(0xFF9CA3AF)
private val SelectedNameColor = Color(0xFF1A1A1A)
private val UnselectedNameColor = Color(0xFF6B7280)
private val ShieldGradientStart = Color(0xFF5B7FD6)
private val ShieldGradientEnd = Color(0xFF4A6091)
private val RadiusCircleColor = Color(0xFF5B7FD6)
private val PointerDotColor = Color(0xFF5B7FD6)
private val EmptyIconColor = Color(0xFFC4C4C4)
private val EmptyTextColor = Color(0xFF9CA3AF)

/**
 * 위치 확인 화면 — 참고 프로젝트 디자인 1:1 구현
 */
@Composable
fun LocationScreen() {
    var selectedGuardianId by remember {
        mutableStateOf(sampleGuardians.firstOrNull()?.id)
    }

    val hasActiveLocation = selectedGuardianId != null &&
            selectedGuardianId in guardiansWithLocation

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        // ── 상단 타이틀 ──
        HeaderTitle()

        // ── 보호자 선택 가로 스크롤 ──
        GuardianSelector(
            guardians = sampleGuardians,
            selectedId = selectedGuardianId,
            onSelect = { selectedGuardianId = it }
        )

        // ── 지도 영역 or 빈 상태 ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor),
            contentAlignment = Alignment.Center
        ) {
            if (hasActiveLocation) {
                MapWithShieldMarker()
            } else {
                EmptyLocationState()
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// 상단 타이틀
// ════════════════════════════════════════════════════════════

@Composable
private fun HeaderTitle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgColor)
            .statusBarsPadding()
            .padding(top = 14.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "위치 확인",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = TitleColor
        )
    }
}

// ════════════════════════════════════════════════════════════
// 보호자 선택 가로 스크롤
// ════════════════════════════════════════════════════════════

@Composable
private fun GuardianSelector(
    guardians: List<Guardian>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    if (guardians.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "등록된 보호자가 없습니다",
                fontSize = 14.sp,
                color = UnselectedNameColor
            )
        }
        return
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(guardians) { _, guardian ->
            val isSelected = selectedId == guardian.id

            GuardianAvatar(
                name = guardian.name,
                isSelected = isSelected,
                onClick = { onSelect(guardian.id) }
            )
        }
    }
}

@Composable
private fun GuardianAvatar(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // 아바타 원형
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(if (isSelected) SelectedAvatarBg else UnselectedAvatarBg)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, SelectedBorderColor, CircleShape)
                    } else {
                        Modifier.border(2.dp, Color.Transparent, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // ep-user 벡터 아이콘
            Image(
                painter = painterResource(id = R.drawable.ic_ep_user),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                colorFilter = ColorFilter.tint(if (isSelected) UnselectedIconColor else Color.White)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 이름
        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) SelectedNameColor else UnselectedNameColor
        )
    }
}

// ════════════════════════════════════════════════════════════
// 지도 뷰 — 방패 마커 + 반경 원
// ════════════════════════════════════════════════════════════

@Composable
private fun MapWithShieldMarker() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 파란 반경 원
        Box(
            modifier = Modifier
                .size(160.dp)
                .offset(y = 8.dp)
                .clip(CircleShape)
                .background(RadiusCircleColor.copy(alpha = 0.15f))
        )

        // 방패 마커 + 포인터 도트
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (-16).dp)
        ) {
            // 방패 아이콘 (Canvas로 그리기)
            Box(
                modifier = Modifier.size(80.dp, 96.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawShieldMarker(this)
                }
                // 방패 내 사용자 아이콘 (흰색)
                Image(
                    painter = painterResource(id = R.drawable.ic_ep_user),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .offset(y = (-6).dp),
                    colorFilter = ColorFilter.tint(Color.White)
                )
            }

            // 포인터 도트
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .offset(y = (-4).dp)
                    .clip(CircleShape)
                    .background(PointerDotColor)
            )
        }
    }
}

/**
 * 방패 모양을 Canvas로 그리기
 */
private fun drawShieldMarker(drawScope: DrawScope) {
    with(drawScope) {
        val w = size.width
        val h = size.height

        // 방패 외곽 경로
        val shieldPath = Path().apply {
            moveTo(w * 0.5f, 0f)                    // 상단 중앙
            lineTo(w, h * 0.167f)                    // 우상단
            lineTo(w, h * 0.5f)                      // 우측 중앙
            cubicTo(w, h * 0.75f, w * 0.75f, h * 0.917f, w * 0.5f, h)  // 우하단 곡선
            cubicTo(w * 0.25f, h * 0.917f, 0f, h * 0.75f, 0f, h * 0.5f) // 좌하단 곡선
            lineTo(0f, h * 0.167f)                   // 좌상단
            close()
        }

        // 방패 그라데이션 채우기
        drawPath(
            path = shieldPath,
            brush = Brush.verticalGradient(
                colors = listOf(ShieldGradientStart, ShieldGradientEnd)
            ),
            style = Fill
        )

        // 방패 안의 흰색 원
        drawCircle(
            color = Color.White,
            radius = w * 0.275f,
            center = Offset(w * 0.5f, h * 0.417f)
        )
    }
}

// ════════════════════════════════════════════════════════════
// 빈 상태 (위치 미활성)
// ════════════════════════════════════════════════════════════

@Composable
private fun EmptyLocationState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // 지도 꺼진 아이콘 (Canvas로 그리기)
        Canvas(
            modifier = Modifier.size(96.dp)
        ) {
            drawMapOffIcon(this)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "위험 감지 이후 30분동안 제공됩니다.",
            fontSize = 16.sp,
            color = EmptyTextColor
        )
    }
}

/**
 * 지도 꺼짐 아이콘을 Canvas로 그리기
 * (3개의 접힌 지도 패널 + 대각선 슬래시)
 */
private fun drawMapOffIcon(drawScope: DrawScope) {
    with(drawScope) {
        val w = size.width
        val h = size.height
        val strokeW = 2.5.dp.toPx()

        // 왼쪽 패널
        val leftPanel = Path().apply {
            moveTo(w * 0.208f, h * 0.25f)   // (20/96, 24/96)
            lineTo(w * 0.375f, h * 0.167f)  // (36/96, 16/96)
            lineTo(w * 0.375f, h * 0.667f)  // (36/96, 64/96)
            lineTo(w * 0.208f, h * 0.75f)   // (20/96, 72/96)
            close()
        }
        drawPath(
            path = leftPanel,
            color = EmptyIconColor,
            style = Stroke(width = strokeW)
        )

        // 중앙 패널
        val centerPanel = Path().apply {
            moveTo(w * 0.375f, h * 0.167f)  // (36/96, 16/96)
            lineTo(w * 0.625f, h * 0.25f)   // (60/96, 24/96)
            lineTo(w * 0.625f, h * 0.75f)   // (60/96, 72/96)
            lineTo(w * 0.375f, h * 0.667f)  // (36/96, 64/96)
            close()
        }
        drawPath(
            path = centerPanel,
            color = EmptyIconColor,
            style = Stroke(width = strokeW)
        )

        // 오른쪽 패널
        val rightPanel = Path().apply {
            moveTo(w * 0.625f, h * 0.25f)   // (60/96, 24/96)
            lineTo(w * 0.792f, h * 0.167f)  // (76/96, 16/96)
            lineTo(w * 0.792f, h * 0.667f)  // (76/96, 64/96)
            lineTo(w * 0.625f, h * 0.75f)   // (60/96, 72/96)
            close()
        }
        drawPath(
            path = rightPanel,
            color = EmptyIconColor,
            style = Stroke(width = strokeW)
        )

        // 대각선 슬래시
        drawLine(
            color = EmptyIconColor,
            start = Offset(w * 0.167f, h * 0.833f),  // (16/96, 80/96)
            end = Offset(w * 0.833f, h * 0.167f),    // (80/96, 16/96)
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}



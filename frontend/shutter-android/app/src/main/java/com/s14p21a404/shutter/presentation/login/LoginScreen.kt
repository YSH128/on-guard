package com.s14p21a404.shutter.presentation.login

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s14p21a404.shutter.R

// ── 색상 상수 ──
private val GradientCenter = Color(0xFF5E84DA)
private val GradientMiddle = Color(0xFF25398D)
private val GradientEdge = Color(0xFF131B52)
private val BottomAreaBg = Color(0xFFF1F4F8)
private val KakaoYellow = Color(0xFFFEE500)
private val KakaoTextColor = Color(0xFF191919)
private val AlertCardBg = Color(0xFFF1F4F8)
private val AlertCardBorder = Color(0x4DFFFFFF) // white/30
private val AlertAvatarBg = Color(0xFFE8ECF2)
private val AlertAvatarIcon = Color(0xFF9CA3AF)
private val AlertTitleColor = Color(0xFF1A1A2E)
private val AlertTimeColor = Color(0xFF4A7CFF)
private val AlertDescColor = Color(0xFF6B7280)
private val PhoneBorderColor = Color(0xFFF1F4F8)
private val PhoneInnerBg = Color(0xFF111111)
private val SubtitleColor = Color(0x99FFFFFF)  // white/60
private val SocialLabelColor = Color(0xFFAAAAAA)

@Composable
fun LoginScreen(
    onKakaoLogin: () -> Unit,
    loginError: String? = null,
    onDismissError: () -> Unit = {}
) {
    // ── 에러 다이얼로그 ──
    if (loginError != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(text = "로그인 실패") },
            text = { Text(text = loginError) },
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text("확인")
                }
            }
        )
    }

    // ── Float 애니메이션 ──
    val infiniteTransition = rememberInfiniteTransition(label = "float")

    val floatAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                0f at 0
                (-8f) at 2000
                0f at 4000
            }, repeatMode = RepeatMode.Restart
        ), label = "floatAnim"
    )

    // Pulse dot
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1800
                0.55f at 0; 0f at 900; 0.55f at 1800
            }, repeatMode = RepeatMode.Restart
        ), label = "pulseAlpha"
    )
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1800
                0f at 0; 5f at 900; 0f at 1800
            }, repeatMode = RepeatMode.Restart
        ), label = "pulseRadius"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ════════════════════════════════════════════
        // 상단: radial-gradient 배경
        // ════════════════════════════════════════════
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.00f to GradientCenter,
                                0.40f to GradientMiddle,
                                1.00f to GradientEdge
                            ),
                            center = Offset(size.width / 2f, size.height * 0.32f),
                            radius = size.width * 1.1f
                        )
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── 로고: SHUTTER ──
                Text(
                    text = "SHUTTER",
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    letterSpacing = 8.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // ── 부제 ──
                Text(
                    text = "영상통화 속 딥페이크를 실시간으로 탐지합니다",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = SubtitleColor,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ════════════════════════════════════════
                // Scene: 폰 + 플로팅 카드들
                // ════════════════════════════════════════
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(448.dp)
                        .padding(horizontal = 12.dp)
                        .zIndex(5f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // ── 중앙: 폰 목업 (z=4, 카드보다 아래) ──
                    PhoneMockup(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = 28.dp)
                            .zIndex(4f)
                    )

                    // ── 왼쪽: AI 위조 감지 카드 (z=10) ──
                    AiForgeryCard(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 2.dp, y = (8 + floatAnim).dp)
                            .zIndex(10f)
                    )

                    // ── 오른쪽: 실시간 AI 감지 중 pill (z=10) ──
                    RealtimePill(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-4).dp, y = (300 + floatAnim).dp)
                            .zIndex(10f),
                        pulseAlpha = pulseAlpha,
                        pulseRadius = pulseRadius
                    )

                    // ── 오른쪽 하단: 긴급 알림 카드 (z=10) ──
                    AlertCard(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-4).dp, y = (340 + floatAnim).dp)
                            .zIndex(10f)
                    )
                }

                // ════════════════════════════════════════
                // 사다리꼴 전환부 — gradient Box 안에 포함시켜 경계선 제거
                // ════════════════════════════════════════
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    val phoneWidthPx = 120.dp.toPx()
                    val left = (size.width - phoneWidthPx) / 2f
                    val right = (size.width + phoneWidthPx) / 2f
                    val path = Path().apply {
                        moveTo(left, 0f)
                        lineTo(right, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path, color = BottomAreaBg, style = Fill)
                }
            }
        }

        // ════════════════════════════════════════════
        // 하단: 소셜 로그인 영역
        // ════════════════════════════════════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BottomAreaBg)
                .padding(horizontal = 24.dp)
                .padding(top = 0.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "소셜 로그인",
                fontSize = 13.sp,
                color = SocialLabelColor,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // ── 카카오 로그인 버튼 ──
            Box(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(KakaoYellow)
                    .clickable { onKakaoLogin() }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 카카오 말풍선 아이콘
                    Icon(
                        painter = painterResource(id = R.drawable.ic_kakao_chat),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = KakaoTextColor
                    )
                    Text(
                        text = "카카오 로그인",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = KakaoTextColor,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    // 오른쪽 밸런스용 빈 공간
                    Spacer(modifier = Modifier.width(18.dp))
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════
// 하위 컴포저블: AI 위조 감지 카드 (WarningOverlay glass card 디자인)
// ════════════════════════════════════════════════════════
@Composable
private fun AiForgeryCard(modifier: Modifier = Modifier) {
    // border gradient 색상 (회색 계열)
    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xCCA0A0A0),
            Color(0x88888888),
            Color(0x44FFFFFF)
        )
    )
    // card 내부 gradient 배경 (회색 계열) — 가독성을 위해 불투명도 상향
    val cardBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xEE6B7280),
            Color(0xCC444444),
            Color(0xAA3A3A3A)
        )
    )
    // icon ring gradient (회색 계열)
    val iconRingBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xCCFFFFFF),
            Color(0x44888888),
            Color(0x22FFFFFF)
        )
    )
    // icon inner gradient
    val iconInnerBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0x66FFFFFF),
            Color(0x33FFFFFF),
            Color(0x22FFFFFF)
        )
    )

    Box(
        modifier = modifier
            .width(140.dp)
            .shadow(24.dp, RoundedCornerShape(20.dp), ambientColor = Color.White, spotColor = Color.White)
            .clip(RoundedCornerShape(20.dp))
            .background(borderBrush)
            .padding(2.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(cardBrush)
            .padding(14.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // ── 아이콘 링 ──
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(iconRingBrush),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(iconInnerBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_warning_triangle),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── 타이틀 ──
            Text(
                text = "AI 위조 감지",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── divider ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x33FFFFFF))
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── 프리뷰 박스 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x33AAAAAA))
                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = Color(0x80FFFFFF)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Detected",
                        fontSize = 8.sp,
                        color = Color(0x80FFFFFF)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 경고 텍스트 (빨간색) ──
            Text(
                text = "딥페이크 의심 영상이\n감지되었습니다",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF8A80),
                lineHeight = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ── 안내 텍스트 ──
            Text(
                text = "통화를 종료해주세요 ▼",
                fontSize = 9.sp,
                color = Color(0xB3FFFFFF),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ════════════════════════════════════════════════════════
// 하위 컴포저블: 폰 목업
// ════════════════════════════════════════════════════════
@Composable
private fun PhoneMockup(modifier: Modifier = Modifier) {
    // 2초 후 on 이미지로 전환하기 위한 상태
    var showOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000L)
        showOn = true
    }
    // opacity 애니메이션
    val onAlpha by animateFloatAsState(
        targetValue = if (showOn) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "detectOnAlpha"
    )

    Box(
        modifier = modifier
            .width(200.dp)
            .height(420.dp)
            .clip(RoundedCornerShape(46.dp))
            .background(PhoneBorderColor)
            .padding(10.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(PhoneInnerBg),
        contentAlignment = Alignment.Center
    ) {
        // detect_off 이미지 (항상 표시, on이 위에 덮음)
        Image(
            painter = painterResource(id = R.drawable.detect_off),
            contentDescription = "감지 비활성",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // detect_on 이미지 (2초 후 fade-in)
        Image(
            painter = painterResource(id = R.drawable.detect_on),
            contentDescription = "감지 활성",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = onAlpha }
        )
    }
}

// ════════════════════════════════════════════════════════
// 하위 컴포저블: 실시간 AI 감지 중 Pill (HomeScreen 상태 뱃지 디자인)
// ════════════════════════════════════════════════════════
private val StatusGreen = Color(0xFF4ADE80)

@Composable
private fun RealtimePill(
    modifier: Modifier = Modifier,
    pulseAlpha: Float,
    pulseRadius: Float
) {
    Surface(
        modifier = modifier
            .shadow(20.dp, RoundedCornerShape(50), ambientColor = Color.White, spotColor = Color.White),
        shape = RoundedCornerShape(50),
        color = Color(0xFF4A4A4A).copy(alpha = 0.75f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Green dot with pulse — 고정 크기 Box로 감싸서 레이아웃 흔들림 방지
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size((6 + pulseRadius * 2).dp)
                        .clip(CircleShape)
                        .background(StatusGreen.copy(alpha = pulseAlpha))
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(StatusGreen)
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "실시간 AI 감지 중",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

// ════════════════════════════════════════════════════════
// 하위 컴포저블: 긴급 알림 카드
// ════════════════════════════════════════════════════════
@Composable
private fun AlertCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(200.dp)
            .shadow(20.dp, RoundedCornerShape(16.dp), ambientColor = Color.White, spotColor = Color.White)
            .clip(RoundedCornerShape(16.dp))
            .background(AlertCardBg)
            .border(1.dp, AlertCardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AlertAvatarBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = AlertAvatarIcon
                )
            }
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "SHUTTER 긴급 알림",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlertTitleColor
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_clock),
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = AlertTimeColor
                        )
                        Text(
                            text = "지금",
                            fontSize = 9.sp,
                            color = AlertTimeColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "김싸피님이 딥페이크 의심 통화 중입니다.",
                    fontSize = 9.sp,
                    color = AlertDescColor,
                    lineHeight = 13.sp
                )
            }
        }
    }
}
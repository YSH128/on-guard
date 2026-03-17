package com.s14p21a404.shutter.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// ── 색상 상수 ──
private val BgColor = Color(0xFFF2F5F9)
private val TitleColor = Color(0xFF4A6091)
private val TextPrimary = Color(0xFF1A1A1A)
private val TextSecondary = Color(0xFF6B7280)
private val CardBg = Color.White
private val AvatarBg = Color(0xFFF3F4F6)
private val AvatarIconColor = Color(0xFF9CA3AF)
private val SelectedBg = Color(0xFFEEF2FF)
private val SelectedBorder = Color(0xFF6083EE)
private val CheckActiveBg = Color(0xFF6083EE)
private val CheckInactiveBg = Color(0xFFD1D5DB)
private val ButtonColor = Color(0xFF2B3A5C)
private val DirectInputBg = Color(0xFFEEF2FF)
private val DirectInputBorder = Color(0xFF6083EE)
private val DisabledColor = Color(0xFFB0B8C1)

/**
 * 카카오 친구 목록 임시 데이터 모델 (카카오 비즈앱 전환 후 실제 API 교체 예정)
 */
private data class KakaoFriend(val id: String, val name: String)

/**
 * 샘플 친구 목록 (카카오 비즈앱 전환 전 임시 데이터)
 */
private val sampleKakaoFriends = listOf(
    KakaoFriend(id = "g1", name = "김철수"),
    KakaoFriend(id = "g2", name = "이영희"),
    KakaoFriend(id = "g3", name = "정민수"),
    KakaoFriend(id = "g4", name = "박지성"),
    KakaoFriend(id = "g5", name = "최수연"),
    KakaoFriend(id = "g6", name = "한지민"),
    KakaoFriend(id = "g7", name = "송중기"),
    KakaoFriend(id = "g8", name = "강다니엘"),
    KakaoFriend(id = "g9", name = "유재석"),
    KakaoFriend(id = "g10", name = "김연아"),
)

/**
 * 보호자 등록 화면
 *
 * 구성:
 *  - 상단: 뒤로가기 + "보호자 등록" 타이틀
 *  - 직접 등록: 카카오 ID 숫자 입력 → API 호출
 *  - 구분선 "또는"
 *  - 친구 목록 선택: 카카오 비즈앱 전환 전 샘플 목록 (API 미연동)
 *
 * [onRegistered] 직접 등록 성공 시 호출 (SettingsScreen 갱신용)
 */
@Composable
fun GuardianRegistrationScreen(
    onBack: () -> Unit,
    onRegistered: () -> Unit = onBack,
    viewModel: GuardianRegistrationViewModel = viewModel(),
) {
    val registrationState by viewModel.state.collectAsState()

    var kakaoIdInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    val filteredFriends = if (searchQuery.isBlank()) {
        sampleKakaoFriends
    } else {
        sampleKakaoFriends.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // 직접 등록 성공 시 콜백 호출
    LaunchedEffect(registrationState) {
        if (registrationState is RegistrationUiState.Success) {
            viewModel.resetState()
            onRegistered()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 상단 헤더 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgColor)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = "뒤로가기",
                        modifier = Modifier.size(24.dp),
                        tint = TitleColor
                    )
                }
                Text(
                    text = "보호자 등록",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TitleColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // ── 카카오 ID 직접 입력 섹션 ──
                item {
                    DirectKakaoIdSection(
                        kakaoIdInput = kakaoIdInput,
                        onInputChange = { kakaoIdInput = it },
                        isLoading = registrationState is RegistrationUiState.Loading,
                        onRegister = {
                            val id = kakaoIdInput.trim().toLongOrNull()
                            if (id != null) viewModel.registerByKakaoId(id)
                        }
                    )
                }

                // ── 에러 메시지 ──
                if (registrationState is RegistrationUiState.Error) {
                    item {
                        Text(
                            text = (registrationState as RegistrationUiState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                // ── 구분선 "또는" ──
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFDDE3EE))
                        Text(
                            text = "  또는  ",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFDDE3EE))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── 친구 목록 섹션 타이틀 ──
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "친구 목록에서 선택",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "카카오 비즈앱 전환 후 활성화",
                            fontSize = 11.sp,
                            color = DisabledColor
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ── 검색창 ──
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = CardBg,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "검색",
                                modifier = Modifier.size(20.dp),
                                tint = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        text = "보호자명을 검색해보세요..",
                                        fontSize = 14.sp,
                                        color = TextSecondary
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                ),
                                enabled = false // 비즈앱 전환 전 비활성
                            )
                        }
                    }
                }

                // ── 친구 목록 (샘플, 비즈앱 전환 전 비활성) ──
                items(filteredFriends) { friend ->
                    val isSelected = friend.id in selectedIds
                    FriendSelectionCard(
                        name = friend.name,
                        isSelected = isSelected,
                        enabled = false, // 비즈앱 전환 전 비활성
                        onToggle = {
                            selectedIds = if (isSelected) selectedIds - friend.id
                            else selectedIds + friend.id
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        // ── 하단 "친구 목록 등록" 버튼 (비즈앱 전환 후 활성화) ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, BgColor, BgColor),
                    )
                )
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 32.dp)
        ) {
            Button(
                onClick = { /* TODO: 카카오 비즈앱 전환 후 선택된 친구 목록으로 등록 */ },
                enabled = false, // 비즈앱 전환 전 비활성
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DisabledColor,
                    contentColor = Color.White,
                    disabledContainerColor = DisabledColor,
                    disabledContentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "친구 목록 등록 완료 (비즈앱 전환 후 활성화)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// 카카오 ID 직접 입력 섹션
// ════════════════════════════════════════════════════════════

@Composable
private fun DirectKakaoIdSection(
    kakaoIdInput: String,
    onInputChange: (String) -> Unit,
    isLoading: Boolean,
    onRegister: () -> Unit,
) {
    val isValidId = kakaoIdInput.trim().toLongOrNull() != null

    Text(
        text = "카카오 ID로 직접 등록",
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = "보호자의 카카오 ID를 직접 입력하여 등록할 수 있습니다.",
        fontSize = 12.sp,
        color = TextSecondary
    )

    Spacer(modifier = Modifier.height(12.dp))

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DirectInputBg,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, DirectInputBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = kakaoIdInput,
                onValueChange = { new ->
                    // 숫자만 입력 허용
                    if (new.all { it.isDigit() }) onInputChange(new)
                },
                label = { Text("카카오 ID (숫자)", fontSize = 13.sp) },
                placeholder = { Text("예: 4790907961", fontSize = 14.sp, color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DirectInputBorder,
                    unfocusedBorderColor = Color(0xFFB8C5E0),
                    focusedContainerColor = CardBg,
                    unfocusedContainerColor = CardBg,
                ),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRegister,
                enabled = isValidId && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonColor,
                    contentColor = Color.White,
                    disabledContainerColor = DisabledColor,
                    disabledContentColor = Color.White,
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(text = "등록", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// 친구 선택 카드 (카카오 비즈앱 전환 후 활성화)
// ════════════════════════════════════════════════════════════

@Composable
private fun FriendSelectionCard(
    name: String,
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected && enabled) {
                    Modifier.border(2.dp, SelectedBorder, RoundedCornerShape(16.dp))
                } else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        color = when {
            !enabled -> Color(0xFFEFF1F5)
            isSelected -> SelectedBg
            else -> CardBg
        },
        onClick = { if (enabled) onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AvatarBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (enabled) AvatarIconColor else DisabledColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) TextPrimary else DisabledColor,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !enabled -> DisabledColor.copy(alpha = 0.4f)
                            isSelected -> CheckActiveBg
                            else -> CheckInactiveBg
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = if (isSelected) "선택됨" else "선택 안됨",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            }
        }
    }
}
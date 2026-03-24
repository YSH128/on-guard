package com.s14p21a404.shutter.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
private val DisabledColor = Color(0xFFB0B8C1)

/**
 * 보호자 등록 화면
 *
 * 카카오톡 친구 목록을 불러와 선택 후 서버에 보호자로 등록한다.
 *
 * [onRegistered] 등록 성공 시 호출
 */
@Composable
fun GuardianRegistrationScreen(
    onBack: () -> Unit,
    onRegistered: () -> Unit = onBack,
    viewModel: GuardianRegistrationViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredFriends = if (searchQuery.isBlank()) {
        uiState.friends
    } else {
        uiState.friends.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // 화면 진입 시 친구 목록 로드
    LaunchedEffect(Unit) { viewModel.loadKakaoFriends() }

    // 등록 성공 시 콜백
    LaunchedEffect(uiState.registrationState) {
        if (uiState.registrationState is RegistrationUiState.Success) {
            viewModel.resetRegistrationState()
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
                        tint = Color(0xFF6083EE)
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

            // ── 검색창 ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = CardBg,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "검색",
                        modifier = Modifier.size(18.dp),
                        tint = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(text = "검색", fontSize = 14.sp, color = TextSecondary)
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
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {

                // ── 로딩 ──
                if (uiState.friendsLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF6083EE))
                        }
                    }
                }

                // ── 에러 ──
                uiState.friendsError?.let { error ->
                    item {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                // ── 등록 에러 ──
                if (uiState.registrationState is RegistrationUiState.Error) {
                    item {
                        Text(
                            text = (uiState.registrationState as RegistrationUiState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                // ── 친구 목록 ──
                items(filteredFriends, key = { it.id }) { friend ->
                    val isSelected = friend.id in uiState.selectedIds
                    FriendSelectionCard(
                        name = friend.name,
                        isSelected = isSelected,
                        onToggle = { viewModel.toggleFriendSelection(friend.id) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ── 친구 없음 ──
                if (!uiState.friendsLoading && uiState.friendsError == null && uiState.friends.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "카카오톡 친구가 없습니다.",
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // ── 하단 등록 버튼 ──
        val isRegistering = uiState.registrationState is RegistrationUiState.Loading
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
                onClick = { viewModel.registerSelectedFriends() },
                enabled = uiState.selectedIds.isNotEmpty() && !isRegistering,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonColor,
                    contentColor = Color.White,
                    disabledContainerColor = DisabledColor,
                    disabledContentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                if (isRegistering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "등록 완료",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// 친구 선택 카드
// ════════════════════════════════════════════════════════════

@Composable
private fun FriendSelectionCard(
    name: String,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(2.dp, SelectedBorder, RoundedCornerShape(16.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) SelectedBg else CardBg,
        onClick = onToggle,
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
                    tint = AvatarIconColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) CheckActiveBg else CheckInactiveBg),
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
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
import com.s14p21a404.shutter.domain.model.Guardian

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

/**
 * 샘플 보호자 목록 (서버 연동 시 API로 교체)
 */
private val allGuardians = listOf(
    Guardian(id = "g1", name = "김철수"),
    Guardian(id = "g2", name = "이영희"),
    Guardian(id = "g3", name = "정민수"),
    Guardian(id = "g4", name = "박지성"),
    Guardian(id = "g5", name = "최수연"),
    Guardian(id = "g6", name = "한지민"),
    Guardian(id = "g7", name = "송중기"),
    Guardian(id = "g8", name = "강다니엘"),
    Guardian(id = "g9", name = "유재석"),
    Guardian(id = "g10", name = "김연아"),
)

/**
 * 보호자 등록 화면 — 참고 프로젝트 디자인 1:1 구현
 *
 * 구성:
 *  - 상단 "보호자 등록" 타이틀 + 뒤로가기
 *  - 검색 입력창
 *  - 보호자 목록 (체크 선택 가능)
 *  - 하단 "등록 완료" 버튼
 */
@Composable
fun GuardianRegistrationScreen(
    onBack: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    val filteredGuardians = if (searchQuery.isBlank()) {
        allGuardians
    } else {
        allGuardians.filter { it.name.contains(searchQuery, ignoreCase = true) }
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
                // 뒤로가기 버튼
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

            // ── 검색 입력창 ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
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
                        )
                    )
                }
            }

            // ── 보호자 목록 ──
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(filteredGuardians) { guardian ->
                    val isSelected = guardian.id in selectedIds
                    GuardianSelectionCard(
                        guardian = guardian,
                        isSelected = isSelected,
                        onToggle = {
                            selectedIds = if (isSelected) {
                                selectedIds - guardian.id
                            } else {
                                selectedIds + guardian.id
                            }
                        }
                    )
                }
            }
        }

        // ── 하단 등록 완료 버튼 (고정) ──
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
                onClick = {
                    // TODO: SettingsViewModel에 선택된 보호자 전달
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonColor,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "등록 완료",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun GuardianSelectionCard(
    guardian: Guardian,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, SelectedBorder, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) SelectedBg else CardBg,
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 아바타
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
                text = guardian.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )

            // 체크 아이콘
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


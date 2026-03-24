package com.s14p21a404.shutter.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.s14p21a404.shutter.domain.model.Guardian

// ── 색상 상수 ──
private val BgColor = Color(0xFFF2F5F9)
private val TitleColor = Color(0xFF4A6091)
private val TextPrimary = Color(0xFF1A1A1A)
private val TextSecondary = Color(0xFF9CA3AF)
private val TextTertiary = Color(0xFF6B7280)
private val CardBg = Color.White
private val ToggleActiveColor = Color(0xFF6083EE)
private val ToggleInactiveColor = Color(0xFFD1D5DB)
private val AddButtonBg = Color(0xFF2B3A5C)
private val AvatarBg = Color(0xFFF3F4F6)
private val DeleteIconColor = Color(0xFF9CA3AF)

/**
 * 환경 설정 화면
 *
 * [refreshKey] 값이 바뀔 때마다 보호자 목록을 서버에서 새로 불러옵니다.
 * (보호자 등록 화면에서 돌아올 때 AppScaffold가 이 값을 증가시킵니다.)
 */
@Composable
fun SettingsScreen(
    refreshKey: Int = 0,
    onNavigateToGuardianRegistration: () -> Unit,
    onLogout: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // refreshKey 변화 시 보호자 목록 갱신 (첫 진입 포함)
    LaunchedEffect(refreshKey) {
        settingsViewModel.loadGuardians()
    }

    // 위치 권한 요청
    var showLocationPermissionDialog by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) settingsViewModel.toggleGpsSharing()
    }

    val handleGpsToggle: () -> Unit = {
        if (!uiState.gpsSharing) {
            val hasLocation = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasLocation) settingsViewModel.toggleGpsSharing()
            else showLocationPermissionDialog = true
        } else {
            settingsViewModel.toggleGpsSharing()
        }
    }

    // ── 위치 권한 다이얼로그 ──
    if (showLocationPermissionDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("위치 권한 필요", fontWeight = FontWeight.Bold) },
            text = { Text("GPS 위치 공유를 사용하려면\n위치 권한을 허용해주세요.") },
            confirmButton = {
                TextButton(onClick = {
                    showLocationPermissionDialog = false
                    locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                }) { Text("허용하기") }
            },
        )
    }

    // ── 에러 스낵바 ──
    uiState.error?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { settingsViewModel.dismissError() },
            title = { Text("오류") },
            text = { Text(errorMsg) },
            confirmButton = {
                TextButton(onClick = { settingsViewModel.dismissError() }) { Text("확인") }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 24.dp, bottom = 96.dp)
                ) {
                    NotificationSettingsSection(
                        gpsSharing = uiState.gpsSharing,
                        onToggleGps = handleGpsToggle
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    if (uiState.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ToggleActiveColor)
                        }
                    } else {
                        GuardianListSection(
                            guardians = uiState.guardians,
                            onAddGuardian = onNavigateToGuardianRegistration,
                            onDeleteGuardian = { settingsViewModel.requestDeleteGuardian(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextTertiary
                        )
                    ) {
                        Text(text = "로그아웃", fontSize = 15.sp)
                    }
                }
            }
        }

        if (uiState.guardianToDelete != null) {
            DeleteGuardianDialog(
                guardian = uiState.guardianToDelete!!,
                onConfirm = { settingsViewModel.confirmDeleteGuardian() },
                onDismiss = { settingsViewModel.dismissDeleteDialog() }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// 상단 헤더
// ════════════════════════════════════════════════════════════

@Composable
private fun SettingsHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgColor)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "환경 설정",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = TitleColor,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// ════════════════════════════════════════════════════════════
// 알림 설정 섹션
// ════════════════════════════════════════════════════════════

@Composable
private fun NotificationSettingsSection(
    gpsSharing: Boolean,
    onToggleGps: () -> Unit,
) {
    Text(
        text = "알림 설정",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )

    Spacer(modifier = Modifier.height(16.dp))

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GPS 위치 공유",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "위험 감지 시 보호자에게 위치 알림",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            CustomToggle(
                checked = gpsSharing,
                onCheckedChange = { onToggleGps() }
            )
        }
    }
}

@Composable
private fun CustomToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        label = "thumb"
    )

    Box(
        modifier = Modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (checked) ToggleActiveColor else ToggleInactiveColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .padding(vertical = 3.dp)
                .size(24.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

// ════════════════════════════════════════════════════════════
// 보호자 목록 섹션
// ════════════════════════════════════════════════════════════

@Composable
private fun GuardianListSection(
    guardians: List<Guardian>,
    onAddGuardian: () -> Unit,
    onDeleteGuardian: (Guardian) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "보호자 목록",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Surface(
            onClick = onAddGuardian,
            shape = RoundedCornerShape(50),
            color = AddButtonBg,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = "보호자 추가",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "추가",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (guardians.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "등록된 보호자가 없습니다",
                fontSize = 14.sp,
                color = TextSecondary
            )
        }
    } else {
        guardians.forEach { guardian ->
            GuardianCard(
                guardian = guardian,
                onDelete = { onDeleteGuardian(guardian) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GuardianCard(
    guardian: Guardian,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        shadowElevation = 1.dp,
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
                if (!guardian.profileImageUrl.isNullOrEmpty()) {
                    SubcomposeAsyncImage(
                        model = guardian.profileImageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = TextSecondary
                            )
                        },
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = guardian.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "삭제",
                    modifier = Modifier.size(20.dp),
                    tint = DeleteIconColor
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// 보호자 삭제 확인 다이얼로그
// ════════════════════════════════════════════════════════════

@Composable
private fun DeleteGuardianDialog(
    guardian: Guardian,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(320.dp)
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "보호자 삭제",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "해당 보호자를 삭제하시겠습니까?",
                    fontSize = 14.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AvatarBg),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!guardian.profileImageUrl.isNullOrEmpty()) {
                            SubcomposeAsyncImage(
                                model = guardian.profileImageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = {
                                    Icon(
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = TextSecondary
                                    )
                                },
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = guardian.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ToggleActiveColor,
                        contentColor = Color.White,
                    )
                ) {
                    Text(text = "확인", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextTertiary)
                ) {
                    Text(text = "취소", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
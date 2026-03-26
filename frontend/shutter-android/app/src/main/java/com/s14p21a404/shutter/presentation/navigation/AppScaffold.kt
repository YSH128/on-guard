package com.s14p21a404.shutter.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.s14p21a404.shutter.data.repository.VideoCallSessionStore
import com.s14p21a404.shutter.presentation.main.MainUiState
import com.s14p21a404.shutter.presentation.screens.GuardianRegistrationScreen
import com.s14p21a404.shutter.presentation.screens.HomeScreen
import com.s14p21a404.shutter.presentation.screens.LocationScreen
import com.s14p21a404.shutter.presentation.screens.RecentRecordsScreen
import com.s14p21a404.shutter.presentation.screens.SettingsScreen
import com.s14p21a404.shutter.presentation.screens.VideoCallScreen

/**
 * 하단 네비게이션 탭 정의
 */
data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "연락처", Icons.Filled.People, Icons.Outlined.People),
    BottomNavItem(Screen.RecentRecords, "최근기록", Icons.Filled.AccessTime, Icons.Outlined.AccessTime),
    BottomNavItem(Screen.Location, "위치", Icons.Filled.LocationOn, Icons.Outlined.LocationOn),
    BottomNavItem(Screen.Settings, "설정", Icons.Filled.Settings, Icons.Outlined.Settings),
)

// ── 색상 상수 ──
private val NavBgColor = Color(0xFFFFFFFF)
private val NavActiveColor = Color(0xFF1A1A1A)
private val NavInactiveColor = Color(0xFF64666E)

/**
 * 로그인 완료 후 표시되는 앱 셸
 * (하단 네비게이션 + NavHost)
 * 홈 화면 진입 시 권한이 미충족이면 권한 요청을 수행한다.
 */
@Composable
fun AppScaffold(
    onLogout: () -> Unit,
    isServiceRunning: Boolean,
    onToggleService: () -> Unit,
    uiState: MainUiState = MainUiState(),
    onRequestPermissions: () -> Unit = {},
    onRequestOverlayPermission: () -> Unit = {},
    onRequestAccessibilityPermission: () -> Unit = {},
    pendingDeepLinkScreen: String? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LaunchedEffect(pendingDeepLinkScreen) {
        when {
            pendingDeepLinkScreen == "location" -> {
                navController.navigate(Screen.Location.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                onDeepLinkHandled()
            }

            pendingDeepLinkScreen?.startsWith("video_call:") == true -> {
                // format: video_call:channelName:callerName:opponentKakaoId
                val parts = pendingDeepLinkScreen.split(":", limit = 4)
                val channelName = parts.getOrElse(1) { "" }
                val callerName = parts.getOrElse(2) { "" }
                val opponentKakaoId = parts.getOrElse(3) { "0" }.toLongOrNull() ?: 0L
                navController.navigate(Screen.VideoCall.createRoute(channelName, callerName, opponentKakaoId))
                onDeepLinkHandled()
            }
        }
    }

    // 보호자 등록·영상통화 화면에서는 하단 바를 숨김
    val showBottomBar = currentDestination?.route != Screen.GuardianRegistration.route
            && currentDestination?.route != Screen.VideoCall.route

    // 보호자 등록 성공 시 SettingsScreen 목록을 갱신하기 위한 키
    var settingsRefreshKey by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 콘텐츠 영역 — 네비게이션 바 위 공간만 차지
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.weight(1f)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    isServiceRunning = isServiceRunning,
                    onToggleService = onToggleService,
                    allPermissionsGranted = uiState.allPermissionsGranted,
                    overlayPermissionGranted = uiState.overlayPermissionGranted,
                    accessibilityServiceEnabled = uiState.accessibilityServiceEnabled,
                    onRequestPermissions = onRequestPermissions,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onRequestAccessibilityPermission = onRequestAccessibilityPermission,
                    onNavigateToVideoCall = { channelId, callerName, opponentKakaoId ->
                        navController.navigate(Screen.VideoCall.createRoute(channelId, callerName, opponentKakaoId))
                    },
                    hasPendingIncomingCall = pendingDeepLinkScreen == "accept_call_pending"
                            || pendingDeepLinkScreen?.startsWith("video_call:") == true,
                )
            }
            composable(Screen.RecentRecords.route) {
                RecentRecordsScreen()
            }
            composable(Screen.Location.route) {
                LocationScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    refreshKey = settingsRefreshKey,
                    onNavigateToGuardianRegistration = {
                        navController.navigate(Screen.GuardianRegistration.route)
                    },
                    onLogout = onLogout,
                )
            }
            composable(Screen.GuardianRegistration.route) {
                GuardianRegistrationScreen(
                    onBack = { navController.popBackStack() },
                    onRegistered = {
                        settingsRefreshKey++
                        navController.popBackStack()
                    },
                )
            }
            composable(Screen.VideoCall.route) { backStackEntry ->
                val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                val callerName = backStackEntry.arguments?.getString("callerName")?.let {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } ?: ""
                val opponentKakaoId = backStackEntry.arguments?.getString("opponentKakaoId")?.toLongOrNull() ?: 0L
                VideoCallScreen(
                    channelId = channelId,
                    callerName = callerName,
                    opponentKakaoId = opponentKakaoId,
                    onCallEnded = {
                        VideoCallSessionStore.clear()
                        navController.popBackStack()
                    }
                )
            }
        }

        // 하단 Pill 네비게이션
        if (showBottomBar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp, top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                PillBottomNavigation(
                    currentDestination = currentDestination,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PillBottomNavigation(
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (String) -> Unit,
) {
    val modifier = Modifier
    Row(
        modifier = modifier
            .fillMaxWidth(0.85f)
            .shadow(6.dp, RoundedCornerShape(40.dp), ambientColor = Color(0xFF666666), spotColor = Color(0x60000000))
            .clip(RoundedCornerShape(40.dp))
            .background(NavBgColor)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any {
                it.route == item.screen.route
            } == true

            PillNavItem(
                icon = if (selected) item.selectedIcon else item.unselectedIcon,
                label = item.label,
                selected = selected,
                onClick = { onNavigate(item.screen.route) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PillNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (selected) NavActiveColor else NavInactiveColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) NavActiveColor else NavInactiveColor
        )
    }
}
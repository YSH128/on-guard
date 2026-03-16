package com.s14p21a404.shutter.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.s14p21a404.shutter.presentation.screens.GuardianRegistrationScreen
import com.s14p21a404.shutter.presentation.screens.HomeScreen
import com.s14p21a404.shutter.presentation.screens.LocationScreen
import com.s14p21a404.shutter.presentation.screens.SettingsScreen

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
    BottomNavItem(Screen.Home, "홈", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Location, "위치", Icons.Filled.LocationOn, Icons.Outlined.LocationOn),
    BottomNavItem(Screen.Settings, "설정", Icons.Filled.Settings, Icons.Outlined.Settings),
)

// ── 색상 상수 ──
private val NavBgColor = Color(0xCCFFFFFF)
private val NavActiveColor = Color(0xFF1A1A1A)
private val NavInactiveColor = Color(0xFF64666E)

/**
 * 로그인 완료 + 권한 획득 후 표시되는 앱 셸
 * (하단 네비게이션 + NavHost)
 */
@Composable
fun AppScaffold(
    onLogout: () -> Unit,
    isServiceRunning: Boolean,
    onToggleService: () -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 보호자 등록 화면에서는 하단 바를 숨김
    val showBottomBar = currentDestination?.route != Screen.GuardianRegistration.route

    Box(modifier = Modifier.fillMaxSize()) {
        // 콘텐츠 영역
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomBar) 72.dp else 0.dp)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    isServiceRunning = isServiceRunning,
                    onToggleService = onToggleService
                )
            }
            composable(Screen.Location.route) {
                LocationScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToGuardianRegistration = {
                        navController.navigate(Screen.GuardianRegistration.route)
                    },
                    onLogout = onLogout
                )
            }
            composable(Screen.GuardianRegistration.route) {
                GuardianRegistrationScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // 하단 Pill 네비게이션
        if (showBottomBar) {
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun PillBottomNavigation(
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
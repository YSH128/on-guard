package com.s14p21a404.shutter.presentation.navigation

import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.screen.route
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(text = item.label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
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
    }
}


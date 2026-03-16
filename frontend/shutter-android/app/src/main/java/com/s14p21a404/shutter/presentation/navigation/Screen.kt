package com.s14p21a404.shutter.presentation.navigation

/**
 * 앱 내 화면(라우트) 정의
 */
sealed class Screen(val route: String) {
    /** 홈 – 방패 아이콘 + 알림 타임라인 */
    object Home : Screen("home")

    /** 위치 확인 – 보호자 위치 지도 */
    object Location : Screen("location")

    /** 환경 설정 – GPS 공유, 보호자 목록 */
    object Settings : Screen("settings")

    /** 보호자 등록 – 설정에서 보호자 추가 시 진입 */
    object GuardianRegistration : Screen("guardian_registration")
}


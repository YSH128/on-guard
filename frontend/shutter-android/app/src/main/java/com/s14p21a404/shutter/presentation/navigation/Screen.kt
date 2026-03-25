package com.s14p21a404.shutter.presentation.navigation

/**
 * 앱 내 화면(라우트) 정의
 */
import java.net.URLEncoder

sealed class Screen(val route: String) {
    /** 연락처 – 카카오 친구 목록 */

    object Home : Screen("home")

    /** 최근 기록 – 알림 타임라인 */
    object RecentRecords : Screen("recent_records")

    /** 위치 확인 – 보호자 위치 지도 */
    object Location : Screen("location")

    /** 환경 설정 – GPS 공유, 보호자 목록 */
    object Settings : Screen("settings")

    /** 보호자 등록 – 설정에서 보호자 추가 시 진입 */
    object GuardianRegistration : Screen("guardian_registration")

    /** 영상 통화 */
    object VideoCall : Screen("video_call/{channelId}/{callerName}/{token}") {

        fun createRoute(
            channelId: String,
            callerName: String,
            token: String
        ): String {
            return "video_call/$channelId/${
                java.net.URLEncoder.encode(callerName, "UTF-8")
            }/${
                java.net.URLEncoder.encode(token, "UTF-8")   // 🔥 추가
            }"
        }
    }
}
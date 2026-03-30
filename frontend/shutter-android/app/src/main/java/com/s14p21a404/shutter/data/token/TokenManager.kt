package com.s14p21a404.shutter.data.token

import android.content.Context
import android.util.Log
import java.util.UUID

private const val PREFS_NAME = "shutter_auth"
private const val KEY_ACCESS_TOKEN = "access_token"
private const val KEY_REFRESH_TOKEN = "refresh_token"
private const val KEY_USER_ID = "user_id"
private const val KEY_DEVICE_UUID = "device_uuid"
private const val KEY_GPS_SHARING = "gps_sharing"
private const val KEY_MY_PROFILE_IMAGE_URL = "my_profile_image_url"

class TokenManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
        Log.d("ShutterToken", "accessToken: $accessToken")
    }

    fun saveAccessToken(accessToken: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun hasToken(): Boolean = getAccessToken() != null

    fun saveUserId(userId: Long) {
        prefs.edit().putLong(KEY_USER_ID, userId).apply()
    }

    fun getUserId(): Long = prefs.getLong(KEY_USER_ID, -1L)

    fun getOrCreateDeviceUuid(): String {
        val existing = prefs.getString(KEY_DEVICE_UUID, null)
        if (existing != null) return existing
        val newUuid = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_UUID, newUuid).apply()
        return newUuid
    }

    fun saveMyProfileImageUrl(url: String) {
        prefs.edit().putString(KEY_MY_PROFILE_IMAGE_URL, url).apply()
    }

    fun getMyProfileImageUrl(): String? = prefs.getString(KEY_MY_PROFILE_IMAGE_URL, null)

    fun saveGpsSharing(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GPS_SHARING, enabled).apply()
    }

    fun getGpsSharing(): Boolean = prefs.getBoolean(KEY_GPS_SHARING, true)

    /**
     * GPS 공유 상태를 읽되, SharedPreferences에 키가 존재하지 않으면
     * 기본값 true를 명시적으로 저장한 뒤 반환한다.
     * 앱 최초 실행 시 서버 동기화 전에 호출하여 타이밍 이슈를 방지한다.
     */
    fun getOrInitGpsSharing(): Boolean {
        if (!prefs.contains(KEY_GPS_SHARING)) {
            prefs.edit().putBoolean(KEY_GPS_SHARING, true).apply()
            return true
        }
        return prefs.getBoolean(KEY_GPS_SHARING, true)
    }

    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .apply()
    }
}
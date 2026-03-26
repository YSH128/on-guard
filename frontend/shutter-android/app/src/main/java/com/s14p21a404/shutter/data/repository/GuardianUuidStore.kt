package com.s14p21a404.shutter.data.repository

import android.content.Context

/**
 * 보호자 등록 시 서버 guardianId → 카카오 UUID 매핑을 SharedPreferences에 저장한다.
 *
 * 딥페이크 알림 발송 시 서버 guardianId로 카카오 UUID를 빠르게 조회하기 위해 사용한다.
 */
class GuardianUuidStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveUuid(guardianId: Long, uuid: String) {
        prefs.edit().putString("uuid_$guardianId", uuid).apply()
    }

    fun getUuid(guardianId: Long): String? =
        prefs.getString("uuid_$guardianId", null)

    fun removeUuid(guardianId: Long) {
        prefs.edit()
            .remove("uuid_$guardianId")
            .remove("kakao_id_$guardianId")
            .apply()
    }

    /** 보호자 등록 시 서버 guardianId → 카카오 ID 매핑을 저장 (UUID 복구용 fallback) */
    fun saveKakaoId(guardianId: Long, kakaoId: Long) {
        prefs.edit().putLong("kakao_id_$guardianId", kakaoId).apply()
    }

    fun getKakaoId(guardianId: Long): Long? {
        val value = prefs.getLong("kakao_id_$guardianId", -1L)
        return if (value == -1L) null else value
    }

    /** 보호자 등록 시 내 카카오 연락처에 저장된 이름을 저장 (설정 화면 표시용) */
    fun saveKakaoName(guardianId: Long, name: String) {
        prefs.edit().putString("kakao_name_$guardianId", name).apply()
    }

    fun getKakaoName(guardianId: Long): String? =
        prefs.getString("kakao_name_$guardianId", null)

    
    fun saveProfileImageUrl(guardianId: Long, url: String) {
        prefs.edit().putString("profile_image_$guardianId", url).apply()
    }

    fun getProfileImageUrl(guardianId: Long): String? =
        prefs.getString("profile_image_$guardianId", null)

    companion object {
        private const val PREFS_NAME = "guardian_uuid_store"
    }
}

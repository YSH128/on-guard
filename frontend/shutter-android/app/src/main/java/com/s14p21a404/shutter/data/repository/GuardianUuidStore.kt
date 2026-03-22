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
        prefs.edit().putString(guardianId.toString(), uuid).apply()
    }

    fun getUuid(guardianId: Long): String? =
        prefs.getString(guardianId.toString(), null)

    fun removeUuid(guardianId: Long) {
        prefs.edit().remove(guardianId.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "guardian_uuid_store"
    }
}

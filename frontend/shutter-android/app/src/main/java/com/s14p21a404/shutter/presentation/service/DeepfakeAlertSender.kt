package com.s14p21a404.shutter.presentation.service

import android.util.Log
import com.kakao.sdk.talk.TalkApiClient
import com.kakao.sdk.talk.model.Friend
import com.kakao.sdk.template.model.Button
import com.kakao.sdk.template.model.Link
import com.kakao.sdk.template.model.TextTemplate
import com.kakao.sdk.user.UserApiClient
import com.s14p21a404.shutter.data.repository.GuardianUuidStore
import com.s14p21a404.shutter.domain.repository.AlertRepository
import com.s14p21a404.shutter.domain.repository.GuardianRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume

/**
 * 딥페이크 감지 시 보호자에게 카카오톡 긴급 알림 메시지를 발송한다.
 *
 * 흐름:
 * 1. Kakao UserApiClient로 피보호자 이름 조회
 * 2. 서버에서 보호자 목록(kakaoId) 조회
 * 3. TalkApiClient.friends()로 카카오 친구 UUID 목록 조회
 * 4. kakaoId 매칭 → UUID 추출
 * 5. 보호자 전원에게 긴급 알림 메시지 발송
 */
class DeepfakeAlertSender(
    private val guardianRepository: GuardianRepository,
    private val guardianUuidStore: GuardianUuidStore,
    private val alertRepository: AlertRepository,
) {

    suspend fun sendAlert(detectedAt: LocalDateTime) {
        val wardName = getWardName() ?: "알 수 없음"
        val timeStr = detectedAt.format(DateTimeFormatter.ofPattern("HH시 mm분 ss초"))
        val callStartedAt = detectedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

        val guardians = guardianRepository.getMyGuardians().getOrElse {
            Log.e(TAG, "보호자 목록 조회 실패", it)
            return
        }
        if (guardians.isEmpty()) {
            Log.d(TAG, "등록된 보호자 없음 — 알림 발송 생략")
            return
        }

        // UUID가 없는 보호자가 있을 경우에만 카카오 친구 목록을 한 번만 조회한다
        val missingUuidGuardians = guardians.filter { guardianUuidStore.getUuid(it.guardianId) == null }
        val kakaoFriends: List<Friend>? = if (missingUuidGuardians.isNotEmpty()) {
            fetchKakaoFriends().also {
                if (it == null) Log.w(TAG, "카카오 친구 목록 조회 실패 — fallback 불가")
            }
        } else null

        val targetUuids = guardians.mapNotNull { guardian ->
            val cached = guardianUuidStore.getUuid(guardian.guardianId)
            if (cached != null) {
                Log.d(TAG, "보호자 ${guardian.name}(id=${guardian.guardianId}) → uuid=$cached")
                cached
            } else {
                recoverUuid(guardian.guardianId, guardian.name, kakaoFriends)
            }
        }

        if (targetUuids.isEmpty()) {
            Log.w(TAG, "발송 대상 UUID 없음 — 알림 발송 생략")
            return
        }

        val message = buildAlertMessage(wardName, timeStr)
        sendKakaoMessage(targetUuids, message)

        alertRepository.sendDeepfakeAlert(callStartedAt)
            .onSuccess { items -> Log.d(TAG, "서버 알림 전송 성공: $items") }
            .onFailure { Log.e(TAG, "서버 알림 전송 실패", it) }
    }

    private fun buildAlertMessage(wardName: String, timeStr: String): String =
        "[긴급 안전 알림: 통화 진행 중]\n" +
        "현재 ${wardName}님에게 AI로 조작된 위조 영상 통화가 연결되어 있습니다. " +
        "피해 예방을 위해 즉각적인 확인이 필요합니다.\n\n" +
        "- 통화 시작 시간: ${timeStr}\n\n" +
        "⚠️ 사기 피해가 발생할 수 있으니, 즉시 ${wardName}님에게 연락하여 통화를 종료하도록 안내해 주세요.\n" +
        "⚠️ Shutter 앱에서 피보호자의 위치가 공유됩니다."

    private suspend fun getWardName(): String? = suspendCancellableCoroutine { cont ->
        UserApiClient.instance.me { user, error ->
            if (error != null) {
                Log.w(TAG, "유저 정보 조회 실패", error)
                cont.resume(null)
            } else {
                cont.resume(user?.kakaoAccount?.name)
            }
        }
    }

    /**
     * UUID가 없는 보호자의 UUID를 카카오 친구 목록에서 복구한다.
     * 저장된 kakaoId로 친구를 찾아 UUID를 가져오고, 이후 재사용을 위해 저장한다.
     */
    private fun recoverUuid(guardianId: Long, name: String, friends: List<Friend>?): String? {
        val kakaoId = guardianUuidStore.getKakaoId(guardianId) ?: run {
            Log.w(TAG, "보호자 $name(id=$guardianId) — 저장된 카카오 ID 없음, fallback 불가")
            return null
        }
        if (friends == null) {
            Log.w(TAG, "보호자 $name(id=$guardianId) — 친구 목록 없어 UUID 복구 불가")
            return null
        }
        val friend = friends.find { it.id == kakaoId } ?: run {
            Log.w(TAG, "보호자 $name(id=$guardianId) — 카카오 친구 목록에서 찾을 수 없음")
            return null
        }
        guardianUuidStore.saveUuid(guardianId, friend.uuid)
        Log.d(TAG, "보호자 $name(id=$guardianId) — UUID 복구 성공: ${friend.uuid}")
        return friend.uuid
    }

    private suspend fun fetchKakaoFriends(): List<Friend>? = suspendCancellableCoroutine { cont ->
        TalkApiClient.instance.friends { friends, error ->
            if (error != null) {
                Log.e(TAG, "카카오 친구 목록 조회 실패", error)
                cont.resume(null)
            } else {
                cont.resume(friends?.elements)
            }
        }
    }

    private suspend fun sendKakaoMessage(uuids: List<String>, text: String) =
        suspendCancellableCoroutine { cont ->
            val deeplink = Link(androidExecutionParams = mapOf("screen" to "location"))
            val template = TextTemplate(
                text = text,
                link = deeplink,
                buttons = listOf(Button(title = "위치 확인하기", link = deeplink)),
            )
            TalkApiClient.instance.sendDefaultMessage(
                receiverUuids = uuids,
                template = template,
            ) { result, error ->
                if (error != null) {
                    Log.e(TAG, "긴급 알림 발송 실패", error)
                } else {
                    Log.d(TAG, "긴급 알림 발송 성공: ${result?.successfulReceiverUuids}")
                }
                cont.resume(Unit)
            }
        }

    companion object {
        private const val TAG = "DeepfakeAlertSender"
    }
}
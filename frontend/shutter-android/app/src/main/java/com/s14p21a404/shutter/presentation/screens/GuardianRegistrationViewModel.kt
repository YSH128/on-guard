package com.s14p21a404.shutter.presentation.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kakao.sdk.talk.TalkApiClient
import com.kakao.sdk.template.model.Button
import com.kakao.sdk.template.model.Link
import com.kakao.sdk.template.model.TextTemplate
import com.kakao.sdk.user.UserApiClient
import com.s14p21a404.shutter.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class KakaoFriendItem(val id: Long, val uuid: String, val name: String, val profileImageUrl: String? = null)

sealed class RegistrationUiState {
    object Idle : RegistrationUiState()
    object Loading : RegistrationUiState()
    object Success : RegistrationUiState()
    data class Error(val message: String) : RegistrationUiState()
}

data class GuardianRegistrationUiState(
    val friends: List<KakaoFriendItem> = emptyList(),
    val friendsLoading: Boolean = false,
    val friendsError: String? = null,
    val selectedIds: Set<Long> = emptySet(),
    val registrationState: RegistrationUiState = RegistrationUiState.Idle,
)

class GuardianRegistrationViewModel(application: Application) : AndroidViewModel(application) {

    private val addGuardianUseCase = ServiceLocator.getInstance(application).addGuardianUseCase
    private val guardianUuidStore = ServiceLocator.getInstance(application).guardianUuidStore

    private val _uiState = MutableStateFlow(GuardianRegistrationUiState())
    val uiState: StateFlow<GuardianRegistrationUiState> = _uiState.asStateFlow()

    fun loadKakaoFriends() {
        viewModelScope.launch {
            _uiState.update { it.copy(friendsLoading = true, friendsError = null) }
            loadFriendsFromKakao()
                .onSuccess { friends ->
                    _uiState.update { it.copy(friendsLoading = false, friends = friends) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            friendsLoading = false,
                            friendsError = e.message ?: "친구 목록을 불러오지 못했습니다."
                        )
                    }
                }
        }
    }

    private suspend fun loadFriendsFromKakao(): Result<List<KakaoFriendItem>> =
        suspendCancellableCoroutine { cont ->
            TalkApiClient.instance.friends { friends, error ->
                if (error != null) {
                    cont.resume(Result.failure(error))
                } else {
                    val items = friends?.elements
                        ?.mapNotNull { friend ->
                            val id = friend.id ?: return@mapNotNull null
                            val name = friend.profileNickname ?: return@mapNotNull null
                            KakaoFriendItem(id = id, uuid = friend.uuid, name = name, profileImageUrl = friend.profileThumbnailImage)
                        } ?: emptyList()
                    cont.resume(Result.success(items))
                }
            }
        }

    fun toggleFriendSelection(id: Long) {
        _uiState.update { state ->
            val selected = state.selectedIds
            state.copy(selectedIds = if (id in selected) selected - id else selected + id)
        }
    }

    fun registerSelectedFriends() {
        val selectedFriends = _uiState.value.friends.filter { it.id in _uiState.value.selectedIds }
        if (selectedFriends.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(registrationState = RegistrationUiState.Loading) }

            // 현재 유저 이름 조회
            val wardName = getCurrentUserName() ?: "알 수 없음"

            var lastError: String? = null
            for (friend in selectedFriends) {
                addGuardianUseCase(friend.id)
                    .onSuccess { guardian ->
                        guardianUuidStore.saveUuid(guardian.guardianId, friend.uuid)
                        guardianUuidStore.saveKakaoId(guardian.guardianId, friend.id)
                        guardianUuidStore.saveKakaoName(guardian.guardianId, friend.name)
                        friend.profileImageUrl?.let { guardianUuidStore.saveProfileImageUrl(guardian.guardianId, it) }
                        sendGuardianMessage(wardName, friend)
                    }
                    .onFailure { e -> lastError = e.message }
            }

            _uiState.update {
                it.copy(
                    registrationState = if (lastError != null)
                        RegistrationUiState.Error(lastError!!)
                    else
                        RegistrationUiState.Success
                )
            }
        }
    }

    private suspend fun getCurrentUserName(): String? = suspendCancellableCoroutine { cont ->
        UserApiClient.instance.me { user, error ->
            if (error != null) {
                Log.w(TAG, "유저 정보 조회 실패", error)
                cont.resume(null)
            } else {
                cont.resume(user?.kakaoAccount?.name)
            }
        }
    }

    private suspend fun sendGuardianMessage(wardName: String, guardian: KakaoFriendItem) =
        suspendCancellableCoroutine { cont ->
            val text = "${wardName}님이 ${guardian.name}님을 보호자로 등록하였습니다." +
                    "\n\n보호자 : ${guardian.name}\n피보호자 : ${wardName}"

            val deeplink = Link(androidExecutionParams = emptyMap())
            val template = TextTemplate(
                text = text,
                link = deeplink,
                buttons = listOf(Button(title = "Shutter 앱 열기", link = deeplink)),
            )

            TalkApiClient.instance.sendDefaultMessage(
                receiverUuids = listOf(guardian.uuid),
                template = template,
            ) { result, error ->
                if (error != null) {
                    Log.e(TAG, "메시지 발송 실패 (uuid: ${guardian.uuid})", error)
                } else {
                    Log.d(TAG, "메시지 발송 성공: ${result?.successfulReceiverUuids}")
                }
                cont.resume(Unit)
            }
        }

    fun resetRegistrationState() {
        _uiState.update { it.copy(registrationState = RegistrationUiState.Idle) }
    }

    companion object {
        private const val TAG = "GuardianRegistrationVM"
    }
}
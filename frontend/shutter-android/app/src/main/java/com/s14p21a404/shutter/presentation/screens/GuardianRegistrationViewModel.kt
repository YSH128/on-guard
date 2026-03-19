package com.s14p21a404.shutter.presentation.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kakao.sdk.talk.TalkApiClient
import com.s14p21a404.shutter.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class KakaoFriendItem(val id: Long, val name: String)

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
                            KakaoFriendItem(id = id, name = name)
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
        val selected = _uiState.value.selectedIds
        if (selected.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(registrationState = RegistrationUiState.Loading) }

            var lastError: String? = null
            for (kakaoId in selected) {
                addGuardianUseCase(kakaoId).onFailure { e -> lastError = e.message }
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

    fun resetRegistrationState() {
        _uiState.update { it.copy(registrationState = RegistrationUiState.Idle) }
    }
}

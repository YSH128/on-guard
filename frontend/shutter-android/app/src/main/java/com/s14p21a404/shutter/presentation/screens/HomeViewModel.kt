package com.s14p21a404.shutter.presentation.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kakao.sdk.talk.TalkApiClient
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ContactsUiState(
    val friends: List<KakaoFriendItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private val _myName = MutableStateFlow("")
    val myName: StateFlow<String> = _myName.asStateFlow()

    fun loadFriends() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            loadKakaoFriends()
                .onSuccess { friends ->
                    _uiState.update { it.copy(friends = friends, isLoading = false) }
                }
                .onFailure { e ->
                    Log.w(TAG, "카카오 친구 목록 조회 실패", e)
                    _uiState.update { it.copy(isLoading = false, error = "친구 목록을 불러오지 못했습니다.") }
                }
        }
    }

    fun loadMyProfile() {
        viewModelScope.launch {
            suspendCancellableCoroutine { cont ->
                UserApiClient.instance.me { user, error ->
                    if (error != null) {
                        Log.w(TAG, "내 프로필 조회 실패", error)
                        cont.resume(Unit)
                    } else {
                        _myName.value = user?.id?.toString() ?: ""
                        Log.d(TAG, "내 이름: ${_myName.value}")
                        cont.resume(Unit)
                    }
                }
            }
        }
    }

    fun getMyName(): String = _myName.value

    private suspend fun loadKakaoFriends(): Result<List<KakaoFriendItem>> =
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

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
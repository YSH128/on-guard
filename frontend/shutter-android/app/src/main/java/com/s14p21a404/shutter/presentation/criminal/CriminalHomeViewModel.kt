package com.s14p21a404.shutter.presentation.criminal

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kakao.sdk.talk.TalkApiClient
import com.kakao.sdk.user.UserApiClient
import com.s14p21a404.shutter.presentation.screens.KakaoFriendItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class CriminalHomeUiState(
    val friends: List<KakaoFriendItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val myKakaoId: String = "",
)

class CriminalHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CriminalHomeUiState())
    val uiState: StateFlow<CriminalHomeUiState> = _uiState.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            loadMyId()
            loadFriends()
        }
    }

    private suspend fun loadMyId() {
        suspendCancellableCoroutine { cont ->
            UserApiClient.instance.me { user, error ->
                if (error == null && user != null) {
                    _uiState.update { it.copy(myKakaoId = user.id?.toString() ?: "") }
                }
                cont.resume(Unit)
            }
        }
    }

    private suspend fun loadFriends() {
        suspendCancellableCoroutine { cont ->
            TalkApiClient.instance.friends { friends, error ->
                if (error != null) {
                    Log.w(TAG, "카카오 친구 목록 조회 실패", error)
                    _uiState.update { it.copy(isLoading = false, error = "친구 목록을 불러오지 못했습니다.") }
                } else {
                    val items = friends?.elements
                        ?.mapNotNull { friend ->
                            val id = friend.id ?: return@mapNotNull null
                            val name = friend.profileNickname ?: return@mapNotNull null
                            KakaoFriendItem(id = id, uuid = friend.uuid, name = name)
                        } ?: emptyList()
                    _uiState.update { it.copy(friends = items, isLoading = false) }
                }
                cont.resume(Unit)
            }
        }
    }

    /** 채널 ID: 내 ID와 상대방 ID를 정렬해서 결합 (타겟 앱과 동일한 규칙) */
    fun buildChannelId(friendId: Long): String {
        val myId = _uiState.value.myKakaoId
        return listOf(myId, friendId.toString()).sorted().joinToString("_")
    }

    companion object {
        private const val TAG = "CriminalHomeViewModel"
    }
}
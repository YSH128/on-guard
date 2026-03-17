package com.s14p21a404.shutter.presentation.screens

import androidx.lifecycle.ViewModel
import com.s14p21a404.shutter.domain.model.Guardian
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


/**
 * 환경 설정 화면 UI 상태
 */
data class SettingsUiState(
    val gpsSharing: Boolean = false,
    val guardians: List<Guardian> = emptyList(),
    /** 삭제 확인 다이얼로그에 표시할 보호자 (null이면 닫힘) */
    val guardianToDelete: Guardian? = null,
)

/**
 * 환경 설정 ViewModel
 */
class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            guardians = listOf(
                Guardian(id = "1", name = "김철수"),
                Guardian(id = "2", name = "김철수"),
                Guardian(id = "3", name = "김철수"),
                Guardian(id = "4", name = "김철수"),
            )
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** GPS 위치 공유 토글 */
    fun toggleGpsSharing() {
        _uiState.update { it.copy(gpsSharing = !it.gpsSharing) }
    }

    /** 삭제 모달 열기 */
    fun requestDeleteGuardian(guardian: Guardian) {
        _uiState.update { it.copy(guardianToDelete = guardian) }
    }

    /** 삭제 모달 닫기 */
    fun dismissDeleteDialog() {
        _uiState.update { it.copy(guardianToDelete = null) }
    }

    /** 보호자 삭제 확정 */
    fun confirmDeleteGuardian() {
        _uiState.update { state ->
            val target = state.guardianToDelete ?: return@update state
            state.copy(
                guardians = state.guardians.filter { it.id != target.id },
                guardianToDelete = null
            )
        }
    }

    /** 보호자 추가 (보호자 등록 화면에서 선택 후 돌아올 때) */
    fun addGuardians(newGuardians: List<Guardian>) {
        _uiState.update { state ->
            val existingIds = state.guardians.map { it.id }.toSet()
            val filtered = newGuardians.filter { it.id !in existingIds }
            state.copy(guardians = state.guardians + filtered)
        }
    }
}


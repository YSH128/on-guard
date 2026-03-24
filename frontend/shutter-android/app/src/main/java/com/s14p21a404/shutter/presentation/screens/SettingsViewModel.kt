package com.s14p21a404.shutter.presentation.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s14p21a404.shutter.di.ServiceLocator
import com.s14p21a404.shutter.domain.model.Guardian
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val gpsSharing: Boolean = false,
    val guardians: List<Guardian> = emptyList(),
    val guardianToDelete: Guardian? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val serviceLocator = ServiceLocator.getInstance(application)
    private val getMyGuardiansUseCase = serviceLocator.getMyGuardiansUseCase
    private val deleteGuardianUseCase = serviceLocator.deleteGuardianUseCase
    private val gpsRepository = serviceLocator.gpsRepository
    private val tokenManager = serviceLocator.tokenManager
    private val guardianUuidStore = serviceLocator.guardianUuidStore

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // SharedPreferences에 저장된 마지막 토글 상태 복원
        _uiState.update { it.copy(gpsSharing = tokenManager.getGpsSharing()) }
    }

    fun loadGuardians() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getMyGuardiansUseCase()
                .onSuccess { guardians ->
                    val displayGuardians = guardians.map { guardian ->
                        val kakaoName = guardianUuidStore.getKakaoName(guardian.guardianId)
                        if (kakaoName != null) guardian.copy(name = kakaoName) else guardian
                    }
                    _uiState.update { it.copy(guardians = displayGuardians, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun toggleGpsSharing() {
        val newState = !_uiState.value.gpsSharing
        viewModelScope.launch {
            gpsRepository.toggleGpsSharing(newState)
                .onSuccess { enabled ->
                    tokenManager.saveGpsSharing(enabled)
                    _uiState.update { it.copy(gpsSharing = enabled) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "GPS 공유 설정 변경에 실패했습니다.") }
                }
        }
    }

    fun requestDeleteGuardian(guardian: Guardian) {
        _uiState.update { it.copy(guardianToDelete = guardian) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(guardianToDelete = null) }
    }

    fun confirmDeleteGuardian() {
        val target = _uiState.value.guardianToDelete ?: return
        _uiState.update { it.copy(guardianToDelete = null) }
        viewModelScope.launch {
            deleteGuardianUseCase(target.relationshipId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            guardians = state.guardians.filter { it.relationshipId != target.relationshipId }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "삭제에 실패했습니다.") }
                }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
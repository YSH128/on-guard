package com.s14p21a404.shutter.presentation.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s14p21a404.shutter.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class RegistrationUiState {
    object Idle : RegistrationUiState()
    object Loading : RegistrationUiState()
    object Success : RegistrationUiState()
    data class Error(val message: String) : RegistrationUiState()
}

class GuardianRegistrationViewModel(application: Application) : AndroidViewModel(application) {

    private val addGuardianUseCase = ServiceLocator.getInstance(application).addGuardianUseCase

    private val _state = MutableStateFlow<RegistrationUiState>(RegistrationUiState.Idle)
    val state: StateFlow<RegistrationUiState> = _state.asStateFlow()

    fun registerByKakaoId(kakaoId: Long) {
        viewModelScope.launch {
            _state.value = RegistrationUiState.Loading
            addGuardianUseCase(kakaoId)
                .onSuccess { _state.value = RegistrationUiState.Success }
                .onFailure { e ->
                    _state.value = RegistrationUiState.Error(e.message ?: "보호자 등록에 실패했습니다.")
                }
        }
    }

    fun resetState() {
        _state.value = RegistrationUiState.Idle
    }
}
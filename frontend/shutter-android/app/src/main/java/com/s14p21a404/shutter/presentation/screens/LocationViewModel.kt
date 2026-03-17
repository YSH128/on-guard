package com.s14p21a404.shutter.presentation.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s14p21a404.shutter.di.ServiceLocator
import com.s14p21a404.shutter.domain.model.Ward
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LocationUiState {
    object Loading : LocationUiState()
    data class Success(val wards: List<Ward>) : LocationUiState()
    data class Error(val message: String) : LocationUiState()
}

class LocationViewModel(application: Application) : AndroidViewModel(application) {

    private val getMyWardsUseCase = ServiceLocator.getInstance(application).getMyWardsUseCase

    private val _uiState = MutableStateFlow<LocationUiState>(LocationUiState.Loading)
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    init {
        loadWards()
    }

    fun loadWards() {
        viewModelScope.launch {
            _uiState.value = LocationUiState.Loading
            getMyWardsUseCase()
                .onSuccess { wards -> _uiState.value = LocationUiState.Success(wards) }
                .onFailure { e ->
                    _uiState.value = LocationUiState.Error(e.message ?: "피보호자 목록 조회에 실패했습니다.")
                }
        }
    }
}
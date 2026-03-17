package com.s14p21a404.shutter.presentation.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s14p21a404.shutter.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    /** "전체" + 피보호자 이름 목록 */
    val filterNames: List<String> = listOf("전체"),
    val selectedFilter: String = "전체",
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val getMyWardsUseCase = ServiceLocator.getInstance(application).getMyWardsUseCase

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadWards()
    }

    fun loadWards() {
        viewModelScope.launch {
            getMyWardsUseCase()
                .onSuccess { wards ->
                    val names = listOf("전체") + wards.map { it.name }
                    _uiState.update { it.copy(filterNames = names) }
                }
                // 실패해도 "전체"만 표시, 조용히 처리
        }
    }

    fun selectFilter(name: String) {
        _uiState.update { it.copy(selectedFilter = name) }
    }
}
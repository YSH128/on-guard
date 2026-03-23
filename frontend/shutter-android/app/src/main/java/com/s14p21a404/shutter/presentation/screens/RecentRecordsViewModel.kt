package com.s14p21a404.shutter.presentation.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s14p21a404.shutter.di.ServiceLocator
import com.s14p21a404.shutter.network.TimelineItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WardFilter(
    val name: String,
    val wardId: Long?,  // null = 전체
)

data class TimelineEntry(
    val wardName: String,
    val item: TimelineItem,
)

data class RecentRecordsUiState(
    val wardFilters: List<WardFilter> = listOf(WardFilter("전체", null)),
    val selectedFilter: String = "전체",
    val timelineEntries: List<TimelineEntry> = emptyList(),
    val isLoading: Boolean = false,
)

class RecentRecordsViewModel(application: Application) : AndroidViewModel(application) {

    private val locator = ServiceLocator.getInstance(application)
    private val getMyWardsUseCase = locator.getMyWardsUseCase
    private val alertRepository = locator.alertRepository

    private val _uiState = MutableStateFlow(RecentRecordsUiState())
    val uiState: StateFlow<RecentRecordsUiState> = _uiState.asStateFlow()

    init {
        loadWards()
    }

    fun loadWards() {
        viewModelScope.launch {
            getMyWardsUseCase()
                .onSuccess { wards ->
                    val filters = listOf(WardFilter("전체", null)) +
                            wards.map { WardFilter(it.name, it.wardId) }
                    _uiState.update { it.copy(wardFilters = filters) }
                    loadTimeline()
                }
        }
    }

    fun selectFilter(name: String) {
        _uiState.update { it.copy(selectedFilter = name) }
        loadTimeline()
    }

    private fun loadTimeline() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val state = _uiState.value
            val wardsToFetch = if (state.selectedFilter == "전체") {
                state.wardFilters.filter { it.wardId != null }
            } else {
                state.wardFilters.filter { it.name == state.selectedFilter && it.wardId != null }
            }

            val entries = wardsToFetch
                .map { ward ->
                    async {
                        alertRepository.getTimeline(ward.wardId!!)
                            .getOrDefault(emptyList())
                            .map { item -> TimelineEntry(wardName = ward.name, item = item) }
                    }
                }
                .awaitAll()
                .flatten()
                .sortedByDescending { it.item.timestamp }

            _uiState.update { it.copy(timelineEntries = entries, isLoading = false) }
        }
    }
}
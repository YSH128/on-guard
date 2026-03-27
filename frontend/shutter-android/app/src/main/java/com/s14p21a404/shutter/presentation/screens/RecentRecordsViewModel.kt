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
import java.time.LocalDate

data class WardFilter(
    val name: String,
    val wardId: Long?,
)

data class TimelineEntry(
    val wardName: String,
    val item: TimelineItem,
)

data class SessionGroup(
    val sessionId: String,        // unique key: "wardName||callStartedAt"
    val wardName: String,
    val sessionDate: String,      // "오늘" or "MM월 DD일"
    val sessionTime: String,      // "HH:mm"
    val entries: List<TimelineEntry>,
)

data class RecentRecordsUiState(
    val wardFilters: List<WardFilter> = listOf(WardFilter("전체", null)),
    val selectedFilter: String = "전체",
    val sessions: List<SessionGroup> = emptyList(),
    val expandedSessions: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
)

class RecentRecordsViewModel(application: Application) : AndroidViewModel(application) {

    private val locator = ServiceLocator.getInstance(application)
    private val getMyWardsUseCase = locator.getMyWardsUseCase
    private val alertRepository = locator.alertRepository
    private val tokenManager = locator.tokenManager

    private val _uiState = MutableStateFlow(RecentRecordsUiState())
    val uiState: StateFlow<RecentRecordsUiState> = _uiState.asStateFlow()

    init {
        loadWards()
    }

    fun loadWards() {
        viewModelScope.launch {
            getMyWardsUseCase()
                .onSuccess { wards ->
                    val myUserId = tokenManager.getUserId()
                    val filters = listOf(WardFilter("전체", null), WardFilter("나", myUserId)) +
                            wards.map { WardFilter(it.name, it.wardId) }
                    _uiState.update { it.copy(wardFilters = filters) }
                    loadTimeline()
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            getMyWardsUseCase()
                .onSuccess { wards ->
                    val myUserId = tokenManager.getUserId()
                    val filters = listOf(WardFilter("전체", null), WardFilter("나", myUserId)) +
                            wards.map { WardFilter(it.name, it.wardId) }
                    _uiState.update { it.copy(wardFilters = filters) }
                }
            loadTimeline()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun selectFilter(name: String) {
        _uiState.update { it.copy(selectedFilter = name) }
        loadTimeline()
    }

    fun toggleSession(sessionId: String) {
        _uiState.update { state ->
            val expanded = state.expandedSessions.toMutableSet()
            if (sessionId in expanded) expanded.remove(sessionId) else expanded.add(sessionId)
            state.copy(expandedSessions = expanded)
        }
    }

    private fun loadTimeline() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val state = _uiState.value
            val wardsToFetch = if (state.selectedFilter == "전체") {
                state.wardFilters.filter { it.wardId != null && it.wardId > 0 }
            } else {
                state.wardFilters.filter { it.name == state.selectedFilter && it.wardId != null && it.wardId > 0 }
            }

            val allEntries = wardsToFetch
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

            val today = LocalDate.now()

            // 같은 callStartedAt을 공유하는 entries = 하나의 통화 세션
            val sessions = allEntries
                .groupBy { entry ->
                    val sessionKey = entry.item.callStartedAt ?: entry.item.timestamp.take(16)
                    "${entry.wardName}||$sessionKey"
                }
                .entries
                .sortedByDescending { (_, entries) ->
                    entries.first().item.callStartedAt ?: entries.first().item.timestamp
                }
                .map { (key, entries) ->
                    val first = entries.first()
                    val rawStart = first.item.callStartedAt ?: first.item.timestamp
                    val date = try { LocalDate.parse(rawStart.take(10)) } catch (e: Exception) { today }
                    val dateLabel = if (date == today) "오늘" else "${date.monthValue}월 ${date.dayOfMonth}일"
                    val timeLabel = rawStart.take(16).drop(11)

                    SessionGroup(
                        sessionId = key,
                        wardName = first.wardName,
                        sessionDate = dateLabel,
                        sessionTime = timeLabel,
                        entries = entries.sortedBy { it.item.timestamp },
                    )
                }

            _uiState.update {
                val firstSessionId = sessions.firstOrNull()?.sessionId
                it.copy(
                    sessions = sessions,
                    isLoading = false,
                    expandedSessions = if (firstSessionId != null) setOf(firstSessionId) else emptySet(),
                )
            }
        }
    }
}
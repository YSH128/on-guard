package com.s14p21a404.shutter.presentation.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s14p21a404.shutter.di.ServiceLocator
import com.s14p21a404.shutter.network.GpsWebSocketEvent
import com.s14p21a404.shutter.presentation.service.LocationReloadBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WardWithLocation(
    val wardId: Long,
    val name: String,
    val lat: Double?,
    val lng: Double?,
    val updatedAt: String?,
    val isSharing: Boolean,
    val profileImageUrl: String? = null,
)

sealed class LocationUiState {
    object Loading : LocationUiState()
    data class Success(val wards: List<WardWithLocation>) : LocationUiState()
    data class Error(val message: String) : LocationUiState()
}

class LocationViewModel(application: Application) : AndroidViewModel(application) {

    private val serviceLocator = ServiceLocator.getInstance(application)
    private val getMyWardsUseCase = serviceLocator.getMyWardsUseCase
    private val gpsRepository = serviceLocator.gpsRepository
    private val gpsWebSocketClient = serviceLocator.gpsWebSocketClient

    private val _uiState = MutableStateFlow<LocationUiState>(LocationUiState.Loading)
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    init {
        loadWards()
        observeWebSocket()
        LocationReloadBridge.onLocationAlertReceived = { loadWards() }
    }

    fun loadWards() {
        viewModelScope.launch {
            _uiState.value = LocationUiState.Loading

            getMyWardsUseCase()
                .onFailure { e ->
                    _uiState.value = LocationUiState.Error(e.message ?: "피보호자 목록 조회에 실패했습니다.")
                    return@launch
                }
                .onSuccess { wards ->
                    if (wards.isEmpty()) {
                        _uiState.value = LocationUiState.Success(emptyList())
                        return@launch
                    }

                    // 각 피보호자의 GPS 초기 위치 조회
                    val wardLocations = wards.map { ward ->
                        gpsRepository.getWardGps(ward.wardId)
                            .getOrNull()
                            ?.let { gps ->
                                WardWithLocation(
                                    wardId = gps.wardId,
                                    name = gps.name,
                                    lat = gps.lat,
                                    lng = gps.lng,
                                    updatedAt = gps.updatedAt,
                                    isSharing = gps.isSharing,
                                    profileImageUrl = ward.profileImageUrl,
                                )
                            } ?: WardWithLocation(
                                wardId = ward.wardId,
                                name = ward.name,
                                lat = null,
                                lng = null,
                                updatedAt = null,
                                isSharing = false,
                                profileImageUrl = ward.profileImageUrl,
                            )
                    }

                    _uiState.value = LocationUiState.Success(wardLocations)

                    // isSharing: true인 피보호자가 있을 때만 WebSocket 연결
                    val sharingWardIds = wardLocations
                        .filter { it.isSharing }
                        .map { it.wardId }
                    if (sharingWardIds.isNotEmpty()) {
                        gpsWebSocketClient.connect(sharingWardIds)
                    }
                }
        }
    }

    private fun observeWebSocket() {
        viewModelScope.launch {
            gpsWebSocketClient.events.collect { event ->
                when (event) {
                    is GpsWebSocketEvent.LocationUpdate -> {
                        Log.d(TAG, "위치 업데이트: wardId=${event.wardId}")
                        updateWardLocation(
                            wardId = event.wardId,
                            lat = event.lat,
                            lng = event.lng,
                            updatedAt = event.updatedAt,
                            isSharing = event.isSharing,
                        )
                    }
                    is GpsWebSocketEvent.LocationExpired -> {
                        Log.d(TAG, "위치 공유 만료: wardId=${event.wardId}")
                        updateWardLocation(
                            wardId = event.wardId,
                            lat = null,
                            lng = null,
                            updatedAt = null,
                            isSharing = false,
                        )
                        // 공유 중인 피보호자가 더 이상 없으면 WebSocket 닫기
                        val current = _uiState.value as? LocationUiState.Success
                        val stillSharing = current?.wards?.any { it.wardId != event.wardId && it.isSharing } ?: false
                        if (!stillSharing) {
                            Log.d(TAG, "모든 위치 공유 종료 — WebSocket 연결 해제")
                            gpsWebSocketClient.disconnect()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun updateWardLocation(
        wardId: Long,
        lat: Double?,
        lng: Double?,
        updatedAt: String?,
        isSharing: Boolean,
    ) {
        val current = _uiState.value as? LocationUiState.Success ?: return
        _uiState.update {
            LocationUiState.Success(
                current.wards.map { ward ->
                    if (ward.wardId == wardId) {
                        ward.copy(lat = lat, lng = lng, updatedAt = updatedAt, isSharing = isSharing)
                    } else ward
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        gpsWebSocketClient.disconnect()
        LocationReloadBridge.onLocationAlertReceived = null
    }

    companion object {
        private const val TAG = "LocationViewModel"
    }
}

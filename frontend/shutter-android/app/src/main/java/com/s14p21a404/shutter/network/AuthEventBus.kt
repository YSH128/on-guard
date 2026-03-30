package com.s14p21a404.shutter.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 네트워크 레이어에서 세션 만료를 UI 레이어로 전달하는 이벤트 버스.
 * Interceptor/Authenticator → ViewModel 방향으로만 사용한다.
 */
object AuthEventBus {
    private val _logoutEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logoutEvent = _logoutEvent.asSharedFlow()

    fun emitLogout() {
        _logoutEvent.tryEmit(Unit)
    }
}

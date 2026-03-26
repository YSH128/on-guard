package com.s14p21a404.shutter.push

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class IncomingCallEvent(
    val channelName: String,
    val callerName: String
)

object IncomingCallEventBus {

    private val _callRejected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val callRejected = _callRejected.asSharedFlow()

    fun notifyCallRejected() {
        _callRejected.tryEmit(Unit)
    }

    private val _callCanceled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val callCanceled = _callCanceled.asSharedFlow()

    fun notifyCallCanceled() {
        _callCanceled.tryEmit(Unit)
    }
}
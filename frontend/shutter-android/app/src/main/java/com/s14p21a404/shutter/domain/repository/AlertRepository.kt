package com.s14p21a404.shutter.domain.repository

import com.s14p21a404.shutter.network.AlertSendItem
import com.s14p21a404.shutter.network.TimelineItem

interface AlertRepository {

    suspend fun sendDeepfakeAlert(
        callStartedAt: String,
    ): Result<List<AlertSendItem>>

    suspend fun sendLocationAlert(): Result<List<AlertSendItem>>

    suspend fun getTimeline(wardId: Long): Result<List<TimelineItem>>
}
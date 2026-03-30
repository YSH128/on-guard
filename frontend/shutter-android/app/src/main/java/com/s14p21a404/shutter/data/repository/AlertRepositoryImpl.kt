package com.s14p21a404.shutter.data.repository

import com.s14p21a404.shutter.domain.repository.AlertRepository
import com.s14p21a404.shutter.network.AlertSendItem
import com.s14p21a404.shutter.network.AlertSendRequest
import com.s14p21a404.shutter.network.ApiService
import com.s14p21a404.shutter.network.TimelineItem

class AlertRepositoryImpl(private val apiService: ApiService) : AlertRepository {

    override suspend fun sendDeepfakeAlert(
        callStartedAt: String,
    ): Result<List<AlertSendItem>> = runCatching {
        val response = apiService.sendAlert(
            AlertSendRequest(
                eventType = "DEEPFAKE",
                callStartedAt = callStartedAt,
            )
        )
        check(response.success) { response.message }
        response.data
    }

    override suspend fun sendLocationAlert(): Result<List<AlertSendItem>> = runCatching {
        val response = apiService.sendAlert(AlertSendRequest(eventType = "LOCATION"))
        check(response.success) { response.message }
        response.data
    }

    override suspend fun getTimeline(wardId: Long): Result<List<TimelineItem>> = runCatching {
        val response = apiService.getTimeline(wardId)
        check(response.success) { response.message }
        response.data.timeline
    }
}
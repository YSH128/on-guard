package com.s14p21a404.shutter.network

import com.s14p21a404.shutter.data.token.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val apiService: ApiService,
) : Authenticator {

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        // 무한루프 방지: 이미 2회 이상 시도했으면 중단
        if (responseCount(response) >= 2) return null

        // 다른 스레드가 이미 토큰을 갱신했는지 확인
        val currentToken = tokenManager.getAccessToken()
        val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        if (currentToken != null && currentToken != requestToken) {
            // 이미 갱신된 토큰으로 재시도
            return response.request.newBuilder()
                .header("Authorization", "Bearer $currentToken")
                .build()
        }

        val refreshToken = tokenManager.getRefreshToken() ?: run {
            tokenManager.clearTokens()
            return null
        }

        return try {
            val refreshResponse = runBlocking {
                apiService.refreshToken(RefreshRequest(refreshToken))
            }
            if (!refreshResponse.success) {
                tokenManager.clearTokens()
                return null
            }
            val newAccessToken = refreshResponse.data
            tokenManager.saveAccessToken(newAccessToken)
            response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        } catch (e: Exception) {
            tokenManager.clearTokens()
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
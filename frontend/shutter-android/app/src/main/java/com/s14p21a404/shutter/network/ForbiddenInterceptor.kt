package com.s14p21a404.shutter.network

import android.util.Log
import com.s14p21a404.shutter.data.token.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 403 Forbidden 응답을 받으면 토큰 갱신 후 원래 요청을 재시도한다.
 * OkHttp Authenticator 는 401만 처리하므로, 403을 처리하기 위해 별도 Interceptor로 구현한다.
 */
class ForbiddenInterceptor(
    private val tokenManager: TokenManager,
    private val refreshApiService: ApiService,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != 403) return response

        return synchronized(this) {
            // 다른 스레드가 이미 토큰을 갱신했는지 확인
            val currentToken = tokenManager.getAccessToken()
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (currentToken != null && currentToken != requestToken) {
                response.close()
                return@synchronized chain.proceed(
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                )
            }

            val refreshToken = tokenManager.getRefreshToken()
            if (refreshToken == null) {
                Log.w(TAG, "403 수신 — 리프레시 토큰 없음, 강제 로그아웃")
                tokenManager.clearTokens()
                AuthEventBus.emitLogout()
                return@synchronized response
            }

            try {
                val refreshResponse = runBlocking {
                    refreshApiService.refreshToken(RefreshRequest(refreshToken))
                }
                if (!refreshResponse.success) {
                    Log.w(TAG, "403 수신 — 토큰 갱신 실패: ${refreshResponse.message}, 강제 로그아웃")
                    tokenManager.clearTokens()
                    AuthEventBus.emitLogout()
                    return@synchronized response
                }
                val newToken = refreshResponse.data
                tokenManager.saveAccessToken(newToken)
                Log.d(TAG, "403 수신 — 토큰 갱신 성공, 요청 재시도")
                response.close()
                chain.proceed(
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                )
            } catch (e: Exception) {
                Log.e(TAG, "403 수신 — 토큰 갱신 중 예외 발생, 강제 로그아웃", e)
                tokenManager.clearTokens()
                AuthEventBus.emitLogout()
                response
            }
        }
    }

    companion object {
        private const val TAG = "ForbiddenInterceptor"
    }
}
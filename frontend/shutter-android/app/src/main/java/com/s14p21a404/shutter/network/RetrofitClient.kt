package com.s14p21a404.shutter.network

import com.s14p21a404.shutter.BuildConfig
import com.s14p21a404.shutter.data.token.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    /** 인증이 필요한 텔레메트리 요청을 위한 클라이언트 생성기 */
    fun createTelemetryApi(tokenManager: TokenManager): TelemetryApi {
        val authInterceptor = Interceptor { chain ->
            val token = tokenManager.getAccessToken()
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
            
        return Retrofit.Builder()
            .baseUrl(BuildConfig.SERVER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TelemetryApi::class.java)
    }

    /** 인증이 필요한 API 요청에 Bearer 토큰을 자동으로 붙여주는 인터셉터 */
    fun create(tokenManager: TokenManager): ApiService {
        // 토큰 갱신 전용 클라이언트 — Authenticator 없이 단순 호출만 수행 (무한루프 방지)
        val refreshApiService = Retrofit.Builder()
            .baseUrl(BuildConfig.SERVER_BASE_URL)
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)

        val authInterceptor = Interceptor { chain ->
            val token = tokenManager.getAccessToken()
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(ForbiddenInterceptor(tokenManager, refreshApiService))
            .addInterceptor(loggingInterceptor)
            .authenticator(TokenAuthenticator(tokenManager, refreshApiService))
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.SERVER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
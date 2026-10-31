package com.shutter.api.global.auth.kakao;

import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 카카오 API 호출 클라이언트
 *
 * 역할: 앱에서 받은 카카오 Access Token의 유효성을 검증하고
 * 카카오 유저 정보(kakaoId, 닉네임 등) 및 토큰 정보(appId 등)를 조회한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoApiClient {

    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";
    private static final String KAKAO_TOKEN_INFO_URL = "https://kapi.kakao.com/v1/user/access_token_info";

    private final WebClient webClient;

    /**
     * 카카오 Access Token으로 유저 정보 조회
     *
     * @param accessToken 앱에서 전달받은 카카오 Access Token
     * @return 카카오 유저 정보 (kakaoId, 닉네임 등)
     * @throws BusinessException 토큰이 유효하지 않을 경우 INVALID_KAKAO_TOKEN
     */
    public KakaoUserInfoResponse getUserInfo(String accessToken) {
        return webClient.get()
                .uri(KAKAO_USER_INFO_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    log.warn("Invalid Kakao access token. status={}", response.statusCode());
                    return response.createException().map(e ->
                            new BusinessException(ErrorCode.INVALID_KAKAO_TOKEN));
                })
                .bodyToMono(KakaoUserInfoResponse.class)
                .block();
    }

    /**
     * 카카오 Access Token 정보 조회 (app_id 교차 검증용)
     *
     * @param accessToken 카카오 Access Token
     * @return 토큰 정보 (app_id, expires_in 등)
     * @throws BusinessException 토큰이 유효하지 않을 경우 INVALID_KAKAO_TOKEN
     */
    public KakaoTokenInfoResponse getTokenInfo(String accessToken) {
        return webClient.get()
                .uri(KAKAO_TOKEN_INFO_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    log.warn("Invalid Kakao access token info. status={}", response.statusCode());
                    return response.createException().map(e ->
                            new BusinessException(ErrorCode.INVALID_KAKAO_TOKEN));
                })
                .bodyToMono(KakaoTokenInfoResponse.class)
                .block();
    }
}

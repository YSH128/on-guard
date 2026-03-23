package com.shutter.api.notification.client;

import com.shutter.api.notification.domain.NotificationEventType;
import com.shutter.api.notification.domain.PushResult;
import com.shutter.api.notification.dto.request.KakaoPushRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * 카카오 푸시 알림 REST API 클라이언트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoPushClient {

    private final WebClient webClient;

    @Value("${kakao.admin-key}")
    private String adminKey;

    private static final String KAKAO_PUSH_SEND_URL = "https://kapi.kakao.com/v2/push/send";

    /**
     * 카카오 푸시 알림 전송
     *
     * @param kakaoId 보호자의 카카오 식별자
     * @param eventType 알림 이벤트 타입 (DEEPFAKE, MESSAGE, LOCATION)
     * @param message 알림 본문 메시지
     * @return 성공 여부 (HTTP 200 시 true)
     */
    public PushResult sendPush(Long kakaoId, NotificationEventType eventType, String message) {
        try {
            KakaoPushRequest request = KakaoPushRequest.builder()
                    .kakaoId(String.valueOf(kakaoId))
                    .eventType(eventType.name())
                    .message(message)
                    .build();

            webClient.post()
                    .uri(KAKAO_PUSH_SEND_URL)
                    .header("Authorization", "KakaoAK " + adminKey)
                    .bodyValue(request)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));

            log.info("Successfully sent Kakao push to kakaoId: {}", kakaoId);
            return PushResult.SUCCESS;
        } catch (WebClientResponseException e) {
            log.error("Failed to send Kakao push to kakaoId: {}. Status: {}, Body: {}", 
                    kakaoId, e.getStatusCode(), e.getResponseBodyAsString());
            
            if (e.getStatusCode().is4xxClientError()) {
                // 401(Unauthorized), 403(Forbidden) -> 설정 오류 가능성 높음
                if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                    return PushResult.CONFIG_ERROR;
                }
                return PushResult.DELIVERY_FAILURE;
            }
            return PushResult.UNKNOWN_ERROR;
        } catch (Exception e) {
            log.error("Unexpected error sending Kakao push to kakaoId: {}. Error: {}", kakaoId, e.getMessage());
            return PushResult.UNKNOWN_ERROR;
        }
    }
}

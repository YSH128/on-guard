package com.shutter.api.notification.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutter.api.notification.domain.NotificationEventType;
import com.shutter.api.notification.domain.PushResult;
import com.shutter.api.notification.dto.request.KakaoPushRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * 카카오 푸시 알림 REST API 클라이언트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoPushClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

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

            // 카카오 푸시 V2 API는 application/x-www-form-urlencoded 형식을 사용하며
            // 각 파라미터(uuids, push_message)는 JSON 문자열로 전달해야 함
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("uuids", objectMapper.writeValueAsString(Collections.singletonList(String.valueOf(kakaoId))));
            formData.add("push_message", objectMapper.writeValueAsString(request.getPushMessage()));

            webClient.post()
                    .uri(KAKAO_PUSH_SEND_URL)
                    .header("Authorization", "KakaoAK " + adminKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));

            log.info("Successfully sent Kakao push to kakaoId: {}", kakaoId);
            return PushResult.SUCCESS;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize push request for kakaoId: {}. Error: {}", kakaoId, e.getMessage());
            return PushResult.UNKNOWN_ERROR;
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

    /**
     * 데이터 전용(사일런트) 카카오 푸시 전송 — notification 필드 없이 custom_field만 포함
     *
     * @param kakaoId 수신자 카카오 식별자
     * @param data    custom_field 에 담을 키-값 쌍
     * @return 전송 결과
     */
    public PushResult sendDataPush(Long kakaoId, Map<String, String> data) {
        try {
            Map<String, Object> forFcm = Map.of("custom_field", data);
            Map<String, Object> pushMessage = Map.of("for_fcm", forFcm);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("uuids", objectMapper.writeValueAsString(Collections.singletonList(String.valueOf(kakaoId))));
            formData.add("push_message", objectMapper.writeValueAsString(pushMessage));

            webClient.post()
                    .uri(KAKAO_PUSH_SEND_URL)
                    .header("Authorization", "KakaoAK " + adminKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(10));

            log.info("Successfully sent data-only Kakao push to kakaoId: {}", kakaoId);
            return PushResult.SUCCESS;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize data push for kakaoId: {}. Error: {}", kakaoId, e.getMessage());
            return PushResult.UNKNOWN_ERROR;
        } catch (WebClientResponseException e) {
            log.error("Failed to send data push to kakaoId: {}. Status: {}, Body: {}",
                    kakaoId, e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                return PushResult.CONFIG_ERROR;
            }
            return PushResult.DELIVERY_FAILURE;
        } catch (Exception e) {
            log.error("Unexpected error sending data push to kakaoId: {}. Error: {}", kakaoId, e.getMessage());
            return PushResult.UNKNOWN_ERROR;
        }
    }
}

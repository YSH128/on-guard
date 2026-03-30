package com.shutter.api.notification.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 카카오 푸시 알림 전송 요청 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KakaoPushRequest {

    private List<String> uuids;

    @JsonProperty("push_message")
    private PushMessage pushMessage;

    @Builder
    public KakaoPushRequest(String kakaoId, String title, String message, String eventType) {
        this.uuids = Collections.singletonList(kakaoId);
        this.pushMessage = PushMessage.builder()
                .title(title)
                .message(message)
                .eventType(eventType)
                .build();
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class PushMessage {
        @JsonProperty("for_apns")
        private ApnsMessage forApns;

        @JsonProperty("for_fcm")
        private FcmMessage forFcm;

        @Builder
        public PushMessage(String title, String message, String eventType) {
            this.forApns = ApnsMessage.builder()
                    .message(message)
                    .build();
            this.forFcm = FcmMessage.builder()
                    .title(title)
                    .message(message)
                    .eventType(eventType)
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class ApnsMessage {
        private String message;
        private String sound = "default";
        private Integer badge = 1;

        @Builder
        public ApnsMessage(String message) {
            this.message = message;
        }
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class FcmMessage {
        private FcmNotification notification;

        @JsonProperty("custom_field")
        private Map<String, String> customField;

        @Builder
        public FcmMessage(String title, String message, String eventType) {
            this.notification = new FcmNotification(title, message);
            this.customField = Map.of("event_type", eventType);
        }
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class FcmNotification {
        private String title;
        private String body;

        public FcmNotification(String title, String body) {
            this.title = (title != null && !title.isBlank()) ? title : "OnGuard 긴급 알림";
            this.body = body;
        }
    }
}

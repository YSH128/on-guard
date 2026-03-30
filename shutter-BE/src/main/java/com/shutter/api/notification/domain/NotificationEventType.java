package com.shutter.api.notification.domain;

public enum NotificationEventType {
    DEEPFAKE("MESSAGE", "자동 발송", "%s님이 딥페이크 의심 통화 중입니다."),
    VOICE_PHISHING("MESSAGE", "피싱 주의", "%s님이 보이스피싱 의심 통화 중입니다."),
    LOCATION("LOCATION", "위치 공유", "%s님의 위치 정보 공유가 시작되었습니다."),
    MESSAGE("MESSAGE", "자동 발송", "%s님에게 알림이 발생했습니다.");

    private final String timelineType;
    private final String timelineTag;
    private final String pushMessageTemplate;

    NotificationEventType(String timelineType, String timelineTag, String pushMessageTemplate) {
        this.timelineType = timelineType;
        this.timelineTag = timelineTag;
        this.pushMessageTemplate = pushMessageTemplate;
    }

    public String getTimelineType() {
        return timelineType;
    }

    public String getTimelineTag() {
        return timelineTag;
    }

    public String getPushMessage(String wardName) {
        return String.format(pushMessageTemplate, wardName);
    }

    public String getPushMessage(String wardName, String phishingType) {
        if (phishingType != null && !phishingType.isBlank()) {
            return switch (phishingType) {
                case "금융거래" -> "금융거래 위험 감지";
                case "특정장소유인" -> "납치·유인 위험 감지";
                case "개인정보요구" -> "개인정보 유출 감지";
                case "일상대화" -> "위험은 없으나 주의 필요";
                default -> {
                    if (this == VOICE_PHISHING) {
                        yield String.format("%s님이 보이스피싱(%s) 의심 통화 중입니다.", wardName, phishingType);
                    }
                    yield getPushMessage(wardName);
                }
            };
        }
        return getPushMessage(wardName);
    }
}

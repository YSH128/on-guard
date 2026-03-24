package com.shutter.api.notification.domain;

public enum NotificationEventType {
    DEEPFAKE("MESSAGE", "자동 발송", "%s님이 딥페이크 의심 통화 중입니다."),
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
}

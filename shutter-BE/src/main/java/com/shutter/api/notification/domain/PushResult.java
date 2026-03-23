package com.shutter.api.notification.domain;

/**
 * 푸시 전송 결과 상태
 */
public enum PushResult {
    SUCCESS,            // 전송 성공
    CONFIG_ERROR,       // 설정 오류 (Admin Key, 가이드 미준수 등)
    DELIVERY_FAILURE,   // 전송 실패 (잘못된 사용자, 토큰 만료 등)
    UNKNOWN_ERROR       // 기타 알 수 없는 오류
}

package com.shutter.api.global.exception;

import lombok.Getter;

/**
 * 알림(Alert) 도메인 관련 예외
 */
@Getter
public class AlertException extends BusinessException {

    public AlertException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AlertException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}

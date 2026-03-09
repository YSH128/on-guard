package com.shutter.api.global.exception;

import lombok.Getter;

/**
 * 비즈니스 로직 예외
 *
 * ErrorCode를 받아 HTTP 상태코드와 메시지를 일관되게 처리한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}

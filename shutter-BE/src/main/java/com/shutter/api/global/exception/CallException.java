package com.shutter.api.global.exception;

import lombok.Getter;

@Getter
public class CallException extends RuntimeException {

    private final ErrorCode errorCode;

    public CallException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

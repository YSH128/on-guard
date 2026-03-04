package com.shutter.api.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_PAYLOAD(HttpStatus.BAD_REQUEST, "Invalid telemetry request"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Telemetry processing failed.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
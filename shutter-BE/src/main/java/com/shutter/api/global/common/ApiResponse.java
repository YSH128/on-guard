package com.shutter.api.global.common;

public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> of(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }
}
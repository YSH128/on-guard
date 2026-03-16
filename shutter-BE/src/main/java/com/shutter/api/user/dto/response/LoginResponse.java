package com.shutter.api.user.dto.response;

public record LoginResponse(
        Long userId,
        String accessToken,
        String refreshToken
) {}

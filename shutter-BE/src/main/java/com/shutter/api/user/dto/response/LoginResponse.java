package com.shutter.api.user.dto.response;

public record LoginResponse(
        String accessToken,
        String refreshToken
) {}

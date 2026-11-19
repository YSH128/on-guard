package com.shutter.api.user.dto.response;

public record TokenRefreshResult(
        String newAccessToken,
        String newRefreshToken
) {
}

package com.shutter.api.user.dto.response;

public record WardResponse(
        Long wardId,
        String name,
        String profileImageUrl,
        Double lat,
        Double lng,
        String gpsUpdatedAt
) {
}
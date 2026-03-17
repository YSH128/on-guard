package com.shutter.api.user.dto.response;

import java.time.LocalDateTime;

public record GuardianResponse(
        Long relationshipId,
        Long guardianId,
        String guardianName,
        LocalDateTime createdAt
) {
}
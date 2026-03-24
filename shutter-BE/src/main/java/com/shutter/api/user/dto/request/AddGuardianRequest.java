package com.shutter.api.user.dto.request;

import jakarta.validation.constraints.NotNull;

public record AddGuardianRequest(
        @NotNull Long guardianKakaoId
) {
}
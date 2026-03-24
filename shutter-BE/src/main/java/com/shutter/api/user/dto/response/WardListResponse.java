package com.shutter.api.user.dto.response;

import java.util.List;

public record WardListResponse(
        List<WardResponse> wards
) {
}
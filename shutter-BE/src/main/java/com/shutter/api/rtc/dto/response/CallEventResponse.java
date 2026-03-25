package com.shutter.api.rtc.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallEventResponse {
    private String type; // CALL_ACCEPTED, CALL_REJECTED
    private String channelName;
}

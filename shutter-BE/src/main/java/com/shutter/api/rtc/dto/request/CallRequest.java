package com.shutter.api.rtc.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CallRequest {
    
    @JsonProperty("callee_kakao_id")
    private Long calleeKakaoId;
}

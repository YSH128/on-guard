package com.shutter.api.rtc.controller;

import com.shutter.api.global.common.ApiResponse;
import com.shutter.api.rtc.dto.request.CallRequest;
import com.shutter.api.rtc.dto.response.CallResponse;
import com.shutter.api.rtc.service.CallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/calls")
public class CallController {

    private final CallService callService;

    @PostMapping
    public ApiResponse<CallResponse> requestCall(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CallRequest request) {

        CallResponse response = callService.requestCall(userId, request);
        return ApiResponse.of("성공적으로 통화 요청이 전송되었습니다.", response);
    }

    @PostMapping("/{channelName}/accept")
    public ApiResponse<CallResponse> acceptCall(
            @AuthenticationPrincipal Long userId,
            @PathVariable("channelName") String channelName) {
        
        CallResponse response = callService.acceptCall(userId, channelName);
        return ApiResponse.of("통화 수락이 완료되었습니다.", response);
    }

    @PostMapping("/{channelName}/reject")
    public ApiResponse<Void> rejectCall(
            @AuthenticationPrincipal Long userId,
            @PathVariable("channelName") String channelName) {
        
        callService.rejectCall(userId, channelName);
        return ApiResponse.of("통화 거절 처리가 완료되었습니다.", null);
    }
}

package com.shutter.api.user.controller;

import com.shutter.api.global.common.ApiResponse;
import com.shutter.api.user.dto.request.AddGuardianRequest;
import com.shutter.api.user.dto.response.DeleteRelationshipResponse;
import com.shutter.api.user.dto.response.GuardianListResponse;
import com.shutter.api.user.dto.response.GuardianResponse;
import com.shutter.api.user.dto.response.WardListResponse;
import com.shutter.api.user.service.GuardianService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/guardians")
@RequiredArgsConstructor
public class GuardianController {

    private final GuardianService guardianService;

    @PostMapping
    public ResponseEntity<ApiResponse<GuardianResponse>> addGuardian(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AddGuardianRequest request
    ) {
        GuardianResponse response = guardianService.addGuardian(userId, request);
        return ResponseEntity.ok(ApiResponse.of("보호자 등록 성공", response));
    }

    @GetMapping("/wards/me")
    public ResponseEntity<ApiResponse<WardListResponse>> getMyWards(
            @AuthenticationPrincipal Long userId
    ) {
        WardListResponse response = guardianService.getMyWards(userId);
        return ResponseEntity.ok(ApiResponse.of("피보호자 목록 조회 성공", response));
    }

    @DeleteMapping("/{relationshipId}")
    public ResponseEntity<ApiResponse<DeleteRelationshipResponse>> deleteRelationship(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long relationshipId
    ) {
        DeleteRelationshipResponse response = guardianService.deleteRelationship(userId, relationshipId);
        return ResponseEntity.ok(ApiResponse.of("보호자 관계 해제 성공", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<GuardianListResponse>> getMyGuardians(
            @AuthenticationPrincipal Long userId
    ) {
        GuardianListResponse response = guardianService.getMyGuardians(userId);
        return ResponseEntity.ok(ApiResponse.of("보호자 목록 조회 성공", response));
    }
}
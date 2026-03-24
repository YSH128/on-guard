package com.shutter.api.user.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutter.api.global.auth.token.TokenRepository;
import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.user.domain.User;
import com.shutter.api.user.domain.UserRelationship;
import com.shutter.api.user.dto.request.AddGuardianRequest;
import com.shutter.api.user.dto.response.DeleteRelationshipResponse;
import com.shutter.api.user.dto.response.GuardianListResponse;
import com.shutter.api.user.dto.response.GuardianResponse;
import com.shutter.api.user.dto.response.WardListResponse;
import com.shutter.api.user.dto.response.WardResponse;
import com.shutter.api.user.repository.UserRelationshipRepository;
import com.shutter.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardianService {

    private final UserRepository userRepository;
    private final UserRelationshipRepository relationshipRepository;
    private final TokenRepository tokenRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public GuardianResponse addGuardian(Long wardId, AddGuardianRequest request) {
        User guardian = userRepository.findByKakaoId(request.guardianKakaoId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GUARDIAN_NOT_FOUND));
        User ward = userRepository.findById(wardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UserRelationship relationship = UserRelationship.builder()
                .guardian(guardian)
                .ward(ward)
                .build();
        relationship = relationshipRepository.save(relationship);

        return new GuardianResponse(
                relationship.getId(),
                guardian.getId(),
                guardian.getName(),
                relationship.getCreatedAt()
        );
    }

    public WardListResponse getMyWards(Long guardianId) {
        List<UserRelationship> relationships = relationshipRepository.findByGuardianIdWithWard(guardianId);
        List<WardResponse> wards = relationships.stream()
                .map(rel -> {
                    User ward = rel.getWard();
                    GpsLocation gps = getGpsLocation(ward.getId());
                    return new WardResponse(
                            ward.getId(),
                            ward.getName(),
                            ward.getProfileImageUrl(),
                            gps != null ? gps.lat() : null,
                            gps != null ? gps.lng() : null,
                            gps != null ? gps.updatedAt() : null
                    );
                })
                .toList();
        return new WardListResponse(wards);
    }

    @Transactional
    public DeleteRelationshipResponse deleteRelationship(Long userId, Long relationshipId) {
        UserRelationship relationship = relationshipRepository.findByIdWithUsers(relationshipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELATIONSHIP_NOT_FOUND));

        boolean isParticipant = relationship.getGuardian().getId().equals(userId)
                || relationship.getWard().getId().equals(userId);
        if (!isParticipant) {
            throw new BusinessException(ErrorCode.RELATIONSHIP_NOT_FOUND);
        }

        relationshipRepository.delete(relationship);
        return new DeleteRelationshipResponse(relationshipId);
    }

    public GuardianListResponse getMyGuardians(Long wardId) {
        List<UserRelationship> relationships = relationshipRepository.findByWardIdWithGuardian(wardId);
        List<GuardianResponse> guardians = relationships.stream()
                .map(rel -> new GuardianResponse(
                        rel.getId(),
                        rel.getGuardian().getId(),
                        rel.getGuardian().getName(),
                        rel.getCreatedAt()
                ))
                .toList();
        return new GuardianListResponse(guardians);
    }

    private GpsLocation getGpsLocation(Long wardId) {
        return tokenRepository.find("location:" + wardId)
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, GpsLocation.class);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GpsLocation(Double lat, Double lng, String updatedAt) {
    }
}
package com.shutter.api.location.service;

import com.shutter.api.global.exception.BusinessException;
import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.location.dto.request.CriminalLocationReportDto;
import com.shutter.api.location.dto.request.CriminalLocationRequestDto;
import com.shutter.api.notification.client.KakaoPushClient;
import com.shutter.api.location.service.GeocodingService;
import com.shutter.api.notification.domain.PushResult;
import com.shutter.api.user.domain.User;
import com.shutter.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CriminalLocationServiceTest {

    @InjectMocks
    private CriminalLocationService criminalLocationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KakaoPushClient kakaoPushClient;

    @Mock
    private GeocodingService geocodingService;

    private User requester;
    private User opponent;

    @BeforeEach
    void setUp() {
        requester = createUser(1L, 123456789L);
        opponent  = createUser(2L, 987654321L);
    }

    // ─── requestCriminalLocation ─────────────────────────────────────────────

    @Test
    @DisplayName("위치 요청 - 성공: 상대방에게 사일런트 FCM 전송")
    void requestCriminalLocation_success() {
        CriminalLocationRequestDto dto = createRequestDto(opponent.getKakaoId());

        given(userRepository.findById(requester.getId())).willReturn(Optional.of(requester));
        given(userRepository.findByKakaoId(opponent.getKakaoId())).willReturn(Optional.of(opponent));
        given(kakaoPushClient.sendDataPush(anyLong(), any())).willReturn(PushResult.SUCCESS);

        criminalLocationService.requestCriminalLocation(requester.getId(), dto);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kakaoPushClient).sendDataPush(anyLong(), dataCaptor.capture());

        Map<String, String> sentData = dataCaptor.getValue();
        assertThat(sentData.get("type")).isEqualTo("location_request");
        assertThat(sentData.get("requesterKakaoId")).isEqualTo(String.valueOf(requester.getKakaoId()));
    }

    @Test
    @DisplayName("위치 요청 - 실패: 상대방 없음 → OPPONENT_NOT_FOUND")
    void requestCriminalLocation_opponentNotFound() {
        CriminalLocationRequestDto dto = createRequestDto(999L);

        given(userRepository.findById(requester.getId())).willReturn(Optional.of(requester));
        given(userRepository.findByKakaoId(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> criminalLocationService.requestCriminalLocation(requester.getId(), dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OPPONENT_NOT_FOUND);
    }

    @Test
    @DisplayName("위치 요청 - 실패: FCM 전송 실패 → FCM_SEND_FAILED")
    void requestCriminalLocation_fcmFailed() {
        CriminalLocationRequestDto dto = createRequestDto(opponent.getKakaoId());

        given(userRepository.findById(requester.getId())).willReturn(Optional.of(requester));
        given(userRepository.findByKakaoId(opponent.getKakaoId())).willReturn(Optional.of(opponent));
        given(kakaoPushClient.sendDataPush(anyLong(), any())).willReturn(PushResult.DELIVERY_FAILURE);

        assertThatThrownBy(() -> criminalLocationService.requestCriminalLocation(requester.getId(), dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FCM_SEND_FAILED);
    }

    // ─── reportCriminalLocation ──────────────────────────────────────────────

    @Test
    @DisplayName("위치 보고 - 성공: 요청자에게 국가명+위도+경도 FCM 전송")
    void reportCriminalLocation_success() {
        CriminalLocationReportDto dto = createReportDto(requester.getKakaoId(), 37.123456, 127.123456);

        given(userRepository.findById(opponent.getId())).willReturn(Optional.of(opponent));
        given(userRepository.findByKakaoId(requester.getKakaoId())).willReturn(Optional.of(requester));
        given(geocodingService.getCountryName(37.123456, 127.123456)).willReturn("대한민국");
        given(kakaoPushClient.sendDataPush(anyLong(), any())).willReturn(PushResult.SUCCESS);

        criminalLocationService.reportCriminalLocation(opponent.getId(), dto);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kakaoPushClient).sendDataPush(anyLong(), dataCaptor.capture());

        Map<String, String> sentData = dataCaptor.getValue();
        assertThat(sentData.get("type")).isEqualTo("criminal_location");
        assertThat(sentData.get("country")).isEqualTo("대한민국");
        assertThat(sentData.get("lat")).isEqualTo("37.123456");
        assertThat(sentData.get("lng")).isEqualTo("127.123456");
    }

    @Test
    @DisplayName("위치 보고 - 실패: 요청자 없음 → OPPONENT_NOT_FOUND")
    void reportCriminalLocation_requesterNotFound() {
        CriminalLocationReportDto dto = createReportDto(999L, 37.0, 127.0);

        given(userRepository.findById(opponent.getId())).willReturn(Optional.of(opponent));
        given(userRepository.findByKakaoId(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> criminalLocationService.reportCriminalLocation(opponent.getId(), dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OPPONENT_NOT_FOUND);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User createUser(Long id, Long kakaoId) {
        User user = User.builder()
                .kakaoId(kakaoId)
                .name("테스트유저")
                .deviceUuid("test-uuid")
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private CriminalLocationRequestDto createRequestDto(Long opponentKakaoId) {
        CriminalLocationRequestDto dto = instantiate(CriminalLocationRequestDto.class);
        ReflectionTestUtils.setField(dto, "opponentKakaoId", opponentKakaoId);
        return dto;
    }

    private CriminalLocationReportDto createReportDto(Long requesterKakaoId, Double lat, Double lng) {
        CriminalLocationReportDto dto = instantiate(CriminalLocationReportDto.class);
        ReflectionTestUtils.setField(dto, "requesterKakaoId", requesterKakaoId);
        ReflectionTestUtils.setField(dto, "lat", lat);
        ReflectionTestUtils.setField(dto, "lng", lng);
        return dto;
    }

    private <T> T instantiate(Class<T> clazz) {
        try {
            var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate " + clazz.getSimpleName(), e);
        }
    }
}

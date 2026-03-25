package com.shutter.api.location.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * 좌표 → 국가명(한국어) 변환 서비스
 * OpenStreetMap Nominatim 역지오코딩 API 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private final WebClient webClient;

    private static final String NOMINATIM_URL =
            "https://nominatim.openstreetmap.org/reverse";

    private static final String FALLBACK = "알 수 없는 위치";

    /**
     * 위도·경도를 한국어 국가명으로 변환
     *
     * @param lat 위도
     * @param lng 경도
     * @return 한국어 국가명 (예: "대한민국", "미국"). 실패 시 "알 수 없는 위치"
     */
    public String getCountryName(double lat, double lng) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("nominatim.openstreetmap.org")
                            .path("/reverse")
                            .queryParam("format", "json")
                            .queryParam("lat", lat)
                            .queryParam("lon", lng)
                            .queryParam("accept-language", "ko")
                            .queryParam("zoom", 3)   // 국가 수준만 조회
                            .build())
                    .header("User-Agent", "shutter-app/1.0")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(5));

            if (response == null) {
                log.warn("Nominatim returned null for lat={}, lng={}", lat, lng);
                return FALLBACK;
            }

            @SuppressWarnings("unchecked")
            Map<String, String> address = (Map<String, String>) response.get("address");
            if (address == null || address.get("country") == null) {
                log.warn("No country in Nominatim response for lat={}, lng={}", lat, lng);
                return FALLBACK;
            }

            String country = address.get("country");
            log.info("Geocoded ({}, {}) -> {}", lat, lng, country);
            return country;

        } catch (Exception e) {
            log.error("Geocoding failed for lat={}, lng={}: {}", lat, lng, e.getMessage());
            return FALLBACK;
        }
    }
}

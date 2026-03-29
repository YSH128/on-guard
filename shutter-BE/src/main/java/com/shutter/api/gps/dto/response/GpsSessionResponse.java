package com.shutter.api.gps.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 통화 세션 상대방 위치 조회 응답 DTO
 * 딥페이크 감지 시 상대방 국가명을 반환한다.
 */
@Getter
@AllArgsConstructor
public class GpsSessionResponse {

    /** 역지오코딩된 국가명 (예: "대한민국", "중국"). 조회 실패 시 "알 수 없는 위치" */
    private String country;
}

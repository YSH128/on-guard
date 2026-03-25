package com.shutter.api.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 서비스 전체 에러 코드 정의
 *
 * 형식: HttpStatus + 에러 코드 문자열 + 메시지
 */
@Getter
public enum ErrorCode {

    // 인증 관련
    INVALID_KAKAO_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_KAKAO_TOKEN", "유효하지 않은 카카오 토큰입니다."),
    KAKAO_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "KAKAO_NAME_REQUIRED", "카카오 계정에 이름 정보가 없습니다. 카카오 동의 항목을 확인해주세요."),
    INVALID_JWT(HttpStatus.UNAUTHORIZED, "INVALID_JWT", "유효하지 않은 토큰입니다."),
    EXPIRED_JWT(HttpStatus.UNAUTHORIZED, "EXPIRED_JWT", "만료된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "유효하지 않은 Refresh Token입니다."),

    // 유저 관련
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "유저를 찾을 수 없습니다."),
    WARD_NOT_FOUND(HttpStatus.NOT_FOUND, "WARD_NOT_FOUND", "해당 피보호자를 찾을 수 없습니다."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", "이미 존재하는 유저입니다."),

    // 보호자 관계 관련
    GUARDIAN_NOT_FOUND(HttpStatus.NOT_FOUND, "GUARDIAN_NOT_FOUND", "해당 보호자를 찾을 수 없습니다."),
    RELATIONSHIP_NOT_FOUND(HttpStatus.FORBIDDEN, "RELATIONSHIP_NOT_FOUND", "보호자-피보호자 관계가 존재하지 않습니다."),

    // 권한 관련
    UNAUTHORIZED_ACCESS(HttpStatus.FORBIDDEN, "UNAUTHORIZED_ACCESS", "접근 권한이 없습니다."),

    // 요청 관련
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "잘못된 요청입니다."),

    // 알림 관련
    FCM_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FCM_SEND_FAILED", "푸시 알림 전송에 실패했습니다."),
    NO_GUARDIANS_REGISTERED(HttpStatus.NOT_FOUND, "NO_GUARDIANS_REGISTERED", "알림을 보낼 보호자가 등록되어 있지 않습니다."),
    KAKAO_PUSH_CONFIG_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "KAKAO_PUSH_CONFIG_ERROR", "카카오 푸시 설정이 올바르지 않습니다."),

    // 범죄자 위치 추적 관련
    OPPONENT_NOT_FOUND(HttpStatus.NOT_FOUND, "OPPONENT_NOT_FOUND", "상대방 정보를 찾을 수 없습니다."),

    // 통화 관련
    CALL_NOT_FOUND(HttpStatus.NOT_FOUND, "CALL_NOT_FOUND", "통화 세션이 만료되었거나 존재하지 않습니다."),
    CALL_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "CALL_ALREADY_IN_PROGRESS", "이미 발신 중인 통화가 존재합니다. 잠시 후 다시 시도해주세요."),
    SELF_CALL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "SELF_CALL_NOT_ALLOWED", "본인에게 통화를 요청할 수 없습니다."),
    CALLEE_NOT_FOUND(HttpStatus.NOT_FOUND, "CALLEE_NOT_FOUND", "수신자가 앱에 가입되어 있지 않습니다."),

    // 서버 관련
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}

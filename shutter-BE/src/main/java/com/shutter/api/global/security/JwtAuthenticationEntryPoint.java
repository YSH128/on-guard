package com.shutter.api.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutter.api.global.common.ErrorResponse;
import com.shutter.api.global.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring Security 인증 실패(401) 처리 진입점
 *
 * 유효하지 않거나 만료된 JWT로 보호된 엔드포인트에 접근할 때,
 * 기본 403 HTML/빈 응답 대신 프로젝트 표준 규격의 ErrorResponse JSON을 반환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        // JwtAuthenticationFilter에서 설정한 세부 에러 코드가 있는지 확인
        ErrorCode errorCode = (ErrorCode) request.getAttribute("exception");
        if (errorCode == null) {
            errorCode = ErrorCode.INVALID_JWT;
        }

        log.warn("인증 실패 (401 Unauthorized): URI={}, Code={}, Message={}",
                request.getRequestURI(), errorCode.getCode(), errorCode.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}

package com.shutter.api.global.jwt;

import com.shutter.api.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * JWT 인증 필터
 *
 * 요청 헤더의 Authorization: Bearer {token}에서 JWT를 추출하고
 * 유효한 경우 SecurityContext에 인증 정보(userId) 및 역할(Role)을 설정한다.
 * 토큰이 만료/위조된 경우 request에 에러 코드를 저장하여 AuthenticationEntryPoint로 위임한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            ErrorCode errorCode = jwtProvider.checkTokenValidity(token);
            if (errorCode == null) {
                // 토큰 정상
                Long userId = jwtProvider.getUserId(token);
                String role = jwtProvider.getRole(token);

                List<GrantedAuthority> authorities = Collections.emptyList();
                if (StringUtils.hasText(role)) {
                    authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                }

                // Principal은 기존 Controller들의 @AuthenticationPrincipal Long userId 호환성을 위해 Long 유지
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                // 토큰 검증 실패 (만료/위조) - EntryPoint에서 401 응답에 활용
                request.setAttribute("exception", errorCode);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}

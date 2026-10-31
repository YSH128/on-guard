package com.shutter.api.global.jwt;

import com.shutter.api.global.exception.ErrorCode;
import com.shutter.api.user.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 발급 및 검증
 *
 * 발급 대상:
 *  - Access Token  : API 인증용 (기본 1시간, role 클레임 포함)
 *  - Refresh Token : Access Token 재발급용 (기본 14일)
 */
@Slf4j
@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secretValue;

    @Value("${jwt.access-expiry-ms}")
    private long accessExpiryMs;

    @Value("${jwt.refresh-expiry-ms}")
    private long refreshExpiryMs;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secretValue.getBytes(StandardCharsets.UTF_8));
    }

    /** Access Token 발급 (Role 클레임 포함) */
    public String generateAccessToken(Long userId, Role role) {
        String roleName = (role != null) ? role.name() : "PENDING";
        return buildToken(userId, roleName, accessExpiryMs);
    }

    /** Access Token 발급 (하위 호환성 오버로딩) */
    public String generateAccessToken(Long userId) {
        return generateAccessToken(userId, null);
    }

    /** Refresh Token 발급 */
    public String generateRefreshToken(Long userId) {
        return buildToken(userId, null, refreshExpiryMs);
    }

    /** 토큰에서 userId 추출 */
    public Long getUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    /** 토큰에서 role 추출 */
    public String getRole(String token) {
        Claims claims = getClaims(token);
        return claims.get("role", String.class);
    }

    /** 토큰 유효성 검증 상세 판정 */
    public ErrorCode checkTokenValidity(String token) {
        try {
            getClaims(token);
            return null; // 정상
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT: {}", e.getMessage());
            return ErrorCode.EXPIRED_JWT;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("유효하지 않은 JWT: {}", e.getMessage());
            return ErrorCode.INVALID_JWT;
        }
    }

    /** 토큰 유효성 검증 */
    public boolean validateToken(String token) {
        return checkTokenValidity(token) == null;
    }

    private String buildToken(Long userId, String role, long expiryMs) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMs));
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.signWith(secretKey).compact();
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

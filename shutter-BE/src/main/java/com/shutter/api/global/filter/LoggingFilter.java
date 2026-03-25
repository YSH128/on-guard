package com.shutter.api.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    private static final String[] EXCLUDE_PATHS = {
            "/actuator", "/grafana", "/favicon.ico",
            "/swagger-ui", "/v3/api-docs"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info("[{}] {} {} - {}ms | IP: {}",
                    response.getStatus(),
                    request.getMethod(),
                    request.getRequestURI(),
                    duration,
                    request.getRemoteAddr()
            );
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String exclude : EXCLUDE_PATHS) {
            if (path.startsWith(exclude)) return true;
        }
        return false;
    }
}
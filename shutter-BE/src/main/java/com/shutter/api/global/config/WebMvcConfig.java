package com.shutter.api.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 전역 Web MVC 설정
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 모든 @RestController 빈의 진입점 경로 앞에 "/api/v1"을 일괄 적용한다.
     * 단, Swagger 관련 컨트롤러(org.springdoc)는 제외한다.
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api/v1", c ->
                c.isAnnotationPresent(RestController.class) &&
                !c.getPackageName().startsWith("org.springdoc")
        );
    }
}


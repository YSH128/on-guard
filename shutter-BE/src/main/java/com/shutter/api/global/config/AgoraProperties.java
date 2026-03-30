package com.shutter.api.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * application.yaml의 agora 프리픽스 설정 바인딩
 * - agora.app-id
 * - agora.app-certificate
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "agora")
public class AgoraProperties {

    private String appId;
    private String appCertificate;
}

package com.mycosmetic.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Claude 호출 설정 ({@code app.ai.*}). 기본값은 application.yml에 있다.
 */
@ConfigurationProperties("app.ai")
public record AiProperties(String model, long maxTokens, Duration timeout, String promptVersion) {
}

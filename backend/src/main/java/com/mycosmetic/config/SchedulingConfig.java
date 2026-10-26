package com.mycosmetic.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 작업을 켠다. 테스트·평가 프로필은 {@code app.scheduling.enabled=false}로 끈다
 * (테스트 중 식약처 배치가 돌거나, 평가 실행기가 끝나지 않는 일을 막는다).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}

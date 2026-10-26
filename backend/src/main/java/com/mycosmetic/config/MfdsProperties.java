package com.mycosmetic.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 식약처 원료성분정보 API 설정 ({@code app.mfds.*}). 기본값은 application.yml에 있다.
 *
 * @param baseUrl    서비스 주소(오퍼레이션 이름 앞까지)
 * @param serviceKey 공공데이터포털 서비스 키(Decoding 키). 값은 .env / Parameter Store의 MFDS_SERVICE_KEY. 비어 있으면 가져오기만 실패한다
 * @param timeout    연결·응답 타임아웃
 * @param pageSize   한 페이지 건수
 * @param pageDelay  페이지 사이 쉼
 */
@ConfigurationProperties("app.mfds")
public record MfdsProperties(String baseUrl, String serviceKey, Duration timeout, int pageSize, Duration pageDelay) {
}

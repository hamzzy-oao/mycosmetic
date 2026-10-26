package com.mycosmetic.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mycosmetic.client.mfds.MfdsIngredientClient;
import com.mycosmetic.client.mfds.MfdsIngredientHttpClient;

/** 식약처 성분 사전 client. 서비스 키가 비어 있어도 앱은 뜨고, 가져오기만 실패한다 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MfdsProperties.class)
public class MfdsConfig {

	@Bean
	MfdsIngredientClient mfdsIngredientClient(MfdsProperties properties) {
		return new MfdsIngredientHttpClient(properties.baseUrl(), properties.serviceKey(), properties.timeout());
	}
}

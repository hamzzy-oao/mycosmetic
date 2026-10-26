package com.mycosmetic.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

/**
 * Anthropic SDK 클라이언트. SDK 자체 재시도는 끄고(maxRetries 0), 재시도는 ExtractionWorker가 설계 6.2대로 한다.
 * app.extraction.client=fake이면 만들지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AnthropicConfig {

	@Bean(destroyMethod = "close")
	@ConditionalOnProperty(name = "app.extraction.client", havingValue = "claude", matchIfMissing = true)
	AnthropicClient anthropicClient(AiProperties properties, @Value("${ANTHROPIC_API_KEY:}") String apiKey) {
		return AnthropicOkHttpClient.builder()
				.apiKey(apiKey)
				.timeout(properties.timeout())
				.maxRetries(0)
				.build();
	}
}

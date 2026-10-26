package com.mycosmetic.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mycosmetic.client.productpage.OliveYoungPageHttpClient;
import com.mycosmetic.client.productpage.ProductPageClient;

/** 상품 상세 링크 미리 채우기 client. 오래 기다리지 않도록 5초에서 끊는다 */
@Configuration(proxyBeanMethods = false)
public class ProductPageConfig {

	@Bean
	ProductPageClient productPageClient() {
		return new OliveYoungPageHttpClient(Duration.ofSeconds(5));
	}
}

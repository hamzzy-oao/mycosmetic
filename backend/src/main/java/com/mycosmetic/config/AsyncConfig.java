package com.mycosmetic.config;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 성분 추출 백그라운드 스레드풀. core 2, max 4, 대기열 50.
 * 대기열까지 차면 제출이 TaskRejectedException으로 거절되고, 접수 API가 AI_BUSY(503)로 응답한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {

	public static final String EXTRACTION_EXECUTOR = "extractionExecutor";

	@Bean(name = EXTRACTION_EXECUTOR)
	ThreadPoolTaskExecutor extractionExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("extraction-");
		executor.setTaskDecorator(new MdcTaskDecorator());
		return executor;
	}

	/** 접수 요청의 traceId·userId(MDC)를 작업 스레드 로그에도 남긴다 */
	static final class MdcTaskDecorator implements TaskDecorator {

		@Override
		public Runnable decorate(Runnable runnable) {
			Map<String, String> context = MDC.getCopyOfContextMap();
			return () -> {
				Map<String, String> previous = MDC.getCopyOfContextMap();
				if (context == null) {
					MDC.clear();
				} else {
					MDC.setContextMap(context);
				}
				try {
					runnable.run();
				} finally {
					if (previous == null) {
						MDC.clear();
					} else {
						MDC.setContextMap(previous);
					}
				}
			};
		}
	}
}

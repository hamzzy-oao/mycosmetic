package com.mycosmetic.service.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 이메일 중복 확인을 IP마다 1분에 30번으로 제한한다(고정 1분 창, 서버 메모리).
 * 가입 여부를 대량으로 알아내는 것을 늦추려는 목적이라 서버가 여러 대이거나 재시작되면 따로 센다.
 */
@Component
public class EmailCheckRateLimiter {

	public static final int MAX_PER_WINDOW = 30;
	public static final Duration WINDOW = Duration.ofMinutes(1);
	/** 창이 끝난 IP 기록은 기록 수가 이만큼 넘을 때 한꺼번에 지운다 */
	private static final int CLEANUP_THRESHOLD = 10_000;

	private record Window(Instant start, int count) {

		boolean expiredAt(Instant now) {
			return !now.isBefore(start.plus(WINDOW));
		}
	}

	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	/** 이번 요청을 세고, 한도 안이면 true */
	public boolean tryAcquire(String key, Instant now) {
		if (windows.size() > CLEANUP_THRESHOLD) {
			windows.values().removeIf(window -> window.expiredAt(now));
		}
		Window window = windows.compute(key, (k, old) -> old == null || old.expiredAt(now)
				? new Window(now, 1)
				: new Window(old.start(), Math.min(old.count() + 1, MAX_PER_WINDOW + 1)));
		return window.count() <= MAX_PER_WINDOW;
	}
}

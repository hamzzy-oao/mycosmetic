package com.mycosmetic.config.logging;

/**
 * 로그에 개인정보를 남기지 않도록 가리는 도구.
 */
public final class LogMasks {

	private static final String MASK = "***";

	private LogMasks() {
	}

	/** "abcdef@gmail.com" -> "ab***@gmail.com", 로컬부가 두 글자 이하면 "a***@gmail.com". */
	public static String email(String email) {
		if (email == null) {
			return null;
		}
		int at = email.indexOf("@");
		if (at <= 0) {
			return MASK;
		}
		int keep = at > 2 ? 2 : 1;
		return email.substring(0, keep) + MASK + email.substring(at);
	}
}

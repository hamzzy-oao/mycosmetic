package com.mycosmetic.client.ai;

/**
 * 성분 추출 호출 실패. 재시도 여부는 {@link #retryable()}로 판단한다(설계 6.2).
 */
public class ExtractionClientException extends RuntimeException {

	public enum Reason {
		TIMEOUT(true),
		RATE_LIMITED(true),
		SERVER_ERROR(true),
		INVALID_RESPONSE(true),
		REFUSED(false),
		/** Claude 400. 사용자가 같은 사진으로 되풀이해 일으킬 수 있어 하루 한도에 센다 */
		BAD_REQUEST(false),
		/** 400·429·5xx가 아닌 Claude 응답(401·403·404·413 등). API 키·권한·서버 설정 문제라 한도에 세지 않는다 */
		CLIENT_CONFIG(false);

		private final boolean retryable;

		Reason(boolean retryable) {
			this.retryable = retryable;
		}
	}

	private final Reason reason;

	public ExtractionClientException(Reason reason, String message) {
		this(reason, message, null);
	}

	public ExtractionClientException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	public boolean retryable() {
		return reason.retryable;
	}
}

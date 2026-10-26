// 서버 오류 응답 { code, message, traceId }의 message를 그대로 쓴다.
// 서버에 닿지 못했거나 형식이 다르면 일반 안내를 쓴다
export const DEFAULT_ERROR_MESSAGE = '요청을 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.';

export function errorMessage(error, fallback = DEFAULT_ERROR_MESSAGE) {
	const data = error && error.response && error.response.data;
	if (data && typeof data.message === 'string' && data.message.trim() !== '') {
		return data.message;
	}
	return fallback;
}

import axios from 'axios';

// 모든 API 요청이 쓰는 클라이언트 (설계 5.2, 5.4)
// - 토큰은 HttpOnly 쿠키라 withCredentials로 보낸다
// - Spring Security의 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보낸다
export const api = axios.create({
	baseURL: '/',
	withCredentials: true,
	xsrfCookieName: 'XSRF-TOKEN',
	xsrfHeaderName: 'X-XSRF-TOKEN',
});

const REFRESH_URL = '/api/auth/refresh';
const REFRESH_LOCK_NAME = 'mycosmetic-refresh';
const AUTH_URL_PATTERN = /^\/?api\/auth\//;

let authFailureHandler = () => {};
let refreshing = null;

// 갱신이 실패했을 때(로그인이 풀렸을 때) 부를 함수를 등록한다
export function setAuthFailureHandler(fn) {
	authFailureHandler = typeof fn === 'function' ? fn : () => {};
}

function doRefresh() {
	return api.post(REFRESH_URL);
}

// 갱신은 한 번에 하나만 한다.
// - 같은 탭: 진행 중인 Promise를 함께 기다린다(single-flight)
// - 여러 탭: navigator.locks로 줄을 세워 같은 리프레시 토큰을 두 번 쓰지 않게 한다
//   (재사용 감지로 계보 전체가 폐기되는 것을 막는다, Review Focus 5)
export function refreshOnce() {
	if (!refreshing) {
		const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
		const run = locks && typeof locks.request === 'function'
			? locks.request(REFRESH_LOCK_NAME, () => doRefresh())
			: doRefresh();
		refreshing = Promise.resolve(run).finally(() => {
			refreshing = null;
		});
	}
	return refreshing;
}

// 테스트에서만 쓴다
export function resetRefreshStateForTest() {
	refreshing = null;
}

function isAuthUrl(url) {
	return typeof url === 'string' && AUTH_URL_PATTERN.test(url);
}

api.interceptors.response.use(
	(response) => response,
	async (error) => {
		const config = error.config;
		const status = error.response && error.response.status;
		if (status !== 401 || !config || config._retried || isAuthUrl(config.url)) {
			return Promise.reject(error);
		}
		try {
			await refreshOnce();
		} catch (refreshError) {
			authFailureHandler();
			return Promise.reject(error);
		}
		config._retried = true;
		return api(config);
	}
);

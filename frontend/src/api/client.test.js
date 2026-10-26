import { AxiosError } from 'axios';
import { api, setAuthFailureHandler, resetRefreshStateForTest } from './client';

// 가짜 어댑터 응답: 2xx는 성공, 나머지는 axios처럼 AxiosError로 거절한다
function respond(config, status, data = {}) {
	const response = { status, statusText: String(status), data, headers: {}, config };
	if (status >= 200 && status < 300) {
		return Promise.resolve(response);
	}
	return Promise.reject(new AxiosError('요청 실패', 'ERR_BAD_REQUEST', config, null, response));
}

function delay(ms) {
	return new Promise((resolve) => setTimeout(resolve, ms));
}

const originalAdapter = api.defaults.adapter;

afterEach(() => {
	api.defaults.adapter = originalAdapter;
	setAuthFailureHandler(() => {});
	resetRefreshStateForTest();
	delete navigator.locks;
});

// 갱신 전에는 401, 갱신 뒤에는 200을 주는 가짜 서버
function fakeServer({ refreshStatus = 200, onRefresh } = {}) {
	const state = { refreshed: false, refreshCalls: 0, calls: [] };
	api.defaults.adapter = jest.fn(async (config) => {
		state.calls.push(config.url);
		if (config.url === '/api/auth/refresh') {
			state.refreshCalls += 1;
			if (onRefresh) onRefresh();
			await delay(10);
			if (refreshStatus === 200) state.refreshed = true;
			return respond(config, refreshStatus);
		}
		if (config.url === '/api/auth/login') {
			return respond(config, 401, { code: 'LOGIN_FAILED' });
		}
		return state.refreshed ? respond(config, 200, { url: config.url }) : respond(config, 401);
	});
	return state;
}

test('CSRF 쿠키를 헤더로 보내도록 설정되어 있다', () => {
	expect(api.defaults.withCredentials).toBe(true);
	expect(api.defaults.xsrfCookieName).toBe('XSRF-TOKEN');
	expect(api.defaults.xsrfHeaderName).toBe('X-XSRF-TOKEN');
});

test('401이면 갱신 후 원래 요청을 다시 보낸다', async () => {
	const server = fakeServer();

	const res = await api.get('/api/shelf');

	expect(res.data).toEqual({ url: '/api/shelf' });
	expect(server.refreshCalls).toBe(1);
	expect(server.calls).toEqual(['/api/shelf', '/api/auth/refresh', '/api/shelf']);
});

test('동시에 3개 요청이 401이면 갱신은 한 번만 한다', async () => {
	const server = fakeServer();

	const results = await Promise.all([api.get('/api/a'), api.get('/api/b'), api.get('/api/c')]);

	expect(results.map((r) => r.data.url)).toEqual(['/api/a', '/api/b', '/api/c']);
	expect(server.refreshCalls).toBe(1);
});

test('refreshIsSerializedAcrossTabs', async () => {
	// 탭 사이 잠금(navigator.locks)을 가짜로 두고, 갱신 요청이 잠금 안에서 나가는지 본다
	let insideLock = false;
	let refreshedInsideLock = null;
	const request = jest.fn(async (name, callback) => {
		insideLock = true;
		try {
			return await callback();
		} finally {
			insideLock = false;
		}
	});
	Object.defineProperty(navigator, 'locks', { value: { request }, configurable: true });
	const server = fakeServer({ onRefresh: () => { refreshedInsideLock = insideLock; } });

	await Promise.all([api.get('/api/a'), api.get('/api/b')]);

	expect(request).toHaveBeenCalledTimes(1);
	expect(request.mock.calls[0][0]).toBe('mycosmetic-refresh');
	expect(refreshedInsideLock).toBe(true);
	expect(server.refreshCalls).toBe(1);
});

test('갱신이 실패하면 onAuthFailure를 부르고 재시도하지 않는다', async () => {
	const onAuthFailure = jest.fn();
	setAuthFailureHandler(onAuthFailure);
	const server = fakeServer({ refreshStatus: 401 });

	await expect(api.get('/api/shelf')).rejects.toMatchObject({ response: { status: 401 } });

	expect(onAuthFailure).toHaveBeenCalledTimes(1);
	expect(server.calls).toEqual(['/api/shelf', '/api/auth/refresh']);
});

test('/api/auth/login의 401은 갱신하지 않는다', async () => {
	const onAuthFailure = jest.fn();
	setAuthFailureHandler(onAuthFailure);
	const server = fakeServer();

	await expect(api.post('/api/auth/login', {})).rejects.toMatchObject({
		response: { status: 401, data: { code: 'LOGIN_FAILED' } },
	});

	expect(server.refreshCalls).toBe(0);
	expect(onAuthFailure).not.toHaveBeenCalled();
});

test('다시 보낸 요청이 또 401이면 더 갱신하지 않는다', async () => {
	let refreshCalls = 0;
	api.defaults.adapter = jest.fn(async (config) => {
		if (config.url === '/api/auth/refresh') {
			refreshCalls += 1;
			return respond(config, 200);
		}
		return respond(config, 401);
	});

	await expect(api.get('/api/shelf')).rejects.toMatchObject({ response: { status: 401 } });

	expect(refreshCalls).toBe(1);
});

import { pollJob } from './pollJob';

// 가짜 타이머에서 시간을 보내고, 그 사이 쌓인 Promise 처리를 끝낸다
async function advance(ms) {
	jest.advanceTimersByTime(ms);
	for (let i = 0; i < 10; i += 1) {
		await Promise.resolve();
	}
}

beforeEach(() => {
	jest.useFakeTimers();
});

afterEach(() => {
	jest.useRealTimers();
});

test('pollJob: DONE이 오면 끝, 1.5초 간격', async () => {
	const fetchJob = jest
		.fn()
		.mockResolvedValueOnce({ jobId: 7, status: 'QUEUED' })
		.mockResolvedValueOnce({ jobId: 7, status: 'RUNNING' })
		.mockResolvedValueOnce({ jobId: 7, status: 'DONE', result: { items: [] } });
	let settled = null;
	pollJob(7, { fetchJob }).then((job) => {
		settled = job;
	});

	await advance(1499);
	expect(fetchJob).toHaveBeenCalledTimes(0);
	await advance(1);
	expect(fetchJob).toHaveBeenCalledTimes(1);
	expect(fetchJob).toHaveBeenCalledWith(7);
	await advance(1500);
	expect(fetchJob).toHaveBeenCalledTimes(2);
	expect(settled).toBeNull();
	await advance(1500);

	expect(fetchJob).toHaveBeenCalledTimes(3);
	expect(settled).toEqual({ jobId: 7, status: 'DONE', result: { items: [] } });
	await advance(6000);
	expect(fetchJob).toHaveBeenCalledTimes(3);
});

test('pollJob: FAILED도 끝난 상태로 돌려준다', async () => {
	const fetchJob = jest.fn().mockResolvedValue({ jobId: 7, status: 'FAILED', errorCode: 'AI_TIMEOUT' });
	let settled = null;
	pollJob(7, { fetchJob }).then((job) => {
		settled = job;
	});

	await advance(1500);

	expect(settled).toEqual({ jobId: 7, status: 'FAILED', errorCode: 'AI_TIMEOUT' });
});

test('pollJob: 90초가 지나면 timedOut', async () => {
	const fetchJob = jest.fn().mockResolvedValue({ jobId: 7, status: 'RUNNING' });
	let settled = null;
	pollJob(7, { fetchJob }).then((job) => {
		settled = job;
	});

	for (let i = 0; i < 59; i += 1) {
		await advance(1500);
	}
	expect(settled).toBeNull();
	await advance(1500);

	expect(settled).toEqual({ timedOut: true });
	expect(fetchJob).toHaveBeenCalledTimes(60);
	await advance(15000);
	expect(fetchJob).toHaveBeenCalledTimes(60);
});

test('pollJob: 조회가 실패하면 그 오류로 끝난다', async () => {
	const error = new Error('네트워크');
	const fetchJob = jest.fn().mockRejectedValue(error);
	let caught = null;
	pollJob(7, { fetchJob }).catch((e) => {
		caught = e;
	});

	await advance(1500);

	expect(caught).toBe(error);
});

test('pollJob: signal로 멈추면 더 조회하지 않는다', async () => {
	const fetchJob = jest.fn().mockResolvedValue({ jobId: 7, status: 'RUNNING' });
	const controller = new AbortController();
	let settled = null;
	pollJob(7, { fetchJob, signal: controller.signal }).then((job) => {
		settled = job;
	});

	await advance(1500);
	controller.abort();
	await advance(1500);

	expect(fetchJob).toHaveBeenCalledTimes(1);
	expect(settled).toEqual({ cancelled: true });
});

// 추출 결과 기다리기 (설계 4.1-7): 1.5초마다 조회하고, 90초가 지나면 멈춘다.
// 끝난 상태(DONE·FAILED)면 job을, 시간이 다 되면 { timedOut: true }를,
// signal로 멈추면 { cancelled: true }를 돌려준다. 조회 오류는 그대로 던진다
const FINISHED = ['DONE', 'FAILED'];

function sleep(ms) {
	return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function pollJob(jobId, { intervalMs = 1500, timeoutMs = 90000, fetchJob, signal } = {}) {
	let elapsed = 0;
	while (elapsed + intervalMs <= timeoutMs) {
		await sleep(intervalMs);
		elapsed += intervalMs;
		if (signal && signal.aborted) {
			return { cancelled: true };
		}
		const job = await fetchJob(jobId);
		if (signal && signal.aborted) {
			return { cancelled: true };
		}
		if (job && FINISHED.includes(job.status)) {
			return job;
		}
	}
	return { timedOut: true };
}

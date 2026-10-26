import { api } from './client';

// 성분 추출 API (설계 4.1-3·7, 계획 Task 15)

// 줄인 JPEG를 multipart image로 보낸다 → 202 { jobId }
export function createExtraction(blob) {
	const form = new FormData();
	form.append('image', blob, 'ingredients.jpg');
	return api.post('/api/extractions', form).then((res) => res.data);
}

// 사진 없이 전성분 직접 붙여넣기 시작(AI를 부르지 않음) → 201 { jobId }. 저장할 때 오늘 횟수에 1회로 센다
export function createManualExtraction() {
	return api.post('/api/extractions/manual').then((res) => res.data);
}

// ExtractionJobResponse: { jobId, status: QUEUED|RUNNING|DONE|FAILED, errorCode, result }
export function getExtraction(jobId) {
	return api.get(`/api/extractions/${jobId}`).then((res) => res.data);
}

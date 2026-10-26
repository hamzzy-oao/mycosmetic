import { api } from './client';

// 새 제품 2주 테스트 API (설계 2026-10-24 B)
// Trial: { id, shelfItemId, product, startedOn, endsOn, day, totalDays, status: READY|ACTIVE|DONE, verdict,
//   summary: { recordedDays, moods: {GOOD, OK, BAD}, tags: {TROUBLE, ...} } }

// 평가 대기 → 진행 중 → 끝낸 것 순
export function getTrials() {
	return api.get('/api/trials').then((res) => res.data);
}

// 이미 테스트한 항목이면 409 { code: 'TRIAL_DUPLICATE' }
export function startTrial(shelfItemId) {
	return api.post('/api/trials', { shelfItemId }).then((res) => res.data);
}

// verdict: GOOD|BAD. 화장대 항목의 평가도 바뀐다
export function finishTrial(id, verdict) {
	return api.post(`/api/trials/${id}/finish`, { verdict }).then((res) => res.data);
}

export function cancelTrial(id) {
	return api.delete(`/api/trials/${id}`);
}

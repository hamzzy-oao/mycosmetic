import { api } from './client';

// 루틴 플래너 API (no-ai-features 설계 A)
// RoutineResponse: { id, name, slot: MORNING|EVENING, days: [MONDAY..SUNDAY](비면 매일), steps: [{ shelfItemId, stepType, position, productId, brand, productName, category }], updatedAt, fromDiagnosis }
// 오늘(GET /today): { date, dayOfWeek, morning: [RoutineResponse], evening, checkedMorning: [shelfItemId], checkedEvening, warnings }
// 요청 본문(draft): { name, slot, days, steps: [{ shelfItemId, stepType }] }
// 검사 결과: { warnings: [{ level: WARNING|CAUTION|INFO, code, message, items: [shelfItemId] }], suggestedOrder: [shelfItemId] }

export function getRoutines() {
	return api.get('/api/routines').then((res) => res.data);
}

export function getTodayRoutines() {
	return api.get('/api/routines/today').then((res) => res.data);
}

export function getRoutine(id) {
	return api.get(`/api/routines/${id}`).then((res) => res.data);
}

export function createRoutine(draft) {
	return api.post('/api/routines', draft).then((res) => res.data);
}

// 이름·시간대·요일·단계를 통째로 바꾼다
export function updateRoutine(id, draft) {
	return api.put(`/api/routines/${id}`, draft).then((res) => res.data);
}

export function deleteRoutine(id) {
	return api.delete(`/api/routines/${id}`);
}

// 저장하기 전 편집 중인 루틴의 경고·권장 순서. routineId는 고치는 중인 루틴(새 루틴이면 없음)
export function checkRoutineDraft(draft, routineId) {
	const params = routineId != null ? { routineId } : undefined;
	return api.post('/api/routines/check', draft, { params }).then((res) => res.data);
}

// 화장대 진단 초안을 매일 하는 아침·저녁 루틴으로 저장한다. 전에 진단에서 저장한 루틴은 새 초안으로 바뀐다. 저장 후 내 루틴 전부
export function saveRoutinesFromDiagnosis() {
	return api.post('/api/routines/from-diagnosis').then((res) => res.data);
}

// 오늘 이 시간대(MORNING|EVENING)에 쓴 제품 체크(checked=true)·해제. 날짜는 서버가 정한다. 바뀐 오늘 루틴 응답을 돌려준다
export function checkToday(slot, shelfItemIds, checked) {
	return api.put('/api/routines/today/checks', { slot, shelfItemIds, checked }).then((res) => res.data);
}

// 최근 4주 제품별 사용 일수: { days, steadyDays, items: [{ shelfItemId, brand, productName, category, verdict, daysUsed, steady }] }
export function getRoutineUsage() {
	return api.get('/api/routines/usage').then((res) => res.data);
}

import { api } from './client';

// 피부 일기 API (설계 2026-10-24 A)
// DiaryEntry: { date: 'YYYY-MM-DD', mood: GOOD|OK|BAD, tags: [TROUBLE|DRYNESS|OILINESS|REDNESS|ITCHINESS], memo }

// { month: 'YYYY-MM', entries: [DiaryEntry] }. month가 없으면 이번 달
export function getDiaryMonth(month) {
	return api.get('/api/diary', { params: month ? { month } : {} }).then((res) => res.data);
}

// 그날 기록을 만들거나 통째로 바꾼다. 오늘보다 뒤인 날은 400
export function putDiaryEntry(date, { mood, tags, memo }) {
	return api.put(`/api/diary/${date}`, { mood, tags, memo }).then((res) => res.data);
}

export function deleteDiaryEntry(date) {
	return api.delete(`/api/diary/${date}`);
}

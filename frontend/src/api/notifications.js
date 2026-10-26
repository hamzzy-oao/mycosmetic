import { api } from './client';

// 알림 API (설계 2026-10-24 C). 서버가 지금 상태로 계산한다
// { items: [{ type: EXPIRED|EXPIRING|TRIAL_READY|DIARY_TODAY, level: WARNING|CAUTION|INFO, title, message, link }] }
export function getNotifications() {
	return api.get('/api/notifications').then((res) => res.data);
}

import { api } from './client';

// 피부 상태 API (설계 3.4, 4.6, no-ai-features 설계 E). 없으면 서버가 { skinTypes: [], personalColor: null }을 준다
export function getSkinProfile() {
	return api.get('/api/skin-profile').then((res) => res.data);
}

// PUT이라 퍼스널컬러도 늘 함께 보낸다(null = 선택 안 함, 서버에서 지운다)
export function saveSkinProfile(skinTypes, personalColor = null) {
	return api.put('/api/skin-profile', { skinTypes, personalColor: personalColor || null }).then((res) => res.data);
}

import { api } from './client';

// 화장대 분석 API (퍼스널 탭 설계 3절). 저장하지 않고 부를 때마다 서버가 계산한다
// { routineDraft: { morning: [{ shelfItemId, stepType, guessed }], evening: [...] }, warnings, gaps, alternates,
//   needsType: [shelfItemId], items: [{ shelfItemId, brand, name, category, verdict, stepType, makeupType, guessed, shadeHex }],
//   looks, needsPersonalColor, shadeGaps }
export function getVanityAnalysis() {
	return api.get('/api/vanity/analysis').then((res) => res.data);
}

import { api } from './client';

// 피부 분석 API (퍼스널 탭 설계 1절). 문항은 로그인 없이, 분석·이력은 로그인 필요
export function getSkinQuestions() {
	return api.get('/api/skin-analysis/questions').then((res) => res.data);
}

// answers: { selfType, middayFeel(잘 모르겠어요일 때만), concerns: [] }
export function analyzeSkin(answers) {
	return api.post('/api/skin-analysis', answers).then((res) => res.data);
}

export function getSkinAnalysisHistory() {
	return api.get('/api/skin-analysis/history').then((res) => res.data);
}

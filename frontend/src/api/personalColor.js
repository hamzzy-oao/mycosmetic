import { api } from './client';

// 퍼스널컬러 API (퍼스널 탭 설계 2절). 사진은 보내지 않고 얼굴 영역의 평균색만 보낸다
// payload: { samples: [{ region: FOREHEAD|LEFT_CHEEK|RIGHT_CHEEK, frames: [{ r, g, b }] }], whiteRef?, client: { faceCount, frontal } }
export function getPalettes() {
	return api.get('/api/personal-color/palettes').then((res) => res.data);
}

// { lab, ita, hue, chroma, candidates, confidence, ordered, quality: { passed, issues, score }, ruleVersion }. 저장하지 않는다
export function estimateColor(payload) {
	return api.post('/api/personal-color/estimate', payload).then((res) => res.data);
}

// body: { method: 'CAMERA', samples: payload, chosen } 또는 { method: 'MANUAL', chosen }
export function savePersonalColor(body) {
	return api.post('/api/personal-color', body).then((res) => res.data);
}

// 저장한 적이 없으면 null(서버 204)
export function getLatestPersonalColor() {
	return api.get('/api/personal-color/latest').then((res) => (res.status === 204 ? null : res.data));
}

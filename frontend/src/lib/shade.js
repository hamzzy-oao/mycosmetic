// 색상과 내 톤(no-ai-features 설계 E). 피부 상태 화면과 내 화장대가 함께 쓴다
// 웜/쿨·계절 판정은 서버가 색만 보고 나눈 단순 근사라서 화면에는 늘 "참고용"이라고 적는다

export const PERSONAL_COLORS = [
	{ value: 'SPRING_WARM', label: '봄 웜' },
	{ value: 'SUMMER_COOL', label: '여름 쿨' },
	{ value: 'AUTUMN_WARM', label: '가을 웜' },
	{ value: 'WINTER_COOL', label: '겨울 쿨' },
];

export const PERSONAL_COLOR_LABELS = Object.fromEntries(PERSONAL_COLORS.map((c) => [c.value, c.label]));

export const TONE_LABELS = { WARM: '웜', COOL: '쿨', NEUTRAL: '중립' };

export const SEASON_LABELS = { SPRING: '봄', SUMMER: '여름', AUTUMN: '가을', WINTER: '겨울' };

export const SHADE_NAME_MAX = 50;

export const SHADE_HEX_PATTERN = /^#[0-9A-Fa-f]{6}$/;

// 색 입력의 처음 값(아직 고른 색이 없을 때)
export const DEFAULT_SHADE_HEX = '#D9737F';

export const TONE_NOTE = '웜·쿨과 계절은 색만 보고 나눈 참고용이에요. 조명과 피부에 따라 달라 보일 수 있어요.';

// 예: "웜 · 봄", "중립"
export function toneText(item) {
	if (!item || !item.shadeTone) return '';
	const tone = TONE_LABELS[item.shadeTone] || item.shadeTone;
	const season = item.shadeSeason ? SEASON_LABELS[item.shadeSeason] || item.shadeSeason : '';
	return season ? `${tone} · ${season}` : tone;
}

export function isMakeup(item) {
	return Boolean(item && item.product && item.product.category === 'MAKEUP');
}

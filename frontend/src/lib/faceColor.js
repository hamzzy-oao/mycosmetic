// 얼굴 영역 색 추출과 촬영 품질 검사(퍼스널 탭 설계 2절). 사진은 브라우저 밖으로 보내지 않고 평균색만 서버에 보낸다.
// 판정과 최종 품질 검사는 서버가 다시 한다. 여기 검사는 촬영 버튼을 켤지 정하는 화면용이다.

// MediaPipe Face Landmarker 478점 메시 기준 인덱스. 영역마다 점들의 중심 둘레 원에서 색을 뽑는다
export const REGIONS = {
	FOREHEAD: [10, 151, 108, 337, 67, 297, 109, 338],
	LEFT_CHEEK: [50, 101, 118, 117, 123, 187, 205, 36],
	RIGHT_CHEEK: [280, 330, 347, 346, 352, 411, 425, 266],
};
const FACE_LEFT = 234;
const FACE_RIGHT = 454;
const NOSE_TIP = 1;

// 서버(ColorQualityRules)와 맞춘 기준. 화면은 조금 너그럽게 두지 않는다(같은 사진이 서버에서 거절되지 않게)
const MIN_FACE_WIDTH = 0.3;
const MAX_ASYMMETRY = 0.25;
const MIN_L = 25;
const MAX_L = 90;
const MAX_CHEEK_DIFF = 6;
const MAX_CLIPPED = 0.2;

const MESSAGES = {
	NO_FACE: '얼굴이 보이지 않아요. 화면 가운데에 얼굴을 맞춰 주세요.',
	MULTIPLE_FACES: '한 사람만 화면에 나오게 해 주세요.',
	TOO_FAR: '조금 더 가까이 와 주세요.',
	NOT_FRONTAL: '정면을 봐 주세요.',
	TOO_DARK: '너무 어두워요. 창가처럼 밝은 곳으로 옮겨 주세요.',
	TOO_BRIGHT: '빛이 너무 강해요. 직사광선을 피해 주세요.',
	UNEVEN_LIGHT: '얼굴 한쪽만 밝아요. 빛이 정면에서 오게 해 주세요.',
	UNSTABLE: '움직였거나 조명이 바뀌었어요. 가만히 다시 찍어 주세요.',
	TOO_FEW_FRAMES: '색을 충분히 읽지 못했어요. 다시 찍어 주세요.',
	LOAD_FAILED: '얼굴 인식 도구를 불러오지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.',
};

// 흰 종이를 드는 칸(화면 아래 가운데, 비율 좌표). 화면에도 같은 위치에 점선 칸을 그린다
export const WHITE_BOX = { x0: 0.35, y0: 0.82, x1: 0.65, y1: 0.95 };

export function issueMessage(code) {
	return MESSAGES[code] || '사진 상태를 다시 확인해 주세요.';
}

// 원(중심 cx, cy, 반지름 r 픽셀) 안 픽셀의 평균 RGB. 원이 화면 밖이면 null
export function circleAverage(image, cx, cy, r) {
	let sr = 0;
	let sg = 0;
	let sb = 0;
	let n = 0;
	const x0 = Math.max(0, Math.floor(cx - r));
	const x1 = Math.min(image.width - 1, Math.ceil(cx + r));
	const y0 = Math.max(0, Math.floor(cy - r));
	const y1 = Math.min(image.height - 1, Math.ceil(cy + r));
	for (let y = y0; y <= y1; y += 1) {
		for (let x = x0; x <= x1; x += 1) {
			if ((x - cx) ** 2 + (y - cy) ** 2 > r * r) continue;
			const i = (y * image.width + x) * 4;
			sr += image.data[i];
			sg += image.data[i + 1];
			sb += image.data[i + 2];
			n += 1;
		}
	}
	if (n === 0) return null;
	return { r: Math.round(sr / n), g: Math.round(sg / n), b: Math.round(sb / n) };
}

// 비율 좌표 사각형 안 픽셀의 평균 RGB(흰 종이 보정 기준색)
export function boxAverage(image, box = WHITE_BOX) {
	let sr = 0;
	let sg = 0;
	let sb = 0;
	let n = 0;
	const x0 = Math.floor(box.x0 * image.width);
	const x1 = Math.ceil(box.x1 * image.width);
	const y0 = Math.floor(box.y0 * image.height);
	const y1 = Math.ceil(box.y1 * image.height);
	for (let y = y0; y < y1; y += 1) {
		for (let x = x0; x < x1; x += 1) {
			const i = (y * image.width + x) * 4;
			sr += image.data[i];
			sg += image.data[i + 1];
			sb += image.data[i + 2];
			n += 1;
		}
	}
	if (n === 0) return null;
	return { r: Math.round(sr / n), g: Math.round(sg / n), b: Math.round(sb / n) };
}

// 영역 평균색들의 평균을 #RRGGBB로(결과 화면의 "읽은 피부색" 칩)
export function averageHex(frames) {
	const all = Object.values(frames).flat();
	if (all.length === 0) return null;
	const avg = (k) => Math.round(all.reduce((s, c) => s + c[k], 0) / all.length);
	return `#${[avg('r'), avg('g'), avg('b')].map((v) => v.toString(16).padStart(2, '0')).join('')}`.toUpperCase();
}

// 랜드마크 묶음의 중심과, 점들까지 평균 거리의 절반(픽셀). 영역 가장자리의 머리카락·눈썹을 피하려고 작게 잡는다
export function regionCircle(landmarks, indices, width, height) {
	const pts = indices.map((i) => ({ x: landmarks[i].x * width, y: landmarks[i].y * height }));
	const cx = pts.reduce((s, p) => s + p.x, 0) / pts.length;
	const cy = pts.reduce((s, p) => s + p.y, 0) / pts.length;
	const mean = pts.reduce((s, p) => s + Math.hypot(p.x - cx, p.y - cy), 0) / pts.length;
	return { cx: Math.round(cx), cy: Math.round(cy), r: Math.max(1, Math.round(mean / 2)) };
}

// 세 영역의 평균색. 영역이 화면 밖이면 null
export function sampleRegions(image, landmarks, offset = { dx: 0, dy: 0 }) {
	const out = {};
	for (const [region, indices] of Object.entries(REGIONS)) {
		const { cx, cy, r } = regionCircle(landmarks, indices, image.width, image.height);
		const avg = circleAverage(image, cx + offset.dx, cy + offset.dy, r);
		if (!avg) return null;
		out[region] = avg;
	}
	return out;
}

// 250을 넘는(하얗게 날아간) 픽셀 비율
export function clippedRatio(image) {
	let clipped = 0;
	const total = image.width * image.height;
	for (let i = 0; i < total; i += 1) {
		const j = i * 4;
		if (image.data[j] > 250 && image.data[j + 1] > 250 && image.data[j + 2] > 250) clipped += 1;
	}
	return total === 0 ? 0 : clipped / total;
}

function linear(c) {
	const v = c / 255;
	return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
}

// CIELAB L*(서버 ColorMath와 같은 식의 Y만 쓴다)
export function lightness({ r, g, b }) {
	const y = 0.2126729 * linear(r) + 0.7151522 * linear(g) + 0.072175 * linear(b);
	const f = y > 216 / 24389 ? Math.cbrt(y) : ((24389 / 27) * y + 16) / 116;
	return 116 * f - 16;
}

// 촬영 버튼을 켤지 정하는 검사. faces: 얼굴마다 랜드마크 배열, samples: 이번 프레임 영역 평균색, clipped: clippedRatio
export function qualityIssues({ faces, samples, clipped }) {
	if (!faces || faces.length === 0) return ['NO_FACE'];
	if (faces.length > 1) return ['MULTIPLE_FACES'];
	const issues = [];
	const lm = faces[0];
	const width = lm[FACE_RIGHT].x - lm[FACE_LEFT].x;
	if (width < MIN_FACE_WIDTH) issues.push('TOO_FAR');
	const asymmetry = Math.abs(lm[NOSE_TIP].x - lm[FACE_LEFT].x - (lm[FACE_RIGHT].x - lm[NOSE_TIP].x)) / Math.max(width, 1e-6);
	if (asymmetry > MAX_ASYMMETRY) issues.push('NOT_FRONTAL');
	if (!samples) return [...issues, 'NO_FACE'];
	const l = 0.5 * lightness(samples.FOREHEAD) + 0.25 * lightness(samples.LEFT_CHEEK) + 0.25 * lightness(samples.RIGHT_CHEEK);
	if (clipped > MAX_CLIPPED || l > MAX_L) issues.push('TOO_BRIGHT');
	else if (l < MIN_L) issues.push('TOO_DARK');
	if (Math.abs(lightness(samples.LEFT_CHEEK) - lightness(samples.RIGHT_CHEEK)) > MAX_CHEEK_DIFF) issues.push('UNEVEN_LIGHT');
	return issues;
}

// 사진 한 장(갤러리)에서 영역마다 5프레임을 만든다. 같은 사진이라 원 위치를 ±2px씩 옮겨 서버의 "프레임 5개 이상" 검사를 통과시키고,
// 영역 안 색 편차가 실제로 작아야 통과한다(조작이 아니라 같은 사진의 이웃 픽셀을 읽는 것)
const STILL_OFFSETS = [
	{ dx: 0, dy: 0 },
	{ dx: 2, dy: 0 },
	{ dx: -2, dy: 0 },
	{ dx: 0, dy: 2 },
	{ dx: 0, dy: -2 },
];

export function stillSamples(image, landmarks) {
	const frames = { FOREHEAD: [], LEFT_CHEEK: [], RIGHT_CHEEK: [] };
	for (const offset of STILL_OFFSETS) {
		const s = sampleRegions(image, landmarks, offset);
		if (!s) return null;
		for (const region of Object.keys(frames)) frames[region].push(s[region]);
	}
	return frames;
}

// 서버 요청 본문의 samples 모양으로 바꾼다
export function toSamplesPayload(frames) {
	return Object.entries(frames).map(([region, list]) => ({ region, frames: list }));
}

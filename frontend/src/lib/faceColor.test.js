import {
	REGIONS,
	circleAverage,
	clippedRatio,
	issueMessage,
	lightness,
	qualityIssues,
	regionCircle,
	stillSamples,
	toSamplesPayload,
	boxAverage,
	averageHex,
	WHITE_BOX,
} from './faceColor';
import { LIBRARY_URL, WASM_URL } from './faceLandmarker';
import pkg from '../../package.json';

// 가로 w, 세로 h의 단색 ImageData 모양 객체. paint(x, y)로 특정 픽셀 색을 바꾼다
function image(w, h, rgb) {
	const data = new Uint8ClampedArray(w * h * 4);
	for (let i = 0; i < w * h; i += 1) {
		data.set([...rgb, 255], i * 4);
	}
	return {
		width: w,
		height: h,
		data,
		paint(x, y, color) {
			data.set([...color, 255], (y * w + x) * 4);
		},
	};
}

// 정면 얼굴 랜드마크(정규화 좌표). 234·454는 얼굴 왼·오른쪽 끝, 1은 코끝
function face({ left = 0.3, right = 0.7, nose = 0.5 } = {}) {
	const points = Array.from({ length: 478 }, () => ({ x: 0.5, y: 0.5 }));
	points[234] = { x: left, y: 0.5 };
	points[454] = { x: right, y: 0.5 };
	points[1] = { x: nose, y: 0.55 };
	return points;
}

test('circleAverage: 원 안 픽셀만 평균낸다', () => {
	const img = image(20, 20, [200, 150, 120]);
	img.paint(0, 0, [0, 0, 0]); // 원 밖
	img.paint(10, 10, [255, 255, 255]); // 원 안
	const avg = circleAverage(img, 10, 10, 1);

	// 반지름 1 → 중심 + 상하좌우 4점 = 5픽셀, 그중 하나만 흰색
	expect(avg).toEqual({ r: Math.round((200 * 4 + 255) / 5), g: Math.round((150 * 4 + 255) / 5), b: Math.round((120 * 4 + 255) / 5) });
	expect(circleAverage(img, -50, -50, 2)).toBeNull();
});

test('regionCircle: 랜드마크 묶음의 중심과 평균 거리 절반을 픽셀로 돌려준다', () => {
	const points = Array.from({ length: 478 }, () => ({ x: 0, y: 0 }));
	points[1] = { x: 0.4, y: 0.5 };
	points[2] = { x: 0.6, y: 0.5 };
	const circle = regionCircle(points, [1, 2], 100, 200);

	expect(circle).toEqual({ cx: 50, cy: 100, r: 5 });
	expect(Object.keys(REGIONS)).toEqual(['FOREHEAD', 'LEFT_CHEEK', 'RIGHT_CHEEK']);
});

test('clippedRatio: 250 넘는 픽셀 비율', () => {
	const img = image(2, 2, [100, 100, 100]);
	img.paint(0, 0, [255, 255, 255]);
	expect(clippedRatio(img)).toBeCloseTo(0.25);
});

test('lightness: 서버와 같은 L* 근사(흰색 100, 검정 0)', () => {
	expect(lightness({ r: 255, g: 255, b: 255 })).toBeCloseTo(100, 0);
	expect(lightness({ r: 0, g: 0, b: 0 })).toBeCloseTo(0, 0);
	expect(lightness({ r: 224, g: 172, b: 150 })).toBeCloseTo(74.5, 0);
});

describe('qualityIssues', () => {
	const ok = { r: 224, g: 172, b: 150 };
	const base = { faces: [face()], samples: { FOREHEAD: ok, LEFT_CHEEK: ok, RIGHT_CHEEK: ok }, clipped: 0 };

	test('정상이면 빈 목록', () => {
		expect(qualityIssues(base)).toEqual([]);
	});

	test('얼굴 수', () => {
		expect(qualityIssues({ ...base, faces: [] })).toEqual(['NO_FACE']);
		expect(qualityIssues({ ...base, faces: [face(), face()] })).toEqual(['MULTIPLE_FACES']);
	});

	test('너무 멀거나 옆을 보면', () => {
		expect(qualityIssues({ ...base, faces: [face({ left: 0.45, right: 0.6, nose: 0.525 })] })).toContain('TOO_FAR');
		expect(qualityIssues({ ...base, faces: [face({ nose: 0.65 })] })).toContain('NOT_FRONTAL');
	});

	test('조명: 어둡다·밝다·좌우 차이·하얗게 날아감', () => {
		const dark = { r: 30, g: 25, b: 20 };
		expect(qualityIssues({ ...base, samples: { FOREHEAD: dark, LEFT_CHEEK: dark, RIGHT_CHEEK: dark } })).toEqual(['TOO_DARK']);
		expect(qualityIssues({ ...base, samples: { ...base.samples, LEFT_CHEEK: { r: 150, g: 110, b: 90 } } })).toEqual(['UNEVEN_LIGHT']);
		expect(qualityIssues({ ...base, clipped: 0.3 })).toEqual(['TOO_BRIGHT']);
	});
});

test('issueMessage: 코드마다 한국어 안내, 모르는 코드는 일반 안내', () => {
	for (const code of ['NO_FACE', 'MULTIPLE_FACES', 'TOO_FAR', 'NOT_FRONTAL', 'TOO_DARK', 'TOO_BRIGHT', 'UNEVEN_LIGHT', 'UNSTABLE', 'TOO_FEW_FRAMES']) {
		expect(issueMessage(code)).toMatch(/[가-힣]/);
	}
	expect(issueMessage('WHAT')).toBe('사진 상태를 다시 확인해 주세요.');
});

test('boxAverage: 흰 종이 칸 평균, averageHex: 평균색을 #RRGGBB로', () => {
	const img = image(20, 20, [100, 100, 100]);
	for (let y = Math.floor(WHITE_BOX.y0 * 20); y < Math.ceil(WHITE_BOX.y1 * 20); y += 1) {
		for (let x = Math.floor(WHITE_BOX.x0 * 20); x < Math.ceil(WHITE_BOX.x1 * 20); x += 1) img.paint(x, y, [240, 240, 250]);
	}
	expect(boxAverage(img)).toEqual({ r: 240, g: 240, b: 250 });
	expect(averageHex({ A: [{ r: 224, g: 172, b: 150 }], B: [{ r: 224, g: 172, b: 150 }] })).toBe('#E0AC96');
	expect(averageHex({})).toBeNull();
});

test('MediaPipe wasm은 외부 CDN이 아니라 우리 출처에서 받고, 시작·빌드 전에 복사한다', () => {
	expect(WASM_URL).toBe('/mediapipe/wasm');
	expect(LIBRARY_URL).toBe('/mediapipe/vision_bundle.mjs');
	// 브라우저 글자 읽기(tesseract) 복사도 같이 돈다
	expect(pkg.scripts.prestart).toContain('node scripts/copy-mediapipe.js');
	expect(pkg.scripts.prebuild).toContain('node scripts/copy-mediapipe.js');
});

test('stillSamples: 사진 한 장에서 영역마다 5프레임, 요청 모양으로 바꾼다', () => {
	const img = image(100, 100, [224, 172, 150]);
	const points = Array.from({ length: 478 }, () => ({ x: 0.5, y: 0.5 }));
	// 영역 점들을 조금씩 퍼뜨려 반지름이 생기게 한다
	REGIONS.FOREHEAD.forEach((i, k) => { points[i] = { x: 0.4 + k * 0.02, y: 0.2 }; });
	REGIONS.LEFT_CHEEK.forEach((i, k) => { points[i] = { x: 0.25 + k * 0.01, y: 0.6 }; });
	REGIONS.RIGHT_CHEEK.forEach((i, k) => { points[i] = { x: 0.65 + k * 0.01, y: 0.6 }; });

	const frames = stillSamples(img, points);

	expect(frames.FOREHEAD).toHaveLength(5);
	expect(frames.LEFT_CHEEK[0]).toEqual({ r: 224, g: 172, b: 150 });
	expect(toSamplesPayload(frames).map((s) => s.region)).toEqual(['FOREHEAD', 'LEFT_CHEEK', 'RIGHT_CHEEK']);
});

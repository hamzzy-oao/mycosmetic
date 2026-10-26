import { cleanOcrText, gradeOcr, ingredientSection, isOcrImage } from './ocr';

// tesseract 줄 모양: 낱말마다 bbox. y는 줄마다 40씩, 내용 칸은 x=420부터(가로 1150 이미지)
function line(y, words) {
	return { words: words.map(([text, x0]) => ({ text, bbox: { x0, y0: y, x1: x0 + text.length * 24, y1: y + 30 } })) };
}

test('상품정보 제공고시 표면 "모든 성분" 칸의 오른쪽 내용만 고른다', () => {
	const lines = [
		line(100, [['전화번호', 30], ['1588-5067', 420]]),
		line(140, [['(주말', 420], ['제외)', 520]]),
		// 표의 다음 칸: 위 칸과 넓게 떨어져 있다
		line(230, [['「화장품법」에', 30], ['따라', 200], ['정제수,', 420], ['나이아신아마이드,', 520]]),
		line(266, [['기재·표시하여야', 30], ['하는', 230], ['글리세린,', 420]]),
		line(302, [['모든', 30], ['성분', 90], ['판테놀,', 420], ['1,2-헥산다이올', 520]]),
		line(338, [['향료', 420]]),
		line(430, [['품질보증기준', 30], ['본', 420], ['제품에', 460]]),
	];
	expect(ingredientSection(lines, 1150)).toBe('정제수, 나이아신아마이드,\n글리세린,\n판테놀, 1,2-헥산다이올\n향료');
});

test('표가 아닌 캡처(전성분 글만)는 null: 읽은 글 전체를 쓴다', () => {
	expect(ingredientSection([line(100, [['정제수,', 30], ['글리세린', 200]])], 1150)).toBeNull();
	expect(ingredientSection([], 1150)).toBeNull();
});

test('한글끼리 끊긴 줄은 붙이고, 한글 사이 공백을 뺀다', () => {
	expect(cleanOcrText('정제수, 글리세\n린, 나이아신아마이 드,\n부틸렌글라이콜')).toBe(
		'정제수, 글리세린, 나이아신아마이드, 부틸렌글라이콜'
	);
	expect(cleanOcrText('글 리 세 린 , 판테놀')).toBe('글리세린, 판테놀');
});

test('영문·숫자가 낀 성분은 그대로 두고, 숫자 사이 쉼표(1,2-헥산다이올)는 띄우지 않는다', () => {
	expect(cleanOcrText('1 , 2-헥산다이올,\nPEG-100스테아레이트, 하이드로제네이티드폴리(C6-14올레핀)')).toBe(
		'1,2-헥산다이올, PEG-100스테아레이트, 하이드로제네이티드폴리(C6-14올레핀)'
	);
});

test('단어별로 이어 붙이며 생긴 빈칸을 없애고, 한글 뒤 마침표는 쉼표로 본다', () => {
	expect(cleanOcrText('1.2- 핵산다이올, 피이지 -13- 데실, 트로메타민 . 향료 (0.2 ppm )')).toBe(
		'1.2-핵산다이올, 피이지-13-데실, 트로메타민, 향료 (0.2 ppm)'
	);
	expect(cleanOcrText('프로판다이올. 글리세린')).toBe('프로판다이올, 글리세린');
});

test('빈 글과 빈 줄', () => {
	expect(cleanOcrText('')).toBe('');
	expect(cleanOcrText('\n\n  정제수  \n\n')).toBe('정제수');
});

test('읽을 수 있는 이미지 종류만 받는다', () => {
	expect(isOcrImage({ type: 'image/png' })).toBe(true);
	expect(isOcrImage({ type: 'image/jpeg' })).toBe(true);
	expect(isOcrImage({ type: 'application/pdf' })).toBe(false);
	expect(isOcrImage(null)).toBe(false);
});

test('읽기 등급: 2026-10-26에 잰 값으로 못 읽음·확실하지 않음·좋음을 나눈다', () => {
	const words = (n, low) => Array.from({ length: n }, (_, i) => ({ confidence: i < low ? 40 : 95 }));
	const ingredients = '정제수, 나이아신아마이드, 글리세린';
	// 보통 크기 글자: 신뢰도 90, 낮은 낱말 2%
	expect(gradeOcr({ confidence: 90, words: words(50, 1), text: ingredients, small: false })).toBe('good');
	// 288px 캡처: 신뢰도 74
	expect(gradeOcr({ confidence: 74, words: words(50, 12), text: ingredients, small: true })).toBe('unsure');
	// 신뢰도는 높아도 작은 캡처면 확실하지 않다
	expect(gradeOcr({ confidence: 90, words: words(50, 1), text: ingredients, small: true })).toBe('unsure');
	// 글자 없는 그림: 신뢰도 27
	expect(gradeOcr({ confidence: 27, words: words(2, 2), text: 'ny', small: false })).toBe('failed');
	// 한글이 거의 없으면(영문 깨진 글자) 전성분으로 보지 않는다
	expect(gradeOcr({ confidence: 70, words: words(20, 5), text: 'ny a8e 20 pum zou ME com prey', small: false })).toBe('failed');
});

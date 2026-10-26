// 전성분 캡처 이미지를 브라우저 안에서 글자로 읽는다(tesseract.js, 2026-10-26 사용자 결정).
// 이미지는 서버로 보내지 않는다. 읽은 글은 붙여넣기 칸에 넣어 사용자가 고친 뒤 성분 사전과 맞춘다.
// 처음 쓸 때만 라이브러리와 한국어·영어 글자 데이터(public/tesseract, 약 4.5MB)를 받는다
const BASE = `${process.env.PUBLIC_URL}/tesseract`;
const HANGUL = '\\uAC00-\\uD7A3';
const BETWEEN_HANGUL = new RegExp(`([${HANGUL}])\\s+(?=[${HANGUL}])`, 'g');
const ENDS_HANGUL = new RegExp(`[${HANGUL}]$`);
const STARTS_HANGUL = new RegExp(`^[${HANGUL}]`);
// 글자 읽기는 쉼표를 마침표로 잘못 읽는 일이 많다. 성분 목록에서 "한글(또는 괄호). 다음말"은 쉼표다
const PERIOD_AS_COMMA = new RegExp(`([${HANGUL})])\\.(?=\\s)`, 'g');

// 글자가 작으면(캡처를 줄였거나 화면이 작으면) 거의 못 읽는다. 가로를 이 정도로 키워서 읽는다
const TARGET_WIDTH = 2400;
const MAX_SCALE = 4;
// 상품정보 제공고시 표의 왼쪽 칸(항목 이름)은 이미지 가로의 이 비율 안에서 시작한다
const LABEL_COLUMN = 0.4;
// 이보다 좁은 캡처는 글자가 너무 작아(약 10px 아래) 키워도 잘 못 읽는다. 288px 캡처는 사전에 맞는 성분이 1개뿐이었고,
// 보통 상세페이지 글자(13~18px)는 19개 중 17개를 그대로 읽었다(2026-10-26 확인)
export const SMALL_IMAGE_WIDTH = 600;
// 읽기 결과가 믿을 만한지 가르는 기준(2026-10-26 측정: 보통 글자 신뢰도 90, 흐린 글자 82, 288px 캡처 74, 글자 없는 그림 27)
const FAILED_CONFIDENCE = 50;
const SURE_CONFIDENCE = 80;
const MIN_HANGUL_RATIO = 0.2;
const MAX_LOW_WORD_RATIO = 0.2;
// 데이터를 못 받거나 기기가 느려 멈춘 것처럼 보이면 그만둔다
export const OCR_TIMEOUT_MS = 90000;

export class OcrTimeoutError extends Error {}

/**
 * 읽기 결과 등급. failed: 전성분으로 볼 수 없음(칸에 넣지 않는다), unsure: 넣되 크게 경고, good: 그대로 넣는다.
 * confidence: tesseract 전체 신뢰도(0~100), words: [{ confidence }], text: 다듬은 글
 */
export function gradeOcr({ confidence, words, text, small }) {
	const letters = String(text || '').replace(/\s/g, '');
	const hangul = (letters.match(new RegExp(`[${HANGUL}]`, 'g')) || []).length;
	if (letters.length < 4 || confidence < FAILED_CONFIDENCE || hangul / letters.length < MIN_HANGUL_RATIO) return 'failed';
	const low = words.filter((w) => w.confidence < 60).length / Math.max(1, words.length);
	return small || confidence < SURE_CONFIDENCE || low > MAX_LOW_WORD_RATIO ? 'unsure' : 'good';
}

function withTimeout(promise, ms) {
	let timer;
	return Promise.race([
		promise,
		new Promise((_, reject) => {
			timer = setTimeout(() => reject(new OcrTimeoutError('글자 읽기 시간 초과')), ms);
		}),
	]).finally(() => clearTimeout(timer));
}

export const OCR_IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp', 'image/bmp'];

export function isOcrImage(file) {
	return Boolean(file && OCR_IMAGE_TYPES.includes(file.type));
}

/**
 * 읽은 글을 전성분 붙여넣기에 맞게 다듬는다.
 * - 줄바꿈은 단어 중간일 수 있어 한글끼리면 붙이고, 아니면 공백으로 바꾼다
 * - 한글 사이 공백을 뺀다(글자 읽기가 "글 리 세 린"처럼 띄우는 일이 많고, 성분 표준명에는 공백이 거의 없다)
 * - 한글·괄호 뒤 마침표는 쉼표로 본다
 * - 쉼표 앞뒤 공백을 정리한다(숫자 사이 쉼표는 붙여 둔다)
 */
export function cleanOcrText(text) {
	return String(text || '')
		.replace(/\r/g, '')
		.split('\n')
		.map((line) => line.trim())
		.filter(Boolean)
		.reduce((acc, line) => {
			if (!acc) return line;
			return acc + (ENDS_HANGUL.test(acc) && STARTS_HANGUL.test(line) ? '' : ' ') + line;
		}, '')
		.replace(BETWEEN_HANGUL, '$1')
		// 단어별로 읽어 이어 붙이면 생기는 빈칸: 하이픈(1,2-헥산다이올, PEG-100) 양옆, 괄호 안쪽, 마침표 앞
		.replace(/\s*-\s*/g, '-')
		.replace(/\(\s+/g, '(')
		.replace(/\s+\)/g, ')')
		.replace(/\s+\./g, '.')
		.replace(PERIOD_AS_COMMA, '$1,')
		.replace(/\s*,\s*/g, ', ')
		// 1,2-헥산다이올처럼 숫자 사이 쉼표는 이름의 일부라 띄우지 않는다
		.replace(/(\d), (?=\d)/g, '$1,')
		.replace(/\s{2,}/g, ' ')
		.trim();
}

/**
 * 상품정보 제공고시 표(왼쪽 항목 이름, 오른쪽 내용)를 통째로 캡처했으면 "…모든 성분" 칸의 오른쪽 내용만 고른다.
 * lines: [{ words: [{ text, bbox: { x0, y0, x1, y1 } }] }] (tesseract의 줄). width: 이미지 가로.
 * 전성분 칸을 못 찾으면 null(전체 글을 쓴다)
 */
export function ingredientSection(lines, width) {
	const rows = lines
		.map((line) => line.words.filter((w) => w.text.trim()).sort((a, b) => a.bbox.x0 - b.bbox.x0))
		.filter((words) => words.length)
		.map((words) => ({
			words,
			y0: Math.min(...words.map((w) => w.bbox.y0)),
			y1: Math.max(...words.map((w) => w.bbox.y1)),
		}));
	// 왼쪽 칸에 "…성분"이 있는 줄. 글자가 낱자로 끊겨 읽혀도 찾도록 붙여서 본다
	const labelAt = rows.findIndex((r) =>
		/성분/.test(r.words.filter((w) => w.bbox.x0 < width * LABEL_COLUMN).map((w) => w.text).join(''))
	);
	if (labelAt < 0) return null;
	const label = rows[labelAt].words;
	// 항목 이름과 내용 사이는 그 줄에서 가장 넓은 빈칸이다. 그 오른쪽이 내용 칸
	let split = -1;
	let widest = 0;
	for (let i = 1; i < label.length; i += 1) {
		const gap = label[i].bbox.x0 - label[i - 1].bbox.x1;
		if (gap > widest) {
			widest = gap;
			split = i;
		}
	}
	if (split < 0) return null;
	const contentX = label[split].bbox.x0 - 4;
	const hasLabel = (row) => row.words.some((w) => w.bbox.x1 < contentX);
	const lineHeight = Math.max(1, rows[labelAt].y1 - rows[labelAt].y0);
	// 표의 칸과 칸 사이는 줄 사이보다 넓다. 이보다 가까우면 같은 칸이다
	const sameCell = (upper, lower) => lower.y0 - upper.y1 < lineHeight * 0.8;
	// 항목 이름이 여러 줄이면("「화장품법」에 따라 기재·표시하여야 하는 / 모든 성분") 그 첫 줄부터 내용이다
	let start = labelAt;
	while (start > 0 && hasLabel(rows[start - 1]) && sameCell(rows[start - 1], rows[start])) start -= 1;
	const picked = [];
	for (let i = start; i < rows.length; i += 1) {
		// 전성분 칸이 끝나고 다음 항목(왼쪽 칸에 새 이름)이 시작되면 멈춘다
		if (i > labelAt && hasLabel(rows[i]) && !sameCell(rows[i - 1], rows[i])) break;
		const content = rows[i].words.filter((w) => w.bbox.x0 >= contentX).map((w) => w.text).join(' ');
		if (content) picked.push(content);
	}
	return picked.length ? picked.join('\n') : null;
}

// 작은 글자를 키우고 흑백으로 바꾼다(색 배경·옅은 글씨에서 잘 읽힌다). 원래 가로도 돌려준다
async function prepare(file) {
	const bitmap = await createImageBitmap(file);
	const scale = Math.min(MAX_SCALE, Math.max(1, TARGET_WIDTH / bitmap.width));
	const canvas = document.createElement('canvas');
	canvas.width = Math.round(bitmap.width * scale);
	canvas.height = Math.round(bitmap.height * scale);
	const ctx = canvas.getContext('2d');
	ctx.imageSmoothingQuality = 'high';
	ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
	const originalWidth = bitmap.width;
	bitmap.close();
	const pixels = ctx.getImageData(0, 0, canvas.width, canvas.height);
	const d = pixels.data;
	for (let i = 0; i < d.length; i += 4) {
		const v = 0.299 * d[i] + 0.587 * d[i + 1] + 0.114 * d[i + 2];
		d[i] = v;
		d[i + 1] = v;
		d[i + 2] = v;
	}
	ctx.putImageData(pixels, 0, 0);
	return { canvas, originalWidth };
}

/**
 * 이미지 파일의 글자를 읽어 { text, small, quality }를 돌려준다. text는 다듬은 글(상품정보 제공고시 표면 전성분 칸만),
 * small은 캡처가 작다는 뜻, quality는 gradeOcr 등급이다. OCR_TIMEOUT_MS 안에 못 끝나면 OcrTimeoutError.
 * onProgress(0~100)는 글자를 읽는 동안 불린다(처음엔 데이터 받는 시간이 더 걸린다)
 */
export function readIngredientText(file, onProgress = () => {}) {
	return withTimeout(read(file, onProgress), OCR_TIMEOUT_MS);
}

async function read(file, onProgress) {
	const [{ createWorker }, { canvas, originalWidth }] = await Promise.all([import('tesseract.js'), prepare(file)]);
	const worker = await createWorker(['kor', 'eng'], 1, {
		workerPath: `${BASE}/worker.min.js`,
		corePath: `${BASE}/core`,
		langPath: `${BASE}/lang`,
		gzip: true,
		logger: (m) => {
			if (m.status === 'recognizing text') onProgress(Math.round(m.progress * 100));
		},
	});
	try {
		const { data } = await worker.recognize(canvas, {}, { text: true, blocks: true });
		const lines = (data.blocks || []).flatMap((b) => b.paragraphs.flatMap((p) => p.lines));
		const text = cleanOcrText(ingredientSection(lines, canvas.width) ?? data.text);
		const small = originalWidth < SMALL_IMAGE_WIDTH;
		const words = lines.flatMap((l) => l.words);
		return { text, small, quality: gradeOcr({ confidence: data.confidence, words, text, small }) };
	} finally {
		await worker.terminate();
	}
}

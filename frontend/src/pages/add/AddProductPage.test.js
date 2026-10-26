// user-event 13은 최상위 @testing-library/dom(10)을 써서 act로 감싸지지 않는다. RTL의 fireEvent를 쓴다
import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { createExtraction, createManualExtraction, getExtraction } from '../../api/extractions';
import { confirmProduct, getProduct, matchIngredientText, searchProducts } from '../../api/products';
import { addToShelf } from '../../api/shelf';
import useIsMobile from '../../hooks/useIsMobile';
import { loadDraft, saveDraft } from '../../lib/draftStorage';
import { readIngredientText } from '../../lib/ocr';
import { resizeToJpeg } from '../../lib/resizeImage';
import AddProductPage from './AddProductPage';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/products', () => ({
	getProduct: jest.fn(),
	confirmProduct: jest.fn(),
	searchProducts: jest.fn(),
	matchIngredientText: jest.fn(),
}));
jest.mock('../../api/extractions', () => ({
	createExtraction: jest.fn(),
	createManualExtraction: jest.fn(),
	getExtraction: jest.fn(),
}));
// 브라우저 글자 읽기(tesseract)는 테스트에서 돌리지 않는다
jest.mock('../../lib/ocr', () => ({
	...jest.requireActual('../../lib/ocr'),
	readIngredientText: jest.fn(),
}));
jest.mock('../../api/shelf', () => ({
	addToShelf: jest.fn(),
}));
jest.mock('../../lib/resizeImage', () => ({
	...jest.requireActual('../../lib/resizeImage'),
	resizeToJpeg: jest.fn(),
}));
jest.mock('../../hooks/useIsMobile', () => ({
	__esModule: true,
	default: jest.fn(),
}));

const originalCreateObjectURL = URL.createObjectURL;
const originalRevokeObjectURL = URL.revokeObjectURL;

// 눌러서 시작된 비동기 처리(Promise)를 끝낸다
async function flush() {
	await act(async () => {
		for (let i = 0; i < 10; i += 1) {
			await Promise.resolve();
		}
	});
}

function renderAdd() {
	return render(
		<MemoryRouter initialEntries={['/add']} future={FUTURE}>
			<Routes>
				<Route path="/add" element={<AddProductPage />} />
				<Route path="/shelf" element={<h1>내 화장대 화면</h1>} />
			</Routes>
		</MemoryRouter>
	);
}

function serverError(status, code, message) {
	const error = new Error(code);
	error.response = { status, data: { code, message, traceId: 'abc123' } };
	return error;
}

// 가짜 타이머에서 시간을 보내고, 그 사이 쌓인 Promise 처리와 화면 갱신을 끝낸다
async function advance(ms) {
	await act(async () => {
		jest.advanceTimersByTime(ms);
		for (let i = 0; i < 10; i += 1) {
			await Promise.resolve();
		}
	});
}

async function searchFor(query) {
	fireEvent.change(screen.getByLabelText('브랜드나 제품명'), { target: { value: query } });
	fireEvent.click(screen.getByRole('button', { name: '찾기' }));
	await flush();
}

async function choosePhotoAndSubmit(name = 'label.jpg', type = 'image/jpeg') {
	const file = new File(['photo'], name, { type });
	fireEvent.change(screen.getByLabelText('전성분 사진 고르기'), { target: { files: [file] } });
	fireEvent.click(screen.getByRole('button', { name: '성분 읽기' }));
	await flush();
	return file;
}

const originalPhotoAi = process.env.REACT_APP_PHOTO_AI;

beforeEach(() => {
	jest.clearAllMocks();
	// 기존 사진(AI) 흐름 테스트는 사진을 켠 상태로 돈다. 숨긴 상태는 아래 따로 확인한다
	process.env.REACT_APP_PHOTO_AI = 'true';
	useIsMobile.mockReturnValue(false);
	URL.createObjectURL = jest.fn(() => 'blob:preview');
	URL.revokeObjectURL = jest.fn();
	resizeToJpeg.mockResolvedValue(new Blob(['jpeg'], { type: 'image/jpeg' }));
	getProduct.mockResolvedValue({
		product: { id: 5 },
		representative: { id: 50, status: 'VERIFIED', confirmCount: 2, items: [{ position: 1, name: '정제수' }] },
		otherLists: [],
	});
});

afterEach(() => {
	window.localStorage.clear();
	URL.createObjectURL = originalCreateObjectURL;
	URL.revokeObjectURL = originalRevokeObjectURL;
	jest.useRealTimers();
});

test('이름으로 찾은 제품을 고르면 대표 성분을 보여주고 담는다', async () => {
	searchProducts.mockResolvedValueOnce([
		{ id: 5, brand: '브랜드A', name: '수분크림', category: 'SKINCARE', representativeListId: 50 },
		{ id: 6, brand: '브랜드A', name: '새 크림', category: 'SKINCARE', representativeListId: null },
	]);
	addToShelf.mockResolvedValue({ id: 1 });
	renderAdd();

	await searchFor('수분크림');

	expect(searchProducts).toHaveBeenCalledWith('수분크림');
	expect(screen.getByRole('button', { name: '성분 확인 전' })).toBeDisabled();
	fireEvent.click(screen.getByRole('button', { name: '이 제품이에요' }));
	await flush();
	expect(getProduct).toHaveBeenCalledWith(5);
	expect(screen.getByText('브랜드A 수분크림')).toBeInTheDocument();
	expect(screen.getByText('정제수')).toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: '내 화장대에 담기' }));
	await flush();
	expect(addToShelf).toHaveBeenCalledWith(50);
	expect(await screen.findByRole('heading', { name: '내 화장대 화면' })).toBeInTheDocument();
});

test('찾는 제품이 없으면 붙여넣기로 등록하라고 안내한다', async () => {
	searchProducts.mockResolvedValueOnce([]);
	createManualExtraction.mockResolvedValue({ jobId: 32 });
	renderAdd();

	await searchFor('없는크림');

	expect(screen.getByRole('status')).toHaveTextContent("'없는크림' 제품은 아직 없어요");
	fireEvent.click(screen.getByRole('button', { name: '찾는 제품이 없어요 (붙여넣기로 등록)' }));
	await flush();
	expect(createManualExtraction).toHaveBeenCalledTimes(1);
	expect(screen.getByLabelText('전성분 붙여넣기')).toBeInTheDocument();
});

test('담기가 실패하면 서버 메시지를 보여준다', async () => {
	searchProducts.mockResolvedValue([{ id: 5, brand: '브랜드A', name: '수분크림', representativeListId: 50 }]);
	addToShelf.mockRejectedValue(serverError(409, 'SHELF_ITEM_DUPLICATE', '이미 내 화장대에 있어요.'));
	renderAdd();
	await searchFor('수분크림');
	fireEvent.click(screen.getByRole('button', { name: '이 제품이에요' }));
	await flush();

	fireEvent.click(await screen.findByRole('button', { name: '내 화장대에 담기' }));
	await flush();

	expect(screen.getByRole('alert')).toHaveTextContent('이미 내 화장대에 있어요.');
});

test('사진을 고르면 잘라서 줄인 JPEG를 올리고 결과를 기다린다', async () => {
	jest.useFakeTimers();
	createExtraction.mockResolvedValue({ jobId: 9 });
	getExtraction.mockResolvedValue({ jobId: 9, status: 'DONE', result: { items: [{}, {}], warning: null } });
	renderAdd();
	fireEvent.click(screen.getByRole('tab', { name: '사진(AI)' }));

	const file = await choosePhotoAndSubmit();

	expect(resizeToJpeg).toHaveBeenCalledWith(file, null);
	expect(createExtraction).toHaveBeenCalledWith(expect.any(Blob));
	expect(screen.getByText('성분을 읽고 있어요...')).toBeInTheDocument();
	await advance(1500);
	expect(getExtraction).toHaveBeenCalledWith(9);
	expect(screen.getByRole('heading', { name: '성분 확인' })).toBeInTheDocument();
});

test('업로드 응답 429면 서버 메시지를 보여준다', async () => {
	createExtraction.mockRejectedValue(
		serverError(429, 'EXTRACTION_DAILY_LIMIT', '오늘 성분 읽기 횟수(20회)를 모두 사용했어요.')
	);
	renderAdd();
	fireEvent.click(screen.getByRole('tab', { name: '사진(AI)' }));

	await choosePhotoAndSubmit();

	expect(screen.getByRole('alert')).toHaveTextContent('오늘 성분 읽기 횟수(20회)를 모두 사용했어요.');
	expect(screen.getByRole('button', { name: '성분 읽기' })).toBeEnabled();
	expect(getExtraction).not.toHaveBeenCalled();
});

test('heicDecodeFailure_showsMessage', async () => {
	resizeToJpeg.mockRejectedValue(new Error('UNSUPPORTED_IMAGE'));
	renderAdd();
	fireEvent.click(screen.getByRole('tab', { name: '사진(AI)' }));

	await choosePhotoAndSubmit('IMG_0001.HEIC', 'image/heic');

	expect(screen.getByRole('alert')).toHaveTextContent(
		'이 사진 형식은 읽을 수 없어요. JPEG로 찍거나 다른 사진을 골라 주세요.'
	);
	expect(createExtraction).not.toHaveBeenCalled();
});

test('90초가 지나면 [다시 확인] 버튼이 보이고 누르면 다시 조회', async () => {
	jest.useFakeTimers();
	createExtraction.mockResolvedValue({ jobId: 9 });
	getExtraction.mockResolvedValue({ jobId: 9, status: 'RUNNING' });
	renderAdd();
	fireEvent.click(screen.getByRole('tab', { name: '사진(AI)' }));
	await choosePhotoAndSubmit();

	for (let i = 0; i < 59; i += 1) {
		await advance(1500);
	}
	expect(screen.queryByRole('button', { name: '다시 확인' })).not.toBeInTheDocument();
	await advance(1500);
	expect(getExtraction).toHaveBeenCalledTimes(60);
	expect(screen.getByRole('button', { name: '다시 확인' })).toBeInTheDocument();
	await advance(15000);
	expect(getExtraction).toHaveBeenCalledTimes(60);

	getExtraction.mockResolvedValue({ jobId: 9, status: 'DONE', result: { items: [] } });
	fireEvent.click(screen.getByRole('button', { name: '다시 확인' }));
	expect(screen.getByText('성분을 읽고 있어요...')).toBeInTheDocument();
	await advance(1500);

	expect(getExtraction).toHaveBeenCalledTimes(61);
	expect(screen.getByRole('heading', { name: '성분 확인' })).toBeInTheDocument();
});

test('FAILED면 실패 이유와 "성분을 직접 붙여넣기" 버튼', async () => {
	jest.useFakeTimers();
	createExtraction.mockResolvedValue({ jobId: 9 });
	getExtraction.mockResolvedValue({ jobId: 9, status: 'FAILED', errorCode: 'AI_UNAVAILABLE', result: null });
	renderAdd();
	fireEvent.click(screen.getByRole('tab', { name: '사진(AI)' }));
	await choosePhotoAndSubmit();

	await advance(1500);

	expect(screen.getByRole('alert')).toHaveTextContent('지금 성분 읽기 서비스를 쓸 수 없어요.');
	expect(screen.getByRole('alert')).toHaveTextContent('오늘 횟수에는 세지 않았어요.');
	expect(screen.getByRole('button', { name: '다시 찍기' })).toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: '성분을 직접 붙여넣기' }));
	expect(screen.getByRole('heading', { name: '성분 확인' })).toBeInTheDocument();
});

test.each([
	['EXTRACTION_IN_PROGRESS', 429, '이미 성분을 읽고 있는 사진이 있어요.'],
	['IMAGE_DIMENSIONS_TOO_LARGE', 400, '사진 크기가 너무 커요.'],
	['UNSUPPORTED_IMAGE', 415, '이 사진 형식은 읽을 수 없어요.'],
])('업로드 %s면 서버 메시지를 보여주고 사진 단계에 머문다', async (code, status, message) => {
	createExtraction.mockRejectedValue(serverError(status, code, message));
	renderAdd();
	fireEvent.click(screen.getByRole('tab', { name: '사진(AI)' }));

	await choosePhotoAndSubmit();

	expect(screen.getByRole('alert')).toHaveTextContent(message);
	expect(screen.getByRole('button', { name: '성분 읽기' })).toBeEnabled();
	expect(getExtraction).not.toHaveBeenCalled();
});

test('업로드 AI_BUSY(503)면 고정 안내를 보여준다', async () => {
	createExtraction.mockRejectedValue(serverError(503, 'AI_BUSY', '서버 원문 메시지'));
	renderAdd();
	fireEvent.click(screen.getByRole('tab', { name: '사진(AI)' }));

	await choosePhotoAndSubmit();

	expect(screen.getByRole('alert')).toHaveTextContent('지금 분석 요청이 많아요. 잠시 후 다시 시도해 주세요.');
	expect(screen.getByRole('button', { name: '성분 읽기' })).toBeEnabled();
});

test('업로드 중 응답이 없으면(네트워크 오류) 다시 찍으라고 안내한다', async () => {
	createExtraction.mockRejectedValue(new Error('Network Error'));
	renderAdd();
	fireEvent.click(screen.getByRole('tab', { name: '사진(AI)' }));

	await choosePhotoAndSubmit();

	expect(screen.getByRole('alert')).toHaveTextContent('사진을 올리지 못했어요. 사진이 너무 크면 다시 찍어 주세요.');
	expect(screen.getByRole('button', { name: '성분 읽기' })).toBeEnabled();
});

test.each(['AI_BUSY', 'INTERNAL_ERROR', 'SOMETHING_NEW'])('FAILED %s면 일반 실패 안내와 같은 버튼', async (errorCode) => {
	jest.useFakeTimers();
	createExtraction.mockResolvedValue({ jobId: 9 });
	getExtraction.mockResolvedValue({ jobId: 9, status: 'FAILED', errorCode, result: null });
	renderAdd();
	fireEvent.click(screen.getByRole('tab', { name: '사진(AI)' }));
	await choosePhotoAndSubmit();

	await advance(1500);

	expect(screen.getByRole('alert')).toHaveTextContent('잠시 문제가 생겼어요. 다시 시도해 주세요.');
	expect(screen.getByRole('alert')).toHaveTextContent('오늘 횟수에는 세지 않았어요.');
	expect(screen.getByRole('button', { name: '다시 찍기' })).toBeInTheDocument();
	expect(screen.getByRole('button', { name: '성분을 직접 붙여넣기' })).toBeInTheDocument();
});

test('화면을 떠나면 조회를 멈춘다', async () => {
	jest.useFakeTimers();
	createExtraction.mockResolvedValue({ jobId: 9 });
	getExtraction.mockResolvedValue({ jobId: 9, status: 'RUNNING' });
	const { unmount } = renderAdd();
	fireEvent.click(screen.getByRole('tab', { name: '사진(AI)' }));
	await choosePhotoAndSubmit();
	await advance(1500);
	expect(getExtraction).toHaveBeenCalledTimes(1);

	unmount();
	await advance(6000);

	expect(getExtraction).toHaveBeenCalledTimes(1);
	expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview');
});

test('폰은 단계별 한 화면, PC는 왼쪽 단계와 오른쪽 사진 미리보기', async () => {
	useIsMobile.mockReturnValue(true);
	const { unmount } = renderAdd();
	expect(screen.getByRole('region', { name: '제품 추가' })).toHaveAttribute('data-layout', 'mobile');
	fireEvent.click(screen.getByRole('tab', { name: '사진(AI)' }));
	fireEvent.change(screen.getByLabelText('전성분 사진 고르기'), {
		target: { files: [new File(['p'], 'a.jpg', { type: 'image/jpeg' })] },
	});
	expect(screen.getByAltText('고른 사진')).toHaveAttribute('src', 'blob:preview');
	unmount();

	useIsMobile.mockReturnValue(false);
	renderAdd();
	expect(screen.getByRole('region', { name: '제품 추가' })).toHaveAttribute('data-layout', 'desktop');
	// 사진을 고르기 전 오른쪽 칸은 전성분으로 바로 등록하는 곳이다
	expect(screen.getByRole('region', { name: '전성분으로 등록하기' })).toBeInTheDocument();
	fireEvent.click(screen.getByRole('tab', { name: '사진(AI)' }));
	expect(screen.getByRole('region', { name: '사진 미리보기' })).toBeInTheDocument();
});

test('PC 오른쪽 칸: "전성분 글 붙여넣기"는 바로 붙여넣기 확인 화면으로', async () => {
	createManualExtraction.mockResolvedValue({ jobId: 41 });
	renderAdd();

	fireEvent.click(screen.getByRole('button', { name: '전성분 글 붙여넣기' }));
	await flush();

	expect(createManualExtraction).toHaveBeenCalledTimes(1);
	expect(screen.getByLabelText('전성분 붙여넣기')).toHaveValue('');
	expect(readIngredientText).not.toHaveBeenCalled();
});

test('PC 오른쪽 칸: 캡처 이미지를 올리면 붙여넣기 화면에서 브라우저가 글자를 읽어 칸에 채운다(바로 넣지는 않는다)', async () => {
	createManualExtraction.mockResolvedValue({ jobId: 42 });
	readIngredientText.mockResolvedValue({ text: '정제수, 글리세린, 판테놀', small: false, quality: 'good' });
	renderAdd();
	const image = new File(['png'], 'capture.png', { type: 'image/png' });

	fireEvent.change(screen.getByLabelText('캡처 이미지로 읽기'), { target: { files: [image] } });
	await flush();

	expect(createManualExtraction).toHaveBeenCalledTimes(1);
	expect(readIngredientText).toHaveBeenCalledWith(image, expect.any(Function));
	expect(screen.getByLabelText('전성분 붙여넣기')).toHaveValue('정제수, 글리세린, 판테놀');
	expect(screen.getByText(/읽은 글을 칸에 넣었어요/)).toBeInTheDocument();
	expect(screen.queryByText(/확실하게 읽지 못했어요/)).not.toBeInTheDocument();
	expect(matchIngredientText).not.toHaveBeenCalled();
});

test('PC 오른쪽 칸: 캡처 이미지는 Ctrl+V로 붙여넣어도 읽는다', async () => {
	createManualExtraction.mockResolvedValue({ jobId: 43 });
	readIngredientText.mockResolvedValue({ text: '정제수', small: true, quality: 'unsure' });
	renderAdd();
	const image = new File(['png'], 'capture.png', { type: 'image/png' });

	// 칸 안 어디에서 붙여넣어도 칸이 받는다
	fireEvent.paste(screen.getByRole('button', { name: '전성분 글 붙여넣기' }), {
		clipboardData: { items: [{ kind: 'file', getAsFile: () => image }] },
	});
	await flush();

	expect(readIngredientText).toHaveBeenCalledWith(image, expect.any(Function));
	expect(screen.getByLabelText('전성분 붙여넣기')).toHaveValue('정제수');
	// 확실하지 않은 결과(작은 캡처)는 칸에 넣되 확인하라고 알린다
	expect(screen.getByText(/글자를 확실하게 읽지 못했어요/)).toBeInTheDocument();
});

async function openPasteAndRead(result) {
	createManualExtraction.mockResolvedValue({ jobId: 45 });
	if (result instanceof Error) readIngredientText.mockRejectedValue(result);
	else readIngredientText.mockResolvedValue(result);
	renderAdd();
	fireEvent.click(screen.getByRole('tab', { name: '상세페이지 붙여넣기' }));
	await flush();
	fireEvent.change(screen.getByLabelText('캡처 이미지에서 글자 읽기'), {
		target: { files: [new File(['jpg'], 'c.jpg', { type: 'image/jpeg' })] },
	});
	await flush();
}

test('붙여넣기 화면: 전성분으로 볼 수 없는 결과(failed)는 칸에 넣지 않고 다른 방법을 안내한다', async () => {
	await openPasteAndRead({ text: 'ny a8e zou ME', small: false, quality: 'failed' });

	expect(screen.getByLabelText('전성분 붙여넣기')).toHaveValue('');
	expect(screen.getByRole('alert')).toHaveTextContent('이 이미지에서는 전성분을 읽지 못했어요');
	expect(screen.getByRole('alert')).toHaveTextContent('구글 렌즈');
	expect(screen.getByRole('button', { name: '목록에 넣기' })).toBeDisabled();
});

test('붙여넣기 화면: 글자 읽기가 오래 걸려 멈추면 알린다', async () => {
	const { OcrTimeoutError } = jest.requireActual('../../lib/ocr');
	await openPasteAndRead(new OcrTimeoutError('timeout'));

	expect(screen.getByRole('alert')).toHaveTextContent('너무 오래 걸려서 멈췄어요');
	expect(screen.getByLabelText('전성분 붙여넣기')).not.toBeDisabled();
});

test('붙여넣기 화면: 읽은 글을 넣었는데 사전과 맞는 성분이 절반도 안 되면 알리고, 방금 넣은 성분을 한 번에 뺄 수 있다', async () => {
	matchIngredientText.mockResolvedValue({
		sections: [{
			label: null,
			items: [
				{ position: 1, rawText: '청제수', type: 'UNMATCHED', ingredientId: null, standardName: null, candidates: [] },
				{ position: 2, rawText: '니야야산야마이드', type: 'UNMATCHED', ingredientId: null, standardName: null, candidates: [] },
				{ position: 3, rawText: '트로메타민', type: 'EXACT', ingredientId: 3, standardName: '트로메타민', candidates: [] },
			],
		}],
	});
	await openPasteAndRead({ text: '청제수, 니야야산야마이드, 트로메타민', small: false, quality: 'good' });

	fireEvent.click(screen.getByRole('button', { name: '목록에 넣기' }));
	await flush();

	expect(screen.getByRole('alert')).toHaveTextContent('읽은 성분 3개 중 사전과 맞는 성분이 1개뿐이에요');
	expect(screen.getByDisplayValue('청제수')).toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: '방금 넣은 성분 3개 빼기' }));
	expect(screen.queryByDisplayValue('청제수')).not.toBeInTheDocument();
	expect(screen.queryByDisplayValue('트로메타민')).not.toBeInTheDocument();
	expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('직접 붙여넣은 글(글자 읽기가 아님)은 사전에 덜 맞아도 글자 읽기 경고를 띄우지 않는다', async () => {
	createManualExtraction.mockResolvedValue({ jobId: 46 });
	matchIngredientText.mockResolvedValue({
		sections: [{ label: null, items: [{ position: 1, rawText: '새성분', type: 'UNMATCHED', ingredientId: null, standardName: null, candidates: [] }] }],
	});
	renderAdd();
	fireEvent.click(screen.getByRole('tab', { name: '상세페이지 붙여넣기' }));
	await flush();
	fireEvent.change(screen.getByLabelText('전성분 붙여넣기'), { target: { value: '새성분' } });
	fireEvent.click(screen.getByRole('button', { name: '목록에 넣기' }));
	await flush();

	expect(screen.queryByText(/사전과 맞는 성분이/)).not.toBeInTheDocument();
});

test('붙여넣기 화면: 글자를 못 읽으면 안내하고 직접 붙여넣을 수 있게 둔다', async () => {
	createManualExtraction.mockResolvedValue({ jobId: 44 });
	readIngredientText.mockRejectedValue(new Error('worker failed'));
	renderAdd();
	fireEvent.click(screen.getByRole('tab', { name: '상세페이지 붙여넣기' }));
	await flush();

	fireEvent.change(screen.getByLabelText('캡처 이미지에서 글자 읽기'), {
		target: { files: [new File(['jpg'], 'c.jpg', { type: 'image/jpeg' })] },
	});
	await flush();

	expect(screen.getByRole('alert')).toHaveTextContent('글자를 읽지 못했어요');
	expect(screen.getByLabelText('전성분 붙여넣기')).not.toBeDisabled();
});

test('새로고침해도 주소의 job으로 성분 확인 화면이 돌아온다', async () => {
	getExtraction.mockResolvedValueOnce({
		jobId: 9,
		status: 'DONE',
		errorCode: null,
		result: {
			ingredientSectionFound: true,
			unreadableParts: [],
			warning: null,
			items: [{ position: 1, rawText: '정제수', type: 'EXACT', ingredientId: 1, standardName: '정제수', candidates: [] }],
		},
	});
	render(
		<MemoryRouter initialEntries={['/add?job=9']} future={FUTURE}>
			<Routes>
				<Route path="/add" element={<AddProductPage />} />
			</Routes>
		</MemoryRouter>
	);
	await flush();

	expect(getExtraction).toHaveBeenCalledWith(9);
	expect(screen.getByRole('heading', { name: '성분 확인' })).toBeInTheDocument();
	expect(screen.getByText('정제수')).toBeInTheDocument();
});

test('주소의 job이 이미 저장되어 결과가 없으면 안내하고 제품 찾기 단계로', async () => {
	getExtraction.mockResolvedValueOnce({ jobId: 9, status: 'DONE', errorCode: null, result: null });
	render(
		<MemoryRouter initialEntries={['/add?job=9']} future={FUTURE}>
			<Routes>
				<Route path="/add" element={<AddProductPage />} />
			</Routes>
		</MemoryRouter>
	);
	await flush();

	expect(screen.getByRole('alert')).toHaveTextContent('이미 저장한 성분 읽기 결과예요.');
	expect(screen.getByRole('heading', { name: '제품 찾기' })).toBeInTheDocument();
});

function renderAt(url) {
	return render(
		<MemoryRouter initialEntries={[url]} future={FUTURE}>
			<Routes>
				<Route path="/add" element={<AddProductPage />} />
				<Route path="/shelf" element={<h1>내 화장대 화면</h1>} />
			</Routes>
		</MemoryRouter>
	);
}

test('직접 붙여넣어 이미 저장한 FAILED job(confirmed)이면 ?paste=1로 새로고침해도 확인 화면 대신 안내하고 제품 찾기 단계로', async () => {
	saveDraft(9, { items: [], form: { brand: '', name: '', volume: '', barcode: '', category: 'SKINCARE' }, chosenProduct: null });
	getExtraction.mockResolvedValueOnce({ jobId: 9, status: 'FAILED', errorCode: 'AI_TIMEOUT', result: null, confirmed: true });
	renderAt('/add?job=9&paste=1');
	await flush();

	expect(screen.getByRole('alert')).toHaveTextContent('이미 저장한 성분 읽기 결과예요.');
	expect(screen.getByRole('heading', { name: '제품 찾기' })).toBeInTheDocument();
	expect(screen.queryByRole('heading', { name: '성분 확인' })).not.toBeInTheDocument();
	expect(loadDraft(9)).toBeNull();
});

test('저장하지 않은 FAILED job(confirmed=false)은 ?paste=1로 새로고침하면 직접 붙여넣기 확인 화면', async () => {
	getExtraction.mockResolvedValueOnce({ jobId: 9, status: 'FAILED', errorCode: 'AI_TIMEOUT', result: null, confirmed: false });
	renderAt('/add?job=9&paste=1');
	await flush();

	expect(screen.getByRole('heading', { name: '성분 확인' })).toBeInTheDocument();
});

test('저장 중 409 EXTRACTION_ALREADY_CONFIRMED면 임시 보관을 지우고 안내와 함께 제품 찾기 단계로', async () => {
	getExtraction.mockResolvedValueOnce({
		jobId: 9,
		status: 'DONE',
		errorCode: null,
		confirmed: false,
		result: {
			ingredientSectionFound: true,
			unreadableParts: [],
			warning: null,
			items: [{ position: 1, rawText: '정제수', type: 'EXACT', ingredientId: 1, standardName: '정제수', candidates: [] }],
		},
	});
	confirmProduct.mockRejectedValueOnce(serverError(409, 'EXTRACTION_ALREADY_CONFIRMED', '이미 저장한 결과예요.'));
	renderAt('/add?job=9');
	await flush();

	fireEvent.change(screen.getByLabelText('브랜드'), { target: { value: '브랜드A' } });
	fireEvent.change(screen.getByLabelText('제품명'), { target: { value: '수분크림' } });
	expect(loadDraft(9)).not.toBeNull();
	fireEvent.click(screen.getByRole('button', { name: '확인하고 화장대에 담기' }));
	await flush();

	expect(screen.getByRole('alert')).toHaveTextContent('이미 저장한 성분 읽기 결과예요.');
	expect(screen.getByRole('heading', { name: '제품 찾기' })).toBeInTheDocument();
	expect(loadDraft(9)).toBeNull();
});

test('등록 방법 탭: 제품 찾기·사진(AI)·상세페이지 붙여넣기 중 고른다', async () => {
	renderAdd();

	expect(screen.getByRole('tab', { name: '제품 찾기' })).toHaveAttribute('aria-selected', 'true');
	fireEvent.click(screen.getByRole('tab', { name: '사진(AI)' }));
	expect(screen.getByRole('heading', { name: '전성분 사진' })).toBeInTheDocument();
	expect(screen.getByRole('tab', { name: '사진(AI)' })).toHaveAttribute('aria-selected', 'true');
	fireEvent.click(screen.getByRole('tab', { name: '제품 찾기' }));
	expect(screen.getByRole('heading', { name: '제품 찾기' })).toBeInTheDocument();
});

test('상세페이지 붙여넣기 탭: AI 없이 직접 입력 job을 만들고 붙여넣기 확인 화면으로', async () => {
	createManualExtraction.mockResolvedValue({ jobId: 31 });
	renderAdd();

	fireEvent.click(screen.getByRole('tab', { name: '상세페이지 붙여넣기' }));
	await flush();

	expect(createManualExtraction).toHaveBeenCalledTimes(1);
	expect(createExtraction).not.toHaveBeenCalled();
	expect(screen.getByRole('heading', { name: '성분 확인' })).toBeInTheDocument();
	expect(screen.getByLabelText('전성분 붙여넣기')).toBeInTheDocument();
});

test('붙여넣기 시작이 한도 초과면 서버 메시지를 보여주고 그 자리에 머문다', async () => {
	createManualExtraction.mockRejectedValue(
		serverError(429, 'EXTRACTION_DAILY_LIMIT', '오늘 성분 읽기 횟수(20회)를 모두 사용했어요.')
	);
	renderAdd();

	fireEvent.click(screen.getByRole('tab', { name: '상세페이지 붙여넣기' }));
	await flush();

	expect(screen.getByRole('alert')).toHaveTextContent('오늘 성분 읽기 횟수(20회)를 모두 사용했어요.');
	expect(screen.getByRole('heading', { name: '제품 찾기' })).toBeInTheDocument();
});

test('바로 붙여넣기 화면에서도 등록 방법 탭으로 사진(AI)·제품 찾기로 바꿀 수 있다', async () => {
	createManualExtraction.mockResolvedValue({ jobId: 33 });
	renderAdd();
	fireEvent.click(screen.getByRole('tab', { name: '상세페이지 붙여넣기' }));
	await flush();

	expect(screen.getByRole('tab', { name: '상세페이지 붙여넣기' })).toHaveAttribute('aria-selected', 'true');
	fireEvent.click(screen.getByRole('tab', { name: '사진(AI)' }));
	expect(screen.getByRole('heading', { name: '전성분 사진' })).toBeInTheDocument();

	fireEvent.click(screen.getByRole('tab', { name: '상세페이지 붙여넣기' }));
	await flush();
	fireEvent.click(screen.getByRole('tab', { name: '제품 찾기' }));
	expect(screen.getByRole('heading', { name: '제품 찾기' })).toBeInTheDocument();
});

test('AI가 실패한 뒤 붙여넣기 화면에는 등록 방법 탭을 두지 않는다', async () => {
	jest.useFakeTimers();
	createExtraction.mockResolvedValue({ jobId: 9 });
	getExtraction.mockResolvedValue({ jobId: 9, status: 'FAILED', errorCode: 'AI_UNAVAILABLE', result: null });
	renderAdd();
	fireEvent.click(screen.getByRole('tab', { name: '사진(AI)' }));
	await choosePhotoAndSubmit();
	await advance(1500);

	fireEvent.click(screen.getByRole('button', { name: '성분을 직접 붙여넣기' }));

	expect(screen.getByRole('heading', { name: '성분 확인' })).toBeInTheDocument();
	expect(screen.queryByRole('tablist', { name: '제품 등록 방법' })).not.toBeInTheDocument();
});

describe('사진(AI)을 숨긴 기본 설정', () => {
	beforeEach(() => {
		delete process.env.REACT_APP_PHOTO_AI;
	});

	afterAll(() => {
		if (originalPhotoAi === undefined) delete process.env.REACT_APP_PHOTO_AI;
		else process.env.REACT_APP_PHOTO_AI = originalPhotoAi;
	});

	test('탭은 제품 찾기·상세페이지 붙여넣기 두 개이고 버튼은 붙여넣기로 안내한다', async () => {
		createManualExtraction.mockResolvedValue({ jobId: 41 });
		renderAdd();

		expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['제품 찾기', '상세페이지 붙여넣기']);
		fireEvent.click(screen.getByRole('button', { name: '찾는 제품이 없어요 (붙여넣기로 등록)' }));
		await flush();

		expect(createManualExtraction).toHaveBeenCalledTimes(1);
		expect(screen.getByLabelText('전성분 붙여넣기')).toBeInTheDocument();
		expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['제품 찾기', '상세페이지 붙여넣기']);
	});
});

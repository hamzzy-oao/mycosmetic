// user-event 13은 act로 감싸지지 않아 RTL의 fireEvent를 쓴다
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { confirmProduct, matchIngredientText, previewProductLink, searchProducts } from '../../api/products';
import useIsMobile from '../../hooks/useIsMobile';
import { loadDraft, saveDraft } from '../../lib/draftStorage';
import ConfirmIngredients from './ConfirmIngredients';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/products', () => ({
	confirmProduct: jest.fn(),
	searchProducts: jest.fn(),
	previewProductLink: jest.fn(),
	matchIngredientText: jest.fn(),
}));
jest.mock('../../hooks/useIsMobile', () => ({
	__esModule: true,
	default: jest.fn(),
}));

// 계획 Task 15의 result_json 형식 (items는 MatchResult)
function makeJob(overrides = {}) {
	return {
		jobId: 9,
		status: 'DONE',
		errorCode: null,
		result: {
			ingredientSectionFound: true,
			unreadableParts: [],
			warning: null,
			items: [
				{ position: 1, rawText: '정제수', type: 'EXACT', ingredientId: 1, standardName: '정제수', candidates: [] },
				{ position: 2, rawText: '니코틴아마이드', type: 'ALIAS', ingredientId: 3, standardName: '나이아신아마이드', candidates: [] },
				{
					position: 3,
					rawText: '글리세릴',
					type: 'FUZZY',
					ingredientId: null,
					standardName: null,
					candidates: [
						{ ingredientId: 2, standardName: '글리세린', similarity: 0.83 },
						{ ingredientId: 7, standardName: '글리세릴카프릴레이트', similarity: 0.8 },
					],
				},
				{ position: 4, rawText: '알수없는성분', type: 'UNMATCHED', ingredientId: null, standardName: null, candidates: [] },
			],
			...overrides,
		},
	};
}

async function flush() {
	await act(async () => {
		for (let i = 0; i < 10; i += 1) {
			await Promise.resolve();
		}
	});
}

function renderConfirm(props = {}) {
	const onRetake = props.onRetake || jest.fn();
	const element = (
		<ConfirmIngredients job={makeJob()} pasteMode={false} previewUrl="" onRetake={onRetake} {...props} />
	);
	return render(
		<MemoryRouter initialEntries={['/add']} future={FUTURE}>
			<Routes>
				<Route path="/add" element={element} />
				<Route path="/shelf" element={<h1>내 화장대 화면</h1>} />
			</Routes>
		</MemoryRouter>
	);
}

function fillProduct(brand = '브랜드A', name = '수분크림') {
	fireEvent.change(screen.getByLabelText('브랜드'), { target: { value: brand } });
	fireEvent.change(screen.getByLabelText('제품명'), { target: { value: name } });
}

async function save() {
	fireEvent.click(screen.getByRole('button', { name: '확인하고 화장대에 담기' }));
	await flush();
}

function sentItems() {
	return confirmProduct.mock.calls[0][0].items;
}

test('가져오는 중에도 링크 수정과 재요청이 가능하고 이전 응답은 무시한다', async () => {
	let resolveOld;
	previewProductLink.mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve; }))
		.mockResolvedValueOnce({ name: '새 제품' });
	renderConfirm();
	const input = screen.getByLabelText('상세페이지 링크');
	fireEvent.change(input, { target: { value: 'https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000000001' } });
	fireEvent.click(screen.getByRole('button', { name: '가져오기' }));
	expect(input).toBeEnabled();
	expect(screen.getByRole('button', { name: '다시 가져오기' })).toBeEnabled();
	const oldSignal = previewProductLink.mock.calls[0][1].signal;
	fireEvent.change(input, { target: { value: 'https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000000002' } });
	expect(oldSignal.aborted).toBe(true);
	fireEvent.click(screen.getByRole('button', { name: '가져오기' }));
	await flush();
	expect(screen.getByLabelText('제품명')).toHaveValue('새 제품');
	resolveOld({ name: '이전 제품' });
	await flush();
	expect(screen.getByLabelText('제품명')).toHaveValue('새 제품');
});

test('링크 요청 시간 초과 후 다시 가져올 수 있다', async () => {
	previewProductLink.mockRejectedValueOnce({ code: 'ECONNABORTED' });
	renderConfirm();
	fireEvent.change(screen.getByLabelText('상세페이지 링크'), { target: { value: 'https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000000001' } });
	fireEvent.click(screen.getByRole('button', { name: '가져오기' }));
	await flush();
	expect(screen.getByRole('alert')).toHaveTextContent('응답이 늦어');
	expect(screen.getByRole('button', { name: '가져오기' })).toBeEnabled();
});

const OY_LINK = 'https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000180162';
const match = (position, rawText, ingredientId = null, standardName = null) => ({
	position,
	rawText,
	type: ingredientId ? 'EXACT' : 'UNMATCHED',
	ingredientId,
	standardName,
	candidates: [],
});

test.each([false, true])('올리브영 링크: 제품명·용량·사용기한을 채우고, 이미 성분이 있으면 바꿀지 묻는다 (mobile=%s)', async (mobile) => {
	useIsMobile.mockReturnValue(mobile);
	previewProductLink.mockResolvedValue({
		name: '그린 마일드 업 선 플러스',
		imageUrl: 'https://image.oliveyoung.co.kr/a.jpg',
		notice: '제품명·사진·전성분을 가져왔어요. 내 제품과 같은지 확인해 주세요.',
		ingredientSections: [{ label: null, items: [match(1, '정제수', 1, '정제수'), match(2, '징크옥사이드')] }],
		volume: '50mL+20mL(증정)',
		paoMonths: 6,
	});
	renderConfirm();
	fireEvent.paste(screen.getByLabelText('상세페이지 링크'), { clipboardData: { getData: () => OY_LINK } });
	await flush();

	expect(previewProductLink).toHaveBeenCalledWith(OY_LINK, { signal: expect.any(AbortSignal) });
	expect(screen.getByLabelText('제품명')).toHaveValue('그린 마일드 업 선 플러스');
	expect(screen.getByLabelText('개봉 후 사용기한(개월)')).toHaveValue(6);
	expect(screen.getByText('나이아신아마이드')).toBeInTheDocument(); // 기존 성분 그대로
	fireEvent.click(screen.getByRole('button', { name: '이 전성분으로 바꾸기' }));
	fillProduct('닥터지', '그린 마일드 업 선 플러스');
	await save();

	const body = confirmProduct.mock.calls[0][0];
	expect(body.items).toEqual([
		{ ingredientId: 1, rawText: '정제수' },
		{ ingredientId: null, rawText: '징크옥사이드' },
	]);
	expect(body.newProduct).toMatchObject({ name: '그린 마일드 업 선 플러스', volume: '50mL+20mL(증정)', barcode: null });
	expect(body.paoMonths).toBe(6);
});

test('올리브영 링크: 성분이 비어 있으면 바로 채우고, 옵션이 여럿이면 고르게 한다', async () => {
	const failed = { jobId: 12, status: 'FAILED', errorCode: 'MANUAL_INPUT', result: null };
	previewProductLink
		.mockResolvedValueOnce({ name: '선크림', ingredientSections: [{ label: null, items: [match(1, '정제수')] }] })
		.mockResolvedValueOnce({
			name: '쿠션',
			ingredientSections: [
				{ label: '크림', items: [match(1, '정제수'), match(2, '마이카')] },
				{ label: '페어', items: [match(1, '정제수'), match(2, '탤크'), match(3, '마이카')] },
			],
		});
	renderConfirm({ job: failed, pasteMode: true });
	const input = screen.getByLabelText('상세페이지 링크');
	fireEvent.change(input, { target: { value: OY_LINK } });
	fireEvent.click(screen.getByRole('button', { name: '가져오기' }));
	await flush();
	expect(screen.getByText(/성분 1개/)).toBeInTheDocument();

	fireEvent.change(input, { target: { value: OY_LINK + '&x=1' } });
	fireEvent.click(screen.getByRole('button', { name: '가져오기' }));
	await flush();
	expect(screen.getByRole('group', { name: '옵션 2개' })).toBeInTheDocument();
	fireEvent.change(screen.getByLabelText('내 제품의 옵션'), { target: { value: '1' } });
	fireEvent.click(screen.getByRole('button', { name: '이 옵션 성분 넣기' }));
	expect(screen.getByText(/성분 3개/)).toBeInTheDocument();
	expect(screen.queryByRole('group', { name: '옵션 2개' })).not.toBeInTheDocument();
});

test('올리브영 접근 실패 시 기존 입력을 유지하고 서버 안내를 보여준다', async () => {
	previewProductLink.mockRejectedValue({
		response: { data: { message: '상품 정보를 가져오지 못했어요. 제품명을 직접 입력해 주세요.' } },
	});
	renderConfirm();
	fillProduct('기존 브랜드', '기존 제품');
	fireEvent.change(screen.getByLabelText('상세페이지 링크'), { target: { value: OY_LINK } });
	fireEvent.click(screen.getByRole('button', { name: '가져오기' }));
	await flush();
	expect(screen.getByRole('alert')).toHaveTextContent('상품 정보를 가져오지 못했어요');
	expect(screen.getByLabelText('제품명')).toHaveValue('기존 제품');
	expect(screen.getByLabelText('브랜드')).toHaveValue('기존 브랜드');
});

function mockClipboard(readText) {
	Object.defineProperty(navigator, 'clipboard', { value: { readText }, configurable: true });
}

test('검색 버튼은 입력한 브랜드·제품명으로 올리브영 검색을 새 탭에 연다', () => {
	renderConfirm();
	expect(screen.getByRole('button', { name: '올리브영에서 찾기' })).toBeDisabled();
	expect(screen.queryByText('화해에서 찾기')).not.toBeInTheDocument();
	fillProduct('닥터지', '선크림');
	const link = screen.getByRole('link', { name: '올리브영에서 찾기' });
	expect(link).toHaveAttribute(
		'href',
		`https://www.oliveyoung.co.kr/store/search/getSearchMain.do?query=${encodeURIComponent('닥터지 선크림')}`
	);
	expect(link).toHaveAttribute('target', '_blank');
	expect(link).toHaveAttribute('rel', 'noopener noreferrer');
	fireEvent.change(screen.getByLabelText('제품명으로 링크 찾기'), { target: { value: '레드 블레미쉬' } });
	expect(screen.getByRole('link', { name: '올리브영에서 찾기' })).toHaveAttribute(
		'href',
		`https://www.oliveyoung.co.kr/store/search/getSearchMain.do?query=${encodeURIComponent('레드 블레미쉬')}`
	);
});

test('검색 탭에서 돌아오면 복사한 올리브영 링크를 자동으로 가져온다', async () => {
	const url = 'https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000012345';
	mockClipboard(jest.fn().mockResolvedValue(`[올리브영] 제품 공유 ${url}`));
	previewProductLink.mockResolvedValue({ name: '자동 제품' });
	renderConfirm();
	fillProduct('닥터지', '선크림');
	const link = screen.getByRole('link', { name: '올리브영에서 찾기' });
	link.addEventListener('click', (e) => e.preventDefault());
	fireEvent.click(link);
	fireEvent.focus(window);
	await flush();
	expect(screen.getByLabelText('상세페이지 링크')).toHaveValue(url);
	expect(previewProductLink).toHaveBeenCalledWith(url, { signal: expect.any(AbortSignal) });
	expect(screen.getByLabelText('제품명')).toHaveValue('자동 제품');
});

test('검색칸에 상세페이지 링크를 붙여넣어도 링크로 가져온다', async () => {
	const url = 'https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000075601';
	previewProductLink.mockResolvedValue({ name: '선스틱' });
	renderConfirm();
	const search = screen.getByLabelText('제품명으로 링크 찾기');
	fireEvent.change(search, { target: { value: url } });
	await flush();
	expect(search).toHaveValue('');
	expect(screen.getByLabelText('상세페이지 링크')).toHaveValue(url);
	expect(screen.getByLabelText('제품명')).toHaveValue('선스틱');
});

test('검색 버튼을 누르지 않았으면 포커스만으로 클립보드를 읽지 않는다', async () => {
	const readText = jest.fn().mockResolvedValue('https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000000001');
	mockClipboard(readText);
	renderConfirm();
	fireEvent.focus(window);
	await flush();
	expect(readText).not.toHaveBeenCalled();
});

test('복사한 링크 넣기: 지원하지 않는 내용이나 읽기 실패는 안내만 한다', async () => {
	mockClipboard(jest.fn().mockResolvedValueOnce('https://example.com/products/1').mockRejectedValueOnce(new Error('denied')));
	renderConfirm();
	fireEvent.click(screen.getByRole('button', { name: '복사한 링크 넣기' }));
	await flush();
	expect(screen.getByText('복사한 내용에 올리브영 제품 링크가 없어요.')).toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: '복사한 링크 넣기' }));
	await flush();
	expect(screen.getByText(/클립보드를 읽지 못했어요/)).toBeInTheDocument();
	expect(previewProductLink).not.toHaveBeenCalled();
});

beforeEach(() => {
	jest.clearAllMocks();
	useIsMobile.mockReturnValue(false);
	confirmProduct.mockResolvedValue({ productId: 5, ingredientListId: 50, status: 'PENDING', shelfItemId: 1 });
});

afterEach(() => {
	jest.restoreAllMocks();
	window.localStorage.clear();
});

test('FUZZY 후보를 고르면 저장 요청의 ingredientId가 채워진다', async () => {
	renderConfirm();
	expect(screen.getByText('나이아신아마이드')).toBeInTheDocument();
	expect(screen.getByText('읽은 글자: 니코틴아마이드')).toBeInTheDocument();

	fireEvent.change(screen.getByLabelText('글리세릴 후보 고르기'), { target: { value: '2' } });
	fillProduct();
	await save();

	expect(confirmProduct).toHaveBeenCalledTimes(1);
	const body = confirmProduct.mock.calls[0][0];
	expect(body.jobId).toBe(9);
	expect(body.productId).toBeUndefined();
	expect(body.paoMonths).toBe(12); // 기본값
	expect(body.newProduct).toEqual({
		barcode: null,
		brand: '브랜드A',
		name: '수분크림',
		volume: null,
		category: 'SKINCARE',
	});
	expect(body.items).toEqual([
		{ ingredientId: 1, rawText: '정제수' },
		{ ingredientId: 3, rawText: '니코틴아마이드' },
		{ ingredientId: 2, rawText: '글리세릴' },
		{ ingredientId: null, rawText: '알수없는성분' },
	]);
});

test('UNMATCHED를 지우고 순서를 바꾼 결과가 그대로 저장된다', async () => {
	renderConfirm();
	fireEvent.click(screen.getByRole('button', { name: '알수없는성분 삭제' }));
	fireEvent.click(screen.getByRole('button', { name: '나이아신아마이드 위로' }));
	fireEvent.click(screen.getByRole('button', { name: '글리세릴 아래로' })); // 이미 마지막이라 움직이지 않는다
	fillProduct();
	await save();

	expect(sentItems()).toEqual([
		{ ingredientId: 3, rawText: '니코틴아마이드' },
		{ ingredientId: 1, rawText: '정제수' },
		{ ingredientId: null, rawText: '글리세릴' },
	]);
});

test('사전에 없는 성분은 고치고, 새 성분을 추가할 수 있다', async () => {
	renderConfirm();
	fireEvent.change(screen.getByLabelText('4번째 성분 이름'), { target: { value: '아데노신' } });
	fireEvent.change(screen.getByLabelText('성분 추가'), { target: { value: '판테놀' } });
	fireEvent.click(screen.getByRole('button', { name: '추가' }));
	fillProduct();
	await save();

	expect(sentItems().slice(3)).toEqual([
		{ ingredientId: null, rawText: '아데노신' },
		{ ingredientId: null, rawText: '판테놀' },
	]);
});

test('편집 중 새로고침해도 임시 보관 내용이 돌아온다', async () => {
	const view = renderConfirm();
	fireEvent.click(screen.getByRole('button', { name: '알수없는성분 삭제' }));
	fireEvent.change(screen.getByLabelText('글리세릴 후보 고르기'), { target: { value: '7' } });
	fillProduct('브랜드B', '토너');
	view.unmount();
	expect(loadDraft(9).items).toHaveLength(3);

	// 새로고침: 같은 job으로 다시 그리면 loadDraft로 초기화된다
	renderConfirm();
	expect(screen.getByRole('status')).toHaveTextContent('임시 보관한 편집 내용을 불러왔어요.');
	expect(screen.queryByText('알수없는성분')).not.toBeInTheDocument();
	expect(screen.getByLabelText('글리세릴 후보 고르기')).toHaveValue('7');
	expect(screen.getByLabelText('브랜드')).toHaveValue('브랜드B');
	expect(screen.getByLabelText('제품명')).toHaveValue('토너');
});

test('다른 job의 임시 보관 내용은 쓰지 않는다', () => {
	saveDraft(8, { items: [], form: { brand: 'X', name: 'Y', volume: '', barcode: '', category: 'SKINCARE' } });
	renderConfirm();
	expect(screen.getByLabelText('브랜드')).toHaveValue('');
	expect(within(screen.getByRole('list', { name: '확인할 성분' })).getAllByRole('listitem')).toHaveLength(4);
});

test('localStorage가 막혀 있어도 화면은 동작한다', async () => {
	jest.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
		throw new Error('SecurityError');
	});
	jest.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
		throw new Error('SecurityError');
	});
	jest.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
		throw new Error('SecurityError');
	});
	renderConfirm();
	fireEvent.click(screen.getByRole('button', { name: '알수없는성분 삭제' }));
	fillProduct();
	await save();

	expect(sentItems()).toHaveLength(3);
	expect(screen.getByRole('heading', { name: '내 화장대 화면' })).toBeInTheDocument();
});

test('MANY_UNMATCHED 경고 문구', () => {
	const onRetake = jest.fn();
	renderConfirm({ job: makeJob({ warning: 'MANY_UNMATCHED', unreadableParts: ['3번째 줄 끝부분'] }), onRetake });
	expect(screen.getByText('사전에 없는 성분이 많아요. 다시 찍을까요?')).toBeInTheDocument();
	expect(screen.getByText('읽지 못한 부분: 3번째 줄 끝부분')).toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: '다시 찍기' }));
	expect(onRetake).toHaveBeenCalledTimes(1);
});

test('NO_INGREDIENT_SECTION이면 촬영 팁', () => {
	renderConfirm({ job: makeJob({ warning: 'NO_INGREDIENT_SECTION', ingredientSectionFound: false, items: [] }) });
	expect(screen.getByText('사진에서 전성분 부분을 찾지 못했어요.')).toBeInTheDocument();
	expect(screen.getByText(/촬영 팁/)).toBeInTheDocument();
});

test('저장 성공하면 임시 보관을 지우고 /shelf로', async () => {
	renderConfirm();
	fillProduct();
	expect(loadDraft(9)).not.toBeNull();
	await save();

	expect(loadDraft(9)).toBeNull();
	expect(screen.getByRole('heading', { name: '내 화장대 화면' })).toBeInTheDocument();
});

test('저장이 실패하면 서버 메시지를 보여주고 임시 보관은 남긴다', async () => {
	const error = new Error('EXTRACTION_NOT_READY');
	error.response = { status: 409, data: { code: 'EXTRACTION_NOT_READY', message: '아직 성분을 읽는 중이에요.', traceId: 't' } };
	confirmProduct.mockRejectedValueOnce(error);
	renderConfirm();
	fillProduct();
	await save();

	expect(screen.getByRole('alert')).toHaveTextContent('아직 성분을 읽는 중이에요.');
	expect(loadDraft(9)).not.toBeNull();
});

test('저장이 409 EXTRACTION_ALREADY_CONFIRMED면 임시 보관을 지우고 onAlreadyConfirmed(jobId)', async () => {
	const error = new Error('EXTRACTION_ALREADY_CONFIRMED');
	error.response = { status: 409, data: { code: 'EXTRACTION_ALREADY_CONFIRMED', message: '이미 저장한 결과예요.', traceId: 't' } };
	confirmProduct.mockRejectedValueOnce(error);
	const onAlreadyConfirmed = jest.fn();
	renderConfirm({ onAlreadyConfirmed });
	fillProduct();
	await save();

	expect(onAlreadyConfirmed).toHaveBeenCalledWith(9);
	expect(loadDraft(9)).toBeNull();
});

test('브랜드·제품명이 없거나 성분이 없으면 보내지 않고 안내', async () => {
	renderConfirm({ job: makeJob({ items: [] }) });
	await save();
	expect(screen.getByRole('alert')).toHaveTextContent('성분을 한 개 이상 넣어 주세요.');
	fireEvent.change(screen.getByLabelText('성분 추가'), { target: { value: '정제수' } });
	fireEvent.click(screen.getByRole('button', { name: '추가' }));
	await save();
	expect(screen.getByRole('alert')).toHaveTextContent('브랜드와 제품명을 입력해 주세요.');
	expect(confirmProduct).not.toHaveBeenCalled();
});

test('브랜드+제품명으로 "이 제품인가요?" 후보를 보여주고 고르면 productId로 저장', async () => {
	searchProducts.mockResolvedValueOnce([
		{ id: 31, barcode: null, brand: '다른브랜드', name: '수분크림', volume: null, category: 'SKINCARE', representativeListId: 1 },
		{ id: 30, barcode: null, brand: '브랜드A', name: '수분크림', volume: '50ml', category: 'SKINCARE', representativeListId: 2 },
	]);
	renderConfirm();
	const findButton = screen.getByRole('button', { name: '같은 제품이 있는지 찾기' });
	expect(findButton).toBeDisabled();
	fillProduct();
	fireEvent.click(findButton);
	await flush();

	expect(searchProducts).toHaveBeenCalledWith('수분크림');
	const group = screen.getByRole('group', { name: '이 제품인가요?' });
	const rows = within(group).getAllByRole('listitem');
	expect(rows[0]).toHaveTextContent('브랜드A 수분크림 · 50ml'); // 같은 브랜드를 먼저
	fireEvent.click(within(rows[0]).getByRole('button', { name: '이 제품이에요' }));
	expect(screen.getByTestId('chosen-product')).toHaveTextContent('브랜드A 수분크림');
	await save();

	const body = confirmProduct.mock.calls[0][0];
	expect(body.productId).toBe(30);
	expect(body.newProduct).toBeUndefined();
});

test('직접 붙여넣기: 서버가 사전과 맞춘 결과로 넣고, 옵션이 여럿이면 고르게 한다', async () => {
	const failed = { jobId: 12, status: 'FAILED', errorCode: 'AI_UNAVAILABLE', result: null };
	matchIngredientText
		.mockResolvedValueOnce({ sections: [{ label: null, items: [match(1, '정제수', 1, '정제수'), match(2, '1,2-헥산다이올')] }] })
		.mockResolvedValueOnce({
			sections: [
				{ label: '01 데이지 · 1번', items: [match(1, '탤크')] },
				{ label: '01 데이지 · 2번', items: [match(1, '마이카')] },
			],
		});
	renderConfirm({ job: failed, pasteMode: true });
	const box = screen.getByLabelText('전성분 붙여넣기');
	fireEvent.change(box, { target: { value: '정제수, 1,2-헥산다이올' } });
	fireEvent.click(screen.getByRole('button', { name: '목록에 넣기' }));
	await flush();
	expect(matchIngredientText).toHaveBeenCalledWith('정제수, 1,2-헥산다이올');
	expect(screen.getByText(/성분 2개/)).toBeInTheDocument();

	fireEvent.change(box, { target: { value: '[01 데이지]\n1. 탤크\n2. 마이카' } });
	fireEvent.click(screen.getByRole('button', { name: '목록에 넣기' }));
	await flush();
	expect(screen.getByRole('group', { name: '옵션 2개' })).toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: '그대로 두기' }));
	fillProduct();
	await save();

	const body = confirmProduct.mock.calls[0][0];
	expect(body.jobId).toBe(12);
	expect(body.items).toEqual([
		{ ingredientId: 1, rawText: '정제수' },
		{ ingredientId: null, rawText: '1,2-헥산다이올' },
	]);
});

test('붙여넣기 매칭이 실패하면 숫자 사이 쉼표·괄호는 지키며 글자 그대로 나눈다', async () => {
	const failed = { jobId: 12, status: 'FAILED', errorCode: 'AI_UNAVAILABLE', result: null };
	matchIngredientText.mockRejectedValue(new Error('network'));
	renderConfirm({ job: failed, pasteMode: true });
	fireEvent.change(screen.getByLabelText('전성분 붙여넣기'), {
		target: { value: '전성분: 정제수, 1,2-헥산다이올\n하이드로제네이티드폴리(C6-14올레핀), 부틸렌글라이콜.' },
	});
	fireEvent.click(screen.getByRole('button', { name: '목록에 넣기' }));
	await flush();
	fillProduct();
	await save();

	expect(confirmProduct.mock.calls[0][0].items).toEqual([
		{ ingredientId: null, rawText: '정제수' },
		{ ingredientId: null, rawText: '1,2-헥산다이올' },
		{ ingredientId: null, rawText: '하이드로제네이티드폴리(C6-14올레핀)' },
		{ ingredientId: null, rawText: '부틸렌글라이콜' },
	]);
});

test('개봉 후 사용기한이 1~60이 아니면 보내지 않고 안내', async () => {
	renderConfirm();
	fillProduct();
	fireEvent.change(screen.getByLabelText('개봉 후 사용기한(개월)'), { target: { value: '99' } });
	await save();
	expect(confirmProduct).not.toHaveBeenCalled();
	expect(screen.getByRole('alert')).toHaveTextContent('1~60개월');
});

test('폰은 하단 고정 저장 버튼, PC는 사진 미리보기와 성분 목록을 나란히', () => {
	useIsMobile.mockReturnValue(true);
	const { unmount } = renderConfirm({ previewUrl: 'blob:preview' });
	expect(screen.getByTestId('confirm-layout')).toHaveAttribute('data-layout', 'mobile');
	expect(screen.getByTestId('confirm-savebar')).toContainElement(
		screen.getByRole('button', { name: '확인하고 화장대에 담기' })
	);
	expect(screen.queryByAltText('올린 전성분 사진')).not.toBeInTheDocument();
	unmount();

	useIsMobile.mockReturnValue(false);
	renderConfirm({ previewUrl: 'blob:preview' });
	expect(screen.getByTestId('confirm-layout')).toHaveAttribute('data-layout', 'desktop');
	expect(screen.getByRole('region', { name: '사진 미리보기' })).toContainElement(screen.getByAltText('올린 전성분 사진'));
	expect(screen.getByRole('list', { name: '확인할 성분' })).toBeInTheDocument();
});

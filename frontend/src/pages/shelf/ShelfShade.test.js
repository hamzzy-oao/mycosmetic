// 내 화장대: 호수 색 칩·편집과 "내 톤에 맞는 색조" 필터(no-ai-features 설계 E)
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { getShelf, getToneMatchedShelf, updateShelfItem } from '../../api/shelf';
import useIsMobile from '../../hooks/useIsMobile';
import { toneText } from '../../lib/shade';
import ShelfPage from './ShelfPage';
import { shelfItem } from './testFixtures';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/shelf', () => ({
	getShelf: jest.fn(),
	getToneMatchedShelf: jest.fn(),
	searchShelfByIngredient: jest.fn(),
	updateShelfItem: jest.fn(),
	removeShelfItem: jest.fn(),
}));
jest.mock('../../api/products', () => ({
	getProductList: jest.fn(),
}));
jest.mock('../../hooks/useIsMobile', () => ({
	__esModule: true,
	default: jest.fn(),
}));

function makeup(overrides = {}) {
	const base = shelfItem(overrides);
	return { ...base, product: { ...base.product, category: 'MAKEUP', name: `립${base.id}` }, ...overrides };
}

const CORAL = {
	shadeName: '코랄 21호',
	shadeHex: '#FF7F50',
	shadeTone: 'WARM',
	shadeSeason: 'SPRING',
	shadeMatchesMe: true,
};

function renderShelf() {
	return render(
		<MemoryRouter initialEntries={['/shelf']} future={FUTURE}>
			<Routes>
				<Route path="/shelf" element={<ShelfPage />} />
				<Route path="/skin" element={<h1>피부 상태 화면</h1>} />
			</Routes>
		</MemoryRouter>
	);
}

function serverError(status, code, message) {
	const error = new Error(code);
	error.response = { status, data: { code, message, traceId: 'abc123' } };
	return error;
}

beforeEach(() => {
	jest.clearAllMocks();
	useIsMobile.mockReturnValue(true);
});

test('toneText: 웜/쿨과 계절, 중립은 계절 없이', () => {
	expect(toneText({ shadeTone: 'WARM', shadeSeason: 'SPRING' })).toBe('웜 · 봄');
	expect(toneText({ shadeTone: 'COOL', shadeSeason: 'WINTER' })).toBe('쿨 · 겨울');
	expect(toneText({ shadeTone: 'NEUTRAL', shadeSeason: null })).toBe('중립');
	expect(toneText({})).toBe('');
});

test.each([
	['폰', true],
	['PC', false],
])('%s: 색조 항목에 색 칩·이름·웜/쿨·"내 톤"이 보인다', async (_, mobile) => {
	useIsMobile.mockReturnValue(mobile);
	getShelf.mockResolvedValue([makeup({ id: 1, ...CORAL }), shelfItem({ id: 2 })]);
	renderShelf();

	expect(await screen.findByText('코랄 21호')).toBeInTheDocument();
	expect(screen.getByText('웜 · 봄')).toBeInTheDocument();
	expect(screen.getByText('내 톤')).toBeInTheDocument();
	const swatch = screen.getByTestId('shade-swatch');
	expect(swatch).toHaveStyle({ backgroundColor: '#FF7F50' });
	expect(swatch).toHaveAttribute('aria-hidden', 'true');
});

test('폰: 색조 항목은 호수 색·이름을 고르고 바뀐 값만 PATCH, 참고용 안내가 있다', async () => {
	const item = makeup({ id: 1 });
	getShelf.mockResolvedValue([item]);
	updateShelfItem.mockResolvedValue({ ...item, ...CORAL, shadeName: '로즈', shadeHex: '#C04060' });
	renderShelf();

	fireEvent.click(await screen.findByRole('button', { name: '메모·기간·호수' }));
	const editor = screen.getByRole('group', { name: '내 호수' });
	expect(within(editor).getByText(/참고용/)).toBeInTheDocument();
	expect(within(editor).getByText(/아직 색을 고르지 않았어요/)).toBeInTheDocument();

	fireEvent.change(within(editor).getByLabelText('색'), { target: { value: '#c04060' } });
	fireEvent.change(within(editor).getByLabelText('이름'), { target: { value: ' 로즈 ' } });
	fireEvent.click(within(editor).getByRole('button', { name: '호수 저장' }));

	expect(await within(editor).findByRole('status')).toHaveTextContent('호수를 저장했어요.');
	expect(updateShelfItem).toHaveBeenCalledWith(1, { shadeName: '로즈', shadeHex: '#C04060' });
	expect(screen.getAllByText('로즈').length).toBeGreaterThan(0);
});

test('PC: 펼친 행에서 호수를 지우면 빈 문자열을 보낸다', async () => {
	useIsMobile.mockReturnValue(false);
	const item = makeup({ id: 1, ...CORAL });
	getShelf.mockResolvedValue([item]);
	updateShelfItem.mockResolvedValue({ ...item, shadeName: null, shadeHex: null, shadeTone: null, shadeSeason: null, shadeMatchesMe: null });
	renderShelf();

	fireEvent.click(await screen.findByRole('button', { name: /성분·기간 보기/ }));
	const editor = screen.getByRole('group', { name: '내 호수' });
	fireEvent.click(within(editor).getByRole('button', { name: '지우기' }));
	fireEvent.click(within(editor).getByRole('button', { name: '호수 저장' }));

	expect(await within(editor).findByRole('status')).toHaveTextContent('호수를 저장했어요.');
	expect(updateShelfItem).toHaveBeenCalledWith(1, { shadeName: '', shadeHex: '' });
});

test('스킨케어 항목에는 호수 편집도 내 톤 필터도 없다', async () => {
	getShelf.mockResolvedValue([shelfItem({ id: 1 })]);
	renderShelf();

	fireEvent.click(await screen.findByRole('button', { name: '메모·기간' }));
	expect(screen.queryByRole('group', { name: '내 호수' })).not.toBeInTheDocument();
	expect(screen.queryByRole('button', { name: '내 톤에 맞는 색조' })).not.toBeInTheDocument();
});

test.each([
	['폰', true],
	['PC', false],
])('%s: 내 톤에 맞는 색조를 켜면 맞는 항목만, 끄면 전체', async (_, mobile) => {
	useIsMobile.mockReturnValue(mobile);
	const coral = makeup({ id: 1, ...CORAL });
	const pink = makeup({ id: 2, shadeHex: '#FFB6C1', shadeTone: 'COOL', shadeSeason: 'SUMMER', shadeMatchesMe: false });
	getShelf.mockResolvedValue([coral, pink, shelfItem({ id: 3 })]);
	getToneMatchedShelf.mockResolvedValue([coral]);
	renderShelf();

	const toggle = await screen.findByRole('button', { name: '내 톤에 맞는 색조' });
	expect(toggle).toHaveAttribute('aria-pressed', 'false');
	fireEvent.click(toggle);

	expect(await screen.findByText('내 톤에 맞는 색조 1개')).toBeInTheDocument();
	expect(toggle).toHaveAttribute('aria-pressed', 'true');
	expect(screen.getByText('브랜드 립1')).toBeInTheDocument();
	expect(screen.queryByText('브랜드 립2')).not.toBeInTheDocument();
	expect(screen.queryByText('브랜드 제품3')).not.toBeInTheDocument();
	expect(screen.getByText(/참고용/)).toBeInTheDocument();

	fireEvent.click(toggle);
	expect(await screen.findByText('브랜드 립2')).toBeInTheDocument();
	expect(getToneMatchedShelf).toHaveBeenCalledTimes(1);
});

test('내 톤 필터: 맞는 색조가 없으면 없다고 알린다', async () => {
	getShelf.mockResolvedValue([makeup({ id: 1 })]);
	getToneMatchedShelf.mockResolvedValue([]);
	renderShelf();

	fireEvent.click(await screen.findByRole('button', { name: '내 톤에 맞는 색조' }));
	expect(await screen.findByText('내 톤에 맞는 색조가 없어요.')).toBeInTheDocument();
});

test('내 톤 필터: 퍼스널컬러가 없으면 피부 상태로 안내, 다른 오류는 서버 메시지', async () => {
	getShelf.mockResolvedValue([makeup({ id: 1 })]);
	getToneMatchedShelf.mockRejectedValueOnce(
		serverError(400, 'PERSONAL_COLOR_REQUIRED', '피부 상태에서 퍼스널컬러를 먼저 골라 주세요.')
	);
	renderShelf();

	fireEvent.click(await screen.findByRole('button', { name: '내 톤에 맞는 색조' }));
	const alert = await screen.findByRole('alert');
	expect(alert).toHaveTextContent('퍼스널컬러를 먼저 골라 주세요.');
	expect(within(alert).getByRole('link', { name: '퍼스널컬러 고르기' })).toHaveAttribute('href', '/skin');

	getToneMatchedShelf.mockRejectedValueOnce(serverError(500, 'INTERNAL_ERROR', '잠시 문제가 생겼어요.'));
	fireEvent.click(screen.getByRole('button', { name: '내 톤에 맞는 색조' }));
	expect(await screen.findByText('잠시 문제가 생겼어요.')).toBeInTheDocument();
	expect(screen.queryByRole('link', { name: '퍼스널컬러 고르기' })).not.toBeInTheDocument();
});

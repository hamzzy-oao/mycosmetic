import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { getListDiff, getProduct } from '../../api/products';
import useIsMobile from '../../hooks/useIsMobile';
import ProductDetailPage from './ProductDetailPage';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/products', () => ({
	getProduct: jest.fn(),
	getListDiff: jest.fn(),
}));
jest.mock('../../hooks/useIsMobile', () => ({
	__esModule: true,
	default: jest.fn(),
}));

// 계획 Task 18의 ProductDetailResponse
const DETAIL = {
	product: { id: 5, barcode: '8801111111111', brand: '브랜드A', name: '수분크림', volume: '50ml', category: 'SKINCARE', representativeListId: 50 },
	representative: {
		id: 50,
		status: 'VERIFIED',
		confirmCount: 3,
		items: [
			{ position: 1, ingredientId: 1, name: '정제수', matchType: 'EXACT' },
			{ position: 2, ingredientId: 2, name: '글리세린', matchType: 'EXACT' },
			{ position: 3, ingredientId: null, name: '신비한추출물', matchType: 'UNMATCHED' },
		],
	},
	otherLists: [
		{ id: 40, status: 'PENDING', confirmCount: 1, createdAt: '2026-09-01T03:00:00Z' },
		{ id: 41, status: 'VERIFIED', confirmCount: 2, createdAt: '2026-08-01T03:00:00Z' },
	],
};

async function flush() {
	await act(async () => {
		for (let i = 0; i < 10; i += 1) {
			await Promise.resolve();
		}
	});
}

async function renderDetail(id = '5') {
	const view = render(
		<MemoryRouter initialEntries={[`/products/${id}`]} future={FUTURE}>
			<Routes>
				<Route path="/products/:id" element={<ProductDetailPage />} />
			</Routes>
		</MemoryRouter>
	);
	await flush();
	return view;
}

beforeEach(() => {
	jest.clearAllMocks();
	useIsMobile.mockReturnValue(false);
	getProduct.mockResolvedValue(DETAIL);
});

test('제품 상세: 다른 버전 알림과 diff의 추가·삭제 성분', async () => {
	getListDiff.mockResolvedValueOnce({ added: ['신비한추출물'], removed: ['부틸렌글라이콜', '향료'], orderChanged: true });
	await renderDetail();

	expect(getProduct).toHaveBeenCalledWith('5');
	expect(screen.getByRole('heading', { name: '브랜드A 수분크림' })).toBeInTheDocument();
	const representative = screen.getByRole('region', { name: '대표 성분' });
	expect(within(representative).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
		'정제수',
		'글리세린',
		'신비한추출물 (사전에 없음)',
	]);
	expect(representative).toHaveTextContent('검증됨 · 확인 3명');
	expect(screen.getByRole('status')).toHaveTextContent('다른 성분 버전이 2개 있어요.');

	// 기본: 다른 버전 중 첫 번째 → 대표 버전
	expect(screen.getByLabelText('기준 버전')).toHaveValue('40');
	expect(screen.getByLabelText('비교할 버전')).toHaveValue('50');
	fireEvent.change(screen.getByLabelText('기준 버전'), { target: { value: '41' } });
	fireEvent.click(screen.getByRole('button', { name: '차이 보기' }));
	await flush();

	expect(getListDiff).toHaveBeenCalledWith('5', '41', '50');
	expect(within(screen.getByRole('list', { name: '추가된 성분' })).getByText('신비한추출물')).toBeInTheDocument();
	const removed = screen.getByRole('list', { name: '빠진 성분' });
	expect(within(removed).getAllByRole('listitem').map((li) => li.textContent)).toEqual(['부틸렌글라이콜', '향료']);
	expect(screen.getByText('성분 순서가 바뀌었어요.')).toBeInTheDocument();
});

test('같은 버전끼리는 비교하지 않고, 차이가 없으면 같다고 알린다', async () => {
	getListDiff.mockResolvedValueOnce({ added: [], removed: [], orderChanged: false });
	await renderDetail();
	fireEvent.change(screen.getByLabelText('기준 버전'), { target: { value: '50' } });
	fireEvent.click(screen.getByRole('button', { name: '차이 보기' }));
	expect(screen.getByRole('alert')).toHaveTextContent('서로 다른 두 버전을 골라 주세요.');
	expect(getListDiff).not.toHaveBeenCalled();

	fireEvent.change(screen.getByLabelText('기준 버전'), { target: { value: '40' } });
	fireEvent.click(screen.getByRole('button', { name: '차이 보기' }));
	await flush();
	expect(screen.getByText('두 버전의 성분이 같아요.')).toBeInTheDocument();
});

test('버전이 하나면 알림과 비교가 없다', async () => {
	getProduct.mockResolvedValueOnce({ ...DETAIL, otherLists: [] });
	await renderDetail();
	expect(screen.queryByRole('status')).not.toBeInTheDocument();
	expect(screen.queryByRole('button', { name: '차이 보기' })).not.toBeInTheDocument();
	expect(screen.getByText('이 제품의 성분 버전은 하나예요.')).toBeInTheDocument();
});

test('불러오기에 실패하면 서버 메시지와 다시 시도', async () => {
	const error = new Error('NOT_FOUND');
	error.response = { status: 404, data: { code: 'NOT_FOUND', message: '찾을 수 없어요.', traceId: 't' } };
	getProduct.mockRejectedValueOnce(error);
	await renderDetail('999');
	expect(screen.getByRole('alert')).toHaveTextContent('찾을 수 없어요.');

	fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
	await flush();
	expect(screen.getByRole('heading', { name: '브랜드A 수분크림' })).toBeInTheDocument();
});

test('폰은 한 열, PC는 대표 성분과 버전 비교를 나란히', async () => {
	useIsMobile.mockReturnValue(true);
	const { unmount } = await renderDetail();
	expect(screen.getByTestId('product-layout')).toHaveAttribute('data-layout', 'mobile');
	expect(screen.queryByTestId('product-columns')).not.toBeInTheDocument();
	unmount();

	useIsMobile.mockReturnValue(false);
	await renderDetail();
	expect(screen.getByTestId('product-layout')).toHaveAttribute('data-layout', 'desktop');
	const columns = screen.getByTestId('product-columns');
	expect(columns).toContainElement(screen.getByRole('region', { name: '대표 성분' }));
	expect(columns).toContainElement(screen.getByRole('region', { name: '성분 버전 비교' }));
});

test('제품 상세에는 나에게 맞을까?·비교하기·내 화장대 버튼을 두지 않는다', async () => {
	await renderDetail();
	expect(screen.queryByRole('navigation', { name: '제품 메뉴' })).not.toBeInTheDocument();
	expect(screen.queryByRole('link', { name: '나에게 맞을까?' })).not.toBeInTheDocument();
	expect(screen.queryByRole('link', { name: '비교하기' })).not.toBeInTheDocument();
});

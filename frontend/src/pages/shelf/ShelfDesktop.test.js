// user-event 13은 최상위 @testing-library/dom(10)을 써서 act로 감싸지지 않는다. RTL의 fireEvent를 쓴다
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { getProductList } from '../../api/products';
import { getShelf, updateShelfItem } from '../../api/shelf';
import useIsMobile from '../../hooks/useIsMobile';
import ShelfPage from './ShelfPage';
import { shelfItem } from './testFixtures';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/shelf', () => ({
	getShelf: jest.fn(),
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

function renderShelf() {
	return render(
		<MemoryRouter initialEntries={['/shelf']} future={FUTURE}>
			<Routes>
				<Route path="/shelf" element={<ShelfPage />} />
				<Route path="/add" element={<h1>제품 추가 화면</h1>} />
			</Routes>
		</MemoryRouter>
	);
}

// 표의 행 머리(제품 이름) 순서
function productNames() {
	return screen.getAllByRole('rowheader').map((cell) => cell.querySelector('.shelf-title').textContent);
}

// GET /api/products/{id}/lists/{listId} 응답(ListView)
function listView(listId, names, status = 'VERIFIED') {
	return {
		id: listId,
		status,
		confirmCount: 2,
		items: names.map((name, i) => ({ position: i + 1, ingredientId: i + 1, name, matchType: 'EXACT' })),
	};
}

const ITEMS = [
	shelfItem({ id: 1, product: { id: 101, brand: '나브랜드', name: '토너' }, ingredientListId: 201, verdict: 'GOOD', updatedAt: '2026-10-01T00:00:00Z' }),
	shelfItem({ id: 2, product: { id: 102, brand: '가브랜드', name: '크림' }, ingredientListId: 202, verdict: 'BAD', updatedAt: '2026-10-03T00:00:00Z' }),
	shelfItem({ id: 3, product: { id: 103, brand: '다브랜드', name: '세럼' }, ingredientListId: 203, verdict: 'GOOD', updatedAt: '2026-10-02T00:00:00Z' }),
];

beforeEach(() => {
	jest.clearAllMocks();
	useIsMobile.mockReturnValue(false);
});

test('PC: 표로 보이고 기본은 최근 수정순', async () => {
	getShelf.mockResolvedValue(ITEMS);
	renderShelf();

	const region = await screen.findByRole('region', { name: '내 제품' });
	expect(region).toHaveAttribute('data-layout', 'desktop');
	expect(await screen.findByRole('table')).toBeInTheDocument();
	expect(productNames()).toEqual(['가브랜드 크림', '다브랜드 세럼', '나브랜드 토너']);
});

test('PC: 평가로 필터, 최근 수정순·제품명순 정렬', async () => {
	getShelf.mockResolvedValue(ITEMS);
	renderShelf();
	await screen.findByRole('table');

	fireEvent.change(screen.getByLabelText('평가 필터'), { target: { value: 'GOOD' } });
	expect(productNames()).toEqual(['다브랜드 세럼', '나브랜드 토너']);

	fireEvent.change(screen.getByLabelText('정렬'), { target: { value: 'name' } });
	expect(productNames()).toEqual(['나브랜드 토너', '다브랜드 세럼']);

	fireEvent.change(screen.getByLabelText('평가 필터'), { target: { value: 'ALL' } });
	expect(productNames()).toEqual(['가브랜드 크림', '나브랜드 토너', '다브랜드 세럼']);

	fireEvent.change(screen.getByLabelText('정렬'), { target: { value: 'updated' } });
	expect(productNames()).toEqual(['가브랜드 크림', '다브랜드 세럼', '나브랜드 토너']);

	// 평가 '보통'은 없앴다
	expect(Array.from(screen.getByLabelText('평가 필터').options).map((o) => o.value)).not.toContain('NEUTRAL');
	fireEvent.change(screen.getByLabelText('평가 필터'), { target: { value: 'UNRATED' } });
	expect(screen.queryByRole('table')).not.toBeInTheDocument();
	expect(screen.getByText('이 평가에 해당하는 제품이 없어요.')).toBeInTheDocument();
});

test('PC: 행을 펼치면 GET /api/products/{id}/lists/{listId}의 내 버전 성분 목록', async () => {
	getShelf.mockResolvedValue(ITEMS);
	getProductList.mockResolvedValue(listView(202, ['정제수', '글리세린', '나이아신아마이드']));
	renderShelf();
	await screen.findByRole('table');

	fireEvent.click(screen.getByRole('button', { name: '가브랜드 크림 성분·기간 보기' }));

	const list = await screen.findByRole('list', { name: '가브랜드 크림 성분' });
	expect(within(list).getAllByRole('listitem').map((li) => li.textContent)).toEqual(['정제수', '글리세린', '나이아신아마이드']);
	expect(screen.getByRole('heading', { name: '내 버전 성분' })).toBeInTheDocument();
	expect(getProductList).toHaveBeenCalledWith(102, 202);
	expect(screen.queryByText(/대표 버전이 아니에요/)).not.toBeInTheDocument();

	// 접었다 다시 펼치면 다시 부르지 않는다
	fireEvent.click(screen.getByRole('button', { name: '가브랜드 크림 접기' }));
	expect(screen.queryByRole('list', { name: '가브랜드 크림 성분' })).not.toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: '가브랜드 크림 성분·기간 보기' }));
	expect(await screen.findByRole('list', { name: '가브랜드 크림 성분' })).toBeInTheDocument();
	expect(getProductList).toHaveBeenCalledTimes(1);
});

test('PC: 내 버전이 대표 버전이 아니어도 내 버전 성분을 보여 주고 버전 차이 링크를 단다', async () => {
	getShelf.mockResolvedValue([{ ...ITEMS[0], listIsRepresentative: false }]);
	getProductList.mockResolvedValue(listView(201, ['정제수', '향료'], 'PENDING'));
	renderShelf();
	await screen.findByRole('table');

	expect(screen.getByRole('link', { name: '새 성분 버전이 있어요' })).toHaveAttribute('href', '/products/101');
	fireEvent.click(screen.getByRole('button', { name: '나브랜드 토너 성분·기간 보기' }));

	const list = await screen.findByRole('list', { name: '나브랜드 토너 성분' });
	expect(within(list).getAllByRole('listitem').map((li) => li.textContent)).toEqual(['정제수', '향료']);
	expect(getProductList).toHaveBeenCalledWith(101, 201);
	expect(screen.getByRole('heading', { name: '내 버전 성분' })).toBeInTheDocument();
	expect(screen.getByText(/내가 담은 버전은 지금 대표 버전이 아니에요/)).toBeInTheDocument();
	expect(screen.getByRole('link', { name: '버전 차이 보기' })).toHaveAttribute('href', '/products/101');
});

test('PC: 성분을 못 불러오면 서버 메시지', async () => {
	getShelf.mockResolvedValue(ITEMS);
	const error = new Error('NOT_FOUND');
	error.response = { status: 404, data: { code: 'NOT_FOUND', message: '찾을 수 없어요.', traceId: 't' } };
	getProductList.mockRejectedValue(error);
	renderShelf();
	await screen.findByRole('table');

	fireEvent.click(screen.getByRole('button', { name: '가브랜드 크림 성분·기간 보기' }));

	expect(await screen.findByRole('alert')).toHaveTextContent('찾을 수 없어요.');
});

test('PC: 평가를 바꾸면 PATCH하고 표에 반영한다', async () => {
	getShelf.mockResolvedValue(ITEMS);
	updateShelfItem.mockResolvedValue({ ...ITEMS[1], verdict: 'UNRATED' });
	renderShelf();
	await screen.findByRole('table');

	fireEvent.change(screen.getByLabelText('가브랜드 크림 평가'), { target: { value: 'UNRATED' } });

	await waitFor(() => expect(screen.getByLabelText('가브랜드 크림 평가')).toHaveValue('UNRATED'));
	expect(updateShelfItem).toHaveBeenCalledWith(2, { verdict: 'UNRATED' });
});

test('PC: 빈 화장대면 제품 추가 안내 버튼', async () => {
	getShelf.mockResolvedValue([]);
	renderShelf();

	expect(await screen.findByRole('link', { name: '제품 추가하기' })).toHaveAttribute('href', '/add');
	expect(screen.queryByRole('table')).not.toBeInTheDocument();
});

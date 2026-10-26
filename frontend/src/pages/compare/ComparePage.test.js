import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { compareProducts } from '../../api/compare';
import { getShelf } from '../../api/shelf';
import useIsMobile from '../../hooks/useIsMobile';
import { shelfItem } from '../shelf/testFixtures';
import ComparePage from './ComparePage';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/compare', () => ({
	compareProducts: jest.fn(),
}));
jest.mock('../../api/shelf', () => ({
	getShelf: jest.fn(),
}));
jest.mock('../../hooks/useIsMobile', () => ({
	__esModule: true,
	default: jest.fn(),
}));

function product(id, name) {
	return { id, barcode: null, brand: '브랜드', name, volume: null, category: 'SKINCARE', representativeListId: id * 10 };
}

const RESULT = {
	a: { product: product(101, '제품1'), listId: 201 },
	b: { product: product(102, '제품2'), listId: 202 },
	common: ['정제수', '글리세린'],
	onlyA: ['레티놀'],
	onlyB: ['나이아신아마이드', '향료'],
	groupsA: ['RETINOID'],
	groupsB: ['NIACINAMIDE'],
};

function LocationProbe() {
	const location = useLocation();
	return <p data-testid="location">{location.search}</p>;
}

function renderCompare(entry = '/compare') {
	return render(
		<MemoryRouter initialEntries={[entry]} future={FUTURE}>
			<Routes>
				<Route
					path="/compare"
					element={
						<>
							<ComparePage />
							<LocationProbe />
						</>
					}
				/>
			</Routes>
		</MemoryRouter>
	);
}

beforeEach(() => {
	jest.clearAllMocks();
	useIsMobile.mockReturnValue(false);
	getShelf.mockResolvedValue([shelfItem({ id: 1 }), shelfItem({ id: 2 })]);
	compareProducts.mockResolvedValue(RESULT);
});

test('PC: 내 화장대에서 두 개를 고르면 공통/A만/B만 3열과 성분 그룹을 보여준다', async () => {
	renderCompare();

	expect(await screen.findByText('두 제품을 고르면 성분을 비교해요.')).toBeInTheDocument();
	expect(screen.getByRole('region', { name: '내 제품 성분 비교' })).toHaveAttribute('data-layout', 'desktop');
	fireEvent.change(screen.getByLabelText('A 제품'), { target: { value: 'item:1' } });
	fireEvent.change(screen.getByLabelText('B 제품'), { target: { value: 'item:2' } });

	expect(await screen.findByRole('list', { name: '공통 성분' })).toBeInTheDocument();
	expect(compareProducts).toHaveBeenLastCalledWith({ a: '1', b: '2' });
	expect(screen.getByTestId('location')).toHaveTextContent('?a=1&b=2');
	expect(within(screen.getByRole('list', { name: '공통 성분' })).getAllByRole('listitem')).toHaveLength(2);
	expect(within(screen.getByRole('list', { name: 'A에만 있는 성분' })).getByText('레티놀')).toBeInTheDocument();
	expect(within(screen.getByRole('list', { name: 'B에만 있는 성분' })).getAllByRole('listitem')).toHaveLength(2);
	expect(screen.getByText('레티노이드')).toBeInTheDocument();
	expect(screen.getAllByText('나이아신아마이드')).toHaveLength(2);
	expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
});

test('폰: 결과를 공통/A만/B만 탭으로 보여준다', async () => {
	useIsMobile.mockReturnValue(true);
	renderCompare('/compare?a=1&b=2');

	const tabs = await screen.findByRole('tablist', { name: '비교 결과' });
	expect(within(tabs).getByRole('tab', { name: '공통 2' })).toHaveAttribute('aria-selected', 'true');
	expect(within(screen.getByRole('tabpanel')).getByText('정제수')).toBeInTheDocument();

	fireEvent.click(within(tabs).getByRole('tab', { name: 'B만 2' }));
	expect(within(screen.getByRole('tabpanel')).getByText('향료')).toBeInTheDocument();
	expect(within(screen.getByRole('tabpanel')).queryByText('정제수')).not.toBeInTheDocument();

	fireEvent.keyDown(tabs, { key: 'ArrowRight' });
	expect(within(tabs).getByRole('tab', { name: '공통 2' })).toHaveAttribute('aria-selected', 'true');
});

test('제품 상세에서 들어오면(?aProduct=) 그 제품의 대표 성분을 A로 둔다', async () => {
	renderCompare('/compare?aProduct=101');

	const selectA = await screen.findByLabelText('A 제품');
	expect(selectA).toHaveValue('product:101');
	expect(compareProducts).not.toHaveBeenCalled();

	fireEvent.change(screen.getByLabelText('B 제품'), { target: { value: 'item:2' } });
	await waitFor(() => expect(compareProducts).toHaveBeenCalledWith({ aProduct: '101', b: '2' }));
	expect(await screen.findByRole('option', { name: '브랜드 제품1 (대표 성분)' })).toBeInTheDocument();
});

test('비교가 실패하면 서버 메시지를 보여준다', async () => {
	const error = new Error('NOT_FOUND');
	error.response = { status: 404, data: { code: 'NOT_FOUND', message: '찾을 수 없어요.', traceId: 'abc' } };
	compareProducts.mockRejectedValue(error);
	renderCompare('/compare?a=1&b=999');

	expect(await screen.findByRole('alert')).toHaveTextContent('찾을 수 없어요.');
	expect(screen.queryByRole('list', { name: '공통 성분' })).not.toBeInTheDocument();
});

test('화장대가 비었으면 제품 추가 안내, 메인으로 가는 로고가 있다', async () => {
	getShelf.mockResolvedValue([]);
	renderCompare();

	expect(await screen.findByText(/비교하려면 먼저 화장대에 제품을 담아 주세요/)).toBeInTheDocument();
	expect(screen.getByRole('link', { name: '제품 추가하기' })).toHaveAttribute('href', '/add');
	expect(screen.getByRole('link', { name: 'Vanity Check 홈' })).toHaveAttribute('href', '/');
});

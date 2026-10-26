import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { getAdvice } from '../../api/advice';
import { addToShelf } from '../../api/shelf';
import useIsMobile from '../../hooks/useIsMobile';
import AdvicePage from './AdvicePage';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/advice', () => ({ getAdvice: jest.fn() }));
jest.mock('../../api/shelf', () => ({ addToShelf: jest.fn() }));
jest.mock('../../hooks/useIsMobile', () => ({ __esModule: true, default: jest.fn() }));

const PRODUCT = { id: 5, brand: '브랜드A', name: '수분크림', volume: '50ml', category: 'SKINCARE', representativeListId: 50 };

const AVOID = {
	verdict: 'AVOID',
	reasons: [
		{ type: 'BAD_INGREDIENT', level: 'WARNING', message: '안 맞았던 제품들에 공통으로 들어 있던 성분이 2개 있어요.', ingredients: ['향료', '리모넨'], relatedShelfItemIds: [11, 12] },
		{ type: 'GOOD_OVERLAP', level: 'INFO', message: '잘 맞았던 제품들과 겹치는 성분은 20%예요.', ingredients: [], relatedShelfItemIds: [] },
	],
	matchedCount: 3,
	unmatchedCount: 1,
	ingredients: ['정제수', '향료', '리모넨', '신비한추출물'],
	product: null,
	alreadyOnShelf: false,
	relatedItems: [
		{ id: 11, productId: 101, brand: '브랜드B', name: '토너', verdict: 'BAD' },
		{ id: 12, productId: 102, brand: '브랜드C', name: '로션', verdict: 'BAD' },
	],
};

const GOOD_PRODUCT = {
	verdict: 'GOOD_FIT',
	reasons: [{ type: 'GOOD_OVERLAP', level: 'POSITIVE', message: '잘 맞았던 제품들과 성분이 60% 겹쳐요.', ingredients: ['판테놀'], relatedShelfItemIds: [] }],
	matchedCount: 2,
	unmatchedCount: 0,
	ingredients: ['정제수', '판테놀'],
	product: PRODUCT,
	alreadyOnShelf: false,
	relatedItems: [],
};

function LocationProbe() {
	const location = useLocation();
	return <p data-testid="location">{location.pathname + location.search}</p>;
}

function renderAdvice(entry = '/advice') {
	return render(
		<MemoryRouter initialEntries={[entry]} future={FUTURE}>
			<Routes>
				<Route
					path="*"
					element={
						<>
							<AdvicePage />
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
	getAdvice.mockResolvedValue(AVOID);
});

test('PC: 붙여넣은 성분으로 판단하고 판정 배지·근거·관련 내 제품·참고용 안내를 보여준다', async () => {
	renderAdvice();
	expect(screen.getByRole('region', { name: '화장대 진단' })).toHaveAttribute('data-layout', 'desktop');

	fireEvent.change(screen.getByLabelText('전성분'), { target: { value: '정제수, 향료\n리모넨' } });
	fireEvent.click(screen.getByRole('button', { name: '판단하기' }));

	expect(await screen.findByTestId('advice-verdict')).toHaveTextContent('피하는 게 좋아요');
	expect(getAdvice).toHaveBeenCalledWith({ ingredientsText: '정제수, 향료\n리모넨' });
	const reasons = screen.getByRole('list', { name: '판단 근거' });
	expect(within(reasons).getByText('경고')).toBeInTheDocument();
	expect(within(reasons).getByText('참고')).toBeInTheDocument();
	expect(within(reasons).getByText('리모넨')).toBeInTheDocument();
	expect(screen.getByRole('link', { name: '브랜드B 토너' })).toHaveAttribute('href', '/products/101');
	expect(screen.getByText(/의학적 판단이 아니에요/)).toBeInTheDocument();
	expect(screen.queryByRole('button', { name: '내 화장대에 담기' })).not.toBeInTheDocument();
});

test('붙여넣기가 비어 있으면 안내하고 요청하지 않는다', () => {
	renderAdvice();
	fireEvent.click(screen.getByRole('button', { name: '판단하기' }));

	expect(screen.getByRole('alert')).toHaveTextContent('성분을 붙여넣어 주세요.');
	expect(getAdvice).not.toHaveBeenCalled();
});

test('제품 상세에서 들어오면(?product=) 제품으로 판단하고 "내 화장대에 담기"로 대표 버전을 담는다', async () => {
	getAdvice.mockResolvedValue(GOOD_PRODUCT);
	addToShelf.mockResolvedValue({ id: 1 });
	renderAdvice('/advice?product=5');

	expect(await screen.findByTestId('advice-verdict')).toHaveTextContent('잘 맞을 것 같아요');
	expect(getAdvice).toHaveBeenCalledWith({ productId: 5 });
	expect(screen.getByText('브랜드A 수분크림')).toBeInTheDocument();
	expect(screen.getByText('좋아요')).toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: '내 화장대에 담기' }));

	await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/shelf'));
	expect(addToShelf).toHaveBeenCalledWith(50);
});

test('이미 화장대에 있는 제품이면 담기 버튼 대신 안내를 보여준다', async () => {
	getAdvice.mockResolvedValue({ ...GOOD_PRODUCT, verdict: 'UNKNOWN', alreadyOnShelf: true });
	renderAdvice('/advice?product=5');

	expect(await screen.findByTestId('advice-verdict')).toHaveTextContent('기록이 더 필요해요');
	expect(screen.getByText('이미 내 화장대에 있는 제품이에요.')).toBeInTheDocument();
	expect(screen.queryByRole('button', { name: '내 화장대에 담기' })).not.toBeInTheDocument();
});

test('폰: 바코드·사진(AI) 없이 붙여넣기만 있고 탭 막대도 없다', () => {
	useIsMobile.mockReturnValue(true);
	renderAdvice();

	expect(screen.getByRole('region', { name: '화장대 진단' })).toHaveAttribute('data-layout', 'mobile');
	expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
	expect(screen.getByLabelText('전성분')).toBeInTheDocument();
	expect(screen.queryByText(/바코드/)).not.toBeInTheDocument();
	expect(screen.queryByLabelText('전성분 사진 고르기')).not.toBeInTheDocument();
});

test('서버 오류 메시지를 그대로 보여준다', async () => {
	getAdvice.mockRejectedValue({ response: { data: { code: 'ADVICE_TOO_MANY_INGREDIENTS', message: '성분은 200개까지 넣을 수 있어요.' } } });
	renderAdvice();
	fireEvent.change(screen.getByLabelText('전성분'), { target: { value: 'a' } });
	fireEvent.click(screen.getByRole('button', { name: '판단하기' }));

	expect(await screen.findByRole('alert')).toHaveTextContent('성분은 200개까지 넣을 수 있어요.');
});

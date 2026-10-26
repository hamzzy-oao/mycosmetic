import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { getShelfStats } from '../../api/shelf';
import useIsMobile from '../../hooks/useIsMobile';
import { shelfItem } from '../shelf/testFixtures';
import StatsPage from './StatsPage';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/shelf', () => ({
	getShelfStats: jest.fn(),
}));
jest.mock('../../hooks/useIsMobile', () => ({
	__esModule: true,
	default: jest.fn(),
}));

const STATS = {
	total: 5,
	inUse: 3,
	finished: 2,
	byCategory: { SKINCARE: 4, MAKEUP: 1 },
	byVerdict: { GOOD: 2, BAD: 1, NEUTRAL: 0, UNRATED: 2 },
	expiring: [],
	expired: [shelfItem({ id: 7, expiresOn: '2026-09-01', expiryStatus: 'EXPIRED' })],
	avgUsageDaysFinished: 7.5,
	ingredientInsights: {
		goodCount: 2,
		badCount: 3,
		ready: true,
		suspects: [{ name: '향료', badCount: 3, goodCount: 0 }, { name: '알코올', badCount: 2, goodCount: 1 }],
		friendly: [{ name: '판테놀', badCount: 0, goodCount: 2 }],
	},
};

function renderStats(entry = '/stats') {
	return render(
		<MemoryRouter initialEntries={[entry]} future={FUTURE}>
			<Routes>
				<Route path="/stats" element={<StatsPage />} />
			</Routes>
		</MemoryRouter>
	);
}

// 숫자 카드(dl): 이름 → 값
function cards() {
	const terms = screen.getAllByRole('term').map((t) => t.textContent);
	const values = screen.getAllByRole('definition').map((d) => d.textContent);
	return Object.fromEntries(terms.map((term, i) => [term, values[i]]));
}

beforeEach(() => {
	jest.clearAllMocks();
	useIsMobile.mockReturnValue(false);
	getShelfStats.mockResolvedValue(STATS);
});

test.each([
	['mobile', true],
	['desktop', false],
])('%s: 숫자 카드, 분류·평가별 막대, 기한 목록', async (layout, mobile) => {
	useIsMobile.mockReturnValue(mobile);
	renderStats();

	const category = await screen.findByRole('region', { name: '분류별' });
	expect(screen.getByRole('region', { name: '화장대 현황' })).toHaveAttribute('data-layout', layout);
	// 화면 개편으로 평균 사용 일수 카드는 뺐다
	expect(cards()).toEqual({ '전체 제품': '5개', '쓰는 중': '3개', '다 쓴 제품': '2개' });

	expect(within(category).getAllByRole('listitem').map((li) => li.textContent)).toEqual(['스킨케어4개', '메이크업1개']);
	const verdict = screen.getByRole('region', { name: '평가별' });
	expect(within(verdict).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
		'맞음2개',
		'안 맞음1개',
		'평가 전2개',
	]);
	// 가장 큰 값이 100%
	const fills = within(category).getAllByTestId('stats-bar-fill');
	expect(fills[0]).toHaveStyle({ width: '100%' });
	expect(fills[1]).toHaveStyle({ width: '25%' });

	const expired = screen.getByRole('region', { name: '사용기한 지남 1개' });
	expect(within(expired).getByText('브랜드 제품7')).toBeInTheDocument();
	expect(within(expired).queryByRole('link', { name: '브랜드 제품7' })).not.toBeInTheDocument(); // 제품 이름은 누르지 않는다
	expect(within(expired).getByText('2026-09-01까지')).toBeInTheDocument();
	expect(screen.getByText('30일 안에 사용기한이 끝나는 제품이 없어요.')).toBeInTheDocument();
});

test('화장대가 비었으면 제품 추가 안내', async () => {
	getShelfStats.mockResolvedValue({ ...STATS, total: 0, inUse: 0, finished: 0, expired: [] });
	renderStats();

	expect(await screen.findByRole('link', { name: '제품 추가하기' })).toHaveAttribute('href', '/add');
	expect(screen.queryByRole('region', { name: '분류별' })).not.toBeInTheDocument();
});

test('불러오지 못하면 메시지와 다시 시도', async () => {
	const error = new Error('x');
	error.response = { status: 500, data: { code: 'INTERNAL_ERROR', message: '잠시 문제가 생겼어요.', traceId: 'a' } };
	getShelfStats.mockRejectedValueOnce(error);
	renderStats();

	expect(await screen.findByRole('alert')).toHaveTextContent('잠시 문제가 생겼어요.');
	fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
	expect(await screen.findByRole('region', { name: '분류별' })).toBeInTheDocument();
	expect(screen.getByRole('link', { name: 'Vanity Check 홈' })).toHaveAttribute('href', '/');
});

test('맞음·안 맞음으로 가려낸 의심 성분과 잘 맞는 성분을 보여준다', async () => {
	getShelfStats.mockResolvedValue(STATS);
	renderStats();

	const section = await screen.findByRole('region', { name: /내 피부와 성분/ });
	const rows = within(section).getAllByRole('listitem').map((li) => li.textContent);
	expect(rows).toEqual([
		'향료 안 맞음 3개에 있음',
		'알코올 안 맞음 2개에 있음 · 맞음 1개에도 있음',
		'판테놀 맞음 2개에 있음',
	]);
});

test('평가가 모자라면 성분 분석 대신 평가를 더 하라고 안내한다', async () => {
	getShelfStats.mockResolvedValue({
		...STATS,
		ingredientInsights: { goodCount: 0, badCount: 1, ready: false, suspects: [], friendly: [] },
	});
	renderStats();

	const section = await screen.findByRole('region', { name: /내 피부와 성분/ });
	expect(section).toHaveTextContent('안 맞음 제품 2개, 맞음 제품 1개 이상을 평가하면');
	expect(within(section).getByRole('link', { name: '평가하러 가기' })).toHaveAttribute('href', '/shelf');
});

test('/stats#skin-ingredients로 들어오면 내 피부와 성분 구역으로 스크롤한다', async () => {
	const scrollIntoView = jest.fn();
	window.HTMLElement.prototype.scrollIntoView = scrollIntoView;
	getShelfStats.mockResolvedValue(STATS);
	renderStats('/stats#skin-ingredients');

	const section = await screen.findByRole('region', { name: /내 피부와 성분/ });
	expect(section).toHaveAttribute('id', 'skin-ingredients');
	expect(scrollIntoView).toHaveBeenCalled();
	delete window.HTMLElement.prototype.scrollIntoView;
});

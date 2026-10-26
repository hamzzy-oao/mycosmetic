// user-event 13은 act로 감싸지지 않는다. RTL의 fireEvent를 쓴다
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { checkToday, getRoutineUsage, getRoutines, getTodayRoutines } from '../../api/routines';
import useIsMobile from '../../hooks/useIsMobile';
import RoutineListPage from './RoutineListPage';
import { routine, routineStep } from './testFixtures';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/routines', () => ({
	getRoutines: jest.fn(),
	getTodayRoutines: jest.fn(),
	getRoutineUsage: jest.fn(),
	checkToday: jest.fn(),
}));
jest.mock('../../hooks/useIsMobile', () => ({
	__esModule: true,
	default: jest.fn(),
}));

const NO_USAGE = { days: 28, steadyDays: 14, items: [] };

function todayOf(overrides = {}) {
	return { date: '2026-10-22', dayOfWeek: 'THURSDAY', morning: [], evening: [], checkedMorning: [], checkedEvening: [], warnings: [], ...overrides };
}

function renderList(entry = '/routines') {
	return render(
		<MemoryRouter initialEntries={[entry]} future={FUTURE}>
			<Routes>
				<Route path="/routines" element={<RoutineListPage />} />
				<Route path="/routines/new" element={<h1>새 루틴 화면</h1>} />
				<Route path="/routines/:id" element={<h1>루틴 편집 화면</h1>} />
				<Route path="/vanity/analysis" element={<h1>화장대 진단 화면</h1>} />
			</Routes>
		</MemoryRouter>
	);
}

beforeEach(() => {
	jest.clearAllMocks();
	useIsMobile.mockReturnValue(true);
	getRoutineUsage.mockResolvedValue(NO_USAGE);
});

test('폰: 오늘 루틴과 내 루틴 목록을 보여준다', async () => {
	const morning = routine({ id: 1, name: '아침 루틴', slot: 'MORNING', steps: [routineStep(3, 'SUNSCREEN', 1)] });
	const evening = routine({ id: 2, name: '월수금 저녁', days: ['MONDAY', 'WEDNESDAY', 'FRIDAY'] });
	getRoutines.mockResolvedValue([morning, evening]);
	getTodayRoutines.mockResolvedValue(todayOf({ morning: [morning] }));
	renderList();

	const region = await screen.findByRole('region', { name: '내 루틴' });
	expect(region).toHaveAttribute('data-layout', 'mobile');
	const today = await screen.findByRole('region', { name: '오늘 루틴 (목요일)' });
	expect(within(today).getByLabelText(/제품3/)).not.toBeChecked();
	expect(within(today).queryByText('월수금 저녁')).not.toBeInTheDocument();
	const all = screen.getByRole('region', { name: '내 루틴 2개' });
	expect(within(all).getByText('저녁 · 월·수·금 · 2단계')).toBeInTheDocument();
	expect(within(all).getByText('아침 · 매일 · 1단계')).toBeInTheDocument();
	expect(within(all).getByText('선크림')).toBeInTheDocument();
});

test('PC: 루틴을 누르면 편집 화면으로, 루틴 만들기는 /routines/new', async () => {
	useIsMobile.mockReturnValue(false);
	getRoutines.mockResolvedValue([routine({ id: 5, name: '밤 루틴' })]);
	getTodayRoutines.mockResolvedValue(todayOf());
	renderList();

	expect(await screen.findByRole('region', { name: '내 루틴' })).toHaveAttribute('data-layout', 'desktop');
	expect(await screen.findByText('오늘 하는 루틴이 없어요.')).toBeInTheDocument();
	expect(screen.getByRole('link', { name: '루틴 만들기' })).toHaveAttribute('href', '/routines/new');
	fireEvent.click(screen.getByRole('link', { name: /밤 루틴/ }));
	expect(await screen.findByRole('heading', { name: '루틴 편집 화면' })).toBeInTheDocument();
});

test.each([
	['폰', true],
	['PC', false],
])('%s: 루틴 만들기는 상단에 하나만 있고 오늘, 저장한 루틴, 사용 기록 순서로 보인다', async (label, mobile) => {
	useIsMobile.mockReturnValue(mobile);
	getRoutines.mockResolvedValue([routine({ id: 1, name: '저장한 루틴' })]);
	getTodayRoutines.mockResolvedValue(todayOf());
	renderList();

	const saved = await screen.findByRole('region', { name: '내 루틴 1개' });
	const today = screen.getByRole('region', { name: '오늘 루틴 (목요일)' });
	const usage = screen.getByRole('region', { name: '최근 4주 사용' });
	const createLinks = screen.getAllByRole('link', { name: '루틴 만들기', exact: true });
	expect(createLinks).toHaveLength(1);
	expect(createLinks[0]).toHaveAttribute('href', '/routines/new');
	expect(createLinks[0].compareDocumentPosition(today) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
	expect(today.compareDocumentPosition(saved) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
	expect(saved.compareDocumentPosition(usage) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

	fireEvent.click(createLinks[0]);
	expect(await screen.findByRole('heading', { name: '새 루틴 화면' })).toBeInTheDocument();
});

test('루틴이 없으면 안내 문구', async () => {
	getRoutines.mockResolvedValue([]);
	getTodayRoutines.mockResolvedValue(todayOf());
	renderList();

	expect(await screen.findByText(/아직 만든 루틴이 없어요/)).toBeInTheDocument();
});

test('못 불러오면 서버 메시지와 다시 시도', async () => {
	const error = new Error('x');
	error.response = { status: 500, data: { code: 'INTERNAL_ERROR', message: '잠시 문제가 생겼어요.', traceId: 'a' } };
	getRoutines.mockRejectedValueOnce(error).mockResolvedValueOnce([]);
	getTodayRoutines.mockResolvedValue(todayOf());
	renderList();

	expect(await screen.findByRole('alert')).toHaveTextContent('잠시 문제가 생겼어요.');
	fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
	expect(await screen.findByText(/아직 만든 루틴이 없어요/)).toBeInTheDocument();
});

test('오늘 쓴 제품을 체크하면 서버에 체크하고, 모두 했어요는 남은 제품을 한 번에 체크한다', async () => {
	const evening = routine({ id: 2, name: '저녁 루틴', fromDiagnosis: true });
	getRoutines.mockResolvedValue([evening]);
	getTodayRoutines.mockResolvedValue(todayOf({ evening: [evening] }));
	checkToday.mockResolvedValueOnce(todayOf({ evening: [evening], checkedEvening: [1] }));
	checkToday.mockResolvedValueOnce(todayOf({ evening: [evening], checkedEvening: [1, 2] }));
	renderList();

	const group = await screen.findByRole('group', { name: '저녁' });
	expect(within(group).getByText('0/2')).toBeInTheDocument();
	fireEvent.click(within(group).getByLabelText(/제품1/));
	expect(checkToday).toHaveBeenCalledWith('EVENING', [1], true);
	expect(await within(group).findByText('1/2')).toBeInTheDocument();
	expect(within(group).getByLabelText(/제품1/)).toBeChecked();

	fireEvent.click(within(group).getByRole('button', { name: '저녁 모두 했어요' }));
	expect(checkToday).toHaveBeenLastCalledWith('EVENING', [2], true);
	expect(await within(group).findByText('저녁 루틴을 다 했어요.')).toBeInTheDocument();
	expect(getRoutineUsage).toHaveBeenCalledTimes(3);
	expect(screen.getByText('진단에서 저장')).toBeInTheDocument();
});

test('체크를 풀면 해제를 보내고, 실패하면 서버 메시지를 보여준다', async () => {
	const evening = routine({ id: 2 });
	getRoutines.mockResolvedValue([evening]);
	getTodayRoutines.mockResolvedValue(todayOf({ evening: [evening], checkedEvening: [1] }));
	const error = new Error('x');
	error.response = { status: 404, data: { code: 'NOT_FOUND', message: '찾을 수 없어요.', traceId: 'a' } };
	checkToday.mockRejectedValue(error);
	renderList();

	fireEvent.click(await screen.findByLabelText(/제품1/));
	expect(checkToday).toHaveBeenCalledWith('EVENING', [1], false);
	expect(await screen.findByRole('alert')).toHaveTextContent('찾을 수 없어요.');
});

test('오늘 루틴의 성분 충돌 경고를 제품 이름과 함께 보여준다', async () => {
	const evening = routine({ id: 2 });
	getRoutines.mockResolvedValue([evening]);
	getTodayRoutines.mockResolvedValue(todayOf({
		evening: [evening],
		warnings: [{ level: 'WARNING', code: 'RETINOID_AHA', message: '레티노이드와 AHA를 한 루틴에 함께 쓰면 자극이 커질 수 있어요.', items: [1, 2] }],
	}));
	renderList();

	expect(await screen.findByText(/레티노이드와 AHA/)).toBeInTheDocument();
	expect(screen.getByText('브랜드 제품1, 브랜드 제품2')).toBeInTheDocument();
});

test('사용 기록: 일수와 꾸준히 쓴 잘맞아요 표시, 성분 분석 링크', async () => {
	getRoutines.mockResolvedValue([]);
	getTodayRoutines.mockResolvedValue(todayOf());
	getRoutineUsage.mockResolvedValue({
		days: 28,
		steadyDays: 14,
		items: [
			{ shelfItemId: 1, brand: '브랜드', productName: '크림', verdict: 'GOOD', daysUsed: 20, steady: true },
			{ shelfItemId: 2, brand: '브랜드', productName: '토너', verdict: null, daysUsed: 3, steady: false },
		],
	});
	renderList();

	const usage = await screen.findByRole('region', { name: '최근 4주 사용' });
	expect(within(usage).getByText('20일')).toBeInTheDocument();
	expect(within(usage).getByText('꾸준히 쓴 잘맞아요')).toBeInTheDocument();
	expect(within(usage).getByText('3일')).toBeInTheDocument();
	expect(within(usage).getByRole('link', { name: '성분 분석 보기' })).toHaveAttribute('href', '/stats');
});

test('루틴이 하나도 없으면 화장대 진단에서 가져오기로 안내한다', async () => {
	getRoutines.mockResolvedValue([]);
	getTodayRoutines.mockResolvedValue(todayOf());
	renderList();

	fireEvent.click(await screen.findByRole('link', { name: '화장대 진단에서 루틴 가져오기' }));
	expect(await screen.findByRole('heading', { name: '화장대 진단 화면' })).toBeInTheDocument();
});

test('진단에서 저장하고 오면 저장했다고 알린다', async () => {
	getRoutines.mockResolvedValue([]);
	getTodayRoutines.mockResolvedValue(todayOf());
	renderList({ pathname: '/routines', state: { savedFromDiagnosis: true } });

	expect(await screen.findByRole('status')).toHaveTextContent('화장대 진단에서 루틴을 저장했어요.');
});

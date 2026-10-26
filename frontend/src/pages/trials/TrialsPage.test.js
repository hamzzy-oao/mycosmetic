import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { getShelf } from '../../api/shelf';
import { cancelTrial, finishTrial, getTrials, startTrial } from '../../api/trials';
import { shelfItem } from '../shelf/testFixtures';
import TrialsPage from './TrialsPage';

jest.mock('../../api/trials', () => ({
	getTrials: jest.fn(),
	startTrial: jest.fn(),
	finishTrial: jest.fn(),
	cancelTrial: jest.fn(),
}));
jest.mock('../../api/shelf', () => ({
	getShelf: jest.fn(),
}));

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

function renderPage() {
	return render(
		<MemoryRouter future={FUTURE}>
			<TrialsPage />
		</MemoryRouter>
	);
}

function days(start, moods) {
	const [y, m, d] = start.split('-').map(Number);
	return Array.from({ length: 14 }, (_, i) => {
		const date = new Date(Date.UTC(y, m - 1, d + i)).toISOString().slice(0, 10);
		return { date, mood: moods[i] || null };
	});
}

function trial(overrides = {}) {
	return {
		id: 9,
		shelfItemId: 1,
		product: shelfItem({ id: 1 }).product,
		startedOn: '2026-10-01',
		endsOn: '2026-10-14',
		finishedOn: null,
		day: 3,
		totalDays: 14,
		status: 'ACTIVE',
		verdict: 'UNRATED',
		summary: { recordedDays: 2, moods: { GOOD: 1, OK: 0, BAD: 1 }, tags: { TROUBLE: 1, DRYNESS: 0, OILINESS: 0, REDNESS: 0, ITCHINESS: 0 } },
		days: days('2026-10-01', ['GOOD', null, 'BAD']),
		...overrides,
	};
}

beforeEach(() => {
	jest.clearAllMocks();
	getShelf.mockResolvedValue([shelfItem({ id: 1 }), shelfItem({ id: 2 }), shelfItem({ id: 3, endedOn: '2026-09-01' })]);
	startTrial.mockResolvedValue(trial());
	finishTrial.mockResolvedValue(trial({ status: 'DONE' }));
	cancelTrial.mockResolvedValue({});
});

test('진행 중 테스트: 14일 칸에 그날 기분, 오늘 피부 기록하기는 피부 일기로', async () => {
	getTrials.mockResolvedValue([trial()]);
	renderPage();

	const active = await screen.findByRole('region', { name: /진행 중/ });
	const strip = within(active).getByRole('list', { name: '브랜드 제품1 14일 기록' });
	const cells = within(strip).getAllByRole('listitem');
	expect(cells).toHaveLength(14);
	expect(cells[0]).toHaveAccessibleName('1일째 10월 1일 좋음');
	expect(cells[1]).toHaveAccessibleName('2일째 10월 2일 기록 없음');
	expect(cells[2]).toHaveAccessibleName('3일째 10월 3일 나쁨');
	expect(within(active).getByText('기록 2일 · 좋음 1 · 보통 0 · 나쁨 1 · 트러블 1일')).toBeInTheDocument();
	expect(within(active).getByRole('link', { name: '오늘 피부 기록하기' })).toHaveAttribute('href', '/diary');
	expect(screen.getAllByRole('link', { name: '피부 일기' })[0]).toHaveAttribute('href', '/diary');
});

test('평가는 맞음·안 맞음 두 가지만, 누르면 끝내고 취소도 된다', async () => {
	getTrials.mockResolvedValue([trial({ status: 'READY', day: 14 })]);
	renderPage();

	const ready = await screen.findByRole('region', { name: /평가를 기다려요/ });
	expect(within(ready).getByText('나에게 맞았나요?')).toBeInTheDocument();
	expect(within(ready).queryByRole('button', { name: /보통으로 평가/ })).not.toBeInTheDocument();
	expect(within(ready).queryByRole('link', { name: '오늘 피부 기록하기' })).not.toBeInTheDocument();

	fireEvent.click(within(ready).getByRole('button', { name: '브랜드 제품1 안 맞음으로 평가' }));
	await waitFor(() => expect(finishTrial).toHaveBeenCalledWith(9, 'BAD'));
	// 다시 불러오는 동안은 버튼이 잠긴다
	await waitFor(() => expect(within(ready).getByRole('button', { name: '테스트 취소' })).not.toBeDisabled());

	fireEvent.click(within(ready).getByRole('button', { name: '테스트 취소' }));
	await waitFor(() => expect(cancelTrial).toHaveBeenCalledWith(9));
});

test('시작: 다 쓴 제품·이미 테스트한 제품은 고를 수 없다', async () => {
	getTrials.mockResolvedValue([trial({ status: 'DONE', verdict: 'GOOD' })]);
	renderPage();

	const select = await screen.findByLabelText('테스트할 제품');
	expect(within(select).getAllByRole('option').map((o) => o.textContent)).toEqual(['화장대에서 고르기', '브랜드 제품2']);
	expect(screen.getByRole('region', { name: /끝난 테스트/ })).toHaveTextContent('평가: 맞음');

	fireEvent.change(select, { target: { value: '2' } });
	fireEvent.click(screen.getByRole('button', { name: '오늘부터 2주 테스트' }));
	await waitFor(() => expect(startTrial).toHaveBeenCalledWith(2));
});

import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { deleteDiaryEntry, getDiaryMonth, putDiaryEntry } from '../../api/diary';
import { getTrials, startTrial } from '../../api/trials';
import { shelfItem } from '../shelf/testFixtures';
import { todayString } from '../shelf/shelfCommon';
import { addMonths, calendarCells, summaryText, trialDates } from './diaryCommon';
import DiaryPageScreen from './DiaryPage';

jest.mock('../../api/diary', () => ({
	getDiaryMonth: jest.fn(),
	putDiaryEntry: jest.fn(),
	deleteDiaryEntry: jest.fn(),
}));
jest.mock('../../api/trials', () => ({
	getTrials: jest.fn(),
	startTrial: jest.fn(),
}));

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

function DiaryPage() {
	return (
		<MemoryRouter future={FUTURE}>
			<DiaryPageScreen />
		</MemoryRouter>
	);
}

const TODAY = todayString();

function trial(overrides = {}) {
	return {
		id: 9,
		shelfItemId: 1,
		product: shelfItem({ id: 1 }).product,
		startedOn: '2026-10-01',
		endsOn: '2026-10-14',
		day: 3,
		totalDays: 14,
		status: 'ACTIVE',
		verdict: 'UNRATED',
		summary: { recordedDays: 2, moods: { GOOD: 1, OK: 0, BAD: 1 }, tags: { TROUBLE: 1, DRYNESS: 0, OILINESS: 0, REDNESS: 0, ITCHINESS: 0 } },
		...overrides,
	};
}

beforeEach(() => {
	jest.clearAllMocks();
	getDiaryMonth.mockResolvedValue({ month: TODAY.slice(0, 7), entries: [] });
	getTrials.mockResolvedValue([]);
	putDiaryEntry.mockImplementation((date, form) => Promise.resolve({ date, ...form }));
	deleteDiaryEntry.mockResolvedValue({});
});

test('달력 칸: 첫 주 앞을 비우고 그 달 날짜를 채운다, 달 더하기', () => {
	const cells = calendarCells('2026-10');
	expect(cells.slice(0, 4)).toEqual([null, null, null, null]);
	expect(cells[4]).toBe('2026-10-01');
	expect(cells[cells.length - 1]).toBe('2026-10-31');
	expect(addMonths('2026-12', 1)).toBe('2027-01');
	expect(addMonths('2026-01', -1)).toBe('2025-12');
	expect(summaryText(trial().summary)).toBe('기록 2일 · 좋음 1 · 보통 0 · 나쁨 1 · 트러블 1일');
});

test('오늘을 골라 기분·태그·메모를 저장하면 달력에 표시된다', async () => {
	render(<DiaryPage />);
	await waitFor(() => expect(getDiaryMonth).toHaveBeenCalled());

	fireEvent.click(screen.getByRole('button', { name: '저장' }));
	expect(screen.getByRole('alert')).toHaveTextContent('오늘 피부가 어땠는지 골라 주세요.');
	expect(putDiaryEntry).not.toHaveBeenCalled();

	fireEvent.click(screen.getByRole('button', { name: '나쁨' }));
	fireEvent.click(screen.getByRole('button', { name: '트러블' }));
	fireEvent.change(screen.getByLabelText('메모'), { target: { value: '턱' } });
	fireEvent.click(screen.getByRole('button', { name: '저장' }));

	await screen.findByText('저장했어요.');
	expect(putDiaryEntry).toHaveBeenCalledWith(TODAY, { mood: 'BAD', tags: ['TROUBLE'], memo: '턱' });
	const day = Number(TODAY.slice(8));
	expect(screen.getByRole('button', { name: new RegExp(`^\\d+월 ${day}일 나쁨 오늘$`) })).toBeInTheDocument();
});

test('기록이 있는 날은 불러와 보여주고 지울 수 있다. 미래 날짜와 다음 달은 누를 수 없다', async () => {
	getDiaryMonth.mockResolvedValue({ month: TODAY.slice(0, 7), entries: [{ date: TODAY, mood: 'GOOD', tags: ['DRYNESS'], memo: '촉촉' }] });
	render(<DiaryPage />);

	expect(await screen.findByDisplayValue('촉촉')).toBeInTheDocument();
	expect(screen.getByRole('button', { name: '좋음' })).toHaveAttribute('aria-pressed', 'true');
	expect(screen.getByRole('button', { name: '건조' })).toHaveAttribute('aria-pressed', 'true');
	expect(screen.getByRole('button', { name: '다음 달' })).toBeDisabled();

	fireEvent.click(screen.getByRole('button', { name: '이 날 기록 지우기' }));
	await screen.findByText('지웠어요.');
	expect(deleteDiaryEntry).toHaveBeenCalledWith(TODAY);

	fireEvent.click(screen.getByRole('button', { name: '이전 달' }));
	await waitFor(() => expect(getDiaryMonth).toHaveBeenLastCalledWith(addMonths(TODAY.slice(0, 7), -1)));
});

test('피부 기록 전용: 테스트 칸은 없고, 테스트 기간인 날만 달력에 표시한다(일찍 끝낸 뒤는 빼고)', async () => {
	const first = `${TODAY.slice(0, 7)}-01`;
	const days = Array.from({ length: 14 }, (_, i) => ({ date: `${TODAY.slice(0, 7)}-${String(i + 1).padStart(2, '0')}`, mood: null }));
	getTrials.mockResolvedValue([trial({ startedOn: first, endsOn: days[13].date, finishedOn: days[2].date, status: 'DONE', days })]);
	render(<DiaryPage />);

	expect(await screen.findByRole('button', { name: /^\d+월 1일 새 제품 테스트 중/ })).toBeInTheDocument();
	expect(screen.getByRole('button', { name: /^\d+월 3일 새 제품 테스트 중/ })).toBeInTheDocument();
	expect(screen.queryByRole('button', { name: /^\d+월 4일 새 제품 테스트 중/ })).not.toBeInTheDocument();
	expect(screen.queryByRole('region', { name: '새 제품 2주 테스트' })).not.toBeInTheDocument();
	expect(screen.getByRole('link', { name: '새 제품 테스트' })).toHaveAttribute('href', '/trials');
	expect(startTrial).not.toHaveBeenCalled();
	expect(trialDates([trial({ days: [{ date: '2026-10-01' }, { date: '2026-10-20' }], endsOn: '2026-10-14' })])).toEqual(
		new Set(['2026-10-01'])
	);
});

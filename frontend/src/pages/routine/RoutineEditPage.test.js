// user-event 13은 act로 감싸지지 않는다. RTL의 fireEvent를 쓴다
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { checkRoutineDraft, createRoutine, deleteRoutine, getRoutine, updateRoutine } from '../../api/routines';
import { getShelf } from '../../api/shelf';
import useIsMobile from '../../hooks/useIsMobile';
import { shelfItem } from '../shelf/testFixtures';
import RoutineEditPage from './RoutineEditPage';
import { STEP_TYPES } from './routineCommon';
import { routine, routineStep } from './testFixtures';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/routines', () => ({
	checkRoutineDraft: jest.fn(),
	createRoutine: jest.fn(),
	deleteRoutine: jest.fn(),
	getRoutine: jest.fn(),
	updateRoutine: jest.fn(),
}));
jest.mock('../../api/shelf', () => ({
	getShelf: jest.fn(),
}));
jest.mock('../../hooks/useIsMobile', () => ({
	__esModule: true,
	default: jest.fn(),
}));

const ORDER = STEP_TYPES.map((t) => t.value);

// 서버 검사 흉내: 1번(레티놀)과 2번(AHA)이 함께 있으면 경고, 권장 순서는 단계 종류 순
function fakeCheck(draft) {
	const ids = draft.steps.map((s) => s.shelfItemId);
	const warnings =
		ids.includes(1) && ids.includes(2)
			? [{ level: 'WARNING', code: 'RETINOID_AHA', message: '레티노이드와 AHA를 한 루틴에 함께 쓰면 자극이 커질 수 있어요.', items: [1, 2] }]
			: [];
	const suggestedOrder = [...draft.steps]
		.sort((a, b) => ORDER.indexOf(a.stepType) - ORDER.indexOf(b.stepType))
		.map((s) => s.shelfItemId);
	return Promise.resolve({ warnings, suggestedOrder });
}

function renderAt(path) {
	return render(
		<MemoryRouter initialEntries={[path]} future={FUTURE}>
			<Routes>
				<Route path="/routines" element={<h1>루틴 목록 화면</h1>} />
				<Route path="/routines/new" element={<RoutineEditPage />} />
				<Route path="/routines/:id" element={<RoutineEditPage />} />
			</Routes>
		</MemoryRouter>
	);
}

function stepTitles() {
	return within(screen.getByRole('list', { name: '루틴 단계' }))
		.getAllByRole('listitem')
		.map((li) => li.getAttribute('aria-label'));
}

function expectEditorFlow(saveButtonName) {
	const basics = screen.getByRole('region', { name: '기본 설정' });
	const products = screen.getByRole('region', { name: /제품 추가·순서/ });
	const save = screen.getByRole('region', { name: '루틴 저장' });
	expect(within(basics).getByLabelText('루틴 이름')).toBeInTheDocument();
	expect(within(basics).getByRole('group', { name: '시간대' })).toBeInTheDocument();
	expect(within(basics).getByRole('group', { name: '요일' })).toBeInTheDocument();
	const addButton = within(products).getByRole('button', { name: '단계 추가' });
	const saveButton = within(save).getByRole('button', { name: saveButtonName });
	expect(basics.compareDocumentPosition(products) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
	expect(products.compareDocumentPosition(save) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
	expect(addButton.compareDocumentPosition(saveButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
	expect(within(save).getByRole('region', { name: '함께 쓰기 확인' })).toBeInTheDocument();
}

let confirmSpy;

beforeEach(() => {
	jest.clearAllMocks();
	useIsMobile.mockReturnValue(true);
	getShelf.mockResolvedValue([shelfItem({ id: 1 }), shelfItem({ id: 2 }), shelfItem({ id: 3 })]);
	checkRoutineDraft.mockImplementation(fakeCheck);
	confirmSpy = jest.spyOn(window, 'confirm');
});

afterEach(() => {
	confirmSpy.mockRestore();
});

function addStep(itemTitle, typeLabel) {
	fireEvent.change(screen.getByLabelText('내 화장대에서 고르기'), {
		target: { value: String(screen.getByRole('option', { name: itemTitle }).value) },
	});
	fireEvent.change(screen.getByLabelText('새 단계 종류'), {
		target: { value: STEP_TYPES.find((t) => t.label === typeLabel).value },
	});
	fireEvent.click(screen.getByRole('button', { name: '단계 추가' }));
}

test('새 루틴: 단계 종류는 한국어로 고르고, 단계를 넣을 때마다 경고를 바로 보여준다', async () => {
	renderAt('/routines/new');

	expect(await screen.findByRole('region', { name: '새 루틴' })).toHaveAttribute('data-layout', 'mobile');
	expect(await screen.findByText('함께 쓰면 안 좋은 조합이 없어요.')).toBeInTheDocument();
	expectEditorFlow('루틴 만들기');
	const typeOptions = within(screen.getByLabelText('새 단계 종류')).getAllByRole('option').map((o) => o.textContent);
	expect(typeOptions).toEqual(['클렌저', '토너', '에센스', '세럼', '앰플', '아이크림', '로션', '크림', '선크림', '마스크', '기타']);

	addStep('브랜드 제품1', '세럼');
	addStep('브랜드 제품2', '토너');

	expect(await screen.findByText('레티노이드와 AHA를 한 루틴에 함께 쓰면 자극이 커질 수 있어요.')).toBeInTheDocument();
	expect(screen.getByText('경고')).toBeInTheDocument();
	expect(screen.getByText('브랜드 제품1, 브랜드 제품2')).toBeInTheDocument();
	expect(checkRoutineDraft).toHaveBeenLastCalledWith(
		{
			name: '편집 중',
			slot: 'MORNING',
			days: [],
			steps: [
				{ shelfItemId: 1, stepType: 'SERUM' },
				{ shelfItemId: 2, stepType: 'TONER' },
			],
		},
		undefined
	);
	// 이미 넣은 제품은 고르기 목록에서 빠진다
	expect(screen.queryByRole('option', { name: '브랜드 제품1' })).not.toBeInTheDocument();
});

test('권장 순서로 정렬하면 단계 종류 순서로 바뀌고, 이미 권장 순서면 버튼이 꺼진다', async () => {
	renderAt('/routines/new');
	await screen.findByText('함께 쓰면 안 좋은 조합이 없어요.');
	addStep('브랜드 제품1', '크림');
	addStep('브랜드 제품3', '클렌저');

	const sortButton = screen.getByRole('button', { name: '권장 순서로 정렬' });
	await waitFor(() => expect(sortButton).toBeEnabled());
	expect(stepTitles()).toEqual(['브랜드 제품1', '브랜드 제품3']);

	fireEvent.click(sortButton);

	expect(stepTitles()).toEqual(['브랜드 제품3', '브랜드 제품1']);
	await waitFor(() => expect(screen.getByRole('button', { name: '권장 순서로 정렬' })).toBeDisabled());
});

test('새 루틴 저장: 이름·시간대·요일(월요일부터 순서대로)을 보내고 편집 화면으로 간다', async () => {
	createRoutine.mockResolvedValue(routine({ id: 42 }));
	getRoutine.mockResolvedValue(routine({ id: 42 }));
	renderAt('/routines/new');
	await screen.findByText('함께 쓰면 안 좋은 조합이 없어요.');

	fireEvent.click(screen.getByRole('button', { name: '루틴 만들기' }));
	expect(await screen.findByRole('alert')).toHaveTextContent('루틴 이름을 적어 주세요.');
	expect(createRoutine).not.toHaveBeenCalled();

	fireEvent.change(screen.getByLabelText('루틴 이름'), { target: { value: ' 밤 루틴 ' } });
	fireEvent.click(screen.getByRole('button', { name: '저녁' }));
	fireEvent.click(screen.getByRole('button', { name: '금' }));
	fireEvent.click(screen.getByRole('button', { name: '월' }));
	expect(screen.getByRole('button', { name: '월' })).toHaveAttribute('aria-pressed', 'true');
	addStep('브랜드 제품3', '토너');
	fireEvent.click(screen.getByRole('button', { name: '루틴 만들기' }));

	await waitFor(() => expect(getRoutine).toHaveBeenCalledWith(42));
	expect(createRoutine).toHaveBeenCalledWith({
		name: '밤 루틴',
		slot: 'EVENING',
		days: ['MONDAY', 'FRIDAY'],
		steps: [{ shelfItemId: 3, stepType: 'TONER' }],
	});
	expect(await screen.findByRole('region', { name: '루틴 편집' })).toBeInTheDocument();
});

test('PC 편집: 불러온 루틴을 보여주고, 단계 종류·순서를 바꾸면 다시 검사한 뒤 저장한다', async () => {
	useIsMobile.mockReturnValue(false);
	getRoutine.mockResolvedValue(routine({ id: 7, days: ['SUNDAY'] }));
	updateRoutine.mockImplementation((id, draft) =>
		Promise.resolve(
			routine({
				id,
				name: draft.name,
				slot: draft.slot,
				days: draft.days,
				steps: draft.steps.map((s, i) => routineStep(s.shelfItemId, s.stepType, i + 1)),
			})
		)
	);
	renderAt('/routines/7');

	expect(await screen.findByRole('region', { name: '루틴 편집' })).toHaveAttribute('data-layout', 'desktop');
	expect(await screen.findByLabelText('루틴 이름')).toHaveValue('저녁 루틴');
	expectEditorFlow('저장');
	expect(screen.getByRole('button', { name: '일' })).toHaveAttribute('aria-pressed', 'true');
	expect(await screen.findByText(/레티노이드와 AHA/)).toBeInTheDocument();
	expect(checkRoutineDraft).toHaveBeenLastCalledWith(expect.objectContaining({ slot: 'EVENING', days: ['SUNDAY'] }), 7);

	fireEvent.change(screen.getByLabelText('브랜드 제품2 단계 종류'), { target: { value: 'CLEANSER' } });
	await waitFor(() =>
		expect(checkRoutineDraft).toHaveBeenLastCalledWith(
			expect.objectContaining({
				steps: [
					{ shelfItemId: 1, stepType: 'TONER' },
					{ shelfItemId: 2, stepType: 'CLEANSER' },
				],
			}),
			7
		)
	);
	fireEvent.click(screen.getByRole('button', { name: '브랜드 제품2 위로' }));
	expect(stepTitles()).toEqual(['브랜드 제품2', '브랜드 제품1']);
	expect(screen.getByRole('button', { name: '브랜드 제품2 위로' })).toBeDisabled();
	fireEvent.click(screen.getByRole('button', { name: '브랜드 제품2 아래로' }));
	expect(stepTitles()).toEqual(['브랜드 제품1', '브랜드 제품2']);

	fireEvent.click(screen.getByRole('button', { name: '브랜드 제품1 빼기' }));
	expect(stepTitles()).toEqual(['브랜드 제품2']);
	await waitFor(() => expect(screen.queryByText(/레티노이드와 AHA/)).not.toBeInTheDocument());

	fireEvent.click(screen.getByRole('button', { name: '저장' }));

	expect(await screen.findByRole('status')).toHaveTextContent('저장했어요.');
	expect(updateRoutine).toHaveBeenCalledWith(7, {
		name: '저녁 루틴',
		slot: 'EVENING',
		days: ['SUNDAY'],
		steps: [{ shelfItemId: 2, stepType: 'CLEANSER' }],
	});
});

test('저장이 실패하면 서버 메시지를 보여준다', async () => {
	getRoutine.mockResolvedValue(routine());
	const error = new Error('x');
	error.response = { status: 404, data: { code: 'NOT_FOUND', message: '찾을 수 없어요.', traceId: 'a' } };
	updateRoutine.mockRejectedValue(error);
	renderAt('/routines/7');

	fireEvent.click(await screen.findByRole('button', { name: '저장' }));

	expect(await screen.findByRole('alert')).toHaveTextContent('찾을 수 없어요.');
});

test('검사가 실패하면 경고 자리에 서버 메시지를 보여준다', async () => {
	const error = new Error('x');
	error.response = { status: 404, data: { code: 'NOT_FOUND', message: '찾을 수 없어요.', traceId: 'a' } };
	checkRoutineDraft.mockRejectedValue(error);
	renderAt('/routines/new');

	const panel = await screen.findByRole('region', { name: '함께 쓰기 확인' });
	expect(await within(panel).findByRole('alert')).toHaveTextContent('찾을 수 없어요.');
});

test('루틴을 못 불러오면 서버 메시지', async () => {
	const error = new Error('x');
	error.response = { status: 404, data: { code: 'NOT_FOUND', message: '찾을 수 없어요.', traceId: 'a' } };
	getRoutine.mockRejectedValue(error);
	renderAt('/routines/99');

	expect(await screen.findByRole('alert')).toHaveTextContent('찾을 수 없어요.');
	expect(screen.queryByLabelText('루틴 이름')).not.toBeInTheDocument();
});

test('삭제는 확인하면 지우고 목록으로, 취소하면 그대로', async () => {
	getRoutine.mockResolvedValue(routine());
	deleteRoutine.mockResolvedValue({});
	renderAt('/routines/7');
	const button = await screen.findByRole('button', { name: '루틴 삭제' });

	confirmSpy.mockReturnValue(false);
	fireEvent.click(button);
	expect(deleteRoutine).not.toHaveBeenCalled();

	confirmSpy.mockReturnValue(true);
	fireEvent.click(button);
	expect(await screen.findByRole('heading', { name: '루틴 목록 화면' })).toBeInTheDocument();
	expect(deleteRoutine).toHaveBeenCalledWith(7);
});

test('화장대가 비었으면 제품 추가로 안내한다', async () => {
	getShelf.mockResolvedValue([]);
	renderAt('/routines/new');

	expect(await screen.findByRole('link', { name: '제품 추가' })).toHaveAttribute('href', '/add');
	expect(screen.getByText(/아직 단계가 없어요/)).toBeInTheDocument();
});

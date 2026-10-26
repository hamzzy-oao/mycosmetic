// 내 화장대: 성분으로 찾기(no-ai-features 설계 C)와 개봉일·사용기한(설계 D)
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { getShelf, searchShelfByIngredient, updateShelfItem } from '../../api/shelf';
import useIsMobile from '../../hooks/useIsMobile';
import ShelfPage from './ShelfPage';
import { daysUntil, todayString } from './shelfCommon';
import { shelfItem } from './testFixtures';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/shelf', () => ({
	getShelf: jest.fn(),
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

function renderShelf() {
	return render(
		<MemoryRouter initialEntries={['/shelf']} future={FUTURE}>
			<Routes>
				<Route path="/shelf" element={<ShelfPage />} />
				<Route path="/compare" element={<h1>비교 화면</h1>} />
				<Route path="/stats" element={<h1>통계 화면</h1>} />
			</Routes>
		</MemoryRouter>
	);
}

function addDays(days) {
	const d = new Date();
	d.setDate(d.getDate() + days);
	return todayString(d);
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

test.each([
	['폰', true],
	['PC', false],
])('%s: 성분으로 찾으면 일치한 항목만 일치한 성분을 강조해 보여주고, 전체 보기로 돌아간다', async (_, mobile) => {
	useIsMobile.mockReturnValue(mobile);
	getShelf.mockResolvedValue([shelfItem({ id: 1 }), shelfItem({ id: 2 })]);
	searchShelfByIngredient.mockResolvedValue([{ ...shelfItem({ id: 2 }), matchedIngredients: ['레티놀', '레티닐팔미테이트'] }]);
	renderShelf();

	const input = await screen.findByLabelText('성분으로 찾기');
	fireEvent.change(input, { target: { value: ' 레티 ' } });
	fireEvent.click(screen.getByRole('button', { name: '찾기' }));

	expect(await screen.findByRole('status')).toHaveTextContent("'레티' 성분이 든 제품 1개");
	expect(searchShelfByIngredient).toHaveBeenCalledWith('레티');
	expect(screen.queryByText('브랜드 제품1')).not.toBeInTheDocument();
	expect(screen.getByText('브랜드 제품2')).toBeInTheDocument();
	expect(screen.getByText('레티놀', { selector: 'mark' })).toBeInTheDocument();
	expect(screen.getByText('레티닐팔미테이트', { selector: 'mark' })).toBeInTheDocument();

	fireEvent.click(screen.getByRole('button', { name: '전체 보기' }));
	expect(await screen.findByText('브랜드 제품1')).toBeInTheDocument();
	expect(screen.queryByText('레티놀', { selector: 'mark' })).not.toBeInTheDocument();
});

test('성분 검색: 빈 검색어는 보내지 않고 안내, 일치가 없으면 없다고 알린다, 서버 오류는 메시지', async () => {
	getShelf.mockResolvedValue([shelfItem({ id: 1 })]);
	renderShelf();

	fireEvent.click(await screen.findByRole('button', { name: '찾기' }));
	expect(await screen.findByRole('alert')).toHaveTextContent('찾을 성분 이름을 적어 주세요.');
	expect(searchShelfByIngredient).not.toHaveBeenCalled();

	searchShelfByIngredient.mockResolvedValueOnce([]);
	fireEvent.change(screen.getByLabelText('성분으로 찾기'), { target: { value: '향료' } });
	fireEvent.click(screen.getByRole('button', { name: '찾기' }));
	expect(await screen.findByRole('status')).toHaveTextContent("'향료' 성분이 든 제품이 없어요.");
	expect(screen.queryByRole('listitem')).not.toBeInTheDocument();

	searchShelfByIngredient.mockRejectedValueOnce(serverError(400, 'INVALID_REQUEST', '입력한 내용을 다시 확인해 주세요.'));
	fireEvent.click(screen.getByRole('button', { name: '찾기' }));
	expect(await screen.findByRole('alert')).toHaveTextContent('입력한 내용을 다시 확인해 주세요.');
});

// 비교·현황 링크는 화면 개편으로 공통 탭(BeautyShell)으로 옮겼다. BeautyShell.test.js에서 본다

test.each([
	['폰', true],
	['PC', false],
])('%s: 사용기한 배지 — 지남은 "기한 지남", 30일 안이면 "곧 끝나요(D-n)", 그 외는 없음', async (_, mobile) => {
	useIsMobile.mockReturnValue(mobile);
	const soon = addDays(5);
	getShelf.mockResolvedValue([
		shelfItem({ id: 1, openedOn: '2025-01-01', paoMonths: 6, expiresOn: '2025-07-01', expiryStatus: 'EXPIRED' }),
		shelfItem({ id: 2, openedOn: '2026-01-01', paoMonths: 12, expiresOn: soon, expiryStatus: 'EXPIRING' }),
		shelfItem({ id: 3, expiryStatus: 'OK', openedOn: todayString(), paoMonths: 12, expiresOn: '2099-01-01' }),
		shelfItem({ id: 4, expiryStatus: 'UNKNOWN' }),
	]);
	renderShelf();

	expect(await screen.findByText('기한 지남')).toBeInTheDocument();
	expect(screen.getByText(`곧 끝나요(D-${daysUntil(soon)})`)).toBeInTheDocument();
	expect(screen.getAllByText(/기한 지남|곧 끝나요/)).toHaveLength(2);
	expect(screen.getByText('개봉 2025-01-01 · 2025-07-01까지')).toBeInTheDocument();
});

test('D-n: 오늘 끝나면 D-day', () => {
	expect(daysUntil('2026-10-27', '2026-10-22')).toBe(5);
	expect(daysUntil('2026-10-22', '2026-10-22')).toBe(0);
	expect(daysUntil('2026-11-01', '2026-10-31')).toBe(1);
});

test('폰: 개봉일·사용기한을 적으면 바뀐 값만 PATCH', async () => {
	const item = shelfItem({ id: 1 });
	getShelf.mockResolvedValue([item]);
	const opened = addDays(-10);
	updateShelfItem.mockResolvedValue({ ...item, openedOn: opened, paoMonths: 12, expiryStatus: 'OK' });
	renderShelf();

	const card = await screen.findByRole('listitem', { name: '브랜드 제품1' });
	fireEvent.click(within(card).getByRole('button', { name: '메모·기간' }));
	fireEvent.change(within(card).getByLabelText('개봉일'), { target: { value: opened } });
	fireEvent.change(within(card).getByLabelText('사용기한(개월)'), { target: { value: '12' } });
	fireEvent.click(within(card).getByRole('button', { name: '메모·기간 저장' }));

	expect(await within(card).findByRole('status')).toHaveTextContent('저장했어요.');
	expect(updateShelfItem).toHaveBeenCalledWith(1, { openedOn: opened, paoMonths: 12 });
});

test.each([
	['0', '사용기한은 1~60개월 사이로 적어 주세요.'],
	['61', '사용기한은 1~60개월 사이로 적어 주세요.'],
	['1.5', '사용기한은 1~60개월 사이로 적어 주세요.'],
])('PC: 사용기한 %s개월은 보내지 않고 안내', async (months, message) => {
	useIsMobile.mockReturnValue(false);
	getShelf.mockResolvedValue([shelfItem({ id: 1 })]);
	renderShelf();

	fireEvent.click(await screen.findByRole('button', { name: '브랜드 제품1 성분·기간 보기' }));
	fireEvent.change(screen.getByLabelText('사용기한(개월)'), { target: { value: months } });
	fireEvent.click(screen.getByRole('button', { name: '메모·기간 저장' }));

	await waitFor(() => expect(screen.getAllByRole('alert').map((a) => a.textContent)).toContain(message));
	expect(updateShelfItem).not.toHaveBeenCalled();
});

test('폰: 개봉일이 오늘보다 뒤면 보내지 않고 안내', async () => {
	getShelf.mockResolvedValue([shelfItem({ id: 1 })]);
	renderShelf();

	const card = await screen.findByRole('listitem', { name: '브랜드 제품1' });
	fireEvent.click(within(card).getByRole('button', { name: '메모·기간' }));
	fireEvent.change(within(card).getByLabelText('개봉일'), { target: { value: addDays(3) } });
	fireEvent.click(within(card).getByRole('button', { name: '메모·기간 저장' }));

	expect(await within(card).findByRole('alert')).toHaveTextContent('개봉일은 오늘보다 뒤일 수 없어요.');
	expect(updateShelfItem).not.toHaveBeenCalled();
});

test('오늘 개봉했어요: 개봉일을 오늘로, 비어 있던 사용기한은 12개월로 채워 저장한다', async () => {
	const item = shelfItem({ id: 1, openedOn: null, paoMonths: null });
	getShelf.mockResolvedValue([item]);
	updateShelfItem.mockResolvedValue({ ...item, openedOn: todayString(), paoMonths: 12, expiryStatus: 'OK' });
	renderShelf();

	const card = await screen.findByRole('listitem', { name: '브랜드 제품1' });
	fireEvent.click(within(card).getByRole('button', { name: '메모·기간' }));
	fireEvent.click(within(card).getByRole('button', { name: '오늘 개봉했어요' }));
	expect(within(card).queryByRole('button', { name: '오늘 개봉했어요' })).not.toBeInTheDocument();
	fireEvent.click(within(card).getByRole('button', { name: '메모·기간 저장' }));

	expect(await within(card).findByRole('status')).toHaveTextContent('저장했어요.');
	expect(updateShelfItem).toHaveBeenCalledWith(1, { openedOn: todayString(), paoMonths: 12 });
});

test('제품명으로 찾기: 입력하는 대로 브랜드·제품명으로 걸러 서버를 부르지 않는다', async () => {
	getShelf.mockResolvedValue([shelfItem({ id: 1 }), shelfItem({ id: 2 })]);
	renderShelf();
	await screen.findByRole('listitem', { name: '브랜드 제품1' });

	fireEvent.change(screen.getByLabelText('제품명으로 찾기'), { target: { value: '제품2' } });

	expect(screen.queryByRole('listitem', { name: '브랜드 제품1' })).not.toBeInTheDocument();
	expect(screen.getByRole('listitem', { name: '브랜드 제품2' })).toBeInTheDocument();
	expect(screen.getByRole('status')).toHaveTextContent("'제품2' 제품 1개");
	expect(searchShelfByIngredient).not.toHaveBeenCalled();
	fireEvent.change(screen.getByLabelText('제품명으로 찾기'), { target: { value: '없는' } });
	expect(screen.getByRole('status')).toHaveTextContent("'없는' 제품이 없어요.");
});

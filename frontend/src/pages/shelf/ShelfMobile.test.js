// user-event 13은 최상위 @testing-library/dom(10)을 써서 act로 감싸지지 않는다. RTL의 fireEvent를 쓴다
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { getShelf, removeShelfItem, updateShelfItem } from '../../api/shelf';
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
				<Route path="/" element={<h1>메인 화면</h1>} />
				<Route path="/shelf" element={<ShelfPage />} />
				<Route path="/add" element={<h1>제품 추가 화면</h1>} />
				<Route path="/products/:id" element={<h1>제품 상세 화면</h1>} />
			</Routes>
		</MemoryRouter>
	);
}

function serverError(status, code, message) {
	const error = new Error(code);
	error.response = { status, data: { code, message, traceId: 'abc123' } };
	return error;
}

let confirmSpy;

beforeEach(() => {
	jest.clearAllMocks();
	useIsMobile.mockReturnValue(true);
	confirmSpy = jest.spyOn(window, 'confirm');
});

afterEach(() => {
	confirmSpy.mockRestore();
});

test('폰: 카드 목록으로 보인다', async () => {
	getShelf.mockResolvedValue([shelfItem({ id: 1 }), shelfItem({ id: 2 })]);
	renderShelf();

	const region = await screen.findByRole('region', { name: '내 제품' });
	expect(region).toHaveAttribute('data-layout', 'mobile');
	expect(await screen.findAllByRole('listitem')).toHaveLength(2);
	expect(screen.queryByRole('table')).not.toBeInTheDocument();
});

test('폰: 맞음 버튼을 누르면 PATCH verdict=GOOD 후 카드 표시가 바뀐다', async () => {
	const item = shelfItem({ id: 1 });
	getShelf.mockResolvedValue([item]);
	updateShelfItem.mockResolvedValue({ ...item, verdict: 'GOOD', updatedAt: '2026-10-22T00:00:00Z' });
	renderShelf();

	const card = await screen.findByRole('listitem', { name: '브랜드 제품1' });
	expect(within(card).getByText('평가 전')).toBeInTheDocument();
	const good = within(card).getByRole('button', { name: '맞음' });
	expect(good).toHaveAttribute('aria-pressed', 'false');

	fireEvent.click(good);

	await waitFor(() => expect(within(card).getByRole('button', { name: '맞음' })).toHaveAttribute('aria-pressed', 'true'));
	expect(updateShelfItem).toHaveBeenCalledWith(1, { verdict: 'GOOD' });
	expect(within(card).queryByText('평가 전')).not.toBeInTheDocument();
	expect(within(card).getAllByText('맞음')).toHaveLength(2);
});

test('폰: 이미 고른 평가를 다시 누르면 평가 전으로 되돌린다', async () => {
	const item = shelfItem({ id: 1, verdict: 'BAD' });
	getShelf.mockResolvedValue([item]);
	updateShelfItem.mockResolvedValue({ ...item, verdict: 'UNRATED' });
	renderShelf();

	const card = await screen.findByRole('listitem', { name: '브랜드 제품1' });
	fireEvent.click(within(card).getByRole('button', { name: '안 맞음' }));

	expect(await within(card).findByText('평가 전')).toBeInTheDocument();
	expect(updateShelfItem).toHaveBeenCalledWith(1, { verdict: 'UNRATED' });
});

test('폰: 평가 수정이 실패하면 서버 메시지를 보여주고 표시는 그대로', async () => {
	getShelf.mockResolvedValue([shelfItem({ id: 1 })]);
	updateShelfItem.mockRejectedValue(serverError(404, 'NOT_FOUND', '찾을 수 없어요.'));
	renderShelf();

	const card = await screen.findByRole('listitem', { name: '브랜드 제품1' });
	fireEvent.click(within(card).getByRole('button', { name: '맞음' }));

	expect(await screen.findByRole('alert')).toHaveTextContent('찾을 수 없어요.');
	expect(within(card).getByText('평가 전')).toBeInTheDocument();
});

test('폰: 대표 버전이 아니면 "새 성분 버전이 있어요" 표시', async () => {
	getShelf.mockResolvedValue([
		shelfItem({ id: 1, listIsRepresentative: false }),
		shelfItem({ id: 2, listIsRepresentative: true }),
	]);
	renderShelf();

	const oldCard = await screen.findByRole('listitem', { name: '브랜드 제품1' });
	const link = within(oldCard).getByRole('link', { name: '새 성분 버전이 있어요' });
	expect(link).toHaveAttribute('href', '/products/101');
	const currentCard = screen.getByRole('listitem', { name: '브랜드 제품2' });
	expect(within(currentCard).queryByText('새 성분 버전이 있어요')).not.toBeInTheDocument();
});

test('빈 화장대면 제품 추가 안내 버튼', async () => {
	getShelf.mockResolvedValue([]);
	renderShelf();

	expect(await screen.findByText('아직 화장대에 담은 제품이 없어요.')).toBeInTheDocument();
	fireEvent.click(screen.getByRole('link', { name: '제품 추가하기' }));
	expect(await screen.findByRole('heading', { name: '제품 추가 화면' })).toBeInTheDocument();
});

test('폰: 메모·기간을 고치면 바뀐 필드만 PATCH', async () => {
	const item = shelfItem({ id: 1, memo: '처음 메모', startedOn: '2026-09-01' });
	getShelf.mockResolvedValue([item]);
	updateShelfItem.mockResolvedValue({ ...item, memo: '바뀐 메모', endedOn: '2026-10-01' });
	renderShelf();

	const card = await screen.findByRole('listitem', { name: '브랜드 제품1' });
	fireEvent.click(within(card).getByRole('button', { name: '메모·기간' }));
	fireEvent.change(within(card).getByLabelText('메모'), { target: { value: '바뀐 메모' } });
	fireEvent.change(within(card).getByLabelText('사용 종료일'), { target: { value: '2026-10-01' } });
	fireEvent.click(within(card).getByRole('button', { name: '메모·기간 저장' }));

	expect(await within(card).findByRole('status')).toHaveTextContent('저장했어요.');
	expect(updateShelfItem).toHaveBeenCalledWith(1, { memo: '바뀐 메모', endedOn: '2026-10-01' });
});

test('폰: 사용 종료일이 시작일보다 앞이면 보내지 않고 안내', async () => {
	getShelf.mockResolvedValue([shelfItem({ id: 1, startedOn: '2026-09-10' })]);
	renderShelf();

	const card = await screen.findByRole('listitem', { name: '브랜드 제품1' });
	fireEvent.click(within(card).getByRole('button', { name: '메모·기간' }));
	fireEvent.change(within(card).getByLabelText('사용 종료일'), { target: { value: '2026-09-01' } });
	fireEvent.click(within(card).getByRole('button', { name: '메모·기간 저장' }));

	expect(await within(card).findByRole('alert')).toHaveTextContent('사용 종료일은 시작일보다 앞일 수 없어요.');
	expect(updateShelfItem).not.toHaveBeenCalled();
});

test('폰: 확인하면 화장대에서 빼고, 취소하면 그대로', async () => {
	getShelf.mockResolvedValue([shelfItem({ id: 1 })]);
	removeShelfItem.mockResolvedValue({});
	renderShelf();

	const card = await screen.findByRole('listitem', { name: '브랜드 제품1' });
	fireEvent.click(within(card).getByRole('button', { name: '메모·기간' }));

	confirmSpy.mockReturnValue(false);
	fireEvent.click(within(card).getByRole('button', { name: '화장대에서 빼기' }));
	expect(removeShelfItem).not.toHaveBeenCalled();

	confirmSpy.mockReturnValue(true);
	fireEvent.click(within(card).getByRole('button', { name: '화장대에서 빼기' }));
	expect(await screen.findByText('아직 화장대에 담은 제품이 없어요.')).toBeInTheDocument();
	expect(removeShelfItem).toHaveBeenCalledWith(1);
});

test('목록을 못 불러오면 서버 메시지와 다시 시도 버튼', async () => {
	getShelf.mockRejectedValueOnce(serverError(500, 'INTERNAL_ERROR', '잠시 뒤 다시 시도해 주세요.'));
	getShelf.mockResolvedValueOnce([shelfItem({ id: 1 })]);
	renderShelf();

	expect(await screen.findByRole('alert')).toHaveTextContent('잠시 뒤 다시 시도해 주세요.');
	fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
	expect(await screen.findByRole('listitem', { name: '브랜드 제품1' })).toBeInTheDocument();
	expect(getShelf).toHaveBeenCalledTimes(2);
});

test('왼쪽 위 로고 링크로 메인(/)에 돌아간다(폰·PC)', async () => {
	getShelf.mockResolvedValue([]);
	useIsMobile.mockReturnValue(false);
	const { unmount } = renderShelf();
	expect(await screen.findByRole('link', { name: 'Vanity Check 홈' })).toHaveAttribute('href', '/');
	unmount();

	useIsMobile.mockReturnValue(true);
	renderShelf();
	fireEvent.click(await screen.findByRole('link', { name: 'Vanity Check 홈' }));
	expect(await screen.findByRole('heading', { name: '메인 화면' })).toBeInTheDocument();
});

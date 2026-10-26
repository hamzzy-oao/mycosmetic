import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { getShelf } from '../../api/shelf';
import { useAuth } from '../../AuthContext';
import { shelfItem } from '../shelf/testFixtures';
import { loadShareCardLogo } from './drawShareCard';
import SharePage from './SharePage';

jest.mock('../../api/shelf', () => ({
	getShelf: jest.fn(),
}));
jest.mock('../../AuthContext', () => ({ useAuth: jest.fn() }));
jest.mock('./drawShareCard', () => ({
	...jest.requireActual('./drawShareCard'),
	loadShareCardLogo: jest.fn(),
}));

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };
const LOGO = { width: 1010, height: 400 };
const shareDescriptor = Object.getOwnPropertyDescriptor(navigator, 'share');
const canShareDescriptor = Object.getOwnPropertyDescriptor(navigator, 'canShare');
let drawImage;

function renderPage() {
	return render(
		<MemoryRouter future={FUTURE}>
			<SharePage />
		</MemoryRouter>
	);
}

beforeEach(() => {
	jest.clearAllMocks();
	loadShareCardLogo.mockReset().mockResolvedValue(LOGO);
	useAuth.mockReturnValue({ user: { id: 1, nickname: '테스터' } });
	// jsdom에는 canvas 그리기가 없다. 그리기 호출만 받아 두는 가짜 2D 컨텍스트
	drawImage = jest.fn();
	const ctx = new Proxy({ drawImage }, { get: (target, key) => (key in target ? target[key] : key === 'measureText' ? () => ({ width: 10 }) : () => {}), set: (target, key, value) => { target[key] = value; return true; } });
	jest.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctx);
});

afterEach(() => {
	jest.restoreAllMocks();
	if (shareDescriptor) Object.defineProperty(navigator, 'share', shareDescriptor);
	else delete navigator.share;
	if (canShareDescriptor) Object.defineProperty(navigator, 'canShare', canShareDescriptor);
	else delete navigator.canShare;
});

test('맞음 제품만 고를 수 있고 최대 5개가 기본 선택된다', async () => {
	getShelf.mockResolvedValue([
		...[1, 2, 3, 4, 5, 6].map((id) => shelfItem({ id, verdict: 'GOOD' })),
		shelfItem({ id: 7, verdict: 'BAD' }),
	]);
	renderPage();

	const boxes = await screen.findAllByRole('checkbox');
	expect(boxes).toHaveLength(6);
	expect(boxes.filter((b) => b.checked)).toHaveLength(5);
	expect(boxes[5]).toBeDisabled();
	expect(screen.getByText('5/5')).toBeInTheDocument();
	const canvas = screen.getByRole('img', { name: /나의 인생템 카드: 브랜드 제품1/ });
	expect(canvas).toHaveAttribute('width', '1080');
	expect(canvas).toHaveAttribute('height', '1350');

	fireEvent.click(boxes[0]);
	expect(screen.getByText('4/5')).toBeInTheDocument();
	expect(boxes[5]).not.toBeDisabled();
	fireEvent.click(boxes[1]);
	expect(screen.getByText('3/5')).toBeInTheDocument();
	expect(canvas).toHaveAttribute('height', '1080');
	await waitFor(() => expect(screen.getByRole('button', { name: '이미지 저장' })).toBeEnabled());
	fireEvent.click(boxes[1]);
	expect(canvas).toHaveAttribute('height', '1350');
	fireEvent.click(boxes[5]);
	expect(screen.getByText('5/5')).toBeInTheDocument();
	expect(boxes[0]).toBeDisabled();
	await waitFor(() => expect(screen.getByRole('button', { name: '이미지 저장' })).toBeEnabled());
});

test('맞음 제품이 없으면 평가하러 가기를 안내한다', async () => {
	getShelf.mockResolvedValue([shelfItem({ id: 1 })]);
	renderPage();

	expect(await screen.findByRole('link', { name: '화장대에서 평가하기' })).toHaveAttribute('href', '/shelf');
	expect(screen.queryByRole('button', { name: '이미지 저장' })).not.toBeInTheDocument();
});

test('로고가 그려지기 전에는 저장과 공유를 막고 완료되면 사용할 수 있다', async () => {
	let resolveLogo;
	loadShareCardLogo.mockReturnValue(new Promise((resolve) => { resolveLogo = resolve; }));
	getShelf.mockResolvedValue([shelfItem({ id: 1, verdict: 'GOOD' })]);
	Object.defineProperty(navigator, 'share', { configurable: true, value: jest.fn() });
	Object.defineProperty(navigator, 'canShare', { configurable: true, value: jest.fn(() => true) });
	renderPage();

	const canvas = await screen.findByRole('img', { name: /나의 인생템 카드/ });
	expect(canvas).toHaveAttribute('aria-busy', 'true');
	expect(screen.getByText('카드를 만들고 있어요…')).toBeInTheDocument();
	expect(screen.getByRole('button', { name: '이미지 저장' })).toBeDisabled();
	expect(screen.getByRole('button', { name: '공유하기' })).toBeDisabled();
	expect(drawImage).not.toHaveBeenCalled();

	await act(async () => { resolveLogo(LOGO); });

	await waitFor(() => expect(screen.getByRole('button', { name: '이미지 저장' })).toBeEnabled());
	expect(screen.getByRole('button', { name: '공유하기' })).toBeEnabled();
	expect(canvas).toHaveAttribute('aria-busy', 'false');
	expect(screen.queryByText('카드를 만들고 있어요…')).not.toBeInTheDocument();
	expect(drawImage.mock.calls.some(([image]) => image === LOGO)).toBe(true);
});

test('로고를 불러오지 못하면 저장을 막고 다시 시도할 수 있다', async () => {
	loadShareCardLogo.mockRejectedValueOnce(new Error('image failed'));
	getShelf.mockResolvedValue([shelfItem({ id: 1, verdict: 'GOOD' })]);
	renderPage();

	expect(await screen.findByRole('alert')).toHaveTextContent('로고를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.');
	expect(screen.getByRole('button', { name: '이미지 저장' })).toBeDisabled();
	expect(drawImage).not.toHaveBeenCalled();

	fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));

	await waitFor(() => expect(screen.getByRole('button', { name: '이미지 저장' })).toBeEnabled());
	expect(loadShareCardLogo).toHaveBeenCalledTimes(2);
	expect(screen.queryByRole('alert')).not.toBeInTheDocument();
	expect(drawImage.mock.calls.some(([image]) => image === LOGO)).toBe(true);
});

test('선택을 모두 해제했다가 다시 고르면 새 카드를 만들 수 있다', async () => {
	getShelf.mockResolvedValue([shelfItem({ id: 1, verdict: 'GOOD' })]);
	renderPage();

	const checkbox = await screen.findByRole('checkbox');
	await waitFor(() => expect(screen.getByRole('button', { name: '이미지 저장' })).toBeEnabled());
	expect(screen.getByRole('img', { name: /나의 인생템 카드/ })).toHaveAttribute('height', '1080');
	fireEvent.click(checkbox);

	expect(screen.queryByRole('img', { name: /나의 인생템 카드/ })).not.toBeInTheDocument();
	expect(screen.getByText('제품을 하나 이상 골라 주세요.')).toBeInTheDocument();
	expect(screen.getByRole('button', { name: '이미지 저장' })).toBeDisabled();

	fireEvent.click(checkbox);

	await waitFor(() => expect(screen.getByRole('button', { name: '이미지 저장' })).toBeEnabled());
	expect(screen.getByRole('img', { name: /나의 인생템 카드/ })).toHaveAttribute('aria-busy', 'false');
	expect(screen.getByRole('img', { name: /나의 인생템 카드/ })).toHaveAttribute('height', '1080');
	expect(drawImage).toHaveBeenCalledTimes(2);
});

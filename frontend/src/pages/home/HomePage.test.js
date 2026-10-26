import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useAuth } from '../../AuthContext';
import { getShelf } from '../../api/shelf';
import useIsMobile from '../../hooks/useIsMobile';
import HomePage from './HomePage';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../AuthContext', () => ({
	useAuth: jest.fn(),
}));
jest.mock('../../hooks/useIsMobile', () => ({
	__esModule: true,
	default: jest.fn(),
}));
jest.mock('../../api/shelf', () => ({
	getShelf: jest.fn(),
}));

const USER = { id: 1, nickname: '테스트' };

function renderHome() {
	return render(
		<MemoryRouter future={FUTURE}>
			<HomePage />
		</MemoryRouter>
	);
}

beforeEach(() => {
	jest.clearAllMocks();
	useIsMobile.mockReturnValue(false);
	getShelf.mockResolvedValue([]);
});

test('로그인하지 않았으면 서비스 소개가 보이고 화장대는 부르지 않는다', () => {
	useAuth.mockReturnValue({ user: null, loading: false });
	renderHome();
	expect(screen.getByRole('heading', { name: 'Vanity Check' })).toBeInTheDocument();
	expect(screen.getByRole('img', { name: 'GOOD SKIN. BETTER HABITS.' })).toBeInTheDocument();
	expect(screen.getByRole('heading', { name: 'MY VANITY' })).toBeInTheDocument();
	expect(screen.queryByRole('heading', { name: 'FOR YOU' })).not.toBeInTheDocument();
	expect(screen.queryByRole('heading', { name: 'DAILY RITUAL' })).not.toBeInTheDocument();
	expect(screen.getByRole('link', { name: /루틴 기록하기/ })).toHaveAttribute('href', '/routines');
	expect(screen.queryByRole('link', { name: 'Login' })).not.toBeInTheDocument();
	expect(getShelf).not.toHaveBeenCalled();
});

test('메뉴는 기능 기준이고, 성분 확인 카드와 마무리 버튼이 각 기능으로 이어진다', () => {
	useAuth.mockReturnValue({ user: null, loading: false });
	renderHome();
	const nav = screen.getByRole('navigation', { name: '메인 메뉴' });
	expect(within(nav).getByRole('link', { name: 'MY VANITY' })).toHaveAttribute('href', '/shelf');
	expect(within(nav).getByRole('link', { name: 'PERSONAL' })).toHaveAttribute('href', '/skin');
	expect(within(nav).getByRole('link', { name: 'ROUTINE' })).toHaveAttribute('href', '/routines');
	expect(screen.getByRole('link', { name: /성분 확인하기/ })).toHaveAttribute('href', '/add');
	expect(screen.getByRole('heading', { name: 'Your skin. Your colors.' })).toBeInTheDocument();
	expect(screen.getByText('화장품 성분을 확인하고 사용 기록을 남겨보세요.')).toBeInTheDocument();
	expect(screen.getByText('피부 타입과 어울리는 컬러를 확인하세요.')).toBeInTheDocument();
	expect(screen.getByRole('link', { name: /내 피부 알아보기/ })).toHaveAttribute('href', '/skin');
	expect(screen.getByRole('link', { name: /내 컬러 찾기/ })).toHaveAttribute('href', '/personal-color');
	expect(screen.getAllByRole('link', { name: /내 화장대 분석하기/ })[0]).toHaveAttribute('href', '/shelf');
});

test('로그인 여부에 따라 상단 메뉴가 SIGN UP | LOGIN 또는 MY PAGE | LOGOUT으로 바뀐다', () => {
	useAuth.mockReturnValue({ user: USER, logout: jest.fn() });
	renderHome();
	expect(screen.getByRole('link', { name: 'MY PAGE' })).toHaveAttribute('href', '/me');
	expect(screen.getByRole('button', { name: 'LOGOUT' })).toBeInTheDocument();
	expect(screen.queryByRole('link', { name: 'SIGN UP' })).not.toBeInTheDocument();
});

test('폰과 PC 레이아웃 표시', () => {
	useAuth.mockReturnValue({ user: null, loading: false });
	useIsMobile.mockReturnValue(true);
	const { unmount } = renderHome();
	expect(screen.getByRole('region', { name: 'Vanity Check' })).toHaveAttribute('data-layout', 'mobile');
	unmount();
	useIsMobile.mockReturnValue(false);
	renderHome();
	expect(screen.getByRole('region', { name: 'Vanity Check' })).toHaveAttribute('data-layout', 'desktop');
});

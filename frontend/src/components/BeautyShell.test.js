import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { getNotifications } from '../api/notifications';
import BeautyShell from './BeautyShell';

jest.mock('../AuthContext', () => ({ useAuth: jest.fn() }));
jest.mock('../api/notifications', () => ({ getNotifications: jest.fn() }));

function renderAt(path) {
	return render(
		<MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
			<BeautyShell>
				<p>화면 내용</p>
			</BeautyShell>
		</MemoryRouter>
	);
}

beforeEach(() => {
	getNotifications.mockResolvedValue({ items: [] });
	useAuth.mockReturnValue({ user: { id: 1, nickname: '테스트' }, logout: jest.fn() });
});

test('메인(/)은 공통 틀 없이 화면만 그린다', () => {
	renderAt('/');

	expect(screen.getByText('화면 내용')).toBeInTheDocument();
	expect(screen.queryByRole('navigation', { name: '뷰티 클럽 메뉴' })).not.toBeInTheDocument();
});

test('화장대 탭: 내 제품·비교·현황, 내 제품 화면에는 제품 추가 버튼', () => {
	renderAt('/shelf');

	const tabs = screen.getByRole('navigation', { name: '화장대 메뉴 탭' });
	expect(within(tabs).getByRole('link', { name: '내 제품' })).toHaveAttribute('aria-current', 'page');
	expect(within(tabs).getByRole('link', { name: '비교' })).toHaveAttribute('href', '/compare');
	expect(within(tabs).getByRole('link', { name: /현황/ })).toHaveAttribute('href', '/stats');
	expect(within(tabs).getByRole('link', { name: '제품 추가 +' })).toHaveAttribute('href', '/add');
});

test('퍼스널 탭: 누르면 기능 화면이 바로 열리고 지금 탭이 선택 상태다', () => {
	renderAt('/personal-color');

	const tabs = screen.getByRole('navigation', { name: '퍼스널 메뉴 탭' });
	expect(within(tabs).getByRole('link', { name: '내 피부 분석' })).toHaveAttribute('href', '/skin');
	expect(within(tabs).getByRole('link', { name: '퍼스널 컬러 진단' })).toHaveAttribute('aria-current', 'page');
	expect(within(tabs).getByRole('link', { name: '화장대 진단' })).toHaveAttribute('href', '/vanity/analysis');
	expect(screen.getByText('PERSONAL', { selector: '.vanity-app-display' })).toBeInTheDocument();
});

test('탭이 없는 화면(내 정보)은 화면 이름만 보여준다', () => {
	renderAt('/me');

	expect(screen.queryByRole('navigation', { name: /메뉴 탭/ })).not.toBeInTheDocument();
	expect(screen.getByText('MY ACCOUNT')).toBeInTheDocument();
});

test('로그인 여부에 따라 MY PAGE·LOGOUT 또는 SIGN UP·LOGIN', () => {
	useAuth.mockReturnValue({ user: null, logout: jest.fn() });
	renderAt('/login');

	expect(screen.getByRole('link', { name: 'SIGN UP' })).toHaveAttribute('href', '/signup');
	expect(screen.queryByRole('link', { name: 'MY PAGE' })).not.toBeInTheDocument();
});

test('루틴 탭: 루틴·피부 일기·새 제품 테스트, 화면마다 하는 일을 한 줄로', () => {
	renderAt('/diary');

	const tabs = screen.getByRole('navigation', { name: '루틴 메뉴 탭' });
	expect(within(tabs).getByRole('link', { name: '피부 일기' })).toHaveAttribute('aria-current', 'page');
	expect(within(tabs).getByRole('link', { name: '루틴' })).toHaveAttribute('href', '/routines');
	expect(within(tabs).getByRole('link', { name: '새 제품 테스트' })).toHaveAttribute('href', '/trials');
	expect(screen.getByText('매일 피부 상태를 기록하세요.')).toBeInTheDocument();
});

test('새 제품 테스트 화면도 루틴 탭 안에 있다', () => {
	renderAt('/trials');

	const tabs = screen.getByRole('navigation', { name: '루틴 메뉴 탭' });
	expect(within(tabs).getByRole('link', { name: '새 제품 테스트' })).toHaveAttribute('aria-current', 'page');
	expect(screen.getByText('새 제품을 2주 동안 써 보고 나에게 맞는지 평가하세요.')).toBeInTheDocument();
});

test('화장대 탭에 자랑 카드가 있다', () => {
	renderAt('/share');

	const tabs = screen.getByRole('navigation', { name: '화장대 메뉴 탭' });
	expect(within(tabs).getByRole('link', { name: '자랑 카드' })).toHaveAttribute('aria-current', 'page');
});

test('로그인하면 헤더에 알림 개수를 보여준다', async () => {
	getNotifications.mockResolvedValue({ items: [{ type: 'DIARY_TODAY' }, { type: 'EXPIRED' }] });
	renderAt('/me');

	expect(await screen.findByRole('link', { name: '알림 2개' })).toHaveAttribute('href', '/notifications');
});

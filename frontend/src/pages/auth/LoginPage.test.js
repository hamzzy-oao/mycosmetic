// user-event 13은 최상위 @testing-library/dom(10)을 써서 act로 감싸지지 않는다. RTL의 fireEvent를 쓴다
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../../AuthContext';
import { ensureCsrf, kakaoLoginUrl, login, me } from '../../api/auth';
import useIsMobile from '../../hooks/useIsMobile';
import LoginPage from './LoginPage';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/auth', () => ({
	ensureCsrf: jest.fn(),
	me: jest.fn(),
	logout: jest.fn(),
	login: jest.fn(),
	kakaoLoginUrl: jest.fn(),
}));
jest.mock('../../hooks/useIsMobile', () => ({
	__esModule: true,
	default: jest.fn(),
}));

function renderLogin(initialEntry = '/login') {
	return render(
		<MemoryRouter initialEntries={[initialEntry]} future={FUTURE}>
			<AuthProvider>
				<Routes>
					<Route path="/login" element={<LoginPage />} />
					<Route path="/" element={<h1>메인</h1>} />
					<Route path="/shelf" element={<h1>내 화장대</h1>} />
					<Route path="/skin" element={<h1>피부 상태</h1>} />
				</Routes>
			</AuthProvider>
		</MemoryRouter>
	);
}

function serverError(status, code, message) {
	const error = new Error(code);
	error.response = { status, data: { code, message, traceId: 'abc123' } };
	return error;
}

beforeEach(() => {
	jest.clearAllMocks();
	ensureCsrf.mockResolvedValue({});
	me.mockRejectedValue(serverError(401, 'UNAUTHORIZED', '로그인이 필요해요.'));
	kakaoLoginUrl.mockImplementation((link) => '/oauth2/authorization/kakao' + (link ? '?link=true' : ''));
	useIsMobile.mockReturnValue(false);
});

async function fillAndSubmit(email, password) {
	fireEvent.change(await screen.findByLabelText('이메일'), { target: { value: email } });
	fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: password } });
	fireEvent.click(screen.getByRole('button', { name: '로그인' }));
}

test('로그인 성공하면 메인(/)으로 이동', async () => {
	login.mockResolvedValue(undefined);
	renderLogin();
	await waitFor(() => expect(me).toHaveBeenCalledTimes(1));
	me.mockResolvedValue({ id: 1, email: 'a@b.com', nickname: '테스트', hasPassword: true, kakaoLinked: false });

	await fillAndSubmit('a@b.com', 'abcd1234');

	expect(await screen.findByRole('heading', { name: '메인' })).toBeInTheDocument();
	expect(login).toHaveBeenCalledWith({ email: 'a@b.com', password: 'abcd1234' });
});

test('로그인 화면으로 오기 전 주소가 있으면 그곳으로 돌아간다', async () => {
	login.mockResolvedValue(undefined);
	render(
		<MemoryRouter initialEntries={[{ pathname: '/login', state: { from: { pathname: '/skin' } } }]} future={FUTURE}>
			<AuthProvider>
				<Routes>
					<Route path="/login" element={<LoginPage />} />
					<Route path="/skin" element={<h1>피부 상태</h1>} />
				</Routes>
			</AuthProvider>
		</MemoryRouter>
	);
	await waitFor(() => expect(me).toHaveBeenCalledTimes(1));
	me.mockResolvedValue({ id: 1, nickname: '테스트' });

	await fillAndSubmit('a@b.com', 'abcd1234');

	expect(await screen.findByRole('heading', { name: '피부 상태' })).toBeInTheDocument();
});

test('서버 오류 메시지(message)를 그대로 보여준다', async () => {
	login.mockRejectedValue(serverError(423, 'ACCOUNT_LOCKED', '로그인을 5번 실패해서 10분 동안 잠겼어요.'));
	renderLogin();

	await fillAndSubmit('a@b.com', 'abcd1234');

	expect(await screen.findByRole('alert')).toHaveTextContent('로그인을 5번 실패해서 10분 동안 잠겼어요.');
	expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument();
});

test('서버에 닿지 못하면 일반 안내를 보여준다', async () => {
	login.mockRejectedValue(new Error('Network Error'));
	renderLogin();

	await fillAndSubmit('a@b.com', 'abcd1234');

	expect(await screen.findByRole('alert')).toHaveTextContent('잠시 뒤 다시 시도해 주세요');
});

test('카카오 버튼은 kakaoLoginUrl(false)로 이동', async () => {
	renderLogin();

	const kakao = await screen.findByRole('link', { name: '카카오로 로그인' });
	expect(kakaoLoginUrl).toHaveBeenCalledWith(false);
	expect(kakao).toHaveAttribute('href', '/oauth2/authorization/kakao');
});

test('카카오 로그인이 실패해서 돌아오면 안내한다', async () => {
	renderLogin('/login?error=kakao');

	expect(await screen.findByRole('alert')).toHaveTextContent('카카오 로그인에 실패했어요');
});

test('PC에서는 가운데 카드, 폰에서는 한 열', async () => {
	useIsMobile.mockReturnValue(false);
	const { unmount } = renderLogin();
	const card = await screen.findByRole('region', { name: '로그인' });
	expect(card).toHaveAttribute('data-layout', 'desktop');
	expect(card).toHaveClass('auth-card');
	unmount();

	useIsMobile.mockReturnValue(true);
	renderLogin();
	const column = await screen.findByRole('region', { name: '로그인' });
	expect(column).toHaveAttribute('data-layout', 'mobile');
	expect(column).not.toHaveClass('auth-card');
});

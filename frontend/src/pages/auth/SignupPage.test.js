// user-event 13은 최상위 @testing-library/dom(10)을 써서 act로 감싸지지 않는다. RTL의 fireEvent를 쓴다
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../../AuthContext';
import { checkEmailAvailable, ensureCsrf, kakaoLoginUrl, login, me, signup } from '../../api/auth';
import useIsMobile from '../../hooks/useIsMobile';
import SignupPage, { EMAIL_CHECK_DELAY_MS } from './SignupPage';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/auth', () => ({
	ensureCsrf: jest.fn(),
	me: jest.fn(),
	logout: jest.fn(),
	login: jest.fn(),
	signup: jest.fn(),
	checkEmailAvailable: jest.fn(),
	kakaoLoginUrl: jest.fn(),
}));
jest.mock('../../hooks/useIsMobile', () => ({
	__esModule: true,
	default: jest.fn(),
}));

function renderSignup() {
	return render(
		<MemoryRouter initialEntries={['/signup']} future={FUTURE}>
			<AuthProvider>
				<Routes>
					<Route path="/signup" element={<SignupPage />} />
					<Route path="/" element={<h1>메인</h1>} />
					<Route path="/shelf" element={<h1>내 화장대</h1>} />
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

// 입력칸이 aria-describedby로 가리키는 안내 줄
function messageOf(label) {
	const input = screen.getByLabelText(label);
	return document.getElementById(input.getAttribute('aria-describedby'));
}

function type(label, value) {
	fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

async function fillValid({ email = 'new@b.com', password = 'abcd1234', nickname = '새사람' } = {}) {
	await screen.findByLabelText('이메일');
	type('이메일', email);
	fireEvent.blur(screen.getByLabelText('이메일'));
	type('비밀번호', password);
	type('비밀번호 확인', password);
	type('닉네임', nickname);
}

beforeEach(() => {
	jest.clearAllMocks();
	ensureCsrf.mockResolvedValue({});
	me.mockRejectedValue(serverError(401, 'UNAUTHORIZED', '로그인이 필요해요.'));
	kakaoLoginUrl.mockReturnValue('/oauth2/authorization/kakao');
	checkEmailAvailable.mockResolvedValue({ available: true });
	useIsMobile.mockReturnValue(false);
});

afterEach(() => {
	jest.useRealTimers();
});

describe('이메일', () => {
	test('입력 중에는 회색 안내, 칸을 벗어났을 때 형식이 틀리면 빨간 안내', async () => {
		renderSignup();
		await screen.findByLabelText('이메일');
		type('이메일', 'abc');
		expect(messageOf('이메일')).toHaveClass('field-message-hint');
		expect(screen.queryByRole('alert')).not.toBeInTheDocument();

		fireEvent.blur(screen.getByLabelText('이메일'));

		expect(screen.getByRole('alert')).toHaveTextContent('이메일 형식을 확인해 주세요.');
		expect(screen.getByLabelText('이메일')).toHaveAttribute('aria-invalid', 'true');
		expect(screen.getByLabelText('이메일')).toHaveClass('field-input-error');
		expect(checkEmailAvailable).not.toHaveBeenCalled();
	});

	test('입력을 멈추면 잠시 뒤 한 번만 중복 확인하고, 쓸 수 있으면 초록 안내', async () => {
		renderSignup();
		await screen.findByLabelText('이메일');
		jest.useFakeTimers();

		type('이메일', 'new@b.co');
		type('이메일', 'new@b.com');
		act(() => {
			jest.advanceTimersByTime(EMAIL_CHECK_DELAY_MS - 1);
		});
		expect(checkEmailAvailable).not.toHaveBeenCalled();
		await act(async () => {
			jest.advanceTimersByTime(1);
		});

		expect(checkEmailAvailable).toHaveBeenCalledTimes(1);
		expect(checkEmailAvailable).toHaveBeenCalledWith('new@b.com');
		expect(messageOf('이메일')).toHaveTextContent('사용할 수 있는 이메일이에요.');
		expect(messageOf('이메일')).toHaveClass('field-message-ok');

		// 같은 값(대소문자만 다름)은 다시 묻지 않는다
		fireEvent.blur(screen.getByLabelText('이메일'));
		type('이메일', 'NEW@b.com');
		await act(async () => {
			jest.advanceTimersByTime(EMAIL_CHECK_DELAY_MS);
		});
		expect(checkEmailAvailable).toHaveBeenCalledTimes(1);
	});

	test('이미 가입된 이메일이면 빨간 안내와 함께 가입 버튼을 막는다', async () => {
		checkEmailAvailable.mockResolvedValue({ available: false });
		renderSignup();

		await fillValid();

		expect(await screen.findByRole('alert')).toHaveTextContent('이미 가입된 이메일이에요.');
		expect(screen.getByRole('button', { name: '가입하기' })).toBeDisabled();
	});

	test('중복 확인을 못 하면 노란 안내만 보이고 가입은 할 수 있다', async () => {
		checkEmailAvailable.mockRejectedValue(new Error('Network Error'));
		renderSignup();

		await fillValid();

		await waitFor(() =>
			expect(messageOf('이메일')).toHaveTextContent('중복 확인을 못 했어요. 가입할 때 다시 확인할게요.')
		);
		expect(messageOf('이메일')).toHaveClass('field-message-notice');
		expect(screen.getByRole('button', { name: '가입하기' })).toBeEnabled();
	});
});

test('비밀번호: 회색 안내 → 부족한 것을 노란 안내 → 맞으면 초록', async () => {
	renderSignup();
	await screen.findByLabelText('비밀번호');
	expect(messageOf('비밀번호')).toHaveTextContent('8자 이상, 영문과 숫자를 섞어 주세요.');
	expect(messageOf('비밀번호')).toHaveClass('field-message-hint');

	type('비밀번호', 'abcd');
	expect(messageOf('비밀번호')).toHaveTextContent('8자 이상 · 숫자');
	expect(messageOf('비밀번호')).toHaveClass('field-message-notice');

	type('비밀번호', '12345678');
	expect(messageOf('비밀번호')).toHaveTextContent('영문');
	expect(messageOf('비밀번호')).not.toHaveTextContent('숫자');

	type('비밀번호', 'abcd1234');
	expect(messageOf('비밀번호')).toHaveTextContent('사용할 수 있는 비밀번호예요.');
	expect(messageOf('비밀번호')).toHaveClass('field-message-ok');
});

test('비밀번호 확인: 다르면 빨강, 같으면 초록', async () => {
	renderSignup();
	await screen.findByLabelText('비밀번호 확인');
	type('비밀번호', 'abcd1234');

	type('비밀번호 확인', 'abcd12');
	expect(screen.getByRole('alert')).toHaveTextContent('비밀번호가 일치하지 않아요.');

	type('비밀번호 확인', 'abcd1234');
	expect(screen.queryByRole('alert')).not.toBeInTheDocument();
	expect(messageOf('비밀번호 확인')).toHaveTextContent('비밀번호가 일치해요.');
});

test('닉네임: 1~20자면 초록, 넘으면 빨강, 비우고 벗어나면 빨강', async () => {
	renderSignup();
	await screen.findByLabelText('닉네임');

	type('닉네임', '봄웜');
	expect(messageOf('닉네임')).toHaveTextContent('사용 가능한 닉네임이에요.');
	expect(messageOf('닉네임')).toHaveClass('field-message-ok');

	type('닉네임', '가'.repeat(21));
	expect(screen.getByRole('alert')).toHaveTextContent('닉네임은 20자까지 쓸 수 있어요.');

	type('닉네임', '  ');
	fireEvent.blur(screen.getByLabelText('닉네임'));
	expect(screen.getByRole('alert')).toHaveTextContent('닉네임을 입력해 주세요.');
});

test('모든 칸이 맞기 전에는 가입 버튼이 꺼져 있다', async () => {
	renderSignup();
	await screen.findByLabelText('이메일');
	const submit = screen.getByRole('button', { name: '가입하기' });
	expect(submit).toBeDisabled();

	type('이메일', 'new@b.com');
	type('비밀번호', 'abcd1234');
	type('닉네임', '새사람');
	expect(submit).toBeDisabled();

	type('비밀번호 확인', 'abcd1234');
	expect(submit).toBeEnabled();
	fireEvent.click(submit);
	await waitFor(() => expect(signup).toHaveBeenCalledTimes(1));
});

test('가입에 성공하면 바로 로그인해서 메인(/)으로 이동', async () => {
	signup.mockResolvedValue(undefined);
	login.mockResolvedValue(undefined);
	renderSignup();
	await waitFor(() => expect(me).toHaveBeenCalledTimes(1));
	me.mockResolvedValue({ id: 2, email: 'new@b.com', nickname: '새사람' });

	await fillValid({ email: ' new@b.com ', nickname: ' 새사람 ' });
	fireEvent.click(screen.getByRole('button', { name: '가입하기' }));

	expect(await screen.findByRole('heading', { name: '메인' })).toBeInTheDocument();
	expect(signup).toHaveBeenCalledWith({ email: 'new@b.com', password: 'abcd1234', nickname: '새사람' });
	expect(login).toHaveBeenCalledWith({ email: 'new@b.com', password: 'abcd1234' });
});

test('가입 때 서버가 409 EMAIL_DUPLICATE면 이메일 칸 아래에 빨간 안내', async () => {
	signup.mockRejectedValue(serverError(409, 'EMAIL_DUPLICATE', '이미 가입된 이메일이에요.'));
	renderSignup();

	await fillValid();
	await waitFor(() => expect(messageOf('이메일')).toHaveTextContent('사용할 수 있는 이메일이에요.'));
	fireEvent.click(screen.getByRole('button', { name: '가입하기' }));

	await waitFor(() => expect(messageOf('이메일')).toHaveTextContent('이미 가입된 이메일이에요.'));
	expect(messageOf('이메일')).toHaveAttribute('role', 'alert');
	expect(screen.getByRole('button', { name: '가입하기' })).toBeDisabled();
	expect(login).not.toHaveBeenCalled();
});

test('그 밖의 서버 오류는 가입 버튼 위에 메시지로 보여준다', async () => {
	signup.mockRejectedValue(serverError(400, 'INVALID_REQUEST', '입력한 내용을 다시 확인해 주세요.'));
	renderSignup();

	await fillValid();
	fireEvent.click(screen.getByRole('button', { name: '가입하기' }));

	expect(await screen.findByRole('alert')).toHaveTextContent('입력한 내용을 다시 확인해 주세요.');
});

test('폰에서는 한 열로 보인다', async () => {
	useIsMobile.mockReturnValue(true);
	renderSignup();
	const column = await screen.findByRole('region', { name: '가입' });
	expect(column).toHaveAttribute('data-layout', 'mobile');
	expect(column).not.toHaveClass('auth-card');
});

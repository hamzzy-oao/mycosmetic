// user-event 13은 최상위 @testing-library/dom(10)을 써서 act로 감싸지지 않는다. RTL의 fireEvent를 쓴다
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../../AuthContext';
import { ensureCsrf, kakaoLoginUrl, logout, me, unlinkKakao, updateNickname, withdraw } from '../../api/auth';
import { refreshOnce } from '../../api/client';
import useIsMobile from '../../hooks/useIsMobile';
import MePage from './MePage';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/auth', () => ({
	ensureCsrf: jest.fn(),
	me: jest.fn(),
	logout: jest.fn(),
	withdraw: jest.fn(),
	unlinkKakao: jest.fn(),
	kakaoLoginUrl: jest.fn(),
	updateNickname: jest.fn(),
}));
jest.mock('../../api/client', () => ({
	...jest.requireActual('../../api/client'),
	refreshOnce: jest.fn(),
}));
// 내 정보 안의 피부 정보 칸(SkinInfoSection)은 따로 테스트한다
jest.mock('../../api/skin', () => ({
	getSkinProfile: jest.fn(() => Promise.resolve({ skinTypes: [], personalColor: null })),
	saveSkinProfile: jest.fn(),
}));
jest.mock('../../hooks/useIsMobile', () => ({
	__esModule: true,
	default: jest.fn(),
}));

const EMAIL_USER = { id: 1, email: 'a@b.com', nickname: '테스트', hasPassword: true, kakaoLinked: false };

function renderMe(initialEntry = '/me') {
	return render(
		<MemoryRouter initialEntries={[initialEntry]} future={FUTURE}>
			<AuthProvider>
				<Routes>
					<Route path="/me" element={<MePage />} />
					<Route path="/login" element={<h1>로그인 화면</h1>} />
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

let confirmSpy;

beforeEach(() => {
	jest.clearAllMocks();
	ensureCsrf.mockResolvedValue({});
	kakaoLoginUrl.mockImplementation((link) => '/oauth2/authorization/kakao' + (link ? '?link=true' : ''));
	useIsMobile.mockReturnValue(false);
	confirmSpy = jest.spyOn(window, 'confirm');
});

afterEach(() => {
	confirmSpy.mockRestore();
});

test('내 정보: 닉네임과 이메일을 보여준다', async () => {
	me.mockResolvedValue(EMAIL_USER);
	renderMe();

	expect(await screen.findByText('테스트')).toBeInTheDocument();
	expect(screen.getByText('a@b.com')).toBeInTheDocument();
});

test('내 정보: 카카오 연결 — 세션을 갱신한 뒤 kakaoLoginUrl(true)로 이동', async () => {
	me.mockResolvedValue(EMAIL_USER);
	refreshOnce.mockResolvedValue({});
	const assign = jest.fn();
	const original = window.location;
	Object.defineProperty(window, 'location', { configurable: true, value: { ...original, assign } });
	try {
		renderMe();

		fireEvent.click(await screen.findByRole('button', { name: '카카오 연결' }));

		await waitFor(() => expect(assign).toHaveBeenCalledWith('/oauth2/authorization/kakao?link=true'));
		expect(refreshOnce).toHaveBeenCalledTimes(1);
		expect(kakaoLoginUrl).toHaveBeenCalledWith(true);
		expect(screen.queryByRole('button', { name: '카카오 연결 해제' })).not.toBeInTheDocument();
	} finally {
		Object.defineProperty(window, 'location', { configurable: true, value: original });
	}
});

test('내 정보: 카카오 연결 — 세션 갱신이 실패하면 안내하고 이동하지 않는다', async () => {
	me.mockResolvedValue(EMAIL_USER);
	refreshOnce.mockRejectedValue(serverError(401, 'UNAUTHORIZED', '로그인이 필요해요.'));
	const assign = jest.fn();
	const original = window.location;
	Object.defineProperty(window, 'location', { configurable: true, value: { ...original, assign } });
	try {
		renderMe();

		fireEvent.click(await screen.findByRole('button', { name: '카카오 연결' }));

		expect(await screen.findByRole('alert')).toHaveTextContent('카카오 계정을 연결하지 못했어요. 다시 시도해 주세요.');
		expect(assign).not.toHaveBeenCalled();
	} finally {
		Object.defineProperty(window, 'location', { configurable: true, value: original });
	}
});

test('내 정보: 카카오 연결에 실패해 돌아오면(?error=kakao) 안내한다', async () => {
	me.mockResolvedValue(EMAIL_USER);
	renderMe('/me?error=kakao');

	expect(await screen.findByRole('alert')).toHaveTextContent('카카오 계정을 연결하지 못했어요. 다시 시도해 주세요.');
});

test('내 정보: 연결하고 돌아오면(?linked=1) 안내한다', async () => {
	me.mockResolvedValue({ ...EMAIL_USER, kakaoLinked: true });
	renderMe('/me?linked=1');

	expect(await screen.findByRole('status')).toHaveTextContent('카카오 계정을 연결했어요.');
});

test('내 정보: 연결 해제 — 해제 후 내 정보를 다시 불러온다', async () => {
	me.mockResolvedValue({ ...EMAIL_USER, kakaoLinked: true });
	unlinkKakao.mockResolvedValue({});
	renderMe();

	const button = await screen.findByRole('button', { name: '카카오 연결 해제' });
	me.mockResolvedValue(EMAIL_USER);
	fireEvent.click(button);

	expect(await screen.findByRole('button', { name: '카카오 연결' })).toBeInTheDocument();
	expect(unlinkKakao).toHaveBeenCalledTimes(1);
	expect(screen.getByRole('status')).toHaveTextContent('카카오 연결을 해제했어요.');
});

test('내 정보: 연결 해제가 거절되면 서버 메시지를 그대로 보여준다', async () => {
	me.mockResolvedValue({ id: 3, email: null, nickname: '카카오님', hasPassword: false, kakaoLinked: true });
	unlinkKakao.mockRejectedValue(
		serverError(409, 'LAST_LOGIN_METHOD', '다른 로그인 방법이 없어서 카카오 연결을 해제할 수 없어요.')
	);
	renderMe();

	fireEvent.click(await screen.findByRole('button', { name: '카카오 연결 해제' }));

	expect(await screen.findByRole('alert')).toHaveTextContent('다른 로그인 방법이 없어서 카카오 연결을 해제할 수 없어요.');
	expect(screen.getByRole('button', { name: '카카오 연결 해제' })).toBeInTheDocument();
});

test('내 정보: 탈퇴(확인 대화상자 후) — 취소하면 탈퇴하지 않는다', async () => {
	me.mockResolvedValue(EMAIL_USER);
	confirmSpy.mockReturnValue(false);
	renderMe();

	fireEvent.click(await screen.findByRole('button', { name: '회원 탈퇴' }));

	expect(confirmSpy).toHaveBeenCalledTimes(1);
	expect(withdraw).not.toHaveBeenCalled();
	expect(screen.getByText('테스트')).toBeInTheDocument();
});

test('내 정보: 탈퇴(확인 대화상자 후) — 확인하면 탈퇴하고 로그인 화면으로', async () => {
	me.mockResolvedValue(EMAIL_USER);
	confirmSpy.mockReturnValue(true);
	withdraw.mockResolvedValue({});
	renderMe();

	fireEvent.click(await screen.findByRole('button', { name: '회원 탈퇴' }));

	expect(await screen.findByRole('heading', { name: '로그인 화면' })).toBeInTheDocument();
	expect(withdraw).toHaveBeenCalledTimes(1);
});

test('내 정보: 탈퇴가 실패하면 서버 메시지를 보여주고 화면에 남는다', async () => {
	me.mockResolvedValue(EMAIL_USER);
	confirmSpy.mockReturnValue(true);
	withdraw.mockRejectedValue(serverError(500, 'INTERNAL_ERROR', '잠시 뒤 다시 시도해 주세요.'));
	renderMe();

	fireEvent.click(await screen.findByRole('button', { name: '회원 탈퇴' }));

	expect(await screen.findByRole('alert')).toHaveTextContent('잠시 뒤 다시 시도해 주세요.');
	expect(screen.getByText('테스트')).toBeInTheDocument();
});

test('내 정보: 로그아웃하면 로그인 화면으로', async () => {
	me.mockResolvedValue(EMAIL_USER);
	logout.mockResolvedValue({});
	renderMe();

	fireEvent.click(await screen.findByRole('button', { name: '로그아웃' }));

	await waitFor(() => expect(logout).toHaveBeenCalledTimes(1));
	expect(await screen.findByRole('heading', { name: '로그인 화면' })).toBeInTheDocument();
});

test('내 정보: 폰에서는 한 열, PC에서는 가운데 카드', async () => {
	me.mockResolvedValue(EMAIL_USER);
	useIsMobile.mockReturnValue(true);
	const { unmount } = renderMe();
	await screen.findByText('테스트');
	const column = screen.getByRole('region', { name: '내 정보' });
	expect(column).toHaveAttribute('data-layout', 'mobile');
	expect(column).not.toHaveClass('auth-card');
	unmount();

	useIsMobile.mockReturnValue(false);
	renderMe();
	await screen.findByText('테스트');
	const card = screen.getByRole('region', { name: '내 정보' });
	expect(card).toHaveAttribute('data-layout', 'desktop');
	expect(card).toHaveClass('auth-card');
});

describe('닉네임 변경', () => {
	test.each([
		['PC', false],
		['폰', true],
	])('%s: 변경 → 입력 → 저장하면 앞뒤 공백을 지워 보내고 화면의 닉네임이 바뀐다', async (_, mobile) => {
		useIsMobile.mockReturnValue(mobile);
		me.mockResolvedValue(EMAIL_USER);
		updateNickname.mockResolvedValue({ ...EMAIL_USER, nickname: '봄웜' });
		renderMe();

		fireEvent.click(await screen.findByRole('button', { name: '닉네임 변경' }));
		const input = screen.getByLabelText('새 닉네임');
		expect(input).toHaveValue('테스트');
		fireEvent.change(input, { target: { value: '  봄웜  ' } });
		fireEvent.click(screen.getByRole('button', { name: '저장' }));

		expect(await screen.findByRole('status')).toHaveTextContent('닉네임을 바꿨어요.');
		expect(updateNickname).toHaveBeenCalledWith('봄웜');
		expect(screen.getByText('봄웜')).toBeInTheDocument();
		expect(screen.queryByLabelText('새 닉네임')).not.toBeInTheDocument();
	});

	test('비었거나 20자를 넘으면 보내지 않고 안내한다', async () => {
		me.mockResolvedValue(EMAIL_USER);
		renderMe();

		fireEvent.click(await screen.findByRole('button', { name: '닉네임 변경' }));
		fireEvent.change(screen.getByLabelText('새 닉네임'), { target: { value: '   ' } });
		fireEvent.click(screen.getByRole('button', { name: '저장' }));
		expect(screen.getByRole('alert')).toHaveTextContent('닉네임을 입력해 주세요.');

		fireEvent.change(screen.getByLabelText('새 닉네임'), { target: { value: '가'.repeat(21) } });
		fireEvent.click(screen.getByRole('button', { name: '저장' }));
		expect(screen.getByRole('alert')).toHaveTextContent('닉네임은 20자까지 쓸 수 있어요.');
		expect(updateNickname).not.toHaveBeenCalled();
	});

	test('입력하는 대로 길이를 안내한다: 1~20자면 초록, 넘으면 빨강', async () => {
		me.mockResolvedValue(EMAIL_USER);
		renderMe();

		fireEvent.click(await screen.findByRole('button', { name: '닉네임 변경' }));
		const input = screen.getByLabelText('새 닉네임');
		expect(input).toHaveAccessibleDescription('사용 가능한 닉네임이에요.');
		expect(screen.getByText('사용 가능한 닉네임이에요.')).toHaveClass('field-message-ok');
		expect(screen.queryByRole('alert')).not.toBeInTheDocument();

		fireEvent.change(input, { target: { value: '가'.repeat(21) } });
		expect(screen.getByRole('alert')).toHaveTextContent('닉네임은 20자까지 쓸 수 있어요.');
		expect(input).toHaveAttribute('aria-invalid', 'true');

		fireEvent.change(input, { target: { value: '봄웜' } });
		expect(input).toHaveAccessibleDescription('사용 가능한 닉네임이에요.');
		expect(input).not.toHaveAttribute('aria-invalid');
	});

	test('서버 오류는 메시지를 보여주고 편집을 유지하며, 취소하면 원래 닉네임으로 돌아간다', async () => {
		me.mockResolvedValue(EMAIL_USER);
		updateNickname.mockRejectedValue(serverError(400, 'INVALID_REQUEST', '입력한 내용을 다시 확인해 주세요.'));
		renderMe();

		fireEvent.click(await screen.findByRole('button', { name: '닉네임 변경' }));
		fireEvent.change(screen.getByLabelText('새 닉네임'), { target: { value: '새이름' } });
		fireEvent.click(screen.getByRole('button', { name: '저장' }));

		expect(await screen.findByRole('alert')).toHaveTextContent('입력한 내용을 다시 확인해 주세요.');
		expect(screen.getByLabelText('새 닉네임')).toHaveValue('새이름');
		fireEvent.click(screen.getByRole('button', { name: '취소' }));
		expect(screen.getByText('테스트')).toBeInTheDocument();
		expect(screen.queryByLabelText('새 닉네임')).not.toBeInTheDocument();
	});
});

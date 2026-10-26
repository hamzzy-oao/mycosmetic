import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { checkEmailAvailable, kakaoLoginUrl, login, signup } from '../../api/auth';
import { useAuth } from '../../AuthContext';
import FieldMessage, { fieldInputProps } from '../../components/FieldMessage';
import ResponsivePage from '../../components/ResponsivePage';
import { errorMessage } from '../../lib/errorMessage';
import { AuthDesktopLayout, AuthMobileLayout } from './AuthLayout';

// 서버 SignupRequest 검증과 같은 규칙 (설계 5.1, Task 8)
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_MIN = 8;
// BCrypt가 보는 72바이트 (서버 AuthService.BCRYPT_MAX_BYTES)
const PASSWORD_MAX_BYTES = 72;
const NICKNAME_MAX = 20;
// 입력을 멈추고 이만큼 지나면 이메일 중복 확인을 한다
export const EMAIL_CHECK_DELAY_MS = 500;

const ok = (text) => ({ tone: 'ok', text });
const error = (text) => ({ tone: 'error', text });
const notice = (text) => ({ tone: 'notice', text });
const hint = (text) => ({ tone: 'hint', text });

function utf8Bytes(value) {
	return encodeURIComponent(value).replace(/%[0-9A-F]{2}/g, 'x').length;
}

// 이메일 안내. availability: 중복 확인 결과(checking | available | taken | failed | undefined)
export function emailMessage(email, touched, availability) {
	if (email === '') {
		return touched ? error('이메일을 입력해 주세요.') : hint('로그인할 때 쓸 이메일을 입력해 주세요.');
	}
	if (!EMAIL_PATTERN.test(email)) {
		return touched ? error('이메일 형식을 확인해 주세요.') : hint('예: vanity@example.com');
	}
	if (availability === 'taken') return error('이미 가입된 이메일이에요.');
	if (availability === 'available') return ok('사용할 수 있는 이메일이에요.');
	if (availability === 'failed') return notice('중복 확인을 못 했어요. 가입할 때 다시 확인할게요.');
	if (availability === 'checking') return hint('가입된 이메일인지 확인하고 있어요.');
	return hint('입력을 마치면 가입된 이메일인지 확인할게요.');
}

export function passwordMessage(password) {
	if (password === '') return hint('8자 이상, 영문과 숫자를 섞어 주세요.');
	if (utf8Bytes(password) > PASSWORD_MAX_BYTES) return error('비밀번호가 너무 길어요. 72자 안으로 줄여 주세요.');
	const missing = [];
	if (password.length < PASSWORD_MIN) missing.push(`${PASSWORD_MIN}자 이상`);
	if (!/[A-Za-z]/.test(password)) missing.push('영문');
	if (!/\d/.test(password)) missing.push('숫자');
	if (missing.length > 0) return notice(`아직 필요해요: ${missing.join(' · ')}`);
	return ok('사용할 수 있는 비밀번호예요.');
}

export function passwordConfirmMessage(password, confirm) {
	if (confirm === '') return hint('');
	return confirm === password ? ok('비밀번호가 일치해요.') : error('비밀번호가 일치하지 않아요.');
}

// 닉네임은 겹쳐도 된다(중복 확인 없음). 앞뒤 공백을 빼고 1~20자
export function nicknameMessage(nickname, touched) {
	const trimmed = nickname.trim();
	if (trimmed === '') {
		return touched ? error('닉네임을 입력해 주세요.') : hint(`1~${NICKNAME_MAX}자로 정해 주세요.`);
	}
	if (trimmed.length > NICKNAME_MAX) return error(`닉네임은 ${NICKNAME_MAX}자까지 쓸 수 있어요.`);
	return ok('사용 가능한 닉네임이에요.');
}

function SignupForm() {
	const { user, reloadUser } = useAuth();
	const [email, setEmail] = useState('');
	const [password, setPassword] = useState('');
	const [passwordConfirm, setPasswordConfirm] = useState('');
	const [nickname, setNickname] = useState('');
	const [touched, setTouched] = useState({});
	// 소문자 이메일 → 중복 확인 결과. 값마다 한 번만 묻는다(서버도 대소문자를 구분하지 않는다)
	const [availability, setAvailability] = useState({});
	const asked = useRef(new Set());
	const [submitting, setSubmitting] = useState(false);
	const [formError, setFormError] = useState('');

	const trimmedEmail = email.trim();
	const emailKey = trimmedEmail.toLowerCase();

	const checkEmail = useCallback(async (value) => {
		const key = value.toLowerCase();
		if (asked.current.has(key)) return;
		asked.current.add(key);
		setAvailability((prev) => ({ ...prev, [key]: 'checking' }));
		let result;
		try {
			const { available } = await checkEmailAvailable(value);
			result = available ? 'available' : 'taken';
		} catch (e) {
			result = 'failed';
		}
		// 응답 전에 가입 409로 'taken'이 정해졌으면 그대로 둔다
		setAvailability((prev) => (prev[key] === 'taken' ? prev : { ...prev, [key]: result }));
	}, []);

	useEffect(() => {
		if (!EMAIL_PATTERN.test(trimmedEmail)) return undefined;
		const timer = setTimeout(() => checkEmail(trimmedEmail), EMAIL_CHECK_DELAY_MS);
		return () => clearTimeout(timer);
	}, [trimmedEmail, checkEmail]);

	if (user) {
		return <Navigate to="/" replace />;
	}

	const touch = (field) => setTouched((prev) => (prev[field] ? prev : { ...prev, [field]: true }));

	const onEmailBlur = () => {
		touch('email');
		if (EMAIL_PATTERN.test(trimmedEmail)) {
			checkEmail(trimmedEmail);
		}
	};

	const messages = {
		email: emailMessage(trimmedEmail, touched.email, availability[emailKey]),
		password: passwordMessage(password),
		passwordConfirm: passwordConfirmMessage(password, passwordConfirm),
		nickname: nicknameMessage(nickname, touched.nickname),
	};
	// 중복 확인 중이거나 확인을 못 했으면 가입은 막지 않는다(서버가 409로 다시 확인한다)
	const emailUsable = EMAIL_PATTERN.test(trimmedEmail) && availability[emailKey] !== 'taken';
	const allValid =
		emailUsable &&
		messages.password.tone === 'ok' &&
		messages.passwordConfirm.tone === 'ok' &&
		messages.nickname.tone === 'ok';

	const onSubmit = async (event) => {
		event.preventDefault();
		if (submitting || !allValid) return;
		const form = { email: trimmedEmail, password, nickname: nickname.trim() };
		setFormError('');
		setSubmitting(true);
		try {
			await signup(form);
		} catch (e) {
			const code = e && e.response && e.response.data && e.response.data.code;
			if (code === 'EMAIL_DUPLICATE') {
				// 이메일 칸 아래에 빨간 안내로 보여 준다
				asked.current.add(emailKey);
				setAvailability((prev) => ({ ...prev, [emailKey]: 'taken' }));
			} else {
				setFormError(errorMessage(e));
			}
			setSubmitting(false);
			return;
		}
		// 가입하면 바로 로그인한다. 로그인만 실패하면 로그인 화면에서 다시 시도할 수 있게 안내한다
		try {
			await login({ email: form.email, password: form.password });
			const loaded = await reloadUser();
			if (!loaded) {
				setFormError('가입했어요. 로그인 화면에서 다시 로그인해 주세요.');
				setSubmitting(false);
			}
		} catch (e) {
			setFormError('가입했어요. 로그인 화면에서 다시 로그인해 주세요. (' + errorMessage(e) + ')');
			setSubmitting(false);
		}
	};

	return (
		<>
			<form className="auth-form" onSubmit={onSubmit} noValidate>
				<label htmlFor="signup-email">이메일</label>
				<input
					id="signup-email"
					type="email"
					autoComplete="email"
					aria-describedby="signup-email-message"
					{...fieldInputProps(messages.email)}
					value={email}
					onChange={(e) => setEmail(e.target.value)}
					onBlur={onEmailBlur}
					required
				/>
				<FieldMessage id="signup-email-message" tone={messages.email.tone}>
					{messages.email.text}
				</FieldMessage>

				<label htmlFor="signup-password">비밀번호</label>
				<input
					id="signup-password"
					type="password"
					autoComplete="new-password"
					aria-describedby="signup-password-message"
					{...fieldInputProps(messages.password)}
					value={password}
					onChange={(e) => setPassword(e.target.value)}
					required
				/>
				<FieldMessage id="signup-password-message" tone={messages.password.tone}>
					{messages.password.text}
				</FieldMessage>

				<label htmlFor="signup-password-confirm">비밀번호 확인</label>
				<input
					id="signup-password-confirm"
					type="password"
					autoComplete="new-password"
					aria-describedby="signup-password-confirm-message"
					{...fieldInputProps(messages.passwordConfirm)}
					value={passwordConfirm}
					onChange={(e) => setPasswordConfirm(e.target.value)}
					required
				/>
				<FieldMessage id="signup-password-confirm-message" tone={messages.passwordConfirm.tone}>
					{messages.passwordConfirm.text}
				</FieldMessage>

				<label htmlFor="signup-nickname">닉네임</label>
				<input
					id="signup-nickname"
					type="text"
					autoComplete="nickname"
					aria-describedby="signup-nickname-message"
					{...fieldInputProps(messages.nickname)}
					value={nickname}
					onChange={(e) => setNickname(e.target.value)}
					onBlur={() => touch('nickname')}
					required
				/>
				<FieldMessage id="signup-nickname-message" tone={messages.nickname.tone}>
					{messages.nickname.text}
				</FieldMessage>

				{formError && <p role="alert" className="auth-error">{formError}</p>}
				<button type="submit" className="auth-primary" disabled={submitting || !allValid}>
					가입하기
				</button>
			</form>
			<a className="auth-kakao" href={kakaoLoginUrl(false)}>
				카카오로 시작하기
			</a>
			<p className="auth-switch">
				이미 가입하셨나요? <Link to="/login">로그인</Link>
			</p>
		</>
	);
}

function SignupMobile() {
	return (
		<AuthMobileLayout title="가입">
			<SignupForm />
		</AuthMobileLayout>
	);
}

function SignupDesktop() {
	return (
		<AuthDesktopLayout title="가입">
			<SignupForm />
		</AuthDesktopLayout>
	);
}

export default function SignupPage() {
	return <ResponsivePage mobile={SignupMobile} desktop={SignupDesktop} />;
}

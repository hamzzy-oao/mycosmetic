import { useState } from 'react';
import { Link, Navigate, useLocation, useSearchParams } from 'react-router-dom';
import { kakaoLoginUrl, login } from '../../api/auth';
import { useAuth } from '../../AuthContext';
import ResponsivePage from '../../components/ResponsivePage';
import { errorMessage } from '../../lib/errorMessage';
import { AuthDesktopLayout, AuthMobileLayout } from './AuthLayout';

const KAKAO_ERROR_MESSAGE = '카카오 로그인에 실패했어요. 이메일로 로그인하거나 잠시 뒤 다시 시도해 주세요.';

// RequireAuth가 기억해 둔 원래 주소. 없거나 로그인·가입 화면이면 메인으로
export function redirectTarget(location) {
	const from = location.state && location.state.from;
	if (!from || !from.pathname || from.pathname === '/login' || from.pathname === '/signup') {
		return '/';
	}
	return from.pathname + (from.search || '');
}

function LoginForm() {
	const { user, reloadUser } = useAuth();
	const location = useLocation();
	const [searchParams] = useSearchParams();
	const [email, setEmail] = useState('');
	const [password, setPassword] = useState('');
	const [submitting, setSubmitting] = useState(false);
	const [error, setError] = useState(() => (searchParams.get('error') === 'kakao' ? KAKAO_ERROR_MESSAGE : ''));
	const notice = location.state && location.state.notice;

	// 로그인되면(이미 로그인 상태로 왔을 때 포함) 원래 가려던 화면으로 보낸다
	if (user) {
		return <Navigate to={redirectTarget(location)} replace />;
	}

	const onSubmit = async (event) => {
		event.preventDefault();
		if (submitting) return;
		setError('');
		setSubmitting(true);
		try {
			await login({ email: email.trim(), password });
			const loaded = await reloadUser();
			if (!loaded) {
				setError('로그인 정보를 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.');
				setSubmitting(false);
			}
		} catch (e) {
			setError(errorMessage(e));
			setSubmitting(false);
		}
	};

	return (
		<>
			{notice && <p role="status" className="auth-notice">{notice}</p>}
			<form className="auth-form" onSubmit={onSubmit} noValidate>
				<label htmlFor="login-email">이메일</label>
				<input
					id="login-email"
					type="email"
					autoComplete="email"
					value={email}
					onChange={(e) => setEmail(e.target.value)}
					required
				/>
				<label htmlFor="login-password">비밀번호</label>
				<input
					id="login-password"
					type="password"
					autoComplete="current-password"
					value={password}
					onChange={(e) => setPassword(e.target.value)}
					required
				/>
				{error && <p role="alert" className="auth-error">{error}</p>}
				<button type="submit" className="auth-primary" disabled={submitting}>
					로그인
				</button>
			</form>
			<a className="auth-kakao" href={kakaoLoginUrl(false)}>
				카카오로 로그인
			</a>
			<p className="auth-switch">
				처음이신가요? <Link to="/signup">이메일로 가입</Link>
			</p>
		</>
	);
}

function LoginMobile() {
	return (
		<AuthMobileLayout title="로그인">
			<LoginForm />
		</AuthMobileLayout>
	);
}

function LoginDesktop() {
	return (
		<AuthDesktopLayout title="로그인">
			<LoginForm />
		</AuthDesktopLayout>
	);
}

export default function LoginPage() {
	return <ResponsivePage mobile={LoginMobile} desktop={LoginDesktop} />;
}

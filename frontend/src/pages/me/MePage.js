import { useId, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { kakaoLoginUrl, unlinkKakao, updateNickname, withdraw } from '../../api/auth';
import { refreshOnce } from '../../api/client';
import { useAuth } from '../../AuthContext';
import FieldMessage, { fieldInputProps } from '../../components/FieldMessage';
import ResponsivePage from '../../components/ResponsivePage';
import { errorMessage } from '../../lib/errorMessage';
import { AuthDesktopLayout, AuthMobileLayout } from '../auth/AuthLayout';
import './MePage.css';
import SkinInfoSection from './SkinInfoSection';

const WITHDRAW_CONFIRM =
	'정말 탈퇴할까요?\n내 화장대, 피부 상태, 로그인 정보가 모두 지워지고 되돌릴 수 없어요.\n' +
	'내가 등록한 제품과 성분 정보는 이름 없이 남아요.';

const KAKAO_LINK_FAILED = '카카오 계정을 연결하지 못했어요. 다시 시도해 주세요.';

export const NICKNAME_MAX = 20;

// 닉네임 확인(가입과 같은 규칙): 앞뒤 공백을 빼고 1~20자. 문제가 없으면 빈 문자열
export function nicknameError(value) {
	const trimmed = value.trim();
	if (trimmed === '') return '닉네임을 입력해 주세요.';
	if (trimmed.length > NICKNAME_MAX) return `닉네임은 ${NICKNAME_MAX}자까지 쓸 수 있어요.`;
	return '';
}

// 닉네임 보기 → [변경] → 입력칸과 저장/취소. 저장하면 AuthContext의 user를 바꿔 다른 화면(헤더 등)에도 바로 반영한다
function NicknameField({ user, setUser, disabled, onSaved }) {
	const inputId = useId();
	const messageId = useId();
	const [editing, setEditing] = useState(false);
	const [value, setValue] = useState(user.nickname || '');
	const [error, setError] = useState('');
	const [saving, setSaving] = useState(false);

	const start = () => {
		setValue(user.nickname || '');
		setError('');
		setEditing(true);
	};

	const cancel = () => {
		setEditing(false);
		setError('');
	};

	const save = async (event) => {
		event.preventDefault();
		const problem = nicknameError(value);
		if (problem) {
			setError(problem);
			return;
		}
		setSaving(true);
		setError('');
		try {
			const updated = await updateNickname(value.trim());
			setUser(updated);
			setEditing(false);
			onSaved();
		} catch (e) {
			setError(errorMessage(e));
		} finally {
			setSaving(false);
		}
	};

	// 저장할 때 받은 오류(서버 메시지 포함)가 있으면 그것을, 아니면 입력하는 대로 길이를 안내한다
	const problem = error || nicknameError(value);
	const message = problem
		? { tone: 'error', text: problem }
		: { tone: 'ok', text: '사용 가능한 닉네임이에요.' };

	if (!editing) {
		return (
			<dd className="me-nickname">
				<span>{user.nickname}</span>
				<button type="button" onClick={start} disabled={disabled} aria-label="닉네임 변경">
					변경
				</button>
			</dd>
		);
	}
	return (
		<dd>
			<form className="me-nickname-form" onSubmit={save} noValidate>
				<label htmlFor={inputId}>새 닉네임</label>
				<input
					id={inputId}
					value={value}
					autoComplete="nickname"
					aria-describedby={messageId}
					{...fieldInputProps(message)}
					onChange={(e) => {
						setValue(e.target.value);
						setError('');
					}}
				/>
				<FieldMessage id={messageId} tone={message.tone}>
					{message.text}
				</FieldMessage>
				<div className="me-nickname-actions">
					<button type="submit" disabled={saving}>
						{saving ? '저장하는 중...' : '저장'}
					</button>
					<button type="button" onClick={cancel} disabled={saving}>
						취소
					</button>
				</div>
			</form>
		</dd>
	);
}

// 내 정보 (설계 5.1 내 정보·카카오 연결/해제·탈퇴, 5.5)
function MeContent() {
	const { user, setUser, reloadUser, logout } = useAuth();
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();
	const [busy, setBusy] = useState(false);
	const [notice, setNotice] = useState(() => (searchParams.get('linked') === '1' ? '카카오 계정을 연결했어요.' : ''));
	const [error, setError] = useState(() => (searchParams.get('error') === 'kakao' ? KAKAO_LINK_FAILED : ''));

	if (!user) {
		return null;
	}

	// 액세스 토큰(15분)이 만료된 채로 이동하면 연결이 아니라 새 계정 로그인이 되므로, 먼저 세션을 갱신한다
	const onLink = async () => {
		if (busy) return;
		setBusy(true);
		setError('');
		setNotice('');
		try {
			await refreshOnce();
		} catch (e) {
			setError(KAKAO_LINK_FAILED);
			setBusy(false);
			return;
		}
		window.location.assign(kakaoLoginUrl(true));
	};

	const onUnlink = async () => {
		if (busy) return;
		setBusy(true);
		setError('');
		setNotice('');
		try {
			await unlinkKakao();
			await reloadUser();
			setNotice('카카오 연결을 해제했어요.');
		} catch (e) {
			setError(errorMessage(e));
		} finally {
			setBusy(false);
		}
	};

	const onWithdraw = async () => {
		if (busy) return;
		if (!window.confirm(WITHDRAW_CONFIRM)) {
			return;
		}
		setBusy(true);
		setError('');
		setNotice('');
		try {
			await withdraw();
		} catch (e) {
			setError(errorMessage(e));
			setBusy(false);
			return;
		}
		// 먼저 로그인 화면으로 옮긴 뒤 사용자 상태를 비운다(RequireAuth가 /me를 원래 주소로 기억하지 않게)
		navigate('/login', { replace: true, state: { notice: '탈퇴했어요. 그동안 이용해 주셔서 고마워요.' } });
		setUser(null);
	};

	const onLogout = async () => {
		if (busy) return;
		setBusy(true);
		navigate('/login', { replace: true });
		try {
			await logout();
		} catch (e) {
			// 서버 로그아웃이 실패해도 화면의 로그인 상태는 비운다(AuthContext.logout)
		}
	};

	return (
		<>
			<div className="me-profile">
			{notice && <p role="status" className="auth-notice">{notice}</p>}
			{error && <p role="alert" className="auth-error">{error}</p>}
			<dl className="me-info">
				<dt>닉네임</dt>
				<NicknameField
					user={user}
					setUser={setUser}
					disabled={busy}
					onSaved={() => {
						setError('');
						setNotice('닉네임을 바꿨어요.');
					}}
				/>
				<dt>이메일</dt>
				<dd>{user.email || '없음 (카카오로 가입)'}</dd>
			</dl>

			<section className="me-kakao" aria-labelledby="me-kakao-title">
			<h2 id="me-kakao-title">카카오</h2>
			{user.kakaoLinked ? (
				<div className="me-row">
					<span>연결됨</span>
					<button type="button" onClick={onUnlink} disabled={busy}>
						카카오 연결 해제
					</button>
				</div>
			) : (
				<div className="me-row">
					<span>연결 안 됨</span>
					<button type="button" className="auth-kakao" onClick={onLink} disabled={busy}>
						카카오 연결
					</button>
				</div>
			)}

			</section>
			<SkinInfoSection />

			<div className="me-actions">
				<button type="button" onClick={onLogout} disabled={busy}>
					로그아웃
				</button>
				<button type="button" className="me-danger" onClick={onWithdraw} disabled={busy}>
					회원 탈퇴
				</button>
			</div>
			</div>
		</>
	);
}

function MeMobile() {
	return (
		<AuthMobileLayout title="내 정보" homeLink>
			<MeContent />
		</AuthMobileLayout>
	);
}

function MeDesktop() {
	return (
		<AuthDesktopLayout title="내 정보" homeLink>
			<MeContent />
		</AuthDesktopLayout>
	);
}

export default function MePage() {
	return <ResponsivePage mobile={MeMobile} desktop={MeDesktop} />;
}

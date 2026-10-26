import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getNotifications } from '../../api/notifications';
import { errorMessage } from '../../lib/errorMessage';
import { notificationPermission, requestNotificationPermission } from '../../lib/browserNotify';
import './NotificationsPage.css';

// 알림 목록 (설계 2026-10-24 C). 서버가 지금 상태로 계산한 목록을 보여주고, 브라우저 알림을 켤 수 있다
const LEVEL_LABELS = {
	WARNING: '확인 필요',
	CAUTION: '주의',
	INFO: '안내',
};

const PERMISSION_TEXT = {
	granted: '브라우저 알림이 켜져 있어요. 앱을 열 때 하루 한 번 요약을 보여드려요.',
	denied: '브라우저에서 알림을 막아 두었어요. 브라우저 설정에서 이 사이트의 알림을 허용해 주세요.',
	unsupported: '이 브라우저는 알림을 지원하지 않아요.',
};

function BrowserNotifySetting() {
	const [permission, setPermission] = useState(notificationPermission());
	const enable = async () => setPermission(await requestNotificationPermission());
	return (
		<section className="notify-card notify-setting" aria-labelledby="notify-setting-title">
			<h2 id="notify-setting-title">브라우저 알림</h2>
			{permission === 'default' ? (
				<>
					<p className="notify-hint">켜 두면 앱을 열 때 하루 한 번 알림 요약을 띄워요. 앱을 닫아 두었을 때는 오지 않아요.</p>
					<button type="button" className="notify-primary" onClick={enable}>
						브라우저 알림 켜기
					</button>
				</>
			) : (
				<p className="notify-hint">{PERMISSION_TEXT[permission]}</p>
			)}
		</section>
	);
}

export default function NotificationsPage() {
	const [items, setItems] = useState(null);
	const [error, setError] = useState('');

	const load = useCallback(async () => {
		setError('');
		try {
			setItems((await getNotifications()).items || []);
		} catch (e) {
			setError(errorMessage(e));
		}
	}, []);

	useEffect(() => {
		load();
	}, [load]);

	return (
		<section className="notify-page" aria-labelledby="notify-title">
			<h1 id="notify-title" className="visually-hidden">
				알림
			</h1>
			<section className="notify-card" aria-labelledby="notify-list-title">
				<h2 id="notify-list-title">
					지금 확인할 것 {items && <span className="notify-count">{items.length}개</span>}
				</h2>
				{error && (
					<div role="alert" className="notify-error">
						<p>{error}</p>
						<button type="button" onClick={load}>
							다시 시도
						</button>
					</div>
				)}
				{!items && !error && <p aria-busy="true">불러오는 중...</p>}
				{items && items.length === 0 && <p className="notify-hint">새 알림이 없어요.</p>}
				{items && items.length > 0 && (
					<ul className="notify-list">
						{items.map((item, i) => (
							<li key={`${item.type}-${i}`} className={`notify-item is-${item.level}`}>
								<span className="notify-level">{LEVEL_LABELS[item.level]}</span>
								<strong>{item.title}</strong>
								<p>{item.message}</p>
								<Link to={item.link}>바로 가기</Link>
							</li>
						))}
					</ul>
				)}
			</section>
			<BrowserNotifySetting />
		</section>
	);
}

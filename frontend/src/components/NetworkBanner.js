import { useEffect, useState } from 'react';

// 네트워크 끊김 배너 (설계 6.4)
export default function NetworkBanner() {
	const [online, setOnline] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine !== false));

	useEffect(() => {
		const goOnline = () => setOnline(true);
		const goOffline = () => setOnline(false);
		window.addEventListener('online', goOnline);
		window.addEventListener('offline', goOffline);
		return () => {
			window.removeEventListener('online', goOnline);
			window.removeEventListener('offline', goOffline);
		};
	}, []);

	if (online) {
		return null;
	}
	return (
		<div role="alert" className="network-banner">
			인터넷 연결이 끊겼어요. 연결되면 다시 시도해 주세요.
		</div>
	);
}

import { useEffect, useState } from 'react';
import { getNotifications } from '../api/notifications';
import { notifyOncePerDay } from '../lib/browserNotify';
import { todayString } from '../pages/shelf/shelfCommon';

// 헤더의 '알림 N'. 로그인했을 때 화면을 옮길 때마다 다시 센다. 실패하면 조용히 0으로 둔다
export default function useNotificationCount(enabled, pathname) {
	const [count, setCount] = useState(0);
	useEffect(() => {
		if (!enabled) {
			setCount(0);
			return undefined;
		}
		let cancelled = false;
		getNotifications()
			.then((data) => {
				if (cancelled) return;
				const items = (data && data.items) || [];
				setCount(items.length);
				notifyOncePerDay(items, todayString());
			})
			.catch(() => !cancelled && setCount(0));
		return () => {
			cancelled = true;
		};
	}, [enabled, pathname]);
	return count;
}

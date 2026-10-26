// 브라우저 알림 (설계 2026-10-24 C): 사용자가 허락하면 앱을 열 때 하루 한 번 요약을 띄운다.
// 앱을 열지 않았을 때 오는 푸시(Web Push)는 하지 않는다. 마지막으로 띄운 날은 이 브라우저에만 저장한다
const LAST_SHOWN_KEY = 'vanity-notify-last-shown';

export function notificationSupported() {
	return typeof window !== 'undefined' && 'Notification' in window;
}

export function notificationPermission() {
	return notificationSupported() ? window.Notification.permission : 'unsupported';
}

// 'granted' | 'denied' | 'default' | 'unsupported'
export async function requestNotificationPermission() {
	if (!notificationSupported()) return 'unsupported';
	try {
		return await window.Notification.requestPermission();
	} catch (e) {
		return 'denied';
	}
}

function readLastShown() {
	try {
		return window.localStorage.getItem(LAST_SHOWN_KEY);
	} catch (e) {
		return null;
	}
}

function writeLastShown(day) {
	try {
		window.localStorage.setItem(LAST_SHOWN_KEY, day);
	} catch (e) {
		// 저장소를 못 쓰면 다음에 또 띄울 수 있다. 막지는 않는다
	}
}

// 허락했고 오늘 아직 안 띄웠으면 알림 요약 하나를 띄운다. 띄웠으면 true
export function notifyOncePerDay(items, today) {
	if (!items || items.length === 0 || notificationPermission() !== 'granted') return false;
	if (readLastShown() === today) return false;
	const body = items.length === 1 ? items[0].message : `${items[0].title} 외 ${items.length - 1}개`;
	try {
		new window.Notification('Vanity Check 알림', { body, tag: 'vanity-daily' });
	} catch (e) {
		return false;
	}
	writeLastShown(today);
	return true;
}

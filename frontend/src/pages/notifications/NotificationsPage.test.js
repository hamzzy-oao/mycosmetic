import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { getNotifications } from '../../api/notifications';
import { notifyOncePerDay } from '../../lib/browserNotify';
import NotificationsPage from './NotificationsPage';

jest.mock('../../api/notifications', () => ({
	getNotifications: jest.fn(),
}));

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

function renderPage() {
	return render(
		<MemoryRouter future={FUTURE}>
			<NotificationsPage />
		</MemoryRouter>
	);
}

const ITEMS = [
	{ type: 'EXPIRED', level: 'WARNING', title: '사용기한이 지났어요', message: '브랜드 토너의 사용기한이 끝났어요.', link: '/shelf' },
	{ type: 'DIARY_TODAY', level: 'INFO', title: '오늘 피부는 어땠나요?', message: '피부 일기를 남겨 보세요.', link: '/diary' },
];

let notifications;

function installNotification(permission) {
	notifications = [];
	function FakeNotification(title, options) {
		notifications.push({ title, ...options });
	}
	FakeNotification.permission = permission;
	FakeNotification.requestPermission = jest.fn(() => Promise.resolve('granted'));
	window.Notification = FakeNotification;
}

beforeEach(() => {
	jest.clearAllMocks();
	window.localStorage.clear();
	installNotification('default');
	getNotifications.mockResolvedValue({ items: ITEMS });
});

afterEach(() => {
	delete window.Notification;
});

test('알림 목록과 바로 가기 링크', async () => {
	renderPage();

	expect(await screen.findByText('사용기한이 지났어요')).toBeInTheDocument();
	expect(screen.getByText('2개')).toBeInTheDocument();
	expect(screen.getAllByRole('link', { name: '바로 가기' }).map((a) => a.getAttribute('href'))).toEqual(['/shelf', '/diary']);
});

test('알림이 없으면 없다고 안내한다', async () => {
	getNotifications.mockResolvedValue({ items: [] });
	renderPage();

	expect(await screen.findByText('새 알림이 없어요.')).toBeInTheDocument();
});

test('브라우저 알림 켜기를 누르면 허락을 묻고 켜졌다고 보여준다', async () => {
	renderPage();

	fireEvent.click(await screen.findByRole('button', { name: '브라우저 알림 켜기' }));

	expect(await screen.findByText(/브라우저 알림이 켜져 있어요/)).toBeInTheDocument();
	expect(window.Notification.requestPermission).toHaveBeenCalled();
});

test('허락했으면 하루 한 번만 요약 알림을 띄운다', () => {
	installNotification('granted');

	expect(notifyOncePerDay(ITEMS, '2026-10-24')).toBe(true);
	expect(notifyOncePerDay(ITEMS, '2026-10-24')).toBe(false);
	expect(notifyOncePerDay(ITEMS, '2026-10-25')).toBe(true);
	expect(notifications).toHaveLength(2);
	expect(notifications[0].body).toBe('사용기한이 지났어요 외 1개');

	installNotification('denied');
	expect(notifyOncePerDay(ITEMS, '2026-10-26')).toBe(false);
});

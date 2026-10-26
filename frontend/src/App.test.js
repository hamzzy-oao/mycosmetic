import { render, screen } from '@testing-library/react';
import App from './App';
import { ensureCsrf, me } from './api/auth';
import { getShelf } from './api/shelf';

jest.mock('./api/auth', () => ({
	ensureCsrf: jest.fn(),
	me: jest.fn(),
	logout: jest.fn(),
	login: jest.fn(),
	kakaoLoginUrl: jest.fn(() => '/oauth2/authorization/kakao'),
}));
jest.mock('./api/notifications', () => ({
	getNotifications: jest.fn(() => Promise.resolve({ items: [] })),
}));
jest.mock('./api/shelf', () => ({
	getShelf: jest.fn(),
	updateShelfItem: jest.fn(),
	removeShelfItem: jest.fn(),
}));

beforeEach(() => {
	ensureCsrf.mockResolvedValue({});
	getShelf.mockResolvedValue([]);
});

afterEach(() => {
	window.history.pushState({}, '', '/');
});

test('/login에서 로그인 버튼이 보인다', async () => {
	me.mockRejectedValue(new Error('401'));
	window.history.pushState({}, '', '/login');

	render(<App />);

	expect(await screen.findByRole('button', { name: '로그인' })).toBeInTheDocument();
	expect(ensureCsrf).toHaveBeenCalled();
});

test('로그인하지 않고 /shelf에 가면 로그인 화면으로 보낸다', async () => {
	me.mockRejectedValue(new Error('401'));
	window.history.pushState({}, '', '/shelf');

	render(<App />);

	expect(await screen.findByRole('button', { name: '로그인' })).toBeInTheDocument();
	expect(window.location.pathname).toBe('/login');
});

test('로그인하지 않고 /에 오면 공개 메인 페이지가 보인다', async () => {
	me.mockRejectedValue(new Error('401'));
	window.history.pushState({}, '', '/');

	render(<App />);

	expect(await screen.findByRole('heading', { name: 'Vanity Check' })).toBeInTheDocument();
	expect(window.location.pathname).toBe('/');
});

test('로그인한 사용자도 /에서는 메인 페이지에 머문다', async () => {
	me.mockResolvedValue({ id: 1, nickname: '테스트' });
	window.history.pushState({}, '', '/');

	render(<App />);

	// 상단 메뉴의 카테고리는 화면 개편으로 바로 가는 링크가 됐다
	expect(await screen.findByRole('link', { name: 'MY VANITY' })).toHaveAttribute('href', '/shelf');
	expect(window.location.pathname).toBe('/');
});

test('없는 주소는 메인 페이지로 보낸다', async () => {
	me.mockRejectedValue(new Error('401'));
	window.history.pushState({}, '', '/no-such-page');

	render(<App />);

	expect(await screen.findByRole('heading', { name: 'Vanity Check' })).toBeInTheDocument();
	expect(window.location.pathname).toBe('/');
});

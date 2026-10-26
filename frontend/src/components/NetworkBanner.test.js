import { act, render, screen } from '@testing-library/react';
import NetworkBanner from './NetworkBanner';

function setOnline(value) {
	Object.defineProperty(navigator, 'onLine', { value, configurable: true });
}

afterEach(() => setOnline(true));

test('오프라인이 되면 네트워크 배너가 보인다', () => {
	setOnline(true);
	render(<NetworkBanner />);
	expect(screen.queryByRole('alert')).not.toBeInTheDocument();

	act(() => {
		setOnline(false);
		window.dispatchEvent(new Event('offline'));
	});
	expect(screen.getByRole('alert')).toHaveTextContent('인터넷 연결이 끊겼어요');

	act(() => {
		setOnline(true);
		window.dispatchEvent(new Event('online'));
	});
	expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

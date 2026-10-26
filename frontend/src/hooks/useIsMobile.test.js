import { act, renderHook } from '@testing-library/react';
import useIsMobile, { MOBILE_QUERY } from './useIsMobile';

// jsdom에는 matchMedia가 없어서 너비로 판정하는 가짜를 둔다
function mockMatchMedia(width) {
	const listeners = new Set();
	const mql = {
		get matches() {
			return width <= 767;
		},
		media: MOBILE_QUERY,
		addEventListener: (type, fn) => listeners.add(fn),
		removeEventListener: (type, fn) => listeners.delete(fn),
	};
	window.matchMedia = jest.fn(() => mql);
	return {
		resize(newWidth) {
			width = newWidth;
			listeners.forEach((fn) => fn({ matches: mql.matches }));
		},
	};
}

afterEach(() => {
	delete window.matchMedia;
});

test('768px 이상이면 useIsMobile은 false', () => {
	mockMatchMedia(1024);
	const { result } = renderHook(() => useIsMobile());
	expect(result.current).toBe(false);
	expect(window.matchMedia).toHaveBeenCalledWith('(max-width: 767px)');
});

test('767px 이하면 true이고 너비가 바뀌면 따라간다', () => {
	const media = mockMatchMedia(375);
	const { result } = renderHook(() => useIsMobile());
	expect(result.current).toBe(true);

	act(() => media.resize(800));
	expect(result.current).toBe(false);
});

test('matchMedia가 없으면 false', () => {
	const { result } = renderHook(() => useIsMobile());
	expect(result.current).toBe(false);
});

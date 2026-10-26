import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import HomeRitual from './HomeRitual';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };
const matchMediaDescriptor = Object.getOwnPropertyDescriptor(window, 'matchMedia');
const viewportDescriptor = Object.getOwnPropertyDescriptor(window, 'innerHeight');
let frames;
let frameId;

function flushFrames() {
	act(() => {
		const pending = [...frames.values()];
		frames.clear();
		pending.forEach((callback) => callback(0));
	});
}

function renderRitual(height, stageHeight) {
	render(<MemoryRouter future={FUTURE}><HomeRitual /></MemoryRouter>);
	const band = screen.getByRole('region');
	let top = window.innerHeight + 64;
	jest.spyOn(band, 'getBoundingClientRect').mockImplementation(() => ({ top, height }));
	jest.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockReturnValue(stageHeight);
	return {
		band,
		scrollTo(nextTop) {
			top = nextTop;
			fireEvent.scroll(window);
			flushFrames();
		},
	};
}

function expectDone(count) {
	const steps = screen.getAllByRole('listitem');
	expect(steps).toHaveLength(7);
	steps.forEach((step, index) => {
		if (index < count) expect(step).toHaveClass('is-done');
		else expect(step).not.toHaveClass('is-done');
	});
}

beforeEach(() => {
	frames = new Map();
	frameId = 0;
	Object.defineProperty(window, 'innerHeight', { configurable: true, value: 900 });
	Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false })) });
	jest.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
		frameId += 1;
		frames.set(frameId, callback);
		return frameId;
	});
	jest.spyOn(window, 'cancelAnimationFrame').mockImplementation((id) => frames.delete(id));
});

afterEach(() => {
	jest.restoreAllMocks();
	if (matchMediaDescriptor) Object.defineProperty(window, 'matchMedia', matchMediaDescriptor);
	else delete window.matchMedia;
	Object.defineProperty(window, 'innerHeight', viewportDescriptor);
});

test.each([
	['PC', 396, 1000, 880, 302],
	['폰', 396, 812, 692, 208],
])('%s의 루틴 영역이 화면에 들어와 중앙에 닿을 때 아침부터 밤까지 재생된다', (label, stageHeight, viewport, start, end) => {
	Object.defineProperty(window, 'innerHeight', { configurable: true, value: viewport });
	const { band, scrollTo } = renderRitual(stageHeight, stageHeight);
	const distance = start - end;
	flushFrames();

	expect(band).toHaveAttribute('data-motion', 'scroll');
	expect(band.style.getPropertyValue('--ritual')).toBe('0.000');
	expect(screen.getByRole('heading', { name: 'Good morning.' })).toBeInTheDocument();
	expectDone(0);

	scrollTo(start);

	expect(band.style.getPropertyValue('--ritual')).toBe('0.000');
	expectDone(0);

	scrollTo(start - distance / 2);

	expect(band.style.getPropertyValue('--ritual')).toBe('0.500');
	expectDone(4);
	expect(band).toHaveAttribute('data-phase', 'morning');

	scrollTo(start - distance * 0.75);

	expect(screen.getByRole('heading', { name: 'Good night.' })).toBeInTheDocument();
	expect(screen.getByText('저녁 루틴 순서를 확인하세요.')).toBeInTheDocument();
	expect(band).toHaveAttribute('data-phase', 'night');

	scrollTo(end);

	expect(band.style.getPropertyValue('--ritual')).toBe('1.000');
	expectDone(7);
	expect(screen.getByRole('link', { name: /루틴 기록하기/ })).toHaveAttribute('href', '/routines');

	scrollTo(end - 100);

	expect(band.style.getPropertyValue('--ritual')).toBe('1.000');
	expectDone(7);

	scrollTo(start + 100);

	expect(band.style.getPropertyValue('--ritual')).toBe('0.000');
	expectDone(0);
	expect(screen.getByRole('heading', { name: 'Good morning.' })).toBeInTheDocument();
});

test('움직임 줄이기에서는 스크롤 없이 아침과 저녁의 모든 단계를 보여준다', () => {
	window.matchMedia.mockReturnValue({ matches: true });
	const { band, scrollTo } = renderRitual(396, 396);

	expect(band).toHaveAttribute('data-motion', 'static');
	expect(screen.getByRole('heading', { name: 'Morning & night.' })).toBeInTheDocument();
	expect(screen.getByText('아침과 저녁 루틴 순서를 확인하세요.')).toBeInTheDocument();
	expectDone(7);
	expect(window.requestAnimationFrame).not.toHaveBeenCalled();

	scrollTo(-500);

	expectDone(7);
	expect(window.requestAnimationFrame).not.toHaveBeenCalled();
});

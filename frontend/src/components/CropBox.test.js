import { fireEvent, render, screen } from '@testing-library/react';
import CropBox from './CropBox';

// jsdom에는 PointerEvent가 없어 clientX가 빠진다. MouseEvent로 대신한다
const originalPointerEvent = window.PointerEvent;
beforeAll(() => {
	window.PointerEvent = class PointerEvent extends MouseEvent {
		constructor(type, init = {}) {
			super(type, init);
			this.pointerId = init.pointerId;
		}
	};
});
afterAll(() => {
	window.PointerEvent = originalPointerEvent;
});

function mockArea() {
	const area = screen.getByTestId('crop-area');
	area.getBoundingClientRect = () => ({ left: 100, top: 50, width: 200, height: 100, right: 300, bottom: 150 });
	return area;
}

test('끌어서 그린 사각형을 사진 크기 비율로 알려준다', () => {
	const onChange = jest.fn();
	render(<CropBox imageUrl="blob:photo" onChange={onChange} />);
	const area = mockArea();

	fireEvent.pointerDown(area, { clientX: 250, clientY: 140 });
	fireEvent.pointerMove(area, { clientX: 150, clientY: 70 });
	fireEvent.pointerUp(area, { clientX: 150, clientY: 70 });

	expect(onChange).toHaveBeenLastCalledWith({ x: 0.25, y: 0.2, width: 0.5, height: 0.7 });
	expect(screen.getByTestId('crop-rect')).toHaveStyle({ left: '25%', width: '50%' });
});

test('기본값은 전체이고, 너무 작게 그리거나 [사진 전체 쓰기]면 null', () => {
	const onChange = jest.fn();
	render(<CropBox imageUrl="blob:photo" onChange={onChange} />);
	const area = mockArea();
	expect(screen.queryByTestId('crop-rect')).not.toBeInTheDocument();
	expect(screen.getByRole('button', { name: '사진 전체 쓰기' })).toBeDisabled();

	fireEvent.pointerDown(area, { clientX: 150, clientY: 60 });
	fireEvent.pointerUp(area, { clientX: 151, clientY: 61 });
	expect(onChange).toHaveBeenLastCalledWith(null);

	fireEvent.pointerDown(area, { clientX: 100, clientY: 50 });
	fireEvent.pointerUp(area, { clientX: 400, clientY: 300 });
	expect(onChange).toHaveBeenLastCalledWith({ x: 0, y: 0, width: 1, height: 1 });

	fireEvent.click(screen.getByRole('button', { name: '사진 전체 쓰기' }));
	expect(onChange).toHaveBeenLastCalledWith(null);
	expect(screen.queryByTestId('crop-rect')).not.toBeInTheDocument();
});

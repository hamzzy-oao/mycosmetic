import { act, render, screen, waitFor } from '@testing-library/react';
import { detectFaces } from '../../lib/faceLandmarker';
import CameraCapture from './CameraCapture';
import { grabVideoFrame } from './captureFrames';

jest.mock('../../lib/faceLandmarker', () => ({ detectFaces: jest.fn() }));
jest.mock('./captureFrames', () => ({ grabVideoFrame: jest.fn(), toPhotoUrl: jest.fn(() => 'data:x') }));

function track() {
	return { stop: jest.fn() };
}

beforeEach(() => {
	jest.useFakeTimers();
	jest.clearAllMocks();
	// jsdom에는 영상 재생이 없다
	window.HTMLMediaElement.prototype.play = jest.fn(() => Promise.resolve());
	Object.defineProperty(window.HTMLVideoElement.prototype, 'videoWidth', { configurable: true, get: () => 640 });
	grabVideoFrame.mockReturnValue({ canvas: {}, image: { width: 10, height: 10, data: new Uint8ClampedArray(400) } });
	detectFaces.mockResolvedValue([]);
});

afterEach(() => {
	jest.useRealTimers();
	delete navigator.mediaDevices;
});

test('카메라를 쓸 수 없으면 onUnavailable', async () => {
	Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: jest.fn(() => Promise.reject(new Error('denied'))) } });
	const onUnavailable = jest.fn();

	render(<CameraCapture onCaptured={jest.fn()} onUnavailable={onUnavailable} />);

	await waitFor(() => expect(onUnavailable).toHaveBeenCalled());
});

test('인식 도구를 불러오지 못하면 얼굴이 없다고 하지 않고 따로 알린다', async () => {
	Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: jest.fn(() => Promise.resolve({ getTracks: () => [track()] })) } });
	detectFaces.mockRejectedValue(new Error('cdn blocked'));

	render(<CameraCapture onCaptured={jest.fn()} onUnavailable={jest.fn()} />);
	// 카메라가 켜진 뒤(안내 문구가 사라진 뒤)에 검사 타이머가 돈다
	await waitFor(() => expect(screen.queryByText('카메라를 켜는 중...')).not.toBeInTheDocument());
	await act(async () => {
		jest.advanceTimersByTime(600);
	});

	expect(await screen.findByText(/얼굴 인식 도구를 불러오지 못했어요/)).toBeInTheDocument();
	expect(screen.getByLabelText(/흰 종이로 조명 색 보정하기/)).not.toBeChecked();
});

test('얼굴이 안 보이면 안내를 보여주고 촬영 버튼은 꺼져 있다. 화면을 떠나면 카메라를 끈다', async () => {
	const t = track();
	Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: jest.fn(() => Promise.resolve({ getTracks: () => [t] })) } });

	const { unmount } = render(<CameraCapture onCaptured={jest.fn()} onUnavailable={jest.fn()} />);
	// 카메라가 켜진 뒤(안내 문구가 사라진 뒤)에 검사 타이머가 돈다
	await waitFor(() => expect(screen.queryByText('카메라를 켜는 중...')).not.toBeInTheDocument());
	await act(async () => {
		jest.advanceTimersByTime(600);
	});

	expect(await screen.findByText('얼굴이 보이지 않아요. 화면 가운데에 얼굴을 맞춰 주세요.')).toBeInTheDocument();
	expect(detectFaces).toHaveBeenCalled();
	expect(screen.getByRole('button', { name: '촬영' })).toBeDisabled();

	unmount();
	expect(t.stop).toHaveBeenCalled();
});

// user-event 13은 최상위 @testing-library/dom(10)을 써서 act로 감싸지지 않는다. RTL의 fireEvent를 쓴다
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { estimateColor, getPalettes, savePersonalColor } from '../../api/personalColor';
import useIsMobile from '../../hooks/useIsMobile';
import { GUIDE } from './CaptureGuide';
import { firstRound, secondRound } from './Draping';
import PersonalColorPage from './PersonalColorPage';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };
const PAYLOAD = {
	samples: [
		{ region: 'FOREHEAD', frames: [{ r: 224, g: 172, b: 150 }] },
		{ region: 'LEFT_CHEEK', frames: [{ r: 224, g: 172, b: 150 }] },
		{ region: 'RIGHT_CHEEK', frames: [{ r: 224, g: 172, b: 150 }] },
	],
	client: { faceCount: 1, frontal: true },
};

jest.mock('../../api/personalColor', () => ({
	getPalettes: jest.fn(),
	estimateColor: jest.fn(),
	savePersonalColor: jest.fn(),
}));
jest.mock('../../hooks/useIsMobile', () => ({ __esModule: true, default: jest.fn() }));
jest.mock('../../lib/faceLandmarker', () => ({ detectFaces: jest.fn() }));
// 카메라는 브라우저 기능이라 별도 테스트(CameraCapture.test.js)에서 본다. 여기서는 촬영 결과만 넘겨주는 가짜를 쓴다
jest.mock('./CameraCapture', () => ({ onCaptured, onUnavailable }) => (
	<div>
		<button type="button" onClick={() => onCaptured(PAYLOAD, 'data:image/jpeg;base64,AAA')}>가짜 촬영</button>
		<button type="button" onClick={onUnavailable}>카메라 거부</button>
	</div>
));

const PALETTES = {
	SPRING_WARM: ['#FF7F50'], SUMMER_COOL: ['#F4A7B9'], AUTUMN_WARM: ['#A0522D'], WINTER_COOL: ['#800020'],
};
const ESTIMATE = {
	lab: { l: 74.5, a: 16, b: 18.7 }, ita: 52.8, hue: 49.4, chroma: 24.6,
	candidates: ['SUMMER_COOL', 'SPRING_WARM'], confidence: 0.6, ordered: true,
	quality: { passed: true, issues: [], score: 0.95 }, ruleVersion: 'pc-v1',
};

function renderPage() {
	return render(
		<MemoryRouter future={FUTURE}>
			<PersonalColorPage />
		</MemoryRouter>
	);
}

beforeEach(() => {
	jest.clearAllMocks();
	useIsMobile.mockReturnValue(true);
	getPalettes.mockResolvedValue({ palettes: PALETTES, ruleVersion: 'pc-v1' });
	estimateColor.mockResolvedValue(ESTIMATE);
	savePersonalColor.mockImplementation((body) => Promise.resolve({ id: 1, method: body.method, chosen: body.chosen, agreed: body.method === 'CAMERA' ? body.chosen === 'SUMMER_COOL' : undefined }));
});

test('첫 화면에 촬영 전 안내 7가지와 시작하기', async () => {
	renderPage();

	for (const g of GUIDE) expect(screen.getByText(g)).toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: '시작하기' }));
	expect(screen.getByRole('button', { name: '가짜 촬영' })).toBeInTheDocument();
});

test('촬영하면 사진이 아니라 색값만 보내고, 후보와 신뢰도를 보여준다', async () => {
	renderPage();
	fireEvent.click(screen.getByRole('button', { name: '시작하기' }));
	fireEvent.click(screen.getByRole('button', { name: '가짜 촬영' }));

	expect(await screen.findByText('여름 쿨, 봄 웜')).toBeInTheDocument();
	expect(estimateColor).toHaveBeenCalledWith(PAYLOAD);
	expect(JSON.stringify(estimateColor.mock.calls[0][0])).not.toContain('data:image');
	expect(screen.getByText(/신뢰도 60%/)).toBeInTheDocument();
	expect(screen.getByText('읽은 피부색 #E0AC96')).toBeInTheDocument();
});

test('품질이 낮으면 사유를 보여주고 다시 찍기만 할 수 있다', async () => {
	estimateColor.mockResolvedValue({ ...ESTIMATE, quality: { passed: false, issues: ['UNEVEN_LIGHT'], score: 0.2 } });
	renderPage();
	fireEvent.click(screen.getByRole('button', { name: '시작하기' }));
	fireEvent.click(screen.getByRole('button', { name: '가짜 촬영' }));

	expect(await screen.findByText('얼굴 한쪽만 밝아요. 빛이 정면에서 오게 해 주세요.')).toBeInTheDocument();
	expect(screen.queryByRole('button', { name: '팔레트 비교하기' })).not.toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: '다시 찍기' }));
	expect(screen.getByRole('button', { name: '가짜 촬영' })).toBeInTheDocument();
});

test('드레이핑 2라운드로 고르면 색값과 고른 계절을 저장한다', async () => {
	renderPage();
	fireEvent.click(screen.getByRole('button', { name: '시작하기' }));
	fireEvent.click(screen.getByRole('button', { name: '가짜 촬영' }));
	fireEvent.click(await screen.findByRole('button', { name: '팔레트 비교하기' }));

	// 1순위가 여름 쿨이라 1라운드는 쿨(여름)을 먼저(A), 웜(봄)을 B로
	expect(screen.getByRole('heading', { name: /1 \/ 2/ })).toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: 'A가 더 잘 어울려요' }));
	// 2라운드는 쿨 안에서 밝음(여름) A, 깊음(겨울) B
	expect(screen.getByRole('heading', { name: /2 \/ 2/ })).toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: 'B가 더 잘 어울려요' }));

	expect(await screen.findByRole('heading', { name: '겨울 쿨로 저장했어요' })).toBeInTheDocument();
	expect(savePersonalColor).toHaveBeenCalledWith({ method: 'CAMERA', samples: PAYLOAD, chosen: 'WINTER_COOL' });
	expect(screen.getByText(/카메라 후보와 다르게 골랐어요/)).toBeInTheDocument();
});

test('판정이 애매하면(ordered=false) 순서를 고정하고 안내한다', async () => {
	estimateColor.mockResolvedValue({ ...ESTIMATE, ordered: false, confidence: 0.1 });
	renderPage();
	fireEvent.click(screen.getByRole('button', { name: '시작하기' }));
	fireEvent.click(screen.getByRole('button', { name: '가짜 촬영' }));
	fireEvent.click(await screen.findByRole('button', { name: '팔레트 비교하기' }));

	expect(screen.getByText('판정이 애매해서 직접 비교해 주세요.')).toBeInTheDocument();
});

test('카메라를 쓸 수 없으면 사진 고르기와 직접 고르기를 보여주고, 직접 고르면 MANUAL로 저장', async () => {
	renderPage();
	fireEvent.click(screen.getByRole('button', { name: '시작하기' }));
	fireEvent.click(screen.getByRole('button', { name: '카메라 거부' }));

	expect(screen.getByLabelText('갤러리에서 사진 고르기')).toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: '이미 알고 있는 퍼스널컬러 직접 고르기' }));
	fireEvent.change(screen.getByLabelText('퍼스널컬러'), { target: { value: 'SPRING_WARM' } });
	fireEvent.click(screen.getByRole('button', { name: '저장' }));

	expect(await screen.findByRole('heading', { name: '봄 웜으로 저장했어요' })).toBeInTheDocument();
	expect(savePersonalColor).toHaveBeenCalledWith({ method: 'MANUAL', chosen: 'SPRING_WARM' });
});

test('카메라 대신 사진을 직접 고르면 "사진으로 진단하기"로 안내한다', async () => {
	renderPage();
	fireEvent.click(screen.getByRole('button', { name: '시작하기' }));
	fireEvent.click(screen.getByRole('button', { name: '카메라 대신 사진 고르기' }));

	expect(screen.getByRole('heading', { name: '사진으로 진단하기' })).toBeInTheDocument();
});

test('저장이 실패하면 서버 메시지를 보여준다', async () => {
	const error = new Error('x');
	error.response = { status: 422, data: { code: 'PERSONAL_COLOR_LOW_QUALITY', message: '사진 상태 때문에 분석하기 어려워요.', traceId: 't' } };
	savePersonalColor.mockRejectedValue(error);
	renderPage();
	fireEvent.click(screen.getByRole('button', { name: '시작하기' }));
	fireEvent.click(screen.getByRole('button', { name: '가짜 촬영' }));
	fireEvent.click(await screen.findByRole('button', { name: '팔레트 비교하기' }));
	fireEvent.click(screen.getByRole('button', { name: 'A가 더 잘 어울려요' }));
	fireEvent.click(screen.getByRole('button', { name: 'A가 더 잘 어울려요' }));

	expect(await screen.findByRole('alert')).toHaveTextContent('사진 상태 때문에 분석하기 어려워요.');
});

test('라운드 순서: 1순위 톤·밝기를 먼저, ordered=false면 웜·밝음 먼저', () => {
	expect(firstRound('AUTUMN_WARM', true)).toEqual(['AUTUMN_WARM', 'WINTER_COOL']);
	expect(firstRound('WINTER_COOL', true)).toEqual(['WINTER_COOL', 'AUTUMN_WARM']);
	expect(firstRound('WINTER_COOL', false)).toEqual(['AUTUMN_WARM', 'WINTER_COOL']);
	expect(secondRound('SPRING_WARM', 'AUTUMN_WARM', true)).toEqual(['AUTUMN_WARM', 'SPRING_WARM']);
	expect(secondRound('SUMMER_COOL', 'AUTUMN_WARM', false)).toEqual(['SUMMER_COOL', 'WINTER_COOL']);
});

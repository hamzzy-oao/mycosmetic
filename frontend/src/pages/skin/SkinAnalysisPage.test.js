// user-event 13은 최상위 @testing-library/dom(10)을 써서 act로 감싸지지 않는다. RTL의 fireEvent를 쓴다
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { getSkinProfile, saveSkinProfile } from '../../api/skin';
import { analyzeSkin, getSkinAnalysisHistory, getSkinQuestions } from '../../api/skinAnalysis';
import useIsMobile from '../../hooks/useIsMobile';
import SkinAnalysisPage from './SkinAnalysisPage';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/skin', () => ({
	getSkinProfile: jest.fn(),
	saveSkinProfile: jest.fn(),
}));
jest.mock('../../api/skinAnalysis', () => ({
	getSkinQuestions: jest.fn(),
	analyzeSkin: jest.fn(),
	getSkinAnalysisHistory: jest.fn(),
}));
jest.mock('../../hooks/useIsMobile', () => ({
	__esModule: true,
	default: jest.fn(),
}));

const QUESTIONS = {
	ruleVersion: 'skin-v1',
	intro: '세안 후 15~30분 동안 아무것도 바르지 않은 상태를 떠올리며 답해 주세요.',
	questions: [
		{ id: 'selfType', text: '본인이 생각하는 피부 유형은?', optional: false, maxSelect: 1, showWhen: null, options: [
			{ code: 'DRY', label: '건성' }, { code: 'OILY', label: '지성' }, { code: 'COMBINATION', label: '복합성' },
			{ code: 'UNSURE', label: '잘 모르겠어요' }] },
		{ id: 'middayFeel', text: '보습제 없이 한낮이 되면 피부가 어떤가요?', optional: false, maxSelect: 1, showWhen: 'selfType=UNSURE', options: [
			{ code: 'ALL_TIGHT', label: '얼굴 전체가 당기고 건조해요' }, { code: 'CHEEK_TIGHT', label: '볼은 당기고 T존은 괜찮아요' },
			{ code: 'TZONE_OILY', label: '볼은 괜찮고 T존이 번들거려요' }, { code: 'ALL_OILY', label: '얼굴 전체가 번들거려요' }] },
		{ id: 'concerns', text: '현재 가장 고민되는 피부 상태는? (최대 3개)', optional: true, maxSelect: 3, showWhen: null, options: [
			{ code: 'DRYNESS', label: '건조함' }, { code: 'OILINESS', label: '유분' }, { code: 'PORES', label: '모공' },
			{ code: 'WRINKLES', label: '주름' }, { code: 'PIGMENTATION', label: '색소침착' }, { code: 'SENSITIVITY', label: '민감함' },
			{ code: 'ACNE', label: '여드름' }] },
	],
};

const RESULT = {
	baseType: 'COMBINATION',
	tags: ['SENSITIVE'],
	concerns: ['PORES', 'SENSITIVITY'],
	reasons: ['본인이 고른 복합성 피부로 정했어요.', '민감함이 고민이라고 답해서 민감성을 붙였어요.'],
	suggestSensitive: false,
	candidateIngredients: [],
	ruleVersion: 'skin-v1',
	createdAt: '2026-10-23T10:00:00Z',
};

function renderPage() {
	return render(
		<MemoryRouter future={FUTURE}>
			<SkinAnalysisPage />
		</MemoryRouter>
	);
}

beforeEach(() => {
	jest.clearAllMocks();
	useIsMobile.mockReturnValue(true);
	getSkinQuestions.mockResolvedValue(QUESTIONS);
	getSkinAnalysisHistory.mockResolvedValue({ items: [], changes: [] });
	analyzeSkin.mockResolvedValue(RESULT);
});

async function start() {
	renderPage();
	fireEvent.click(await screen.findByRole('button', { name: '시작하기' }));
}

test('첫 화면에 안내 문구, 시작하면 Q1 네 선택지', async () => {
	renderPage();
	expect(await screen.findByText(/세안 후 15~30분/)).toBeInTheDocument();

	fireEvent.click(screen.getByRole('button', { name: '시작하기' }));

	expect(screen.getByRole('radiogroup', { name: '본인이 생각하는 피부 유형은?' })).toBeInTheDocument();
	expect(screen.getAllByRole('radio')).toHaveLength(4);
});

test('잘 모르겠어요를 고르면 한낮 문항이 나오고, 다른 타입이면 바로 고민 문항', async () => {
	await start();
	fireEvent.click(screen.getByRole('radio', { name: '잘 모르겠어요' }));
	fireEvent.click(screen.getByRole('button', { name: '다음' }));
	expect(screen.getByRole('radiogroup', { name: '보습제 없이 한낮이 되면 피부가 어떤가요?' })).toBeInTheDocument();

	fireEvent.click(screen.getByRole('button', { name: '이전' }));
	fireEvent.click(screen.getByRole('radio', { name: '지성' }));
	fireEvent.click(screen.getByRole('button', { name: '다음' }));
	expect(screen.getByRole('group', { name: '현재 가장 고민되는 피부 상태는? (최대 3개)' })).toBeInTheDocument();
});

test('답을 고르기 전에는 다음으로 갈 수 없다', async () => {
	await start();
	expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
});

test('고민은 3개까지만 고를 수 있다', async () => {
	await start();
	fireEvent.click(screen.getByRole('radio', { name: '건성' }));
	fireEvent.click(screen.getByRole('button', { name: '다음' }));

	fireEvent.click(screen.getByRole('button', { name: '건조함' }));
	fireEvent.click(screen.getByRole('button', { name: '모공' }));
	fireEvent.click(screen.getByRole('button', { name: '주름' }));

	expect(screen.getByRole('button', { name: '여드름' })).toBeDisabled();
	expect(screen.getByText('최대 3개까지 고를 수 있어요.')).toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: '주름' }));
	expect(screen.getByRole('button', { name: '여드름' })).toBeEnabled();
});

test('제출하면 답을 보내고 결과 카드를 보여준다', async () => {
	await start();
	fireEvent.click(screen.getByRole('radio', { name: '잘 모르겠어요' }));
	fireEvent.click(screen.getByRole('button', { name: '다음' }));
	fireEvent.click(screen.getByRole('radio', { name: '볼은 괜찮고 T존이 번들거려요' }));
	fireEvent.click(screen.getByRole('button', { name: '다음' }));
	fireEvent.click(screen.getByRole('button', { name: '모공' }));
	fireEvent.click(screen.getByRole('button', { name: '민감함' }));
	fireEvent.click(screen.getByRole('button', { name: '결과 보기' }));

	expect(await screen.findByRole('heading', { name: '복합성 · 민감성' })).toBeInTheDocument();
	expect(analyzeSkin).toHaveBeenCalledWith({ selfType: 'UNSURE', middayFeel: 'TZONE_OILY', concerns: ['PORES', 'SENSITIVITY'] });
	expect(screen.getByText('민감함이 고민이라고 답해서 민감성을 붙였어요.')).toBeInTheDocument();
	expect(screen.getByText('참고용 분석이며 의학적 판단이 아닙니다.')).toBeInTheDocument();
	expect(screen.getByText(/모공/)).toBeInTheDocument();
});

test('잘 모르겠어요가 아니면 한낮 답을 보내지 않는다', async () => {
	await start();
	fireEvent.click(screen.getByRole('radio', { name: '건성' }));
	fireEvent.click(screen.getByRole('button', { name: '다음' }));
	fireEvent.click(screen.getByRole('button', { name: '결과 보기' }));

	await screen.findByRole('heading', { name: '복합성 · 민감성' });
	expect(analyzeSkin).toHaveBeenCalledWith({ selfType: 'DRY', middayFeel: null, concerns: [] });
});

test('민감성 제안을 받아들이면 지금 타입에 민감성을 더해 저장한다', async () => {
	analyzeSkin.mockResolvedValue({ ...RESULT, baseType: 'DRY', tags: [], concerns: [], suggestSensitive: true,
		candidateIngredients: ['리모넨'] });
	getSkinProfile.mockResolvedValue({ skinTypes: ['DRY'], personalColor: 'SPRING_WARM' });
	saveSkinProfile.mockResolvedValue({ skinTypes: ['SENSITIVE', 'DRY'], personalColor: 'SPRING_WARM' });
	await start();
	fireEvent.click(screen.getByRole('radio', { name: '건성' }));
	fireEvent.click(screen.getByRole('button', { name: '다음' }));
	fireEvent.click(screen.getByRole('button', { name: '결과 보기' }));

	expect(await screen.findByText('리모넨')).toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: '민감성 추가하기' }));

	expect(await screen.findByRole('status')).toHaveTextContent('민감성을 추가했어요.');
	expect(saveSkinProfile).toHaveBeenCalledWith(['SENSITIVE', 'DRY'], 'SPRING_WARM');
	expect(screen.getByRole('heading', { name: '건성 · 민감성' })).toBeInTheDocument();
});

test('분석이 실패하면 서버 메시지를 보여준다', async () => {
	const error = new Error('INVALID_SKIN_ANSWERS');
	error.response = { status: 400, data: { code: 'INVALID_SKIN_ANSWERS', message: '답변을 다시 확인해 주세요.', traceId: 't' } };
	analyzeSkin.mockRejectedValue(error);
	await start();
	fireEvent.click(screen.getByRole('radio', { name: '건성' }));
	fireEvent.click(screen.getByRole('button', { name: '다음' }));
	fireEvent.click(screen.getByRole('button', { name: '결과 보기' }));

	expect(await screen.findByRole('alert')).toHaveTextContent('답변을 다시 확인해 주세요.');
});

test('결과 화면에서도 방금 분석이 반영된 이력과 달라진 점을 보여준다', async () => {
	getSkinAnalysisHistory
		.mockResolvedValueOnce({ items: [], changes: [] })
		.mockResolvedValueOnce({
			items: [
				{ id: 2, baseType: 'COMBINATION', tags: ['SENSITIVE'], concerns: [], ruleVersion: 'skin-v1', createdAt: '2026-10-23T10:00:00Z' },
				{ id: 1, baseType: 'DRY', tags: [], concerns: [], ruleVersion: 'skin-v1', createdAt: '2026-10-20T10:00:00Z' },
			],
			changes: ['피부 타입이 건성에서 복합성으로 바뀌었어요.'],
		});
	await start();
	fireEvent.click(screen.getByRole('radio', { name: '복합성' }));
	fireEvent.click(screen.getByRole('button', { name: '다음' }));
	fireEvent.click(screen.getByRole('button', { name: '결과 보기' }));

	expect(await screen.findByText('피부 타입이 건성에서 복합성으로 바뀌었어요.')).toBeInTheDocument();
	expect(screen.getByRole('heading', { name: '최근 분석' })).toBeInTheDocument();
});

test('첫 화면에 최근 분석 이력과 달라진 점을 보여준다', async () => {
	getSkinAnalysisHistory.mockResolvedValue({
		items: [
			{ id: 2, baseType: 'DEHYDRATED_OILY', tags: [], concerns: ['DRYNESS'], ruleVersion: 'skin-v1', createdAt: '2026-10-23T10:00:00Z' },
			{ id: 1, baseType: 'COMBINATION', tags: ['ACNE_PRONE'], concerns: [], ruleVersion: 'skin-v1', createdAt: '2026-10-20T10:00:00Z' },
		],
		changes: ['피부 타입이 복합성에서 수부지로 바뀌었어요.'],
	});
	renderPage();

	expect(await screen.findByText('피부 타입이 복합성에서 수부지로 바뀌었어요.')).toBeInTheDocument();
	expect(screen.getByText('수부지')).toBeInTheDocument();
	expect(screen.getByText('복합성 · 트러블성')).toBeInTheDocument();
});

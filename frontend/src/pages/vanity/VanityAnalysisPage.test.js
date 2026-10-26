// user-event 13은 최상위 @testing-library/dom(10)을 써서 act로 감싸지지 않는다. RTL의 fireEvent를 쓴다
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { saveRoutinesFromDiagnosis } from '../../api/routines';
import { updateShelfItem } from '../../api/shelf';
import { getVanityAnalysis } from '../../api/vanity';
import useIsMobile from '../../hooks/useIsMobile';
import VanityAnalysisPage from './VanityAnalysisPage';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/vanity', () => ({ getVanityAnalysis: jest.fn() }));
jest.mock('../../api/routines', () => ({ saveRoutinesFromDiagnosis: jest.fn() }));
jest.mock('../../api/shelf', () => ({ updateShelfItem: jest.fn() }));
jest.mock('../../hooks/useIsMobile', () => ({ __esModule: true, default: jest.fn() }));

const ITEMS = [
	{ shelfItemId: 1, brand: 'A', name: '약산성 클렌징폼', category: 'SKINCARE', verdict: 'GOOD', stepType: 'CLEANSER', makeupType: null, guessed: true, shadeHex: null },
	{ shelfItemId: 2, brand: 'B', name: '세라 크림', category: 'SKINCARE', verdict: 'UNRATED', stepType: 'CREAM', makeupType: null, guessed: false, shadeHex: null },
	{ shelfItemId: 3, brand: 'C', name: '나이트 세럼', category: 'SKINCARE', verdict: 'UNRATED', stepType: 'SERUM', makeupType: null, guessed: true, shadeHex: null },
	{ shelfItemId: 4, brand: 'D', name: '수분 크림 2', category: 'SKINCARE', verdict: 'NEUTRAL', stepType: 'CREAM', makeupType: null, guessed: true, shadeHex: null },
	{ shelfItemId: 5, brand: 'E', name: '멀티 밤', category: 'SKINCARE', verdict: 'UNRATED', stepType: null, makeupType: null, guessed: false, shadeHex: null },
];

const ANALYSIS = {
	routineDraft: {
		morning: [{ shelfItemId: 1, stepType: 'CLEANSER', guessed: true }, { shelfItemId: 2, stepType: 'CREAM', guessed: false }],
		evening: [{ shelfItemId: 1, stepType: 'CLEANSER', guessed: true }, { shelfItemId: 3, stepType: 'SERUM', guessed: true }, { shelfItemId: 2, stepType: 'CREAM', guessed: false }],
	},
	warnings: [{ level: 'CAUTION', code: 'AHA_BHA', message: 'AHA와 BHA를 함께 쓰면 자극이 생길 수 있어요.', items: [3] }],
	gaps: [{ role: 'SUNSCREEN', level: 'ESSENTIAL', message: '아침 루틴에 선크림이 없어요. 가지고 있다면 등록해 주세요.', ingredientHints: ['자외선 차단 제품'], contentId: 'tip-sunscreen' }],
	alternates: [{ stepType: 'CREAM', shelfItemIds: [4] }],
	needsType: [5],
	items: ITEMS,
	looks: [],
	needsPersonalColor: false,
	shadeGaps: [],
};

function renderPage() {
	return render(
		<MemoryRouter initialEntries={['/vanity/analysis']} future={FUTURE}>
			<Routes>
				<Route path="/vanity/analysis" element={<VanityAnalysisPage />} />
				<Route path="/routines" element={<p>루틴 목록</p>} />
			</Routes>
		</MemoryRouter>
	);
}

beforeEach(() => {
	jest.clearAllMocks();
	useIsMobile.mockReturnValue(true);
	getVanityAnalysis.mockResolvedValue(ANALYSIS);
});

test('아침·저녁 초안을 단계 순서대로, 추정한 단계에는 "추정"을 붙여 보여준다', async () => {
	renderPage();

	const morning = await screen.findByRole('region', { name: '아침 루틴' });
	const items = within(morning).getAllByRole('listitem');
	expect(items).toHaveLength(2);
	expect(items[0]).toHaveTextContent('클렌저');
	expect(items[0]).toHaveTextContent('약산성 클렌징폼');
	expect(items[0]).toHaveTextContent('추정');
	expect(items[1]).not.toHaveTextContent('추정');
	expect(within(screen.getByRole('region', { name: '저녁 루틴' })).getAllByRole('listitem')).toHaveLength(3);
});

test('빈 자리 카드에 안내·성분·사용법을, 선택 단계는 없어도 된다고 보여준다', async () => {
	renderPage();

	expect(await screen.findByText('아침 루틴에 선크림이 없어요. 가지고 있다면 등록해 주세요.')).toBeInTheDocument();
	expect(screen.getByText('찾아볼 것: 자외선 차단 제품')).toBeInTheDocument();
	expect(screen.getByText('선크림은 아침 루틴의 마지막 단계예요')).toBeInTheDocument();
	expect(screen.getByText('토너·에센스·세럼은 없어도 괜찮아요.')).toBeInTheDocument();
});

test('경고와 교대 후보를 보여준다', async () => {
	renderPage();

	expect(await screen.findByText('AHA와 BHA를 함께 쓰면 자극이 생길 수 있어요.')).toBeInTheDocument();
	expect(screen.getByText(/교대로 쓸 수 있어요/)).toHaveTextContent('수분 크림 2');
});

test('단계를 모르는 제품에서 단계를 고르면 저장하고 분석을 다시 부른다', async () => {
	updateShelfItem.mockResolvedValue({});
	renderPage();

	const select = await screen.findByLabelText('멀티 밤 단계');
	fireEvent.change(select, { target: { value: 'CREAM' } });

	expect(updateShelfItem).toHaveBeenCalledWith(5, { stepType: 'CREAM' });
	await screen.findAllByRole('region', { name: '아침 루틴' });
	expect(getVanityAnalysis).toHaveBeenCalledTimes(2);
});

test('루틴으로 저장하면 서버에 한 번 저장을 맡기고 루틴 목록으로 간다', async () => {
	saveRoutinesFromDiagnosis.mockResolvedValue([{ id: 10 }, { id: 11 }]);
	renderPage();

	fireEvent.click(await screen.findByRole('button', { name: '루틴으로 저장' }));

	expect(await screen.findByText('루틴 목록')).toBeInTheDocument();
	expect(saveRoutinesFromDiagnosis).toHaveBeenCalledTimes(1);
});

test('저장에 실패하면 서버 메시지를 보여주고 다시 누를 수 있다', async () => {
	const error = new Error('x');
	error.response = { status: 400, data: { code: 'INVALID_REQUEST', message: '요청이 올바르지 않아요.', traceId: 't' } };
	saveRoutinesFromDiagnosis.mockRejectedValue(error);
	renderPage();

	fireEvent.click(await screen.findByRole('button', { name: '루틴으로 저장' }));

	expect(await screen.findByRole('alert')).toHaveTextContent('요청이 올바르지 않아요.');
	expect(screen.getByRole('button', { name: '루틴으로 저장' })).toBeEnabled();
});

test('화장대가 비어 있으면 등록을 권하고 루틴 만들기 버튼이 없다', async () => {
	getVanityAnalysis.mockResolvedValue({ ...ANALYSIS, routineDraft: { morning: [], evening: [] }, warnings: [], alternates: [], needsType: [], items: [] });
	renderPage();

	expect(await screen.findByText(/내 화장대에 사용 중인 제품을 등록하면/)).toBeInTheDocument();
	expect(screen.queryByRole('button', { name: '루틴으로 저장' })).not.toBeInTheDocument();
});

test('불러오기에 실패하면 서버 메시지를 보여준다', async () => {
	const error = new Error('x');
	error.response = { status: 500, data: { code: 'INTERNAL_ERROR', message: '잠시 문제가 생겼어요.', traceId: 't' } };
	getVanityAnalysis.mockRejectedValue(error);
	renderPage();

	expect(await screen.findByRole('alert')).toHaveTextContent('잠시 문제가 생겼어요.');
});

test('마스크·기타는 매일 루틴 대신 따로 보여 주고, 색조 항목의 종류 고르기는 스킨케어 탭에 없다', async () => {
	getVanityAnalysis.mockResolvedValue({
		...ANALYSIS,
		needsType: [5, 7],
		items: [...ITEMS,
			{ shelfItemId: 6, brand: 'F', name: '수딩 마스크팩', category: 'SKINCARE', verdict: 'UNRATED', stepType: 'MASK', makeupType: null, guessed: true, shadeHex: null },
			{ shelfItemId: 7, brand: 'G', name: '멀티 스틱', category: 'MAKEUP', verdict: 'UNRATED', stepType: null, makeupType: null, guessed: false, shadeHex: null }],
	});
	renderPage();

	expect(await screen.findByText(/매일 루틴에 넣지 않은 제품/)).toHaveTextContent('수딩 마스크팩');
	expect(screen.getByLabelText('멀티 밤 단계')).toBeInTheDocument();
	expect(screen.queryByLabelText('멀티 스틱 종류')).not.toBeInTheDocument();
});

const MAKEUP = {
	...ANALYSIS,
	needsType: [12],
	items: [
		{ shelfItemId: 10, brand: 'L', name: '코랄 립스틱', category: 'MAKEUP', verdict: 'GOOD', stepType: null, makeupType: 'LIP', guessed: true, shadeHex: '#D9A08C' },
		{ shelfItemId: 11, brand: 'C', name: '피치 블러셔', category: 'MAKEUP', verdict: 'UNRATED', stepType: null, makeupType: 'CHEEK', guessed: true, shadeHex: '#E8B9A0' },
		{ shelfItemId: 12, brand: 'M', name: '멀티 스틱', category: 'MAKEUP', verdict: 'UNRATED', stepType: null, makeupType: null, guessed: false, shadeHex: '#C08070' },
	],
	looks: [{ kind: 'DAILY', shelfItemIds: [10, 11], score: 7, reasons: ['모두 부드러운 색이라 매일 쓰기 좋아요.'] }],
	needsPersonalColor: false,
	shadeGaps: [{ makeupType: 'EYE', season: 'AUTUMN_WARM', hexes: ['#A0522D', '#8B4513'] }, { makeupType: 'LIP', season: 'AUTUMN_WARM', hexes: ['#A0522D'] }],
};

test('색조 탭: 조합 카드, 색 범위, 종류 고르기', async () => {
	getVanityAnalysis.mockResolvedValue(MAKEUP);
	updateShelfItem.mockResolvedValue({});
	renderPage();
	fireEvent.click(await screen.findByRole('tab', { name: '색조' }));

	expect(screen.getByRole('heading', { name: '데일리' })).toBeInTheDocument();
	expect(screen.getByText('립 · L 코랄 립스틱')).toBeInTheDocument();
	expect(screen.getByText('모두 부드러운 색이라 매일 쓰기 좋아요.')).toBeInTheDocument();
	expect(screen.getByText('가을 웜 섀도가 있으면 조합을 더 만들 수 있어요.')).toBeInTheDocument();
	expect(screen.getByText('가을 웜 립이 있으면 조합을 더 만들 수 있어요.')).toBeInTheDocument();

	fireEvent.change(screen.getByLabelText('멀티 스틱 종류'), { target: { value: 'LIP' } });
	expect(updateShelfItem).toHaveBeenCalledWith(12, { makeupType: 'LIP' });
});

test('색조 탭: 퍼스널컬러가 없으면 진단으로 안내하고, 조합이 없으면 색을 넣으라고 한다', async () => {
	getVanityAnalysis.mockResolvedValue({ ...MAKEUP, looks: [], needsPersonalColor: true, shadeGaps: [] });
	renderPage();
	fireEvent.click(await screen.findByRole('tab', { name: '색조' }));

	expect(screen.getByRole('link', { name: '퍼스널 컬러 진단' })).toHaveAttribute('href', '/personal-color');
	expect(screen.getByText(/두 종류 이상에 내 호수 색을 넣으면/)).toBeInTheDocument();
});

test('이름으로 추정한 단계는 확인하고 바꿀 수 있다', async () => {
	updateShelfItem.mockResolvedValue({});
	renderPage();

	const select = await screen.findByLabelText('약산성 클렌징폼 단계');
	expect(select).toHaveValue('CLEANSER');
	fireEvent.change(select, { target: { value: 'TONER' } });
	expect(updateShelfItem).toHaveBeenCalledWith(1, { stepType: 'TONER' });
});

test('스킨케어·색조 탭을 오간다', async () => {
	renderPage();
	await screen.findByRole('region', { name: '아침 루틴' });

	fireEvent.click(screen.getByRole('tab', { name: '색조' }));
	expect(screen.getByRole('tab', { name: '색조' })).toHaveAttribute('aria-selected', 'true');
	expect(screen.queryByRole('region', { name: '아침 루틴' })).not.toBeInTheDocument();
});

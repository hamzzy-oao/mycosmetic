import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useAuth } from '../../AuthContext';
import { api } from '../../api/client';
import AdminReviewPage from './AdminReviewPage';

jest.mock('../../AuthContext', () => ({ useAuth: jest.fn() }));
jest.mock('../../api/client', () => ({
	api: { get: jest.fn(), post: jest.fn(), put: jest.fn(), delete: jest.fn() },
}));

const COUNTS = {
	NEW_USER_PRODUCT: 0, NAME_FORMAT: 1, BRAND_VARIANT: 0, DUPLICATE_SUSPECT: 1,
	INGREDIENT_SPACING: 0, INGREDIENT_NOT_LIST: 0, INGREDIENT_UNMATCHED: 0,
};
const NAME_ISSUE = {
	productId: 108, brand: '일리윤', name: '일리윤 탑투토 워시 500ml', volume: null, category: 'SKINCARE',
	type: 'NAME_FORMAT', reasons: ['제품명 앞에 브랜드가 또 있어요'], examples: ['일리윤 탑투토 워시 500ml → 탑투토 워시'],
	suggestion: { brand: null, name: '탑투토 워시', volume: '500ml' }, signature: 'sig-1',
};
const DUP_ISSUE = {
	productId: 42, brand: '설화수', name: '진설크림', volume: '60ml', category: 'SKINCARE',
	type: 'DUPLICATE_SUSPECT', reasons: ['이름이 거의 같은 제품이 2개 있어요'], examples: [],
	suggestion: null, signature: '42,43',
	members: [
		{ productId: 42, brand: '설화수', name: '진설크림', volume: '60ml', createdAt: '2026-10-20T01:00:00Z', shelfCount: 3, ingredientLines: 40 },
		{ productId: 43, brand: '설화수', name: '진설 크림', volume: null, createdAt: '2026-10-23T01:00:00Z', shelfCount: 0, ingredientLines: 4 },
	],
};
const REVIEW = { checkedProducts: 140, counts: COUNTS, spacingLines: 106, dismissed: 0, issues: [NAME_ISSUE, DUP_ISSUE] };

function renderPage() {
	return render(
		<MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
			<AdminReviewPage />
		</MemoryRouter>
	);
}

beforeEach(() => {
	jest.resetAllMocks();
	useAuth.mockReturnValue({ user: { role: 'ADMIN' } });
	api.get.mockResolvedValue({ data: REVIEW });
});

test('관리자가 아니면 점검함을 보여주지 않는다', () => {
	useAuth.mockReturnValue({ user: { role: 'USER' } });
	renderPage();
	expect(screen.getByText('접근 권한이 없습니다.')).toBeInTheDocument();
	expect(api.get).not.toHaveBeenCalled();
});

test('요약, 띄어쓰기 줄 수, 종류별 개수와 첫 종류의 문제를 보여준다', async () => {
	renderPage();

	expect(await screen.findByText('2개')).toBeInTheDocument();
	expect(screen.getByText('106줄')).toBeInTheDocument();
	// 첫 탭은 새로 등록된 제품. 비었으면 안내만 보인다
	expect(screen.getByRole('button', { name: '새로 등록된 제품 0' })).toHaveAttribute('aria-pressed', 'true');
	expect(screen.getByText('이 종류는 확인할 게 없어요.')).toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: '제품명 표기 1' }));
	expect(screen.getByText('일리윤 탑투토 워시 500ml')).toBeInTheDocument();
	expect(screen.getByText('제안: 제품명 "탑투토 워시", 용량 "500ml"')).toBeInTheDocument();
	expect(screen.queryByText('진설 크림')).not.toBeInTheDocument();
});

test('중복 의심은 두 제품을 나란히 보여 주고, 고른 제품만 삭제한다', async () => {
	jest.spyOn(window, 'confirm').mockReturnValue(true);
	api.delete.mockResolvedValue({});
	renderPage();

	fireEvent.click(await screen.findByRole('button', { name: '중복 의심 1' }));
	expect(screen.getByText('진설크림')).toBeInTheDocument();
	expect(screen.getByText('진설 크림')).toBeInTheDocument();
	expect(screen.getByText('3개')).toBeInTheDocument();
	expect(screen.getByText('4줄')).toBeInTheDocument();
	expect(screen.getByRole('button', { name: '둘 다 그대로 두기' })).toBeInTheDocument();
	expect(screen.queryByRole('button', { name: '제안대로 고치기' })).not.toBeInTheDocument();

	fireEvent.click(screen.getByRole('button', { name: '#43 진설 크림 삭제' }));

	await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/api/admin/products/43'));
	expect(api.delete).toHaveBeenCalledTimes(1);
	expect(await screen.findByRole('status')).toHaveTextContent('#43 진설 크림을(를) 삭제했어요.');
});

test('둘 다 그대로 두기는 묶음 signature로 숨긴다', async () => {
	api.post.mockResolvedValue({});
	renderPage();

	fireEvent.click(await screen.findByRole('button', { name: '중복 의심 1' }));
	fireEvent.click(screen.getByRole('button', { name: '둘 다 그대로 두기' }));

	await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/admin/review/dismissals', {
		productId: 42, type: 'DUPLICATE_SUSPECT', signature: '42,43',
	}));
	expect(await screen.findByRole('status')).toHaveTextContent('모두 그대로 두고 점검함에서 숨겼어요.');
});

test('제안대로 고치기는 제안에 없는 값을 지금 값으로 채워 제품 정보를 바꾸고 다시 불러온다', async () => {
	api.put.mockResolvedValue({ data: {} });
	renderPage();

	fireEvent.click(await screen.findByRole('button', { name: '제품명 표기 1' }));
	fireEvent.click(screen.getByRole('button', { name: '제안대로 고치기' }));

	await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/admin/products/108', {
		brand: '일리윤', name: '탑투토 워시', volume: '500ml', category: 'SKINCARE',
	}));
	expect(await screen.findByRole('status')).toHaveTextContent('고쳤어요');
	expect(api.get).toHaveBeenCalledTimes(2);
});

test('문제없음 · 숨기기는 받은 signature를 그대로 보낸다', async () => {
	api.post.mockResolvedValue({});
	renderPage();

	// 제품명 표기 탭에는 이 문제 하나만 있다
	fireEvent.click(await screen.findByRole('button', { name: '제품명 표기 1' }));
	fireEvent.click(screen.getByRole('button', { name: '문제없음 · 숨기기' }));

	await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/admin/review/dismissals', {
		productId: 108, type: 'NAME_FORMAT', signature: 'sig-1',
	}));
});

test('띄어쓰기 모두 고치기는 결과를 알려준다', async () => {
	api.post.mockResolvedValue({ data: { lists: 12, items: 106, skipped: 0 } });
	renderPage();

	fireEvent.click(await screen.findByRole('button', { name: '띄어쓰기 모두 고치기' }));

	expect(await screen.findByRole('status')).toHaveTextContent('성분 버전 12개에서 106줄을 사전에 맞췄어요.');
	expect(api.post).toHaveBeenCalledWith('/api/admin/review/fix-spacing');
});

test('불러오지 못하면 서버 메시지를 보여준다', async () => {
	api.get.mockRejectedValue({ response: { data: { code: 'FORBIDDEN', message: '이 요청을 처리할 수 없어요.' } } });
	renderPage();
	expect(await screen.findByRole('alert')).toHaveTextContent('이 요청을 처리할 수 없어요.');
});

test('새로 등록된 제품은 확인 완료로 검수한다', async () => {
	const fresh = {
		productId: 77, brand: '새브랜드', name: '수분 크림', volume: null, category: 'SKINCARE', type: 'NEW_USER_PRODUCT',
		reasons: ['새로 등록된 제품이에요(사용자가 직접 입력함). 브랜드·제품명·성분이 맞는지 확인해 주세요'],
		examples: [], suggestion: null, signature: 'unverified',
	};
	api.get.mockResolvedValue({ data: { ...REVIEW, counts: { ...COUNTS, NEW_USER_PRODUCT: 1 }, issues: [fresh, ...REVIEW.issues] } });
	api.post.mockResolvedValue({});
	renderPage();

	expect(await screen.findByText('수분 크림')).toBeInTheDocument();
	expect(screen.queryByRole('button', { name: '문제없음 · 숨기기' })).not.toBeInTheDocument();
	fireEvent.click(screen.getByRole('button', { name: '확인 완료' }));

	await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/admin/review/products/77/verify'));
	expect(await screen.findByRole('status')).toHaveTextContent('새브랜드 수분 크림을(를) 확인했어요.');
});

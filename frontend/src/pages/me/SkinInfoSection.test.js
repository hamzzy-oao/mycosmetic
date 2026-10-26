// user-event 13은 최상위 @testing-library/dom(10)을 써서 act로 감싸지지 않는다. RTL의 fireEvent를 쓴다
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { getSkinProfile, saveSkinProfile } from '../../api/skin';
import SkinInfoSection from './SkinInfoSection';

const FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true };

jest.mock('../../api/skin', () => ({
	getSkinProfile: jest.fn(),
	saveSkinProfile: jest.fn(),
}));

function renderSection() {
	return render(
		<MemoryRouter future={FUTURE}>
			<SkinInfoSection />
		</MemoryRouter>
	);
}

beforeEach(() => {
	jest.clearAllMocks();
});

test('저장된 피부 타입이 선택된 칩으로 보인다', async () => {
	getSkinProfile.mockResolvedValue({ skinTypes: ['DRY'] });
	renderSection();

	expect(await screen.findByRole('button', { name: '건성' })).toHaveAttribute('aria-pressed', 'true');
	expect(screen.getByRole('button', { name: '지성' })).toHaveAttribute('aria-pressed', 'false');
	expect(screen.getByRole('link', { name: '내 피부 분석' })).toHaveAttribute('href', '/skin');
});

test('기본 타입은 하나만, 태그는 함께 고르고 저장하면 PUT', async () => {
	getSkinProfile.mockResolvedValue({ skinTypes: [] });
	saveSkinProfile.mockResolvedValue({ skinTypes: ['SENSITIVE', 'ACNE_PRONE', 'COMBINATION'] });
	renderSection();

	fireEvent.click(await screen.findByRole('button', { name: '건성' }));
	fireEvent.click(screen.getByRole('button', { name: '민감성' }));
	fireEvent.click(screen.getByRole('button', { name: '트러블성' }));
	fireEvent.click(screen.getByRole('button', { name: '복합성' }));
	expect(screen.getByRole('button', { name: '건성' })).toHaveAttribute('aria-pressed', 'false');
	fireEvent.click(screen.getByRole('button', { name: '피부 정보 저장' }));

	expect(await screen.findByRole('status')).toHaveTextContent('피부 정보를 저장했어요.');
	expect(saveSkinProfile).toHaveBeenCalledWith(['SENSITIVE', 'ACNE_PRONE', 'COMBINATION'], null);
	expect(screen.getByRole('button', { name: '복합성' })).toHaveAttribute('aria-pressed', 'true');
});

test('수부지를 고를 수 있고, 태그만 고르면 저장할 수 없다', async () => {
	getSkinProfile.mockResolvedValue({ skinTypes: [] });
	renderSection();

	fireEvent.click(await screen.findByRole('button', { name: '민감성' }));
	expect(screen.getByRole('button', { name: '피부 정보 저장' })).toBeDisabled();
	expect(screen.getByText('건성·지성·복합성·수부지 중 하나를 골라 주세요.')).toBeInTheDocument();

	fireEvent.click(screen.getByRole('button', { name: '수부지' }));
	expect(screen.getByRole('button', { name: '피부 정보 저장' })).toBeEnabled();
});

test('저장이 실패하면 서버 메시지를 보여준다', async () => {
	getSkinProfile.mockResolvedValue({ skinTypes: [] });
	const error = new Error('INVALID_REQUEST');
	error.response = { status: 400, data: { code: 'INVALID_REQUEST', message: '요청 내용을 확인해 주세요.', traceId: 't' } };
	saveSkinProfile.mockRejectedValue(error);
	renderSection();

	fireEvent.click(await screen.findByRole('button', { name: '피부 정보 저장' }));

	expect(await screen.findByRole('alert')).toHaveTextContent('요청 내용을 확인해 주세요.');
});

test('퍼스널컬러: 저장된 값을 보여주고, 바꾸면 피부 타입과 함께 PUT, 선택 안 함은 null', async () => {
	getSkinProfile.mockResolvedValue({ skinTypes: ['DRY'], personalColor: 'SUMMER_COOL' });
	saveSkinProfile.mockResolvedValueOnce({ skinTypes: ['DRY'], personalColor: 'AUTUMN_WARM' });
	renderSection();

	const select = await screen.findByLabelText('퍼스널컬러');
	expect(select).toHaveValue('SUMMER_COOL');
	expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual([
		'선택 안 함',
		'봄 웜',
		'여름 쿨',
		'가을 웜',
		'겨울 쿨',
	]);

	fireEvent.change(select, { target: { value: 'AUTUMN_WARM' } });
	fireEvent.click(screen.getByRole('button', { name: '피부 정보 저장' }));
	expect(await screen.findByRole('status')).toHaveTextContent('피부 정보를 저장했어요.');
	expect(saveSkinProfile).toHaveBeenLastCalledWith(['DRY'], 'AUTUMN_WARM');

	saveSkinProfile.mockResolvedValueOnce({ skinTypes: ['DRY'], personalColor: null });
	fireEvent.change(select, { target: { value: '' } });
	fireEvent.click(screen.getByRole('button', { name: '피부 정보 저장' }));
	expect(await screen.findByRole('status')).toHaveTextContent('피부 정보를 저장했어요.');
	expect(saveSkinProfile).toHaveBeenLastCalledWith(['DRY'], null);
});

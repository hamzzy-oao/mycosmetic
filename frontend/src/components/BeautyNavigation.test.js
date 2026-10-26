import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import BeautyNavigation, { activeGroup } from './BeautyNavigation';

function renderMenu(path = '/') {
	return render(
		<MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
			<BeautyNavigation />
		</MemoryRouter>
	);
}

test('세 카테고리는 대표 화면으로 바로 가는 링크다', () => {
	renderMenu();

	expect(screen.getByRole('link', { name: 'MY VANITY' })).toHaveAttribute('href', '/shelf');
	expect(screen.getByRole('link', { name: 'PERSONAL' })).toHaveAttribute('href', '/skin');
	expect(screen.getByRole('link', { name: 'ROUTINE' })).toHaveAttribute('href', '/routines');
	expect(screen.queryByRole('button')).not.toBeInTheDocument();
});

test('카테고리 안의 어느 화면에 있어도 그 카테고리가 선택 상태다', () => {
	renderMenu('/personal-color');

	expect(screen.getByRole('link', { name: 'PERSONAL' })).toHaveAttribute('aria-current', 'page');
	expect(screen.getByRole('link', { name: 'MY VANITY' })).not.toHaveAttribute('aria-current');
});

test('activeGroup: 화면 주소로 카테고리를 찾는다', () => {
	expect(activeGroup('/vanity/analysis')).toBe('personal');
	expect(activeGroup('/skin')).toBe('personal');
	expect(activeGroup('/compare')).toBe('vanity');
	expect(activeGroup('/routines/12')).toBe('routine');
	expect(activeGroup('/me')).toBeNull();
});

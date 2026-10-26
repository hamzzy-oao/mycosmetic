import { render, screen } from '@testing-library/react';
import FieldMessage, { fieldInputProps } from './FieldMessage';

test('오류는 role=alert, 나머지는 aria-live=polite로 읽힌다', () => {
	const { rerender } = render(<FieldMessage id="m" tone="error">틀렸어요</FieldMessage>);
	expect(screen.getByRole('alert')).toHaveTextContent('틀렸어요');

	rerender(<FieldMessage id="m" tone="ok">좋아요</FieldMessage>);
	expect(screen.queryByRole('alert')).not.toBeInTheDocument();
	const ok = screen.getByText('좋아요');
	expect(ok).toHaveAttribute('aria-live', 'polite');
	expect(ok).toHaveClass('field-message-ok');
});

test('입력칸 속성: 오류면 aria-invalid, 테두리 클래스는 안내 종류를 따른다', () => {
	expect(fieldInputProps({ tone: 'error', text: 'x' })).toEqual({ className: 'field-input field-input-error', 'aria-invalid': 'true' });
	expect(fieldInputProps({ tone: 'ok', text: 'x' })).toEqual({ className: 'field-input field-input-ok', 'aria-invalid': undefined });
	expect(fieldInputProps(null).className).toBe('field-input field-input-hint');
});

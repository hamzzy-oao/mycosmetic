// 입력칸 아래 한 줄 안내 (가입·닉네임 변경)
// tone: ok(초록, 체크 아이콘) | error(빨강) | notice(노랑) | hint(회색)
// 입력칸은 aria-describedby={id}로 이 줄을 가리킨다. 오류는 role="alert"로 바로 읽히고,
// 나머지는 aria-live="polite"로 입력을 방해하지 않고 읽힌다. 내용이 없으면 빈 줄(자리는 차지하지 않음)
export default function FieldMessage({ id, tone = 'hint', children }) {
	const live = tone === 'error' ? { role: 'alert' } : { 'aria-live': 'polite' };
	return (
		<p id={id} className={`field-message field-message-${tone}`} {...live}>
			{children && tone === 'ok' && (
				<svg className="field-message-icon" width="14" height="14" viewBox="0 0 14 14" aria-hidden="true" focusable="false">
					<path d="M2.5 7.5l3 3 6-7" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
				</svg>
			)}
			{children}
		</p>
	);
}

// 안내 종류에 맞는 입력칸 클래스와 aria-invalid
export function fieldInputProps(message) {
	const tone = message && message.text ? message.tone : 'hint';
	return {
		className: `field-input field-input-${tone}`,
		'aria-invalid': tone === 'error' ? 'true' : undefined,
	};
}

import { useId, useState } from 'react';
import { Link } from 'react-router-dom';

// "이거 사도 돼?" 폰·PC 화면이 함께 쓰는 조각 (no-ai-features 설계 F)

export const MAX_TEXT = 3000;

export const VERDICTS = {
	GOOD_FIT: { label: '잘 맞을 것 같아요', tone: 'good' },
	CAUTION: { label: '주의해서 써 보세요', tone: 'caution' },
	AVOID: { label: '피하는 게 좋아요', tone: 'avoid' },
	UNKNOWN: { label: '기록이 더 필요해요', tone: 'unknown' },
};

export const LEVEL_LABELS = {
	WARNING: '경고',
	CAUTION: '주의',
	POSITIVE: '좋아요',
	INFO: '참고',
};

// 바코드 찾기는 2026-10-23 사용자 결정으로 뺐다. 탭이 하나면 탭 막대를 그리지 않는다
export const TABS = [{ key: 'paste', label: '붙여넣기' }];

export const MEDICAL_NOTE = '참고용이에요. 내 기록과 성분 규칙으로만 판단하며 의학적 판단이 아니에요.';

const VERDICT_LABELS = { GOOD: '잘 맞음', BAD: '안 맞음', NEUTRAL: '보통', UNRATED: '평가 전' };

export function productTitle(product) {
	if (!product) return '';
	return [product.brand, product.name].filter(Boolean).join(' ');
}

function ErrorText({ error }) {
	return error ? <p role="alert" className="auth-error">{error}</p> : null;
}

// 입력 방법 탭. 좌우 화살표로도 옮긴다
export function InputTabs({ tab, onTab }) {
	if (TABS.length < 2) return null;
	const onKeyDown = (event) => {
		if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
		event.preventDefault();
		const index = TABS.findIndex((t) => t.key === tab);
		const step = event.key === 'ArrowRight' ? 1 : TABS.length - 1;
		onTab(TABS[(index + step) % TABS.length].key);
	};
	return (
		<div role="tablist" aria-label="확인 방법" className="advice-tabs" onKeyDown={onKeyDown}>
			{TABS.map((t) => (
				<button
					key={t.key}
					type="button"
					role="tab"
					id={`advice-tab-${t.key}`}
					aria-selected={t.key === tab}
					aria-controls="advice-input-panel"
					tabIndex={t.key === tab ? 0 : -1}
					className={t.key === tab ? 'is-active' : undefined}
					onClick={() => onTab(t.key)}
				>
					{t.label}
				</button>
			))}
		</div>
	);
}


function PastePanel({ busy, error, onPaste }) {
	const textId = useId();
	const [text, setText] = useState('');
	const [localError, setLocalError] = useState('');
	const submit = (event) => {
		event.preventDefault();
		if (text.trim() === '') {
			setLocalError('성분을 붙여넣어 주세요.');
			return;
		}
		setLocalError('');
		onPaste(text);
	};
	return (
		<form className="advice-panel-body" onSubmit={submit} noValidate>
			<label htmlFor={textId} className="advice-label">
				전성분
			</label>
			<textarea
				id={textId}
				value={text}
				maxLength={MAX_TEXT}
				rows={6}
				placeholder="정제수, 글리세린, 나이아신아마이드..."
				onChange={(e) => setText(e.target.value)}
			/>
			<p className="auth-hint">
				쉼표나 줄바꿈으로 나눠요. {text.length}/{MAX_TEXT}자
			</p>
			<ErrorText error={localError || error} />
			<button type="submit" className="auth-primary" disabled={busy}>
				{busy ? '판단하는 중...' : '판단하기'}
			</button>
		</form>
	);
}

export function InputPanel({ tab, ...props }) {
	const body = <PastePanel {...props} />;
	return (
		<div role="tabpanel" id="advice-input-panel" aria-labelledby={`advice-tab-${tab}`} className="advice-panel">
			{body}
		</div>
	);
}

function ReasonItem({ reason, relatedById }) {
	const related = (reason.relatedShelfItemIds || []).map((id) => relatedById[id]).filter(Boolean);
	const ingredients = reason.ingredients || [];
	return (
		<li className={`advice-reason is-${String(reason.level).toLowerCase()}`}>
			<span className="advice-level">{LEVEL_LABELS[reason.level] || reason.level}</span>
			<p className="advice-reason-message">{reason.message}</p>
			{ingredients.length > 0 && (
				<ul className="advice-chips" aria-label="관련 성분">
					{ingredients.map((name, index) => (
						<li key={`${index}-${name}`}>{name}</li>
					))}
				</ul>
			)}
			{related.length > 0 && (
				<ul className="advice-related" aria-label="관련 내 제품">
					{related.map((item) => (
						<li key={item.id}>
							<Link to={`/products/${item.productId}`}>{productTitle(item)}</Link>
							<span className="advice-related-verdict"> · {VERDICT_LABELS[item.verdict] || item.verdict}</span>
						</li>
					))}
				</ul>
			)}
		</li>
	);
}

// 결과 카드: 판정 배지, 근거 목록, 관련 내 제품 링크, "내 화장대에 담기", 참고용 안내
export function ResultCard({ advice, adding, addError, onAdd }) {
	const verdict = VERDICTS[advice.verdict] || VERDICTS.UNKNOWN;
	const relatedById = {};
	(advice.relatedItems || []).forEach((item) => {
		relatedById[item.id] = item;
	});
	const reasons = advice.reasons || [];
	const product = advice.product;
	return (
		<section className="advice-result" aria-labelledby="advice-result-title">
			<h2 id="advice-result-title" className="advice-result-title">
				판단 결과
			</h2>
			{product && <p className="advice-product">{productTitle(product)}</p>}
			<p className={`advice-badge is-${verdict.tone}`} data-testid="advice-verdict">
				{verdict.label}
			</p>
			<p className="auth-hint">
				성분 {advice.matchedCount + advice.unmatchedCount}개 중 사전에 있는 성분 {advice.matchedCount}개로 판단했어요.
			</p>
			{reasons.length > 0 && (
				<ul className="advice-reasons" aria-label="판단 근거">
					{reasons.map((reason, index) => (
						<ReasonItem key={`${reason.type}-${index}`} reason={reason} relatedById={relatedById} />
					))}
				</ul>
			)}
			{product && product.representativeListId && !advice.alreadyOnShelf && (
				<button type="button" className="auth-primary" onClick={onAdd} disabled={adding}>
					{adding ? '담는 중...' : '내 화장대에 담기'}
				</button>
			)}
			{product && advice.alreadyOnShelf && <p className="auth-hint">이미 내 화장대에 있는 제품이에요.</p>}
			<ErrorText error={addError} />
			<p className="advice-note">{MEDICAL_NOTE}</p>
		</section>
	);
}

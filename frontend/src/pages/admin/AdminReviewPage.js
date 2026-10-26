import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../AuthContext';
import { applySuggestion, archiveProduct, dismissIssue, fixSpacing, getReview, verifyProduct } from '../../api/adminReview';
import { errorMessage } from '../../lib/errorMessage';
import './AdminReviewPage.css';

// 관리자 점검함(2026-10-24): 공유 제품 데이터를 규칙으로 점검한 문제 목록.
// 이름·브랜드는 제안대로 바로 고치고, 성분은 제품 관리 화면에서 고친다. 문제가 아닌 건 "문제없음 · 숨기기"로 숨긴다.
// 중복 의심은 묶음 제품을 나란히 보여 주고, 하나를 삭제하거나 모두 그대로 둔다
export const TYPES = [
	['NEW_USER_PRODUCT', '새로 등록된 제품'],
	['NAME_FORMAT', '제품명 표기'],
	['BRAND_VARIANT', '브랜드 표기'],
	['DUPLICATE_SUSPECT', '중복 의심'],
	['INGREDIENT_SPACING', '성분 띄어쓰기'],
	['INGREDIENT_NOT_LIST', '성분 아닌 글'],
	['INGREDIENT_UNMATCHED', '사전에 없는 성분'],
];

function suggestionText(issue) {
	const s = issue.suggestion;
	if (!s) return null;
	const parts = [];
	if (s.brand) parts.push(`브랜드 "${s.brand}"`);
	if (s.name) parts.push(`제품명 "${s.name}"`);
	if (s.volume) parts.push(`용량 "${s.volume}"`);
	return parts.join(', ');
}

function formatDate(instant) {
	return instant ? new Date(instant).toLocaleDateString('ko-KR') : '-';
}

// 중복 의심: 묶음 제품을 나란히 보여 주고, 지울 제품을 고르거나 모두 그대로 둔다
function DuplicateCard({ issue, busy, onDismiss, onArchive }) {
	const members = issue.members || [];
	return (
		<li className="admin-review-card">
			<p className="admin-review-dup-title">
				<span className="admin-review-brand">{issue.brand}</span> 이름이 거의 같은 제품 {members.length}개
			</p>
			<p className="admin-review-hint">
				같은 제품이면 하나만 남기고 나머지를 삭제하세요. 리필·용량처럼 다른 제품이면 모두 그대로 두세요.
				삭제한 제품은 검색·관리 목록에서 빠지고, 이미 담은 사람의 화장대 기록은 그대로예요.
			</p>
			<ul className="admin-review-members">
				{members.map((m) => (
					<li key={m.productId} className="admin-review-member">
						<Link to={`/products/${m.productId}`} className="admin-review-member-name">{m.name}</Link>
						<dl>
							<div><dt>번호</dt><dd>#{m.productId}</dd></div>
							<div><dt>용량</dt><dd>{m.volume || '-'}</dd></div>
							<div><dt>등록일</dt><dd>{formatDate(m.createdAt)}</dd></div>
							<div><dt>담은 화장대</dt><dd>{m.shelfCount}개</dd></div>
							<div><dt>성분</dt><dd>{m.ingredientLines}줄</dd></div>
						</dl>
						<button type="button" className="admin-review-danger" disabled={busy}
							aria-label={`#${m.productId} ${m.name} 삭제`} onClick={() => onArchive(m)}>
							이 제품 삭제
						</button>
					</li>
				))}
			</ul>
			<div className="admin-review-actions">
				<button type="button" disabled={busy} onClick={() => onDismiss(issue)}>
					{members.length > 2 ? '모두 그대로 두기' : '둘 다 그대로 두기'}
				</button>
			</div>
		</li>
	);
}

// 사용자가 직접 등록한 제품(2026-10-26): 브랜드·제품명·성분을 보고 맞으면 확인 완료, 아니면 제품 관리에서 고친다
function NewProductCard({ issue, busy, onVerify }) {
	return (
		<li className="admin-review-card">
			<div className="admin-review-card-head">
				<span className="admin-review-brand">{issue.brand}</span>
				<Link to={`/products/${issue.productId}`}>{issue.name}</Link>
				<span className="admin-review-id">#{issue.productId}{issue.volume ? ` · ${issue.volume}` : ''}</span>
			</div>
			<ul className="admin-review-reasons">
				{issue.reasons.map((r) => <li key={r}>{r}</li>)}
			</ul>
			<p className="admin-review-hint">틀린 곳이 있으면 제품 관리에서 고친 뒤 확인 완료를 눌러 주세요. 다른 탭에 이 제품의 문제가 같이 뜰 수 있어요.</p>
			<div className="admin-review-actions">
				<button type="button" className="admin-review-primary" disabled={busy} onClick={() => onVerify(issue)}>
					확인 완료
				</button>
			</div>
		</li>
	);
}

function IssueCard({ issue, busy, onApply, onDismiss, onArchive, onVerify }) {
	if (issue.type === 'NEW_USER_PRODUCT') {
		return <NewProductCard issue={issue} busy={busy} onVerify={onVerify} />;
	}
	if (issue.type === 'DUPLICATE_SUSPECT') {
		return <DuplicateCard issue={issue} busy={busy} onDismiss={onDismiss} onArchive={onArchive} />;
	}
	const suggestion = suggestionText(issue);
	return (
		<li className="admin-review-card">
			<div className="admin-review-card-head">
				<span className="admin-review-brand">{issue.brand}</span>
				<Link to={`/products/${issue.productId}`}>{issue.name}</Link>
				<span className="admin-review-id">#{issue.productId}{issue.volume ? ` · ${issue.volume}` : ''}</span>
			</div>
			<ul className="admin-review-reasons">
				{issue.reasons.map((r) => <li key={r}>{r}</li>)}
			</ul>
			{issue.examples.length > 0 && (
				<ul className="admin-review-examples" aria-label="예시">
					{issue.examples.map((e) => <li key={e}>{e}</li>)}
				</ul>
			)}
			{suggestion && <p className="admin-review-suggestion">제안: {suggestion}</p>}
			{issue.type.startsWith('INGREDIENT_') && issue.type !== 'INGREDIENT_SPACING' && (
				<p className="admin-review-hint">제품 관리에서 이 제품을 찾아 성분을 고쳐 주세요.</p>
			)}
			<div className="admin-review-actions">
				<button type="button" disabled={busy} onClick={() => onDismiss(issue)}>문제없음 · 숨기기</button>
				{suggestion && (
					<button type="button" className="admin-review-primary" disabled={busy} onClick={() => onApply(issue)}>
						제안대로 고치기
					</button>
				)}
			</div>
		</li>
	);
}

function ReviewInbox() {
	const [review, setReview] = useState(null);
	const [type, setType] = useState(TYPES[0][0]);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	const [notice, setNotice] = useState('');

	const load = useCallback(() => {
		setError('');
		return getReview().then(setReview).catch((e) => setError(errorMessage(e)));
	}, []);

	useEffect(() => {
		load();
	}, [load]);

	const run = async (action, message) => {
		setBusy(true);
		setError('');
		setNotice('');
		try {
			const result = await action();
			setNotice(typeof message === 'function' ? message(result) : message);
			await load();
		} catch (e) {
			setError(errorMessage(e));
		} finally {
			setBusy(false);
		}
	};

	const onApply = (issue) => run(() => applySuggestion(issue), `${issue.brand} ${issue.name}을(를) 고쳤어요.`);
	const onDismiss = (issue) => run(() => dismissIssue(issue), issue.type === 'DUPLICATE_SUSPECT'
		? '모두 그대로 두고 점검함에서 숨겼어요. 같은 이름의 제품이 새로 생기면 다시 보여 드려요.'
		: '점검함에서 숨겼어요. 제품 정보가 바뀌면 다시 보여 드려요.');
	// 삭제 = 보관: 검색·관리 목록에서 빠지고 기존 화장대 기록은 그대로
	const onArchive = (product) => {
		if (!window.confirm(`#${product.productId} ${product.name}\n이 제품을 삭제할까요? 검색·관리 목록에서 빠지고, 이미 담은 사람의 화장대 기록은 그대로예요.`)) return;
		run(() => archiveProduct(product.productId), `#${product.productId} ${product.name}을(를) 삭제했어요.`);
	};
	const onVerify = (issue) => run(() => verifyProduct(issue.productId), `${issue.brand} ${issue.name}을(를) 확인했어요.`);
	const onFixSpacing = () => run(fixSpacing, (r) => `성분 버전 ${r.lists}개에서 ${r.items}줄을 사전에 맞췄어요.`);

	const issues = review ? review.issues.filter((i) => i.type === type) : [];
	const total = review ? Object.values(review.counts).reduce((a, b) => a + b, 0) : 0;

	return (
		<section className="admin-review" aria-labelledby="admin-review-title">
			<div className="admin-review-heading">
				<h1 id="admin-review-title">데이터 점검함</h1>
				<Link to="/admin">제품 관리로</Link>
			</div>
			<p className="admin-review-rule">
				올리브영식 표기를 기준으로 봐요: 제품명에는 브랜드·용량·기획·증정 문구를 넣지 않고, 용량은 용량 칸에 따로 적어요.
			</p>
			{error && <p role="alert" className="auth-error">{error}</p>}
			{notice && <p role="status" className="auth-notice">{notice}</p>}
			{!review ? (
				!error && <p aria-busy="true">점검하는 중...</p>
			) : (
				<>
					<p className="admin-review-summary" aria-live="polite">
						제품 {review.checkedProducts.toLocaleString('ko-KR')}개를 점검했어요 · 확인할 것 <strong>{total}개</strong>
						{review.dismissed > 0 && ` · 문제없음으로 숨긴 것 ${review.dismissed}개`}
					</p>
					{review.spacingLines > 0 && (
						<div className="admin-review-spacing">
							<p>띄어쓰기만 고치면 사전에 맞는 성분 줄이 <strong>{review.spacingLines}줄</strong> 있어요. 원문은 그대로 두고 성분만 이어요.</p>
							<button type="button" className="admin-review-primary" disabled={busy} onClick={onFixSpacing}>
								{busy ? '고치는 중...' : '띄어쓰기 모두 고치기'}
							</button>
						</div>
					)}
					<div className="admin-review-tabs" role="group" aria-label="문제 종류">
						{TYPES.map(([key, label]) => (
							<button key={key} type="button" aria-pressed={type === key} onClick={() => setType(key)}>
								{label} <span>{review.counts[key] ?? 0}</span>
							</button>
						))}
					</div>
					{issues.length ? (
						<ul className="admin-review-list">
							{issues.map((issue) => (
								<IssueCard key={`${issue.type}-${issue.productId}`} issue={issue} busy={busy}
									onApply={onApply} onDismiss={onDismiss} onArchive={onArchive} onVerify={onVerify} />
							))}
						</ul>
					) : (
						<p className="admin-review-empty">이 종류는 확인할 게 없어요.</p>
					)}
				</>
			)}
		</section>
	);
}

export default function AdminReviewPage() {
	const { user } = useAuth();
	return user?.role === 'ADMIN' ? <ReviewInbox /> : (
		<section className="admin-review"><h1>접근 권한이 없습니다.</h1><Link to="/shelf">내 화장대</Link></section>
	);
}

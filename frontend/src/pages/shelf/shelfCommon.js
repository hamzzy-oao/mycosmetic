import { useState } from 'react';
import { Link } from 'react-router-dom';
import { DEFAULT_SHADE_HEX, SHADE_HEX_PATTERN, SHADE_NAME_MAX, TONE_NOTE, toneText } from '../../lib/shade';

// 내 화장대 폰·PC 화면이 함께 쓰는 것

// 평가는 맞음·안 맞음·평가 전 세 가지. '보통'은 없앴다(2026-10-24, 저장된 값은 V16에서 평가 전으로)
export const VERDICTS = ['GOOD', 'BAD', 'UNRATED'];

export const VERDICT_LABELS = {
	GOOD: '맞음',
	BAD: '안 맞음',
	UNRATED: '평가 전',
};

export const INGREDIENT_QUERY_MAX = 50;
export const PAO_MIN = 1;
export const PAO_MAX = 60;

export function productTitle(product) {
	if (!product) return '';
	return [product.brand, product.name].filter(Boolean).join(' ');
}

function pad(n) {
	return String(n).padStart(2, '0');
}

// 이 기기의 오늘 날짜(YYYY-MM-DD). 개봉일 입력의 최댓값과 D-n 계산에 쓴다
export function todayString(now = new Date()) {
	return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

function toUtcDay(value) {
	const [y, m, d] = String(value).slice(0, 10).split('-').map(Number);
	return Date.UTC(y, m - 1, d);
}

// 오늘부터 날짜까지 남은 일수(지났으면 음수)
export function daysUntil(date, today = todayString()) {
	return Math.round((toUtcDay(date) - toUtcDay(today)) / 86400000);
}

// 개봉 후 사용기한 배지(no-ai-features 설계 D). 지났거나 30일 안에 끝날 때만 보인다
export function ExpiryBadge({ item }) {
	if (item.expiryStatus === 'EXPIRED') {
		return <span className="shelf-expiry is-expired">기한 지남</span>;
	}
	if (item.expiryStatus === 'EXPIRING' && item.expiresOn) {
		const days = Math.max(0, daysUntil(item.expiresOn));
		return <span className="shelf-expiry is-expiring">곧 끝나요(D-{days === 0 ? 'day' : days})</span>;
	}
	return null;
}

// 개봉일·사용기한 요약(예: "개봉 2026-01-01 · ~2026-07-01까지")
export function openingText(item) {
	if (!item.openedOn) return '';
	return item.expiresOn ? `개봉 ${item.openedOn} · ${item.expiresOn}까지` : `개봉 ${item.openedOn}`;
}

// 개봉 상태를 목록에서 바로 보여준다. 안 열었으면 "오늘 개봉" 한 번으로 개봉일을 넣고,
// 사용기한이 비어 있으면 흔한 값 12개월을 함께 넣는다(v-7). 날짜를 바꾸려면 자세히 열어 고친다
export function OpeningQuick({ item, onUpdate, as: Tag = 'p' }) {
	if (item.openedOn) {
		return <Tag className="shelf-opening">{openingText(item)}</Tag>;
	}
	return (
		<Tag className="shelf-opening shelf-opening-none">
			<span>미개봉</span>
			<button
				type="button"
				className="shelf-open-today"
				aria-label={`${productTitle(item.product)} 오늘 개봉`}
				onClick={() => onUpdate(item.id, { openedOn: todayString(), ...(item.paoMonths == null ? { paoMonths: 12 } : {}) })}
			>
				오늘 개봉
			</button>
		</Tag>
	);
}

// 내 항목은 내가 확인한 버전에 고정된다(설계 3.5-5). 대표 버전이 바뀌었으면 알려준다
export function NewVersionNotice({ item }) {
	if (item.listIsRepresentative !== false || !item.product) {
		return null;
	}
	return (
		<p className="shelf-new-version">
			<Link to={`/products/${item.product.id}`}>새 성분 버전이 있어요</Link>
		</p>
	);
}

// 성분으로 찾기 결과에서 일치한 성분을 강조한다
export function MatchedIngredients({ item }) {
	const names = Array.isArray(item.matchedIngredients) ? item.matchedIngredients : [];
	if (names.length === 0) return null;
	return (
		<p className="shelf-matched">
			일치한 성분{' '}
			{names.map((name, index) => (
				<mark key={`${index}-${name}`}>{name}</mark>
			))}
		</p>
	);
}

export function EmptyShelf() {
	return (
		<div className="shelf-empty">
			<p>아직 화장대에 담은 제품이 없어요.</p>
			<Link className="shelf-primary" to="/add">
				제품 추가하기
			</Link>
		</div>
	);
}

export function ShelfLinks() {
 return <nav className="shelf-links" aria-label="화장대 메뉴">
  <Link to="/add" className="shelf-add-product">제품 추가 +</Link>
 </nav>;
}

// 제품명·브랜드로 바로 거르기(v-1). 이미 불러온 목록에서 거르므로 서버를 부르지 않는다
export function matchesProductName(item, query) {
	const q = String(query || '').trim().toLowerCase();
	if (!q) return true;
	return productTitle(item && item.product).toLowerCase().includes(q);
}

export function ShelfNameFilter({ nameFilter }) {
	return (
		<div className="shelf-search shelf-name-filter">
			<label htmlFor="shelf-name-input">제품명으로 찾기</label>
			<div className="shelf-search-row">
				<input
					id="shelf-name-input"
					type="search"
					value={nameFilter.query}
					maxLength={100}
					placeholder="예: 닥터지, 선크림"
					onChange={(e) => nameFilter.onChange(e.target.value)}
				/>
			</div>
		</div>
	);
}

// 화장대 상단 성분 검색창(no-ai-features 설계 C). search: { query, active, busy, error, onSearch, onClear }
export function ShelfSearch({ search }) {
	const [text, setText] = useState(search.query || '');
	const [localError, setLocalError] = useState('');

	const onSubmit = (event) => {
		event.preventDefault();
		const q = text.trim();
		if (!q) {
			setLocalError('찾을 성분 이름을 적어 주세요.');
			return;
		}
		setLocalError('');
		search.onSearch(q);
	};

	const onClear = () => {
		setText('');
		setLocalError('');
		search.onClear();
	};

	const error = localError || search.error;
	return (
		<form role="search" className="shelf-search" onSubmit={onSubmit} aria-label="화장대 성분 검색">
			<label htmlFor="shelf-search-input">성분으로 찾기</label>
			<div className="shelf-search-row">
				<input
					id="shelf-search-input"
					type="search"
					value={text}
					maxLength={INGREDIENT_QUERY_MAX}
					placeholder="예: 레티놀, 나이아신아마이드"
					onChange={(e) => setText(e.target.value)}
				/>
				<button type="submit" className="shelf-search-submit" disabled={search.busy}>
					{search.busy ? '찾는 중...' : '찾기'}
				</button>
				{search.active && (
					<button type="button" className="shelf-search-clear" onClick={onClear}>
						전체 보기
					</button>
				)}
			</div>
			{error && <p role="alert" className="auth-error">{error}</p>}
		</form>
	);
}

// 검색 중일 때 결과 개수 안내. 화장대가 비었으면 빈 화장대 안내를 대신 보여준다
export function ShelfListNotice({ search, nameFilter, loading, loadError, allCount, count }) {
	if (loading || loadError) return null;
	if (allCount === 0) return <EmptyShelf />;
	const name = nameFilter && nameFilter.query.trim();
	if (!search.active) {
		if (!name) return null;
		return (
			<p role="status" className="shelf-search-result">
				{count === 0 ? `'${name}' 제품이 없어요.` : `'${name}' 제품 ${count}개`}
			</p>
		);
	}
	return (
		<p role="status" className="shelf-search-result">
			{count === 0
				? `'${search.query}' 성분이 든 제품이 없어요.`
				: `'${search.query}' 성분이 든 제품 ${count}개`}
		</p>
	);
}

// 메모·사용 기간·개봉일·사용기한 수정. 바뀐 필드만 보낸다(서버는 null 필드를 바꾸지 않는다)
export function ShelfItemEditor({ item, onUpdate }) {
	const [memo, setMemo] = useState(item.memo || '');
	const [startedOn, setStartedOn] = useState(item.startedOn || '');
	const [endedOn, setEndedOn] = useState(item.endedOn || '');
	const [openedOn, setOpenedOn] = useState(item.openedOn || '');
	const [paoMonths, setPaoMonths] = useState(item.paoMonths != null ? String(item.paoMonths) : '');
	const [error, setError] = useState('');
	const [saved, setSaved] = useState(false);
	const [busy, setBusy] = useState(false);
	const today = todayString();

	const validate = () => {
		if (startedOn && endedOn && endedOn < startedOn) return '사용 종료일은 시작일보다 앞일 수 없어요.';
		if (openedOn && openedOn > today) return '개봉일은 오늘보다 뒤일 수 없어요.';
		if (paoMonths !== '') {
			const months = Number(paoMonths);
			if (!Number.isInteger(months) || months < PAO_MIN || months > PAO_MAX) {
				return `사용기한은 ${PAO_MIN}~${PAO_MAX}개월 사이로 적어 주세요.`;
			}
		}
		return '';
	};

	const onSubmit = async (event) => {
		event.preventDefault();
		if (busy) return;
		setSaved(false);
		const invalid = validate();
		if (invalid) {
			setError(invalid);
			return;
		}
		const changes = {};
		if (memo !== (item.memo || '')) changes.memo = memo;
		if (startedOn && startedOn !== item.startedOn) changes.startedOn = startedOn;
		if (endedOn && endedOn !== item.endedOn) changes.endedOn = endedOn;
		if (openedOn && openedOn !== item.openedOn) changes.openedOn = openedOn;
		if (paoMonths !== '' && Number(paoMonths) !== item.paoMonths) changes.paoMonths = Number(paoMonths);
		if (Object.keys(changes).length === 0) {
			setError('');
			return;
		}
		setBusy(true);
		const message = await onUpdate(item.id, changes, { showError: false });
		setBusy(false);
		if (message) {
			setError(message);
		} else {
			setError('');
			setSaved(true);
		}
	};

	const idBase = `shelf-${item.id}`;
	return (
		<form className="shelf-editor" onSubmit={onSubmit}>
			<label htmlFor={`${idBase}-memo`}>메모</label>
			<textarea
				id={`${idBase}-memo`}
				value={memo}
				maxLength={500}
				rows={2}
				onChange={(e) => setMemo(e.target.value)}
			/>
			<div className="shelf-dates">
				<label htmlFor={`${idBase}-start`}>사용 시작일</label>
				<input id={`${idBase}-start`} type="date" value={startedOn} onChange={(e) => setStartedOn(e.target.value)} />
				<label htmlFor={`${idBase}-end`}>사용 종료일</label>
				<input id={`${idBase}-end`} type="date" value={endedOn} onChange={(e) => setEndedOn(e.target.value)} />
				<label htmlFor={`${idBase}-opened`}>개봉일</label>
				<input
					id={`${idBase}-opened`}
					type="date"
					value={openedOn}
					max={today}
					onChange={(e) => setOpenedOn(e.target.value)}
				/>
				<label htmlFor={`${idBase}-pao`}>사용기한(개월)</label>
				<input
					id={`${idBase}-pao`}
					type="number"
					inputMode="numeric"
					min={PAO_MIN}
					max={PAO_MAX}
					step={1}
					placeholder="예: 12"
					value={paoMonths}
					onChange={(e) => setPaoMonths(e.target.value)}
				/>
			</div>
			<p className="auth-hint">사용기한은 용기의 열린 뚜껑 그림(예: 12M)에 적힌 개월 수예요.</p>
			{!openedOn && (
				<button
					type="button"
					className="add-secondary"
					onClick={() => {
						// 오늘 열었다고 표시하고, 사용기한이 비었으면 흔한 값 12개월을 채운다(v-7). 저장은 버튼으로
						setOpenedOn(today);
						if (paoMonths === '') setPaoMonths('12');
					}}
				>
					오늘 개봉했어요
				</button>
			)}
			{error && <p role="alert" className="auth-error">{error}</p>}
			{saved && <p role="status" className="auth-notice">저장했어요.</p>}
			<button type="submit" disabled={busy}>
				메모·기간 저장
			</button>
		</form>
	);
}

// 내 호수 색 칩(no-ai-features 설계 E): 색 동그라미 + 이름 + 웜/쿨·계절 + 내 톤과 맞으면 표시
export function ShadeChip({ item }) {
	if (!item.shadeHex && !item.shadeName) return null;
	const tone = toneText(item);
	return (
		<span className="shelf-shade">
			{item.shadeHex && (
				<span
					className="shelf-shade-swatch"
					data-testid="shade-swatch"
					style={{ backgroundColor: item.shadeHex }}
					aria-hidden="true"
				/>
			)}
			<span className="shelf-shade-name">{item.shadeName || item.shadeHex}</span>
			{tone && <span className="shelf-shade-tone">{tone}</span>}
			{item.shadeMatchesMe === true && <span className="shelf-shade-match">내 톤</span>}
		</span>
	);
}

// 색조(MAKEUP) 항목의 호수 이름·색 수정. 바뀐 필드만 보내고, 지우면 빈 문자열을 보낸다(서버가 지운다)
export function ShadeEditor({ item, onUpdate }) {
	const [name, setName] = useState(item.shadeName || '');
	const [hex, setHex] = useState(item.shadeHex || '');
	const [error, setError] = useState('');
	const [saved, setSaved] = useState(false);
	const [busy, setBusy] = useState(false);

	const onSubmit = async (event) => {
		event.preventDefault();
		if (busy) return;
		setSaved(false);
		if (hex && !SHADE_HEX_PATTERN.test(hex)) {
			setError('색을 다시 골라 주세요.');
			return;
		}
		const changes = {};
		if (name.trim() !== (item.shadeName || '')) changes.shadeName = name.trim();
		if (hex.toUpperCase() !== (item.shadeHex || '').toUpperCase()) changes.shadeHex = hex.toUpperCase();
		if (Object.keys(changes).length === 0) {
			setError('');
			return;
		}
		setBusy(true);
		const message = await onUpdate(item.id, changes, { showError: false });
		setBusy(false);
		if (message) {
			setError(message);
		} else {
			setError('');
			setSaved(true);
		}
	};

	const onClear = () => {
		setSaved(false);
		setName('');
		setHex('');
	};

	const idBase = `shelf-${item.id}-shade`;
	return (
		<form className="shelf-shade-editor" onSubmit={onSubmit}>
			<fieldset>
				<legend>내 호수</legend>
				<div className="shelf-shade-fields">
					<label htmlFor={`${idBase}-hex`}>색</label>
					<input
						id={`${idBase}-hex`}
						type="color"
						value={(hex || DEFAULT_SHADE_HEX).toLowerCase()}
						onChange={(e) => {
							setSaved(false);
							setHex(e.target.value.toUpperCase());
						}}
					/>
					<label htmlFor={`${idBase}-name`}>이름</label>
					<input
						id={`${idBase}-name`}
						type="text"
						value={name}
						maxLength={SHADE_NAME_MAX}
						placeholder="예: 21호, 로즈 베이지"
						onChange={(e) => {
							setSaved(false);
							setName(e.target.value);
						}}
					/>
				</div>
				<p className="auth-hint">{hex ? `고른 색 ${hex}` : '아직 색을 고르지 않았어요.'} {TONE_NOTE}</p>
				{error && <p role="alert" className="auth-error">{error}</p>}
				{saved && <p role="status" className="auth-notice">호수를 저장했어요.</p>}
				<div className="shelf-shade-actions">
					<button type="submit" disabled={busy}>
						호수 저장
					</button>
					{(hex || name) && (
						<button type="button" className="shelf-shade-clear" onClick={onClear}>
							지우기
						</button>
					)}
				</div>
			</fieldset>
		</form>
	);
}

// "내 톤에 맞는 색조" 필터(no-ai-features 설계 E). tone: { active, busy, error, needsColor, onToggle }
export function ShelfToneFilter({ tone, count }) {
	return (
		<div className="shelf-tone">
			<button
				type="button"
				className={tone.active ? 'shelf-tone-toggle is-selected' : 'shelf-tone-toggle'}
				aria-pressed={tone.active}
				disabled={tone.busy}
				onClick={tone.onToggle}
			>
				{tone.busy ? '찾는 중...' : '내 톤에 맞는 색조'}
			</button>
			{tone.active && (
				<p role="status" className="shelf-search-result">
					{count === 0 ? '내 톤에 맞는 색조가 없어요.' : `내 톤에 맞는 색조 ${count}개`}
				</p>
			)}
			{tone.needsColor && (
				<p role="alert" className="shelf-tone-need">
					피부 상태에서 퍼스널컬러를 먼저 골라 주세요. <Link to="/skin">퍼스널컬러 고르기</Link>
				</p>
			)}
			{tone.error && <p role="alert" className="auth-error">{tone.error}</p>}
			{tone.active && <p className="auth-hint">{TONE_NOTE}</p>}
		</div>
	);
}

// 폰·PC 화면이 함께 쓰는 불러오기·오류 표시
export function ShelfStatus({ loading, loadError, actionError, onReload }) {
	if (loading) {
		return <p aria-busy="true">불러오는 중...</p>;
	}
	if (loadError) {
		return (
			<div role="alert" className="auth-error">
				<p>{loadError}</p>
				<button type="button" onClick={onReload}>
					다시 시도
				</button>
			</div>
		);
	}
	if (actionError) {
		return <p role="alert" className="auth-error">{actionError}</p>;
	}
	return null;
}

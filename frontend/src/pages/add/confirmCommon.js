import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { OCR_IMAGE_TYPES, OcrTimeoutError, isOcrImage, readIngredientText } from '../../lib/ocr';

// 성분 확인 폰·PC 화면이 함께 쓰는 것 (설계 4.1-8)

export const CATEGORIES = {
	SKINCARE: '스킨케어',
	MAKEUP: '메이크업',
};

export const MAX_ITEMS = 200;

let keySeq = 0;
// 화면 안에서만 쓰는 항목 키. 임시 보관 후 새로고침해도 겹치지 않게 시간도 섞는다
export function newKey() {
	keySeq += 1;
	return `i${Date.now().toString(36)}${keySeq}`;
}

// job result의 items(MatchResult: position, rawText, type, ingredientId, standardName, candidates)를
// 확인 화면 항목으로 바꾼다. FUZZY는 사용자가 후보를 고르기 전까지 ingredientId가 비어 있다
export function itemsFromResult(result) {
	const raw = (result && Array.isArray(result.items) && result.items) || [];
	return [...raw]
		.sort((a, b) => (a.position || 0) - (b.position || 0))
		.map((item) => ({
			key: newKey(),
			rawText: item.rawText || '',
			ingredientId: item.ingredientId == null ? null : item.ingredientId,
			standardName: item.standardName || null,
			matchType: item.type || item.matchType || 'UNMATCHED',
			candidates: Array.isArray(item.candidates) ? item.candidates : [],
		}));
}

// 쉼표·줄바꿈으로 성분 이름을 나눈다. 숫자 사이 쉼표("1,2-헥산다이올")와 괄호 안 쉼표는 나누지 않는다.
// 서버(IngredientTextSections.splitNames)와 같은 규칙이고, 서버 매칭을 못 쓸 때만 쓴다
export function splitIngredientNames(text) {
	const s = String(text || '');
	const names = [];
	let current = '';
	let depth = 0;
	const push = () => {
		const name = current
			.replace(/[[\]]/g, ' ')
			.trim()
			.replace(/^(\+\s*\/\s*-|±)\s*[:：]?\s*/, '')
			.replace(/^[-•*·]+\s*/, '')
			.replace(/[.。]+$/, '')
			.trim();
		if (name && name !== '등') names.push(name);
		current = '';
	};
	for (let i = 0; i < s.length; i += 1) {
		const c = s[i];
		if (c === '(' || c === '（') depth += 1;
		else if ((c === ')' || c === '）') && depth > 0) depth -= 1;
		const betweenDigits = i > 0 && /\d/.test(s[i - 1]) && /\d/.test(s[i + 1] || '');
		if (c === '\n' || c === '\r' || ((c === ',' || c === '，' || c === '、') && depth === 0 && !betweenDigits)) {
			push();
		} else {
			current += c;
		}
	}
	push();
	return names.map((name) => name.replace(/^(전성분|모든\s*성분|성분)\s*[:：]\s*/, '')).filter(Boolean);
}

// 붙여넣은 전성분을 나눠 사전에 없는 항목으로 만든다(설계 6.3). 서버 매칭이 실패했을 때 쓴다
export function itemsFromText(text) {
	return splitIngredientNames(text)
		.map((rawText) => ({
			key: newKey(),
			rawText,
			ingredientId: null,
			standardName: null,
			matchType: 'UNMATCHED',
			candidates: [],
		}));
}

export function displayName(item) {
	return item.ingredientId != null && item.standardName ? item.standardName : item.rawText;
}

// ok: 사전과 맞음(EXACT/ALIAS 또는 후보를 고른 FUZZY), fuzzy: 후보 고르기 전, unmatched: 사전에 없음
export function itemState(item) {
	if (item.ingredientId != null) return 'ok';
	if (item.matchType === 'FUZZY' && item.candidates.length > 0) return 'fuzzy';
	return 'unmatched';
}

const STATE_BADGES = {
	ok: { icon: '✅', label: '사전과 맞음' },
	fuzzy: { icon: '🟡', label: '후보 확인 필요' },
	unmatched: { icon: '⚠️', label: '사전에 없음' },
};

function percent(similarity) {
	return typeof similarity === 'number' ? ` (${Math.round(similarity * 100)}%)` : '';
}

export function ErrorText({ error }) {
	return error ? <p role="alert" className="auth-error">{error}</p> : null;
}

// 추출 경고(계획 Task 15 warning)와 읽지 못한 부분
export function ConfirmWarning({ result, onRetake }) {
	const warning = result && result.warning;
	const unreadable = (result && Array.isArray(result.unreadableParts) && result.unreadableParts) || [];
	if (!warning && unreadable.length === 0) return null;
	return (
		<div className="confirm-warning" role="status">
			{warning === 'NO_INGREDIENT_SECTION' && (
				<>
					<p>사진에서 전성분 부분을 찾지 못했어요.</p>
					<p className="auth-hint">
						촬영 팁: "전성분" 글자부터 끝까지 한 화면에 들어오게, 빛 반사 없이 가까이 찍어 주세요.
					</p>
				</>
			)}
			{warning === 'MANY_UNMATCHED' && <p>사전에 없는 성분이 많아요. 다시 찍을까요?</p>}
			{unreadable.length > 0 && <p className="auth-hint">읽지 못한 부분: {unreadable.join(', ')}</p>}
			{warning && onRetake && (
				<button type="button" className="add-secondary" onClick={onRetake}>
					다시 찍기
				</button>
			)}
		</div>
	);
}

// 성분 한 줄: 상태 표시, FUZZY 후보 고르기, 사전에 없는 성분 고치기, 위·아래 이동, 삭제
export function IngredientRow({ item, index, total, onPick, onEdit, onMove, onRemove }) {
	const state = itemState(item);
	const name = displayName(item) || `${index + 1}번째 성분`;
	const badge = STATE_BADGES[state];
	const showCandidates = item.matchType === 'FUZZY' && item.candidates.length > 0;
	return (
		<li className={`confirm-item confirm-item-${state}`} data-state={state}>
			<span className="confirm-badge" title={badge.label}>
				<span aria-hidden="true">{badge.icon}</span>
				<span className="visually-hidden">{badge.label}</span>
			</span>
			<div className="confirm-item-body">
				{state === 'unmatched' && !showCandidates ? (
					<input
						type="text"
						aria-label={`${index + 1}번째 성분 이름`}
						value={item.rawText}
						maxLength={200}
						onChange={(e) => onEdit(item.key, e.target.value)}
					/>
				) : (
					<span className="confirm-name">{name}</span>
				)}
				{item.ingredientId != null && item.standardName && item.rawText && item.rawText !== item.standardName && (
					<span className="confirm-raw">읽은 글자: {item.rawText}</span>
				)}
				{showCandidates && (
					<select
						aria-label={`${item.rawText} 후보 고르기`}
						value={item.ingredientId == null ? '' : String(item.ingredientId)}
						onChange={(e) => onPick(item.key, e.target.value)}
					>
						<option value="">고르지 않음 (읽은 글자 그대로)</option>
						{item.candidates.map((candidate) => (
							<option key={candidate.ingredientId} value={String(candidate.ingredientId)}>
								{candidate.standardName}
								{percent(candidate.similarity)}
							</option>
						))}
					</select>
				)}
			</div>
			<div className="confirm-item-actions">
				<button type="button" aria-label={`${name} 위로`} disabled={index === 0} onClick={() => onMove(item.key, -1)}>
					↑
				</button>
				<button
					type="button"
					aria-label={`${name} 아래로`}
					disabled={index === total - 1}
					onClick={() => onMove(item.key, 1)}
				>
					↓
				</button>
				<button type="button" className="me-danger" aria-label={`${name} 삭제`} onClick={() => onRemove(item.key)}>
					삭제
				</button>
			</div>
		</li>
	);
}

export function IngredientList({ items, onPick, onEdit, onMove, onRemove }) {
	const fuzzyLeft = items.filter((item) => itemState(item) === 'fuzzy').length;
	if (items.length === 0) {
		return <p className="auth-hint">아직 성분이 없어요. 아래에서 추가하거나 붙여넣어 주세요.</p>;
	}
	return (
		<>
			<p className="auth-hint">
				성분 {items.length}개
				{fuzzyLeft > 0 && ` · 🟡 ${fuzzyLeft}개는 후보를 고르지 않으면 읽은 글자 그대로 저장돼요`}
			</p>
			<ol className="confirm-list" aria-label="확인할 성분">
				{items.map((item, index) => (
					<IngredientRow
						key={item.key}
						item={item}
						index={index}
						total={items.length}
						onPick={onPick}
						onEdit={onEdit}
						onMove={onMove}
						onRemove={onRemove}
					/>
				))}
			</ol>
		</>
	);
}

export function AddIngredient({ onAdd }) {
	const inputId = useId();
	const [text, setText] = useState('');
	const submit = () => {
		if (!text.trim()) return;
		onAdd(text);
		setText('');
	};
	return (
		<div className="confirm-add">
			<label htmlFor={inputId}>성분 추가</label>
			<input
				id={inputId}
				type="text"
				value={text}
				maxLength={200}
				onChange={(e) => setText(e.target.value)}
				onKeyDown={(e) => {
					if (e.key === 'Enter') {
						e.preventDefault();
						submit();
					}
				}}
			/>
			<button type="button" onClick={submit} disabled={!text.trim()}>
				추가
			</button>
		</div>
	);
}

// 클립보드·끌어다 놓기에서 읽을 수 있는 이미지 파일 하나
function imageFrom(items) {
	return Array.from(items || [])
		.map((item) => (item.getAsFile ? (item.kind === 'file' ? item.getAsFile() : null) : item))
		.find(isOcrImage) || null;
}

// 글자를 못 읽었을 때 다시 해 볼 방법
const OCR_TIPS = '전성분 글자가 보이는 부분만 크게 캡처해 다시 올리거나, 상세페이지의 전성분 글을 복사해 붙여넣어 주세요. 화장품 뒷면은 폰 카메라의 글자 인식(아이폰 라이브 텍스트, 구글 렌즈)으로 복사할 수 있어요.';
// 사전에 맞는 성분이 이 비율보다 적으면 글자를 잘못 읽었을 가능성이 크다
const OCR_MIN_MATCH_RATIO = 0.5;

// 전성분을 직접 붙여넣는다(설계 6.3). 서버가 옵션별로 나눠 성분 사전과 맞춘다.
// 전성분 캡처 이미지를 고르거나 붙여넣으면(Ctrl+V)·끌어다 놓으면 브라우저 안에서 글자를 읽어 칸에 채운다(2026-10-26).
// 못 읽은 경우(예외 처리):
// - 글자 읽기 실패·시간 초과·전성분으로 볼 수 없는 결과(failed)면 칸에 넣지 않고 다른 방법을 안내한다
// - 확실하지 않은 결과(unsure: 작은 캡처, 낮은 신뢰도)는 칸에 넣되 확인하라고 크게 알린다
// - 읽은 글을 목록에 넣었는데 사전에 맞는 성분이 절반도 안 되면 알리고, 방금 넣은 성분을 한 번에 뺄 수 있게 한다
// initialImage가 있으면 처음에 바로 읽는다
export function PasteBox({ onPaste, onUndo, busy, initialImage }) {
	const inputId = useId();
	const fileId = useId();
	const [text, setText] = useState('');
	const [reading, setReading] = useState(null); // null | 진행률(0~100)
	const [readError, setReadError] = useState('');
	const [quality, setQuality] = useState(null); // null | 'good' | 'unsure'
	const [fromOcr, setFromOcr] = useState(false);
	const [lowMatch, setLowMatch] = useState(null); // { keys, total, matched }
	const startedRef = useRef(false);

	const readImage = useCallback(async (file) => {
		if (!isOcrImage(file)) {
			setReadError('PNG·JPG·WEBP 이미지만 읽을 수 있어요.');
			return;
		}
		setReadError('');
		setQuality(null);
		setLowMatch(null);
		setReading(0);
		try {
			const result = await readIngredientText(file, setReading);
			if (result.quality === 'failed' || !result.text) {
				setReadError(`이 이미지에서는 전성분을 읽지 못했어요. ${OCR_TIPS}`);
				return;
			}
			setText((prev) => (prev.trim() ? `${prev.trim()}
${result.text}` : result.text));
			setFromOcr(true);
			setQuality(result.quality);
		} catch (e) {
			setReadError(e instanceof OcrTimeoutError
				? `글자 읽기가 너무 오래 걸려서 멈췄어요. 인터넷 연결을 확인하고 다시 시도해 주세요. ${OCR_TIPS}`
				: `글자를 읽지 못했어요. ${OCR_TIPS}`);
		} finally {
			setReading(null);
		}
	}, []);

	useEffect(() => {
		if (initialImage && !startedRef.current) {
			startedRef.current = true;
			readImage(initialImage);
		}
	}, [initialImage, readImage]);

	const submit = async () => {
		const ocr = fromOcr;
		const pasted = text;
		setText('');
		setFromOcr(false);
		setQuality(null);
		setLowMatch(null);
		const summary = await onPaste(pasted);
		if (ocr && summary && summary.total > 0 && summary.matched / summary.total < OCR_MIN_MATCH_RATIO) {
			setLowMatch(summary);
		}
	};

	const isReading = reading !== null;
	return (
		<div
			className="confirm-paste"
			onDragOver={(e) => e.preventDefault()}
			onDrop={(e) => {
				const image = imageFrom(e.dataTransfer && e.dataTransfer.files);
				if (image) {
					e.preventDefault();
					readImage(image);
				}
			}}
		>
			<label htmlFor={inputId}>전성분 붙여넣기</label>
			<p className="auth-hint">
				상세페이지의 전성분 글을 복사해 붙여넣거나, 전성분 캡처 이미지를 올리면 글자를 읽어 채워요. 이미지는 서버로 보내지 않고 이
				브라우저 안에서만 읽어요.
			</p>
			<div className="confirm-ocr">
				<input
					id={fileId}
					type="file"
					accept={OCR_IMAGE_TYPES.join(',')}
					className="visually-hidden"
					disabled={isReading || busy}
					onChange={(e) => {
						const file = e.target.files && e.target.files[0];
						e.target.value = '';
						if (file) readImage(file);
					}}
				/>
				<label htmlFor={fileId} className="confirm-ocr-button" aria-disabled={isReading || busy}>
					캡처 이미지에서 글자 읽기
				</label>
				{isReading && (
					<p role="status" aria-busy="true" className="confirm-ocr-status">
						{reading > 0 ? `글자 읽는 중... ${reading}%` : '글자 읽기 준비 중... (처음 한 번은 조금 걸려요)'}
					</p>
				)}
				{quality === 'good' && !isReading && (
					<p role="status" className="confirm-ocr-status">
						읽은 글을 칸에 넣었어요. 틀린 글자가 없는지 확인한 뒤 목록에 넣어 주세요.
					</p>
				)}
				{quality === 'unsure' && !isReading && (
					<p role="status" className="auth-error">
						글자를 확실하게 읽지 못했어요. 칸의 글을 꼭 확인해 주세요. 틀린 글자가 많으면 상세페이지를 확대(Ctrl + 휠)한 뒤 전성분
						부분만 크게 다시 캡처해 주세요.
					</p>
				)}
				{readError && <p role="alert" className="auth-error">{readError}</p>}
			</div>
			<textarea
				id={inputId}
				rows={isReading || text ? 8 : 4}
				value={text}
				disabled={isReading}
				onChange={(e) => setText(e.target.value)}
				onPaste={(e) => {
					const image = imageFrom(e.clipboardData && e.clipboardData.items);
					if (image) {
						e.preventDefault();
						readImage(image);
					}
				}}
			/>
			<button type="button" disabled={!text.trim() || busy || isReading} onClick={submit}>
				{busy ? '성분 맞추는 중...' : '목록에 넣기'}
			</button>
			{lowMatch && (
				<div role="alert" className="confirm-ocr-lowmatch">
					<p>
						읽은 성분 {lowMatch.total}개 중 사전과 맞는 성분이 {lowMatch.matched}개뿐이에요. 글자를 잘못 읽었을 수 있어요. 아래 목록에서
						&quot;사전에 없음&quot; 성분을 고치거나, 방금 넣은 성분을 빼고 다시 해 주세요.
					</p>
					<button
						type="button"
						onClick={() => {
							if (onUndo) onUndo(lowMatch.keys);
							setLowMatch(null);
						}}
					>
						방금 넣은 성분 {lowMatch.total}개 빼기
					</button>
				</div>
			)}
		</div>
	);
}

// 전성분에 옵션(호수·구성품)이 여러 개일 때 내 제품의 옵션을 고른다.
// 이미 성분 목록이 있으면 하나뿐이어도 바로 덮어쓰지 않고 여기서 바꿀지 묻는다
export function SectionPicker({ sections, onApply, onDismiss }) {
	const selectId = useId();
	const [index, setIndex] = useState(0);
	if (!Array.isArray(sections) || sections.length === 0) return null;
	const single = sections.length === 1;
	const chosen = Math.min(index, sections.length - 1);
	return (
		<fieldset className="confirm-sections">
			<legend>{single ? '가져온 전성분' : `옵션 ${sections.length}개`}</legend>
			{single ? (
				<p className="auth-hint">가져온 전성분 {sections[0].items.length}개로 지금 목록을 바꿀까요?</p>
			) : (
				<div className="confirm-field">
					<label htmlFor={selectId}>내 제품의 옵션</label>
					<select id={selectId} value={chosen} onChange={(e) => setIndex(Number(e.target.value))}>
						{sections.map((section, i) => (
							<option key={`${section.label}-${i}`} value={i}>
								{section.label || `옵션 ${i + 1}`} ({section.items.length}개)
							</option>
						))}
					</select>
				</div>
			)}
			<div className="confirm-link-sites">
				<button type="button" className="add-secondary" onClick={() => onApply(sections[chosen])}>
					{single ? '이 전성분으로 바꾸기' : '이 옵션 성분 넣기'}
				</button>
				<button type="button" className="add-secondary" onClick={onDismiss}>
					그대로 두기
				</button>
			</div>
		</fieldset>
	);
}

const LINK_HOSTS = ['oliveyoung.co.kr', 'www.oliveyoung.co.kr', 'm.oliveyoung.co.kr'];

// 복사한 글에서 올리브영 링크만 꺼낸다. 앱 공유 글처럼 앞뒤에 다른 글자가 있어도 된다. 없으면 null
export function findProductLink(text) {
	const urls = String(text || '').match(/https?:\/\/[^\s<>"']+/gi) || [];
	for (const candidate of urls) {
		try {
			if (LINK_HOSTS.includes(new URL(candidate).hostname.toLowerCase())) return candidate;
		} catch {
			// 주소 모양이 아니면 다음 후보를 본다
		}
	}
	return null;
}

// 사용자 브라우저에서 사이트 검색 결과를 연다. 우리 서버는 검색하지 않는다
export const SEARCH_SITES = [
	{
		key: 'oliveyoung',
		label: '올리브영에서 찾기',
		url: (q) => `https://www.oliveyoung.co.kr/store/search/getSearchMain.do?query=${encodeURIComponent(q)}`,
	},
];

// 검색어 기본값: 입력해 둔 브랜드 + 제품명
export function linkQuery(form) {
	return form ? [form.brand, form.name].map((v) => String(v || '').trim()).filter(Boolean).join(' ') : '';
}

function readClipboard() {
	if (!navigator.clipboard || !navigator.clipboard.readText) return Promise.reject(new Error('unsupported'));
	return navigator.clipboard.readText();
}

// 지원하는 공개 페이지에서 확인 가능한 정보만 채운다.
// 검색 버튼으로 사이트를 연 뒤 이 화면으로 돌아오면 복사해 둔 링크를 읽어 자동으로 가져온다
export function LinkImport({ link, onImport, onChange, query = '' }) {
	const inputId = useId();
	const searchId = useId();
	const [url, setUrl] = useState('');
	const [searchText, setSearchText] = useState('');
	const [waiting, setWaiting] = useState(false);
	const [clipNotice, setClipNotice] = useState('');
	const loading = Boolean(link && link.loading);
	const term = searchText.trim() || query.trim();

	const importFrom = useCallback((text, fromButton) => {
		const found = findProductLink(text);
		if (!found) {
			if (fromButton) setClipNotice('복사한 내용에 올리브영 제품 링크가 없어요.');
			return false;
		}
		setClipNotice('');
		setWaiting(false);
		setUrl(found);
		onChange?.();
		onImport(found);
		return true;
	}, [onChange, onImport]);

	// 검색 탭에서 돌아왔을 때 한 번만 클립보드를 본다. 권한이 없거나 브라우저가 막으면 버튼으로 안내한다
	useEffect(() => {
		if (!waiting) return undefined;
		const check = () => {
			if (document.visibilityState === 'hidden') return;
			readClipboard()
				.then((text) => importFrom(text, false))
				.catch(() => {});
		};
		window.addEventListener('focus', check);
		document.addEventListener('visibilitychange', check);
		return () => {
			window.removeEventListener('focus', check);
			document.removeEventListener('visibilitychange', check);
		};
	}, [waiting, importFrom]);

	const pasteFromClipboard = () => {
		readClipboard()
			.then((text) => importFrom(text, true))
			.catch(() => setClipNotice('클립보드를 읽지 못했어요. 링크를 아래 입력칸에 직접 붙여넣어 주세요.'));
	};

	return (
		<div className="confirm-link">
			<div className="confirm-link-search">
				<label htmlFor={searchId}>제품명으로 링크 찾기</label>
				<p className="auth-hint">올리브영에서 제품을 찾은 뒤, 상세페이지 링크를 복사해서 이 화면으로 돌아오면 자동으로 채워져요.</p>
				<input
					id={searchId}
					type="search"
					maxLength={100}
					placeholder={query.trim() || '예: 닥터지 선크림'}
					value={searchText}
					onChange={(e) => {
						// 검색칸에 상세페이지 링크를 붙여넣어도 바로 가져온다
						if (importFrom(e.target.value, false)) setSearchText('');
						else setSearchText(e.target.value);
					}}
				/>
				<div className="confirm-link-sites">
					{SEARCH_SITES.map((site) =>
						term ? (
							<a
								key={site.key}
								className="add-secondary confirm-site-link"
								href={site.url(term)}
								target="_blank"
								rel="noopener noreferrer"
								onClick={() => {
									setClipNotice('');
									setWaiting(true);
								}}
							>
								{site.label}
							</a>
						) : (
							<button key={site.key} type="button" className="add-secondary" disabled>
								{site.label}
							</button>
						)
					)}
					<button type="button" className="add-secondary" onClick={pasteFromClipboard}>
						복사한 링크 넣기
					</button>
				</div>
				{clipNotice && <p className="auth-hint" aria-live="polite">{clipNotice}</p>}
			</div>
			<label htmlFor={inputId}>상세페이지 링크</label>
			<p className="auth-hint">올리브영 제품 링크를 붙여넣으면 제품명·사진·전성분을 가져와요. 가져오지 못하면 전성분을 직접 붙여넣어 주세요.</p>
			<div className="confirm-link-row">
				<input
					id={inputId}
					type="url"
					inputMode="url"
					maxLength={2000}
					placeholder="제품 상세페이지 URL을 붙여넣어 주세요"
					value={url}
					onChange={(e) => {
						setUrl(e.target.value);
						onChange?.();
					}}
					onPaste={(e) => {
						const pasted = e.clipboardData.getData('text').trim();
						if (!/^https?:\/\//i.test(pasted)) return;
						e.preventDefault();
						setUrl(pasted);
						onImport(pasted);
					}}
				/>
				<button type="button" disabled={!url.trim()} onClick={() => onImport(url.trim())}>
					{loading ? '다시 가져오기' : '가져오기'}
				</button>
			</div>
			{link && link.error && <ErrorText error={link.error} />}
			{link && link.done && (
				<p role="status" className="auth-notice">
					{link.notice || '가져온 정보를 확인해 주세요. 없는 항목은 직접 입력해 주세요.'}
				</p>
			)}
		</div>
	);
}

export function productTitle(product) {
	if (!product) return '';
	return [product.brand, product.name].filter(Boolean).join(' ');
}

// 브랜드+제품명으로 기존 제품 후보를 보여주고 "이 제품인가요?"를 묻는다
function ProductCandidates({ search, onChoose }) {
	if (!search) return null;
	if (search.loading) return <p aria-busy="true">같은 제품을 찾는 중...</p>;
	if (search.error) return <ErrorText error={search.error} />;
	if (search.results.length === 0) {
		return <p className="auth-hint">등록된 같은 제품이 없어요. 새 제품으로 저장돼요.</p>;
	}
	return (
		<fieldset className="confirm-candidates">
			<legend>이 제품인가요?</legend>
			<ul>
				{search.results.map((product) => (
					<li key={product.id}>
						<span>
							{productTitle(product)}
							{product.volume ? ` · ${product.volume}` : ''}
						</span>
						<button type="button" onClick={() => onChoose(product)}>
							이 제품이에요
						</button>
					</li>
				))}
			</ul>
			<button type="button" className="add-secondary" onClick={() => onChoose(null)}>
				목록에 없어요 (새 제품으로 저장)
			</button>
		</fieldset>
	);
}

// 제품 정보: 브랜드, 제품명, 개봉 후 사용기한, 분류 (설계 4.1-8. 용량·바코드 입력은 v-4로 뺐다)
export function ProductInfoForm({ form, chosenProduct, search, brands, onField, onSearch, onChoose, onClearChoice }) {
	const baseId = useId();
	if (chosenProduct) {
		return (
			<section className="confirm-product" aria-label="제품 정보">
				<h3>제품 정보</h3>
				<p className="add-product-title" data-testid="chosen-product">
					{productTitle(chosenProduct)}
				</p>
				<p className="auth-hint">이미 등록된 이 제품에 성분을 저장해요.</p>
				<button type="button" className="add-secondary" onClick={onClearChoice}>
					다른 제품으로 바꾸기
				</button>
			</section>
		);
	}
	const field = (name, label, props = {}) => (
		<div className="confirm-field">
			<label htmlFor={`${baseId}-${name}`}>{label}</label>
			<input
				id={`${baseId}-${name}`}
				type="text"
				value={form[name]}
				onChange={(e) => onField(name, e.target.value)}
				{...props}
			/>
		</div>
	);
	return (
		<section className="confirm-product" aria-label="제품 정보">
			<h3>제품 정보</h3>
			{field('brand', '브랜드', { maxLength: 100, list: `${baseId}-brands`, autoComplete: 'off' })}
			{/* 등록된 브랜드 표기로 고르게 해 같은 브랜드를 다르게 적지 않게 한다(2026-10-26) */}
			<datalist id={`${baseId}-brands`}>
				{(brands || []).map((b) => (
					<option key={b} value={b} />
				))}
			</datalist>
			{field('name', '제품명', { maxLength: 200 })}
			<div className="confirm-field">
				<label htmlFor={`${baseId}-pao`}>개봉 후 사용기한(개월)</label>
				<input
					id={`${baseId}-pao`}
					type="number"
					inputMode="numeric"
					min={1}
					max={60}
					value={form.paoMonths || ''}
					onChange={(e) => onField('paoMonths', e.target.value)}
				/>
				<p className="auth-hint">용기 뒷면의 열린 통 그림 숫자(예: 12M)예요. 개봉일은 화장대에서 열 때 넣어요.</p>
			</div>
			<div className="confirm-field">
				<label htmlFor={`${baseId}-category`}>분류</label>
				<select id={`${baseId}-category`} value={form.category} onChange={(e) => onField('category', e.target.value)}>
					{Object.entries(CATEGORIES).map(([value, label]) => (
						<option key={value} value={value}>
							{label}
						</option>
					))}
				</select>
			</div>
			<button
				type="button"
				className="add-secondary"
				disabled={!form.brand.trim() || !form.name.trim() || (search && search.loading)}
				onClick={onSearch}
			>
				같은 제품이 있는지 찾기
			</button>
			<ProductCandidates search={search} onChoose={onChoose} />
		</section>
	);
}

// 새 제품을 저장하기 전 이름 점검 결과(2026-10-26 제품명 예외처리). 올리브영식 이름 정리, 등록된 브랜드 표기,
// 같거나 비슷한 제품을 보여 주고, 고치거나 기존 제품을 고르거나 입력한 그대로 저장하게 한다
export function NameCheckPanel({ nameCheck, busy, onApplyBrand, onApplyName, onChooseChecked, onSaveAnyway }) {
	if (!nameCheck) return null;
	if (nameCheck.loading) return <p aria-busy="true" className="auth-hint">제품 이름을 확인하는 중...</p>;
	const r = nameCheck.result;
	const hasSame = r.similar.some((s) => s.sameName);
	return (
		<section className="confirm-namecheck" aria-label="저장 전 확인">
			<h3>저장하기 전에 확인해 주세요</h3>
			{r.brandSuggestion && (
				<div className="confirm-namecheck-row">
					<p>
						{r.brandKnown
							? `등록된 브랜드 표기는 "${r.brandSuggestion}"예요.`
							: `처음 보는 브랜드예요. 혹시 "${r.brandSuggestion}"인가요?`}
					</p>
					<button type="button" disabled={busy} onClick={() => onApplyBrand(r.brandSuggestion)}>
						&quot;{r.brandSuggestion}&quot;로 바꾸기
					</button>
				</div>
			)}
			{!r.brandKnown && !r.brandSuggestion && (
				<p className="auth-hint">처음 등록되는 브랜드예요. 브랜드 이름이 맞는지 한 번 더 확인해 주세요.</p>
			)}
			{r.reasons.length > 0 && (
				<div className="confirm-namecheck-row">
					<p>
						제품명은 &quot;{r.name}&quot;{r.volume ? `, 용량은 ${r.volume}` : ''}(으)로 저장하는 게 좋아요.
					</p>
					<ul>
						{r.reasons.map((reason) => (
							<li key={reason}>{reason}</li>
						))}
					</ul>
					<button type="button" disabled={busy} onClick={() => onApplyName(r)}>
						정리한 이름 쓰기
					</button>
				</div>
			)}
			{r.similar.length > 0 && (
				<fieldset className="confirm-candidates">
					<legend>{hasSame ? '같은 이름의 제품이 이미 있어요' : '비슷한 이름의 제품이 있어요'}</legend>
					<ul>
						{r.similar.map((s) => (
							<li key={s.product.id}>
								<span>
									{productTitle(s.product)}
									{s.product.volume ? ` · ${s.product.volume}` : ''}
									{s.sameName ? '' : ` · ${s.similarity}% 비슷`}
								</span>
								<button type="button" disabled={busy} onClick={() => onChooseChecked(s.product)}>
									이 제품이에요
								</button>
							</li>
						))}
					</ul>
				</fieldset>
			)}
			<button type="button" className="add-secondary" disabled={busy} onClick={onSaveAnyway}>
				{r.similar.length > 0 ? '다른 제품이에요 (입력한 그대로 저장)' : '입력한 그대로 저장'}
			</button>
		</section>
	);
}

export function SaveButton({ busy, onSave, className = 'auth-primary' }) {
	return (
		<button type="button" className={className} onClick={onSave} disabled={busy}>
			{busy ? '저장하는 중...' : '확인하고 화장대에 담기'}
		</button>
	);
}

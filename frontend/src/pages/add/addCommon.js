import { useId, useState } from 'react';
import CropBox from '../../components/CropBox';
import ConfirmIngredients from './ConfirmIngredients';
import './AddProduct.css';

// 제품 추가 폰·PC 화면이 함께 쓰는 단계별 조각

// 추출 실패 이유 (설계 6.2, 계획 Task 15·20의 error_code). 실패한 추출은 한도에 세지 않는다
export const FAILURE_REASONS = {
	AI_TIMEOUT: '성분을 읽는 데 시간이 너무 오래 걸렸어요.',
	AI_UNAVAILABLE: '지금 성분 읽기 서비스를 쓸 수 없어요.',
	AI_INVALID_RESPONSE: '사진에서 성분을 제대로 읽지 못했어요.',
	AI_REFUSED: '이 사진에서는 성분을 읽을 수 없었어요.',
	SERVER_RESTARTED: '서버가 다시 시작되어 성분 읽기가 멈췄어요.',
	AI_BUSY: '잠시 문제가 생겼어요. 다시 시도해 주세요.',
	INTERNAL_ERROR: '잠시 문제가 생겼어요. 다시 시도해 주세요.',
};
// 알 수 없는 코드도 일반 안내로 보여준다
const DEFAULT_FAILURE = '잠시 문제가 생겼어요. 다시 시도해 주세요.';

export function failureReason(errorCode) {
	return FAILURE_REASONS[errorCode] || DEFAULT_FAILURE;
}

export function productTitle(product) {
	if (!product) return '';
	return [product.brand, product.name].filter(Boolean).join(' ');
}

function ErrorText({ error }) {
	return error ? <p role="alert" className="auth-error">{error}</p> : null;
}

// 제품을 등록하는 방법. 먼저 다른 사용자가 등록한 제품을 이름으로 찾고(v-6), 없으면 붙여넣기로 등록한다.
// 바코드 등록은 2026-10-23 사용자 결정으로 뺐다(v-5). 붙여넣기는 AI를 부르지 않아 API 키 없이도 쓸 수 있다
export const METHODS = [
	{ key: 'search', label: '제품 찾기' },
	{ key: 'photo', label: '사진(AI)' },
	{ key: 'paste', label: '상세페이지 붙여넣기' },
];

// 사진(AI) 등록은 2026-10-23 사용자 결정으로 화면에서 숨긴다(코드는 남겨 둠).
// frontend/.env에 REACT_APP_PHOTO_AI=true를 넣고 npm start를 다시 하면 다시 보인다
export function photoAiEnabled() {
	return process.env.REACT_APP_PHOTO_AI === 'true';
}

function visibleMethods() {
	return photoAiEnabled() ? METHODS : METHODS.filter((m) => m.key !== 'photo');
}

// 등록 방법 탭. 좌우 화살표로도 옮긴다
export function MethodTabs({ method, busy, className, onSearchMethod, onPhotoMethod, onPasteDirect }) {
	const actions = { search: onSearchMethod, photo: onPhotoMethod, paste: onPasteDirect };
	const choose = (key) => {
		if (key !== method && !busy && actions[key]) actions[key]();
	};
	const onKeyDown = (event) => {
		if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
		event.preventDefault();
		const methods = visibleMethods();
		const index = methods.findIndex((m) => m.key === method);
		const step = event.key === 'ArrowRight' ? 1 : methods.length - 1;
		choose(methods[(index + step) % methods.length].key);
	};
	return (
		<div
			role="tablist"
			aria-label="제품 등록 방법"
			className={className ? `add-tabs ${className}` : 'add-tabs'}
			onKeyDown={onKeyDown}
		>
			{visibleMethods().map((m) => (
				<button
					key={m.key}
					type="button"
					role="tab"
					aria-selected={m.key === method}
					tabIndex={m.key === method ? 0 : -1}
					className={m.key === method ? 'is-active' : undefined}
					disabled={busy && m.key !== method}
					onClick={() => choose(m.key)}
				>
					{m.label}
				</button>
			))}
		</div>
	);
}

// 다른 사용자가 이미 등록한 제품을 이름으로 찾는다. 성분이 확인된 제품은 바로 담고, 없으면 붙여넣기로 등록한다
export function SearchStep({ search, busy, error, onSearchProducts, onChooseProduct, onPhotoMethod, onPasteDirect, ...rest }) {
	const inputId = useId();
	const [text, setText] = useState(search.query || '');
	const results = search.results || [];
	const submit = (event) => {
		event.preventDefault();
		if (text.trim().length >= 2) onSearchProducts(text.trim());
	};
	return (
		<div className="add-step">
			<MethodTabs {...rest} method="search" busy={busy} onPhotoMethod={onPhotoMethod} onPasteDirect={onPasteDirect} />
			<h2>제품 찾기</h2>
			<p className="auth-hint">다른 사람이 이미 등록한 제품이면 전성분 입력 없이 바로 담을 수 있어요.</p>
			<form role="search" className="add-search" onSubmit={submit} aria-label="등록된 제품 찾기">
				<label htmlFor={inputId}>브랜드나 제품명</label>
				<div className="confirm-link-row">
					<input
						id={inputId}
						type="search"
						maxLength={100}
						placeholder="예: 닥터지 선크림"
						value={text}
						onChange={(e) => setText(e.target.value)}
					/>
					<button type="submit" disabled={text.trim().length < 2 || search.loading}>
						{search.loading ? '찾는 중...' : '찾기'}
					</button>
				</div>
			</form>
			{search.done && results.length > 0 && (
				<ul className="add-search-results" aria-label="찾은 제품">
					{results.map((product) => (
						<li key={product.id}>
							<span>{productTitle(product)}</span>
							<button
								type="button"
								className="add-secondary"
								disabled={busy || !product.representativeListId}
								onClick={() => onChooseProduct(product)}
							>
								{product.representativeListId ? '이 제품이에요' : '성분 확인 전'}
							</button>
						</li>
					))}
				</ul>
			)}
			{search.done && results.length === 0 && (
				<p role="status" className="auth-notice">
					'{search.query}' 제품은 아직 없어요. 상세페이지의 전성분을 붙여넣어 등록해 주세요.
				</p>
			)}
			{busy && <p aria-busy="true">불러오는 중...</p>}
			<ErrorText error={error} />
			<button type="button" className="add-secondary" onClick={onPasteDirect} disabled={busy}>
				찾는 제품이 없어요 (붙여넣기로 등록)
			</button>
		</div>
	);
}

export function FoundStep({ product, representative, busy, error, onAddFound, onRestart }) {
	const items = (representative && Array.isArray(representative.items) && representative.items) || [];
	return (
		<div className="add-step">
			<h2>등록된 제품이 있어요</h2>
			<p className="add-product-title">{productTitle(product)}</p>
			{product.volume && <p className="auth-hint">{product.volume}</p>}
			{items.length > 0 && (
				<ol className="add-ingredients" aria-label="대표 성분">
					{items.map((item) => (
						<li key={item.position}>{item.name}</li>
					))}
				</ol>
			)}
			<ErrorText error={error} />
			<button type="button" className="auth-primary" onClick={onAddFound} disabled={busy}>
				내 화장대에 담기
			</button>
			<button type="button" className="add-secondary" onClick={onRestart} disabled={busy}>
				다른 제품 찾기
			</button>
		</div>
	);
}

// 사진 고르기와 접수 버튼. 폰은 input이 카메라 촬영·앨범 고르기를 함께 띄운다
export function PhotoPicker({ file, busy, error, onFile, onSubmitPhoto, onRestart, ...rest }) {
	const fileId = useId();
	return (
		<div className="add-step">
			<MethodTabs {...rest} method="photo" busy={busy} onSearchMethod={onRestart} />
			<h2>전성분 사진</h2>
			<p className="auth-hint">전성분이 잘 보이게, 흔들리지 않게 찍어 주세요. AI가 성분을 읽어요.</p>
			<label htmlFor={fileId} className="add-file-label">
				전성분 사진 고르기
			</label>
			<input
				id={fileId}
				type="file"
				accept="image/*"
				disabled={busy}
				onChange={(e) => onFile(e.target.files && e.target.files[0])}
			/>
			<ErrorText error={error} />
			<button type="button" className="auth-primary" onClick={onSubmitPhoto} disabled={!file || busy}>
				{busy ? '사진을 보내는 중...' : '성분 읽기'}
			</button>
			<button type="button" className="add-secondary" onClick={onRestart} disabled={busy}>
				제품 찾기부터 다시
			</button>
		</div>
	);
}

export function PhotoCrop({ previewUrl, onCrop }) {
	if (!previewUrl) {
		return <p className="auth-hint">사진을 고르면 여기에서 전성분 부분을 자를 수 있어요.</p>;
	}
	return <CropBox imageUrl={previewUrl} onChange={onCrop} />;
}

export function WaitingStep({ timedOut, error, onRecheck, onRetake }) {
	if (!timedOut) {
		return (
			<div className="add-step" aria-busy="true">
				<h2>성분 읽는 중</h2>
				<p>성분을 읽고 있어요...</p>
				<p className="auth-hint">보통 30초 안에 끝나요. 이 화면을 그대로 두세요.</p>
			</div>
		);
	}
	return (
		<div className="add-step">
			<h2>성분 읽는 중</h2>
			<p>아직 결과가 나오지 않았어요. 잠시 뒤 다시 확인해 주세요.</p>
			<ErrorText error={error} />
			<button type="button" className="auth-primary" onClick={onRecheck}>
				다시 확인
			</button>
			<button type="button" className="add-secondary" onClick={onRetake}>
				다시 찍기
			</button>
		</div>
	);
}

export function FailedStep({ job, onPaste, onRetake }) {
	return (
		<div className="add-step">
			<h2>성분을 읽지 못했어요</h2>
			<p role="alert" className="auth-error">
				{failureReason(job && job.errorCode)} 오늘 횟수에는 세지 않았어요.
			</p>
			<button type="button" className="auth-primary" onClick={onPaste}>
				성분을 직접 붙여넣기
			</button>
			<button type="button" className="add-secondary" onClick={onRetake}>
				다시 찍기
			</button>
		</div>
	);
}

// 사진 없이 바로 붙여넣기로 시작한 job인가(AI 실패 뒤 붙여넣기와 구분)
function isManualJob(job) {
	return Boolean(job && job.errorCode === 'MANUAL_INPUT');
}

// 성분 확인 화면 (Task 25). jobId별로 편집 내용을 따로 보관하므로 job이 바뀌면 새로 그린다.
// 바로 붙여넣기로 들어왔으면 위에 등록 방법 탭을 두어 제품 찾기·사진(AI)으로 바꿀 수 있다
export function ConfirmStep({ job, pasteMode, previewUrl, pasteImage, busy, onRetake, onRestart, onAlreadyConfirmed }) {
	const confirm = (
		<ConfirmIngredients
			key={`${job && job.jobId}-${pasteMode ? 'paste' : 'result'}`}
			job={job}
			pasteMode={pasteMode}
			previewUrl={previewUrl}
			initialImage={pasteImage}
			onRetake={onRetake}
			onAlreadyConfirmed={onAlreadyConfirmed}
		/>
	);
	if (!pasteMode || !isManualJob(job)) return confirm;
	return (
		<>
			<MethodTabs
				method="paste"
				busy={busy}
				className="add-tabs-confirm"
				onSearchMethod={onRestart}
				onPhotoMethod={onRetake}
			/>
			{confirm}
		</>
	);
}

// 지금 단계의 왼쪽(폰은 전체) 내용. 사진 자르기는 화면마다 놓는 자리가 달라 따로 둔다
export function StepPanel(props) {
	switch (props.step) {
		case 'found':
			return <FoundStep {...props} />;
		case 'photo':
			return <PhotoPicker {...props} />;
		case 'waiting':
			return <WaitingStep {...props} />;
		case 'failed':
			return <FailedStep {...props} />;
		case 'confirm':
			return <ConfirmStep {...props} />;
		default:
			return <SearchStep {...props} />;
	}
}

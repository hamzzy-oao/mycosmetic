import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getListDiff, getProduct } from '../../api/products';
import HomeLink from '../../components/HomeLink';
import ResponsivePage from '../../components/ResponsivePage';
import { errorMessage } from '../../lib/errorMessage';

// 제품 상세 (설계 4.6, 7장): 대표 성분 버전, 다른 버전 알림, 두 버전 차이.
// 폰은 한 열, PC는 대표 성분과 버전 비교를 좌우로
const STATUS_LABELS = {
	VERIFIED: '검증됨',
	PENDING: '확인 대기',
};

const CATEGORY_LABELS = {
	SKINCARE: '스킨케어',
	MAKEUP: '메이크업',
};

function productTitle(product) {
	if (!product) return '';
	return [product.brand, product.name].filter(Boolean).join(' ');
}

function statusText(list) {
	return `${STATUS_LABELS[list.status] || list.status} · 확인 ${list.confirmCount}명`;
}

function versionLabel(list, isRepresentative) {
	const date = list.createdAt ? ` · ${String(list.createdAt).slice(0, 10)}` : '';
	return `${isRepresentative ? '대표 버전' : `버전 #${list.id}`} · ${statusText(list)}${date}`;
}

function ProductHeader({ product }) {
	const facts = [product.volume, CATEGORY_LABELS[product.category]].filter(
		Boolean
	);
	return (
		<div className="product-head">
			<div>
				<h1>{productTitle(product)}</h1>
				{facts.length > 0 && <p className="auth-hint">{facts.join(' · ')}</p>}
			</div>
		</div>
	);
}

function RepresentativeList({ representative }) {
	if (!representative || !Array.isArray(representative.items)) {
		return <p>아직 성분 정보가 없어요.</p>;
	}
	return (
		<section className="product-ingredients" aria-label="대표 성분">
			<h2>대표 성분</h2>
			<p className="auth-hint">{statusText(representative)}</p>
			<ol>
				{representative.items.map((item) => (
					<li key={item.position}>
						{item.name}
						{item.matchType === 'UNMATCHED' && <span className="product-unmatched"> (사전에 없음)</span>}
					</li>
				))}
			</ol>
		</section>
	);
}

function DiffList({ title, names }) {
	return (
		<div className="product-diff-list">
			<h3>{title}</h3>
			{names.length === 0 ? (
				<p className="auth-hint">없음</p>
			) : (
				<ul aria-label={title}>
					{names.map((name, index) => (
						<li key={`${index}-${name}`}>{name}</li>
					))}
				</ul>
			)}
		</div>
	);
}

// 다른 버전이 있으면 알리고, 두 버전을 골라 차이(GET /api/products/{id}/lists/diff)를 보여준다
function VersionCompare({ productId, representative, otherLists }) {
	const baseId = useId();
	const others = Array.isArray(otherLists) ? otherLists : [];
	const versions = [
		...(representative ? [{ list: representative, isRepresentative: true }] : []),
		...others.map((list) => ({ list, isRepresentative: false })),
	];
	const [from, setFrom] = useState(others.length > 0 ? String(others[0].id) : '');
	const [to, setTo] = useState(representative ? String(representative.id) : '');
	const [diff, setDiff] = useState(null);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	const mountedRef = useRef(true);

	useEffect(() => {
		mountedRef.current = true;
		return () => {
			mountedRef.current = false;
		};
	}, []);

	if (others.length === 0) {
		return <p className="auth-hint">이 제품의 성분 버전은 하나예요.</p>;
	}

	const onCompare = async () => {
		if (!from || !to || from === to) {
			setError('서로 다른 두 버전을 골라 주세요.');
			setDiff(null);
			return;
		}
		setError('');
		setBusy(true);
		try {
			const result = await getListDiff(productId, from, to);
			if (mountedRef.current) setDiff(result);
		} catch (e) {
			if (mountedRef.current) setError(errorMessage(e));
		} finally {
			if (mountedRef.current) setBusy(false);
		}
	};

	const select = (key, label, value, onChange) => (
		<div className="confirm-field">
			<label htmlFor={`${baseId}-${key}`}>{label}</label>
			<select
				id={`${baseId}-${key}`}
				value={value}
				onChange={(e) => {
					onChange(e.target.value);
					setDiff(null);
				}}
			>
				{versions.map(({ list, isRepresentative }) => (
					<option key={list.id} value={String(list.id)}>
						{versionLabel(list, isRepresentative)}
					</option>
				))}
			</select>
		</div>
	);

	const added = (diff && Array.isArray(diff.added) && diff.added) || [];
	const removed = (diff && Array.isArray(diff.removed) && diff.removed) || [];
	return (
		<section className="product-versions" aria-label="성분 버전 비교">
			<p role="status" className="shelf-new-version">
				다른 성분 버전이 {others.length}개 있어요. 리뉴얼되었거나 사용자가 다르게 확인한 성분이에요.
			</p>
			<h2>버전 비교</h2>
			{select('from', '기준 버전', from, setFrom)}
			{select('to', '비교할 버전', to, setTo)}
			{error && <p role="alert" className="auth-error">{error}</p>}
			<button type="button" className="add-secondary" onClick={onCompare} disabled={busy}>
				{busy ? '비교하는 중...' : '차이 보기'}
			</button>
			{diff && (
				<div className="product-diff" aria-label="버전 차이">
					{added.length === 0 && removed.length === 0 && !diff.orderChanged ? (
						<p>두 버전의 성분이 같아요.</p>
					) : (
						<>
							<DiffList title="추가된 성분" names={added} />
							<DiffList title="빠진 성분" names={removed} />
							{diff.orderChanged && <p>성분 순서가 바뀌었어요.</p>}
						</>
					)}
				</div>
			)}
		</section>
	);
}

function DetailBody({ loading, loadError, onReload }) {
	if (loading) return <p aria-busy="true">불러오는 중...</p>;
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
	return null;
}

function ProductDetailMobile(props) {
	const { detail, id } = props;
	return (
		<section data-layout="mobile" className="product-mobile" data-testid="product-layout">
			<HomeLink />
			<DetailBody {...props} />
			{detail && (
				<>
					<ProductHeader product={detail.product} />
					<RepresentativeList representative={detail.representative} />
					<VersionCompare productId={id} representative={detail.representative} otherLists={detail.otherLists} />
				</>
			)}
		</section>
	);
}

function ProductDetailDesktop(props) {
	const { detail, id } = props;
	return (
		<section data-layout="desktop" className="product-desktop" data-testid="product-layout">
			<HomeLink />
			<DetailBody {...props} />
			{detail && (
				<>
					<ProductHeader product={detail.product} />
					<div className="product-columns" data-testid="product-columns">
						<RepresentativeList representative={detail.representative} />
						<VersionCompare productId={id} representative={detail.representative} otherLists={detail.otherLists} />
					</div>
				</>
			)}
		</section>
	);
}

export default function ProductDetailPage() {
	const { id } = useParams();
	const [detail, setDetail] = useState(null);
	const [loading, setLoading] = useState(true);
	const [loadError, setLoadError] = useState('');

	const load = useCallback(async () => {
		setLoading(true);
		setLoadError('');
		try {
			const data = await getProduct(id);
			setDetail(data);
		} catch (e) {
			setLoadError(errorMessage(e, '제품 정보를 불러오지 못했어요.'));
		} finally {
			setLoading(false);
		}
	}, [id]);

	useEffect(() => {
		let active = true;
		setLoading(true);
		setLoadError('');
		getProduct(id)
			.then((data) => {
				if (active) setDetail(data);
			})
			.catch((e) => {
				if (active) setLoadError(errorMessage(e, '제품 정보를 불러오지 못했어요.'));
			})
			.finally(() => {
				if (active) setLoading(false);
			});
		return () => {
			active = false;
		};
	}, [id]);

	return (
		<ResponsivePage
			mobile={ProductDetailMobile}
			desktop={ProductDetailDesktop}
			id={id}
			detail={loading || loadError ? null : detail}
			loading={loading}
			loadError={loadError}
			onReload={load}
		/>
	);
}

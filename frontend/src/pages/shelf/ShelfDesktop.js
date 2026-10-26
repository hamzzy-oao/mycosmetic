import { Fragment, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getProductList } from '../../api/products';
import HomeLink from '../../components/HomeLink';
import { errorMessage } from '../../lib/errorMessage';
import { isMakeup } from '../../lib/shade';
import {
	ExpiryBadge,
	MatchedIngredients,
	NewVersionNotice,
	ShadeChip,
	ShadeEditor,
	ShelfItemEditor,
	ShelfListNotice,
	ShelfNameFilter,
	ShelfSearch,
	ShelfStatus,
	ShelfToneFilter,
	VERDICTS,
	VERDICT_LABELS,
	OpeningQuick,
	productTitle,
} from './shelfCommon';

// PC: 표 + 평가 필터·정렬 + 성분 펼쳐보기 (설계 7장)
const SORTS = {
	updated: '최근 수정순',
	name: '제품명순',
};

function compareByUpdated(a, b) {
	return (Date.parse(b.updatedAt) || 0) - (Date.parse(a.updatedAt) || 0);
}

function compareByName(a, b) {
	return productTitle(a.product).localeCompare(productTitle(b.product), 'ko');
}

function formatPeriod(item) {
	if (!item.startedOn && !item.endedOn) return '';
	return `${item.startedOn || ''} ~ ${item.endedOn || ''}`;
}

function formatDate(value) {
	return value ? String(value).slice(0, 10) : '';
}

// 펼친 행: 내가 담은 버전(GET /api/products/{id}/lists/{listId})의 성분을 보여준다 (설계 3.5-5).
// 내 버전이 대표 버전이 아니면 그렇다고 알리고 버전 차이를 볼 수 있는 상세로 이어 준다
function IngredientPanel({ item, detail }) {
	if (!detail) {
		return <p aria-busy="true">성분을 불러오는 중...</p>;
	}
	if (detail.error) {
		return <p role="alert" className="auth-error">{detail.error}</p>;
	}
	const list = detail.data;
	if (!list || !Array.isArray(list.items)) {
		return <p>성분 정보가 없어요.</p>;
	}
	return (
		<div className="shelf-ingredients">
			<h3>내 버전 성분</h3>
			{item.listIsRepresentative === false && item.product && (
				<p className="shelf-new-version">
					내가 담은 버전은 지금 대표 버전이 아니에요.{' '}
					<Link to={`/products/${item.product.id}`}>버전 차이 보기</Link>
				</p>
			)}
			<ol aria-label={`${productTitle(item.product)} 성분`}>
				{list.items.map((ingredient) => (
					<li key={ingredient.position}>{ingredient.name}</li>
				))}
			</ol>
		</div>
	);
}

function ShelfRow({ item, expanded, detail, onToggle, onUpdate, onRemove }) {
	const title = productTitle(item.product);
	return (
		<Fragment>
			<tr>
				<th scope="row">
					<span className="shelf-title">{title}</span>
					<ExpiryBadge item={item} />
					<ShadeChip item={item} />
					<NewVersionNotice item={item} />
					<MatchedIngredients item={item} />
				</th>
				<td>
					<select
						aria-label={`${title} 평가`}
						value={item.verdict}
						onChange={(e) => onUpdate(item.id, { verdict: e.target.value })}
					>
						{VERDICTS.map((verdict) => (
							<option key={verdict} value={verdict}>
								{VERDICT_LABELS[verdict]}
							</option>
						))}
					</select>
				</td>
				<td>{formatPeriod(item)}</td>
				<td>
					<OpeningQuick item={item} onUpdate={onUpdate} as="span" />
				</td>
				<td className="shelf-memo">{item.memo}</td>
				<td>{formatDate(item.updatedAt)}</td>
				<td className="shelf-row-actions">
					<button type="button" aria-expanded={expanded} aria-label={`${title} ${expanded ? '접기' : '성분·기간 보기'}`} onClick={onToggle}>
						{expanded ? '접기' : '성분·기간'}
					</button>
					<button type="button" className="me-danger" aria-label={`${title} 빼기`} onClick={() => onRemove(item.id)}>
						빼기
					</button>
				</td>
			</tr>
			{expanded && (
				<tr className="shelf-expanded">
					<td colSpan={7}>
						<IngredientPanel item={item} detail={detail} />
						<ShelfItemEditor item={item} onUpdate={onUpdate} />
						{isMakeup(item) && <ShadeEditor item={item} onUpdate={onUpdate} />}
					</td>
				</tr>
			)}
		</Fragment>
	);
}

export default function ShelfDesktop({
	items,
	allCount,
	search,
	nameFilter,
	tone,
	loading,
	loadError,
	actionError,
	onReload,
	onUpdate,
	onRemove,
}) {
	const [verdictFilter, setVerdictFilter] = useState('ALL');
	const [sort, setSort] = useState('updated');
	const [expandedId, setExpandedId] = useState(null);
	// 성분 버전 ID별 응답: { data } 또는 { error }
	const [details, setDetails] = useState({});

	const visible = useMemo(() => {
		const filtered = verdictFilter === 'ALL' ? items : items.filter((item) => item.verdict === verdictFilter);
		return [...filtered].sort(sort === 'name' ? compareByName : compareByUpdated);
	}, [items, verdictFilter, sort]);

	const toggle = async (item) => {
		if (expandedId === item.id) {
			setExpandedId(null);
			return;
		}
		setExpandedId(item.id);
		const productId = item.product && item.product.id;
		const listId = item.ingredientListId;
		if (productId == null || listId == null || (details[listId] && !details[listId].error)) {
			return;
		}
		setDetails((prev) => ({ ...prev, [listId]: undefined }));
		try {
			const data = await getProductList(productId, listId);
			setDetails((prev) => ({ ...prev, [listId]: { data } }));
		} catch (e) {
			setDetails((prev) => ({ ...prev, [listId]: { error: errorMessage(e) } }));
		}
	};

	return (
		<section data-layout="desktop" className="shelf-desktop" aria-labelledby="shelf-title">
			<HomeLink />
			<div className="shelf-desktop-head">
				<h1 id="shelf-title" className="visually-hidden">내 제품</h1>
				
			</div>
			{allCount > 0 && (
				<div className="shelf-search-grid">
					{nameFilter && <ShelfNameFilter nameFilter={nameFilter} />}
					<ShelfSearch search={search} />
				</div>
			)}
			{tone && tone.available && <ShelfToneFilter tone={tone} count={items.length} />}
			<ShelfStatus loading={loading} loadError={loadError} actionError={actionError} onReload={onReload} />
			<ShelfListNotice
				search={search}
				nameFilter={nameFilter}
				loading={loading}
				loadError={loadError}
				allCount={allCount}
				count={items.length}
			/>
			{items.length > 0 && (
				<>
					<div className="shelf-controls">
						<label>
							평가 필터{' '}
							<select value={verdictFilter} onChange={(e) => setVerdictFilter(e.target.value)}>
								<option value="ALL">전체</option>
								{VERDICTS.map((verdict) => (
									<option key={verdict} value={verdict}>
										{VERDICT_LABELS[verdict]}
									</option>
								))}
							</select>
						</label>
						<label>
							정렬{' '}
							<select value={sort} onChange={(e) => setSort(e.target.value)}>
								{Object.entries(SORTS).map(([value, label]) => (
									<option key={value} value={value}>
										{label}
									</option>
								))}
							</select>
						</label>
					</div>
					{visible.length === 0 ? (
						<p>이 평가에 해당하는 제품이 없어요.</p>
					) : (
						<div className="shelf-table-scroll">
						<table className="shelf-table">
							<colgroup>
								<col className="shelf-col-product" />
								<col className="shelf-col-verdict" />
								<col className="shelf-col-period" />
								<col className="shelf-col-opening" />
								<col className="shelf-col-memo" />
								<col className="shelf-col-date" />
								<col className="shelf-col-actions" />
							</colgroup>
							<thead>
								<tr>
									<th scope="col">제품</th>
									<th scope="col">평가</th>
									<th scope="col">사용 기간</th>
									<th scope="col">개봉</th>
									<th scope="col">메모</th>
									<th scope="col">수정일</th>
									<th scope="col">
										관리
									</th>
								</tr>
							</thead>
							<tbody>
								{visible.map((item) => (
									<ShelfRow
										key={item.id}
										item={item}
										expanded={expandedId === item.id}
										detail={item.product ? details[item.ingredientListId] : null}
										onToggle={() => toggle(item)}
										onUpdate={onUpdate}
										onRemove={onRemove}
									/>
								))}
							</tbody>
						</table>
						</div>
					)}
				</>
			)}
		</section>
	);
}

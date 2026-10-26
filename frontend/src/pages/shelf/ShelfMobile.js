import { useState } from 'react';
import HomeLink from '../../components/HomeLink';
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
	VERDICT_LABELS,
	OpeningQuick,
	productTitle,
} from './shelfCommon';

// 폰: 카드 목록 + 맞음/안 맞음 빠른 버튼 (설계 7장)
const QUICK_VERDICTS = ['GOOD', 'BAD'];

function ShelfCard({ item, onUpdate, onRemove }) {
	const [open, setOpen] = useState(false);
	const title = productTitle(item.product);
	const makeup = isMakeup(item);

	// 이미 고른 평가를 다시 누르면 평가 전으로 되돌린다
	const onQuick = (verdict) => {
		onUpdate(item.id, { verdict: item.verdict === verdict ? 'UNRATED' : verdict });
	};

	return (
		<li className="shelf-card" aria-label={title}>
			<div className="shelf-card-head">
				<span className="shelf-title">{title}</span>
				<span className="shelf-badges">
					<ExpiryBadge item={item} />
					<span className={`shelf-verdict shelf-verdict-${item.verdict}`}>{VERDICT_LABELS[item.verdict] || item.verdict}</span>
				</span>
			</div>
			<NewVersionNotice item={item} />
			<MatchedIngredients item={item} />
			{(item.shadeHex || item.shadeName) && (
				<p className="shelf-shade-line">
					<ShadeChip item={item} />
				</p>
			)}
			<OpeningQuick item={item} onUpdate={onUpdate} />
			{item.memo && <p className="shelf-memo">{item.memo}</p>}
			<div className="shelf-quick">
				{QUICK_VERDICTS.map((verdict) => (
					<button
						key={verdict}
						type="button"
						aria-pressed={item.verdict === verdict}
						className={item.verdict === verdict ? 'is-selected' : undefined}
						onClick={() => onQuick(verdict)}
					>
						{VERDICT_LABELS[verdict]}
					</button>
				))}
				<button type="button" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
					{open ? '접기' : makeup ? '메모·기간·호수' : '메모·기간'}
				</button>
			</div>
			{open && (
				<>
					<ShelfItemEditor item={item} onUpdate={onUpdate} />
					{makeup && <ShadeEditor item={item} onUpdate={onUpdate} />}
					<button type="button" className="me-danger" onClick={() => onRemove(item.id)}>
						화장대에서 빼기
					</button>
				</>
			)}
		</li>
	);
}

export default function ShelfMobile({
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
	return (
		<section data-layout="mobile" className="shelf-mobile" aria-labelledby="shelf-title">
			<HomeLink />
			<div className="shelf-desktop-head"><h1 id="shelf-title" className="visually-hidden">내 제품</h1></div>
			{allCount > 0 && nameFilter && <ShelfNameFilter nameFilter={nameFilter} />}
			{allCount > 0 && <ShelfSearch search={search} />}
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
				<ul className="shelf-cards">
					{items.map((item) => (
						<ShelfCard key={item.id} item={item} onUpdate={onUpdate} onRemove={onRemove} />
					))}
				</ul>
			)}
		</section>
	);
}

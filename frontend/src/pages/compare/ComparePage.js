import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { compareProducts } from '../../api/compare';
import { getShelf } from '../../api/shelf';
import HomeLink from '../../components/HomeLink';
import ResponsivePage from '../../components/ResponsivePage';
import { errorMessage } from '../../lib/errorMessage';
import { VERDICTS, VERDICT_LABELS, productTitle } from '../shelf/shelfCommon';
import './ComparePage.css';

// 제품 비교 (no-ai-features 설계 B). 고른 두 제품은 주소(?a=&b= 또는 ?aProduct=&bProduct=)에 둔다.
// 주소의 ?aProduct=로도 들어올 수 있다. 폰은 공통/A만/B만을 탭으로, PC는 3열로 보여준다
export const GROUP_LABELS = {
	RETINOID: '레티노이드',
	AHA: 'AHA',
	BHA: 'BHA',
	VITAMIN_C: '비타민C',
	BENZOYL_PEROXIDE: '벤조일퍼옥사이드',
	NIACINAMIDE: '나이아신아마이드',
};

const SIDES = [
	{ key: 'a', productKey: 'aProduct', label: 'A 제품' },
	{ key: 'b', productKey: 'bProduct', label: 'B 제품' },
];

// Keep keyword matching aligned with the server's IngredientGroup.
const GROUP_KEYWORDS = {
	RETINOID: ['레티노이드', '레티놀', '레티날', '레티닐', '트레티노인', '아다팔렌', 'retinol', 'retinal', 'retinyl', 'tretinoin', 'adapalene'],
	AHA: ['글라이콜릭애씨드', '락틱애씨드', '만델릭애씨드', 'glycolicacid', 'lacticacid', 'mandelicacid'],
	BHA: ['살리실릭애씨드', '베타인살리실레이트', 'salicylicacid', 'betainesalicylate'],
	VITAMIN_C: ['비타민c', '아스코빅애씨드', '아스코빌', '에칠아스코빌', 'ascorbicacid', 'ascorbyl'],
	BENZOYL_PEROXIDE: ['벤조일퍼옥사이드', 'benzoylperoxide'],
	NIACINAMIDE: ['나이아신아마이드', 'niacinamide'],
};

function ingredientGroups(name) {
	const key = name.normalize('NFKC').toLowerCase().replace(/\s+/g, '');
	return Object.entries(GROUP_KEYWORDS)
		.filter(([, keywords]) => keywords.some((keyword) => key.includes(keyword)))
		.map(([group]) => GROUP_LABELS[group]);
}

// 주소의 한쪽 선택 → 'item:12' | 'product:34' | ''
function pickFromParams(params, side) {
	if (params.get(side.key)) return `item:${params.get(side.key)}`;
	if (params.get(side.productKey)) return `product:${params.get(side.productKey)}`;
	return '';
}

function requestPart(pick, side) {
	const [kind, id] = pick.split(':');
	return kind === 'item' ? { [side.key]: id } : { [side.productKey]: id };
}

// 화장대 제품을 평가(맞음·안 맞음·평가 전)별로 묶어 보여준다(v-6)
function SidePicker({ side, pick, items, result, onPick }) {
	const id = `compare-${side.key}`;
	const fromProduct = pick.startsWith('product:');
	const pickedProduct = result && result[side.key] && result[side.key].product;
	return (
		<div className="compare-picker">
			<label htmlFor={id}>{side.label}</label>
			<select id={id} value={pick} onChange={(e) => onPick(side, e.target.value)}>
				<option value="">내 화장대에서 고르기</option>
				{fromProduct && (
					<option value={pick}>{pickedProduct ? productTitle(pickedProduct) : '제품 상세에서 고른 제품'} (대표 성분)</option>
				)}
				{VERDICTS.map((verdict) => {
					const group = items.filter((item) => (item.verdict || 'UNRATED') === verdict);
					if (group.length === 0) return null;
					return (
						<optgroup key={verdict} label={VERDICT_LABELS[verdict]}>
							{group.map((item) => (
								<option key={item.id} value={`item:${item.id}`}>
									{productTitle(item.product)}
								</option>
							))}
						</optgroup>
					);
				})}
			</select>
		</div>
	);
}

function GroupChips({ groups }) {
	const list = Array.isArray(groups) ? groups : [];
	if (list.length === 0) return <p className="compare-groups-empty">주의할 성분 그룹 없음</p>;
	return (
		<ul className="compare-groups" aria-label="성분 그룹">
			{list.map((group) => (
				<li key={group}>{GROUP_LABELS[group] || group}</li>
			))}
		</ul>
	);
}

function SideSummary({ title, side, groups }) {
	return (
		<div className="compare-side">
			<p className="compare-side-label">{title}</p>
			{side && side.product ? <p className="compare-side-name">{productTitle(side.product)}</p> : null}
			<GroupChips groups={groups} />
		</div>
	);
}

function columnsOf(result) {
	return [
		{ key: 'common', title: '공통 성분', short: '공통', names: result.common || [] },
		{ key: 'onlyA', title: 'A에만 있는 성분', short: 'A만', names: result.onlyA || [] },
		{ key: 'onlyB', title: 'B에만 있는 성분', short: 'B만', names: result.onlyB || [] },
	];
}

function NameList({ title, names }) {
	if (names.length === 0) return <p className="compare-none">없음</p>;
	return (
		<ul className="compare-names" aria-label={title}>
			{names.map((name, index) => {
				const groups = ingredientGroups(name);
				return (
					<li key={`${index}-${name}`}>
						<span>{name}</span>
						{groups.length > 0 && (
							<span className="compare-ingredient-marker" role="img" aria-label={`주의 성분: ${groups.join(', ')}`} title={groups.join(', ')}>🔴</span>
						)}
					</li>
				);
			})}
		</ul>
	);
}

// 폰: 탭 3개(공통/A만/B만)
function ResultTabs({ result }) {
	const columns = columnsOf(result);
	const [active, setActive] = useState('common');
	const current = columns.find((c) => c.key === active) || columns[0];
	const onKeyDown = (event) => {
		const index = columns.findIndex((c) => c.key === active);
		if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
			event.preventDefault();
			const step = event.key === 'ArrowRight' ? 1 : columns.length - 1;
			setActive(columns[(index + step) % columns.length].key);
		}
	};
	return (
		<div className="compare-tabs-wrap">
			<div role="tablist" aria-label="비교 결과" className="compare-tabs" onKeyDown={onKeyDown}>
				{columns.map((c) => (
					<button
						key={c.key}
						type="button"
						role="tab"
						id={`compare-tab-${c.key}`}
						aria-selected={c.key === active}
						aria-controls="compare-tabpanel"
						tabIndex={c.key === active ? 0 : -1}
						className={c.key === active ? 'is-active' : undefined}
						onClick={() => setActive(c.key)}
					>
						{c.short} {c.names.length}
					</button>
				))}
			</div>
			<div role="tabpanel" id="compare-tabpanel" aria-labelledby={`compare-tab-${current.key}`} className="compare-panel">
				<NameList title={current.title} names={current.names} />
			</div>
		</div>
	);
}

// PC: 3열
function ResultColumns({ result }) {
	return (
		<div className="compare-columns">
			{columnsOf(result).map((c) => (
				<section key={c.key} className={`compare-column is-${c.key}`} aria-labelledby={`compare-col-${c.key}`}>
					<h2 id={`compare-col-${c.key}`}>
						{c.title} <span className="compare-count">{c.names.length}개</span>
					</h2>
					<NameList title={c.title} names={c.names} />
				</section>
			))}
		</div>
	);
}

function CompareBody({ layout, items, loading, loadError, onReload, picks, onPick, result, comparing, compareError }) {
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
	const noChoices = items.length === 0 && !picks.a.startsWith('product:') && !picks.b.startsWith('product:');
	if (noChoices) {
		return (
			<p className="compare-empty">
				비교하려면 먼저 화장대에 제품을 담아 주세요. <Link to="/add">제품 추가하기</Link>
			</p>
		);
	}
	return (
		<>
			<div className="compare-pickers">
				{SIDES.map((side) => (
					<SidePicker key={side.key} side={side} pick={picks[side.key]} items={items} result={result} onPick={onPick} />
				))}
			</div>
			{(!picks.a || !picks.b) && <p className="auth-hint">두 제품을 고르면 성분을 비교해요.</p>}
			{comparing && <p aria-busy="true">비교하는 중...</p>}
			{compareError && <p role="alert" className="auth-error">{compareError}</p>}
			{result && !comparing && (
				<div className="compare-result" aria-label="비교 결과">
					<div className="compare-sides">
						<SideSummary title="A" side={result.a} groups={result.groupsA} />
						<SideSummary title="B" side={result.b} groups={result.groupsB} />
					</div>
					{layout === 'mobile' ? <ResultTabs result={result} /> : <ResultColumns result={result} />}
				</div>
			)}
		</>
	);
}

function CompareHead() {
	return (
		<div className="compare-head">
			<h1 id="compare-title" className="visually-hidden">내 제품 성분 비교</h1>
			<nav aria-label="비교 메뉴" className="compare-links">
				<Link to="/shelf">내 화장대</Link>
				<Link to="/advice">구매 적합도 확인</Link>
			</nav>
		</div>
	);
}

function CompareMobile(props) {
	return (
		<section data-layout="mobile" className="compare-page compare-mobile" aria-labelledby="compare-title">
			<HomeLink />
			<CompareHead />
			<CompareBody layout="mobile" {...props} />
		</section>
	);
}

function CompareDesktop(props) {
	return (
		<section data-layout="desktop" className="compare-page compare-desktop" aria-labelledby="compare-title">
			<HomeLink />
			<CompareHead />
			<CompareBody layout="desktop" {...props} />
		</section>
	);
}

export default function ComparePage() {
	const [params, setParams] = useSearchParams();
	const pickA = pickFromParams(params, SIDES[0]);
	const pickB = pickFromParams(params, SIDES[1]);
	const [items, setItems] = useState([]);
	const [loading, setLoading] = useState(true);
	const [loadError, setLoadError] = useState('');
	const [result, setResult] = useState(null);
	const [comparing, setComparing] = useState(false);
	const [compareError, setCompareError] = useState('');

	const load = useCallback(async () => {
		setLoading(true);
		setLoadError('');
		try {
			const data = await getShelf();
			setItems(Array.isArray(data) ? data : []);
		} catch (e) {
			setLoadError(errorMessage(e));
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		load();
	}, [load]);

	useEffect(() => {
		setCompareError('');
		if (!pickA || !pickB) {
			setResult(null);
			return undefined;
		}
		let active = true;
		setComparing(true);
		compareProducts({ ...requestPart(pickA, SIDES[0]), ...requestPart(pickB, SIDES[1]) })
			.then((data) => {
				if (active) setResult(data);
			})
			.catch((e) => {
				if (active) {
					setResult(null);
					setCompareError(errorMessage(e));
				}
			})
			.finally(() => {
				if (active) setComparing(false);
			});
		return () => {
			active = false;
		};
	}, [pickA, pickB]);

	const onPick = useCallback(
		(side, value) => {
			const next = new URLSearchParams(params);
			next.delete(side.key);
			next.delete(side.productKey);
			if (value) {
				const [kind, id] = value.split(':');
				next.set(kind === 'item' ? side.key : side.productKey, id);
			}
			setParams(next, { replace: true });
		},
		[params, setParams]
	);

	return (
		<ResponsivePage
			mobile={CompareMobile}
			desktop={CompareDesktop}
			items={items}
			loading={loading}
			loadError={loadError}
			onReload={load}
			picks={{ a: pickA, b: pickB }}
			onPick={onPick}
			result={result}
			comparing={comparing}
			compareError={compareError}
		/>
	);
}

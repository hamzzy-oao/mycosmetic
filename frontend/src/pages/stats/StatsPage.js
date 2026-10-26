import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { getShelfStats } from '../../api/shelf';
import HomeLink from '../../components/HomeLink';
import ResponsivePage from '../../components/ResponsivePage';
import { errorMessage } from '../../lib/errorMessage';
import { ExpiryBadge, VERDICTS, VERDICT_LABELS, productTitle } from '../shelf/shelfCommon';
import '../shelf/ShelfPage.css';
import './StatsPage.css';

// 화장대 통계 (no-ai-features 설계 D): 숫자 카드 + CSS 막대(차트 라이브러리 없음) + 사용기한 임박·지남 목록
const CATEGORY_LABELS = {
	SKINCARE: '스킨케어',
	MAKEUP: '메이크업',
};

function NumberCards({ stats }) {
	const cards = [
		{ key: 'total', label: '전체 제품', value: `${stats.total}개` },
		{ key: 'inUse', label: '쓰는 중', value: `${stats.inUse}개` },
		{ key: 'finished', label: '다 쓴 제품', value: `${stats.finished}개` },
	];
	return (
		<dl className="stats-cards">
			{cards.map((card) => (
				<div key={card.key} className="stats-card">
					<dt>{card.label}</dt>
					<dd>{card.value}</dd>
				</div>
			))}
		</dl>
	);
}

// 막대 길이는 가장 큰 값 대비 비율. 막대는 장식이고 숫자는 글자로 보여준다
function BarChart({ id, title, rows }) {
	const max = Math.max(1, ...rows.map((r) => r.value));
	return (
		<section className="stats-bars" aria-labelledby={id}>
			<h2 id={id}>{title}</h2>
			<ul>
				{rows.map((row) => (
					<li key={row.key} className="stats-bar-row">
						<span className="stats-bar-label">{row.label}</span>
						<span className="stats-bar-track" aria-hidden="true">
							<span data-testid="stats-bar-fill" className={`stats-bar-fill is-${row.key}`} style={{ width: `${(row.value / max) * 100}%` }} />
						</span>
						<span className="stats-bar-value">{row.value}개</span>
					</li>
				))}
			</ul>
		</section>
	);
}

function ExpiryList({ id, title, items, empty }) {
	const list = Array.isArray(items) ? items : [];
	return (
		<section className="stats-expiry" aria-labelledby={id}>
			<h2 id={id}>
				{title} <span className="stats-count">{list.length}개</span>
			</h2>
			{list.length === 0 ? (
				<p className="auth-hint">{empty}</p>
			) : (
				<ul>
					{list.map((item) => (
						<li key={item.id}>
							{item.product ? <span>{productTitle(item.product)}</span> : null}
							<span className="stats-expiry-meta">
								<ExpiryBadge item={item} />
								{item.expiresOn && <span>{item.expiresOn}까지</span>}
							</span>
						</li>
					))}
				</ul>
			)}
		</section>
	);
}

// /stats#skin-ingredients로 들어오면 이 구역으로 스크롤한다
export const SKIN_INGREDIENTS_ANCHOR = 'skin-ingredients';

// 맞음·안 맞음 평가로 가려낸 성분(v-8). 평가가 모자라면 무엇을 더 하면 되는지 알려준다
function IngredientInsights({ insights }) {
	const { hash } = useLocation();
	const ref = useRef(null);
	useEffect(() => {
		if (insights && hash === `#${SKIN_INGREDIENTS_ANCHOR}` && ref.current && ref.current.scrollIntoView) {
			ref.current.scrollIntoView({ block: 'start' });
		}
	}, [insights, hash]);
	if (!insights) return null;
	const title = (
		<h2 id="stats-insights">
			내 피부와 성분 <span className="stats-count">맞음 {insights.goodCount} · 안 맞음 {insights.badCount}</span>
		</h2>
	);
	if (!insights.ready) {
		return (
			<section id={SKIN_INGREDIENTS_ANCHOR} ref={ref} className="stats-insights" aria-labelledby="stats-insights">
				{title}
				<p className="auth-hint">
					안 맞음 제품 2개, 맞음 제품 1개 이상을 평가하면 어떤 성분이 나와 안 맞는지 찾아 드려요.{' '}
					<Link to="/shelf">평가하러 가기</Link>
				</p>
			</section>
		);
	}
	const list = (items, empty, describe) =>
		items.length === 0 ? (
			<p className="auth-hint">{empty}</p>
		) : (
			<ul>
				{items.map((item) => (
					<li key={item.name}>
						<strong>{item.name}</strong> <span className="stats-expiry-meta">{describe(item)}</span>
					</li>
				))}
			</ul>
		);
	return (
		<section id={SKIN_INGREDIENTS_ANCHOR} ref={ref} className="stats-insights" aria-labelledby="stats-insights">
			{title}
			<h3>의심 성분</h3>
			{list(
				insights.suspects,
				'안 맞는 제품에만 겹치는 성분이 아직 없어요.',
				(item) => `안 맞음 ${item.badCount}개에 있음${item.goodCount ? ` · 맞음 ${item.goodCount}개에도 있음` : ''}`
			)}
			<h3>잘 맞는 성분</h3>
			{list(insights.friendly, '맞는 제품에만 겹치는 성분이 아직 없어요.', (item) => `맞음 ${item.goodCount}개에 있음`)}
			<p className="auth-hint">내 평가 기록에서 겹치는 성분을 센 참고 결과예요. 의학적 진단이 아니에요.</p>
		</section>
	);
}

function StatsBody({ stats, loading, loadError, onReload }) {
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
	if (!stats) return null;
	if (stats.total === 0) {
		return (
			<p className="stats-empty">
				아직 화장대에 담은 제품이 없어요. <Link to="/add">제품 추가하기</Link>
			</p>
		);
	}
	const byCategory = stats.byCategory || {};
	const byVerdict = stats.byVerdict || {};
	return (
		<>
			<section className="stats-section" aria-labelledby="stats-products-heading">
			<h2 id="stats-products-heading" className="stats-section-title">제품현황</h2>
			<NumberCards stats={stats} />
			<div className="stats-grid">
				<BarChart
					id="stats-category"
					title="분류별"
					rows={Object.keys(CATEGORY_LABELS).map((key) => ({
						key,
						label: CATEGORY_LABELS[key],
						value: byCategory[key] || 0,
					}))}
				/>
				<BarChart
					id="stats-verdict"
					title="평가별"
					rows={VERDICTS.map((key) => ({ key, label: VERDICT_LABELS[key], value: byVerdict[key] || 0 }))}
				/>
			</div>
			</section>
			<section className="stats-section" aria-labelledby="stats-expiry-heading">
			<h2 id="stats-expiry-heading" className="stats-section-title">사용기한</h2>
			<div className="stats-grid">
				<ExpiryList
					id="stats-expiring"
					title="사용기한 임박 (30일 내로 임박해요)"
					items={stats.expiring}
					empty="30일 안에 사용기한이 끝나는 제품이 없어요."
				/>
				<ExpiryList id="stats-expired" title="사용기한 지남" items={stats.expired} empty="사용기한이 지난 제품이 없어요." />
			</div>
			</section>
			<section className="stats-section" aria-labelledby="stats-ingredients-heading">
			<h2 id="stats-ingredients-heading" className="stats-section-title">성분 분석</h2>
			<IngredientInsights insights={stats.ingredientInsights} />
			</section>
		</>
	);
}

function StatsHead() {
	return (
		<div className="stats-head">
			<h1 id="stats-title" className="visually-hidden">화장대 현황</h1>
			<nav aria-label="통계 메뉴" className="stats-links">
				<Link to="/shelf">내 화장대</Link>
				<Link to="/compare">비교</Link>
			</nav>
		</div>
	);
}

function StatsMobile(props) {
	return (
		<section data-layout="mobile" className="stats-page stats-mobile" aria-labelledby="stats-title">
			<HomeLink />
			<StatsHead />
			<StatsBody {...props} />
		</section>
	);
}

function StatsDesktop(props) {
	return (
		<section data-layout="desktop" className="stats-page stats-desktop" aria-labelledby="stats-title">
			<HomeLink />
			<StatsHead />
			<StatsBody {...props} />
		</section>
	);
}

export default function StatsPage() {
	const [stats, setStats] = useState(null);
	const [loading, setLoading] = useState(true);
	const [loadError, setLoadError] = useState('');

	const load = useCallback(async () => {
		setLoading(true);
		setLoadError('');
		try {
			setStats(await getShelfStats());
		} catch (e) {
			setLoadError(errorMessage(e));
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		load();
	}, [load]);

	return (
		<ResponsivePage
			mobile={StatsMobile}
			desktop={StatsDesktop}
			stats={stats}
			loading={loading}
			loadError={loadError}
			onReload={load}
		/>
	);
}

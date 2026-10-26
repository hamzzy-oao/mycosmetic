import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { checkToday, getRoutineUsage, getRoutines, getTodayRoutines } from '../../api/routines';
import HomeLink from '../../components/HomeLink';
import ResponsivePage from '../../components/ResponsivePage';
import { errorMessage } from '../../lib/errorMessage';
import { DAYS, RoutineCard, STEP_LABELS, WarningList, stepTitle } from './routineCommon';
import './RoutinePage.css';

// 루틴 탭: 화장대 진단에서 저장한 루틴으로 오늘 사용 체크, 오늘 루틴의 성분 충돌 경고, 쌓인 사용 기록
const SLOTS = [
	{ key: 'morning', slot: 'MORNING', label: '아침', checked: 'checkedMorning' },
	{ key: 'evening', slot: 'EVENING', label: '저녁', checked: 'checkedEvening' },
];

// 이 시간대 루틴들의 단계. 같은 제품이 두 루틴에 있으면 한 번만
function slotSteps(routines) {
	const seen = new Set();
	return routines.flatMap((r) => r.steps).filter((s) => !seen.has(s.shelfItemId) && seen.add(s.shelfItemId));
}

function SlotChecklist({ label, slot, steps, checked, onCheck, busy }) {
	const done = steps.filter((s) => checked.includes(s.shelfItemId)).length;
	const left = steps.filter((s) => !checked.includes(s.shelfItemId)).map((s) => s.shelfItemId);
	const titleId = `routine-check-${slot}`;
	return (
		<div className="vanity-routine-checklist" role="group" aria-labelledby={titleId}>
			<div className="vanity-routine-checklist-head">
				<h3 id={titleId}>{label}</h3>
				<span className="vanity-routine-progress">
					{done}/{steps.length}
				</span>
			</div>
			<ul>
				{steps.map((s) => {
					const id = `routine-check-${slot}-${s.shelfItemId}`;
					return (
						<li key={s.shelfItemId}>
							<input
								id={id}
								type="checkbox"
								checked={checked.includes(s.shelfItemId)}
								disabled={busy}
								onChange={(e) => onCheck(slot, [s.shelfItemId], e.target.checked)}
							/>
							<label htmlFor={id}>
								<span className="vanity-routine-step-type">{STEP_LABELS[s.stepType] || s.stepType}</span> {stepTitle(s)}
							</label>
						</li>
					);
				})}
			</ul>
			{left.length > 0 ? (
				<button type="button" className="vanity-routine-secondary" disabled={busy} onClick={() => onCheck(slot, left, true)}>
					{label} 모두 했어요
				</button>
			) : (
				<p className="vanity-routine-ok">{label} 루틴을 다 했어요.</p>
			)}
		</div>
	);
}

// 오늘(서버 기준 Asia/Seoul) 아침·저녁에 쓸 제품 체크와 성분 충돌 경고
function TodayPanel({ today, hasRoutines, onCheck, busy, checkError }) {
	if (!today) return null;
	const day = DAYS.find((d) => d.value === today.dayOfWeek);
	const hasWarnings = (today.warnings || []).some((warning) => warning.level === 'WARNING' || warning.level === 'CAUTION');
	const slots = SLOTS.map((s) => ({ ...s, steps: slotSteps(today[s.key]) })).filter((s) => s.steps.length > 0);
	const names = Object.fromEntries(slots.flatMap((s) => s.steps).map((s) => [s.shelfItemId, stepTitle(s)]));
	return (
		<section className="vanity-routine-today" aria-labelledby="routine-today-title">
			<h2 id="routine-today-title">오늘 루틴{day ? ` (${day.label}요일)` : ''}</h2>
			{slots.length === 0 ? (
				<>
					<p>오늘 하는 루틴이 없어요.</p>
					{!hasRoutines && (
						<Link className="vanity-routine-primary" to="/vanity/analysis">
							화장대 진단에서 루틴 가져오기
						</Link>
					)}
				</>
			) : (
				<>
					<p className="vanity-routine-hint">쓴 제품을 체크하면 기록이 쌓여요.</p>
					{slots.map((s) => (
						<SlotChecklist
							key={s.slot}
							label={s.label}
							slot={s.slot}
							steps={s.steps}
							checked={today[s.checked] || []}
							onCheck={onCheck}
							busy={busy}
						/>
					))}
					{checkError && (
						<p role="alert" className="auth-error">
							{checkError}
						</p>
					)}
					<div className="vanity-routine-combination">
						<h3 className={hasWarnings ? 'has-warning' : undefined}>{hasWarnings ? '성분 충돌 경고' : '성분 조합 확인'}</h3>
						<WarningList warnings={today.warnings} names={names} />
					</div>
				</>
			)}
		</section>
	);
}

// 최근 4주 사용 일수. 꾸준히 쓴 제품의 잘맞아요는 성분 분석(v-8)에서 더 믿을 수 있다
function UsagePanel({ usage }) {
	if (!usage) return null;
	return (
		<section className="vanity-routine-usage" aria-labelledby="routine-usage-title">
			<h2 id="routine-usage-title">최근 {usage.days / 7}주 사용</h2>
			{usage.items.length === 0 ? (
				<p>오늘 사용을 체크하면 제품마다 며칠 썼는지 쌓여요.</p>
			) : (
				<ul>
					{usage.items.map((i) => (
						<li key={i.shelfItemId}>
							<span className="vanity-routine-usage-name">{[i.brand, i.productName].filter(Boolean).join(' ')}</span>
							<span className="vanity-routine-usage-days">{i.daysUsed}일</span>
							{i.steady && <span className="vanity-routine-badge">{i.verdict === 'GOOD' ? '꾸준히 쓴 잘맞아요' : '꾸준히 씀'}</span>}
						</li>
					))}
				</ul>
			)}
			<p className="vanity-routine-hint">
				{usage.days}일 중 {usage.steadyDays}일 이상 쓴 제품의 잘맞아요는 더 믿을 수 있어요. <Link to="/stats">성분 분석 보기</Link>
			</p>
		</section>
	);
}

function AllRoutines({ routines }) {
	return (
		<section className="vanity-routine-all" aria-labelledby="routine-all-title">
			<div className="vanity-routine-section-head">
				<h2 id="routine-all-title">내 루틴 {routines.length}개</h2>
			</div>
			{routines.length === 0 ? (
				<p>아직 만든 루틴이 없어요. 화장대 진단에서 가진 제품으로 만든 초안을 루틴으로 저장해 보세요.</p>
			) : (
				<ul className="vanity-routine-cards">
					{routines.map((r) => (
						<RoutineCard key={r.id} routine={r} />
					))}
				</ul>
			)}
		</section>
	);
}

function Status({ loading, error, onReload }) {
	if (loading) return <p aria-busy="true">불러오는 중...</p>;
	if (error) {
		return (
			<div role="alert" className="auth-error">
				<p>{error}</p>
				<button type="button" onClick={onReload}>
					다시 시도
				</button>
			</div>
		);
	}
	return null;
}

function Saved({ saved }) {
	if (!saved) return null;
	return (
		<p role="status" className="vanity-routine-saved">
			화장대 진단에서 루틴을 저장했어요. 오늘 쓴 제품을 체크해 보세요.
		</p>
	);
}

function ListHead() {
	return (
		<div className="vanity-routine-head">
			<h1 id="routine-title">내 루틴</h1>
			<Link className="vanity-routine-primary" to="/routines/new">
				<span aria-hidden="true">+</span> 루틴 만들기
			</Link>
		</div>
	);
}

function RoutineListMobile({ loading, error, onReload, routines, today, usage, onCheck, busy, checkError, saved }) {
	return (
		<section data-layout="mobile" className="vanity-routine vanity-routine-mobile vanity-routine-list" aria-labelledby="routine-title">
			<HomeLink />
			<ListHead />
			<Status loading={loading} error={error} onReload={onReload} />
			{!loading && !error && (
				<>
					<Saved saved={saved} />
					<TodayPanel today={today} hasRoutines={routines.length > 0} onCheck={onCheck} busy={busy} checkError={checkError} />
					<AllRoutines routines={routines} />
					<UsagePanel usage={usage} />
				</>
			)}
		</section>
	);
}

function RoutineListDesktop({ loading, error, onReload, routines, today, usage, onCheck, busy, checkError, saved }) {
	return (
		<section data-layout="desktop" className="vanity-routine vanity-routine-desktop vanity-routine-list" aria-labelledby="routine-title">
			<HomeLink />
			<ListHead />
			<Status loading={loading} error={error} onReload={onReload} />
			{!loading && !error && (
				<>
					<Saved saved={saved} />
					<div className="vanity-routine-columns">
						<TodayPanel today={today} hasRoutines={routines.length > 0} onCheck={onCheck} busy={busy} checkError={checkError} />
						<div>
							<AllRoutines routines={routines} />
							<UsagePanel usage={usage} />
						</div>
					</div>
				</>
			)}
		</section>
	);
}

// 루틴 탭 (/routines)
export default function RoutineListPage() {
	const location = useLocation();
	const [routines, setRoutines] = useState([]);
	const [today, setToday] = useState(null);
	const [usage, setUsage] = useState(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState('');
	const [busy, setBusy] = useState(false);
	const [checkError, setCheckError] = useState('');

	const load = useCallback(async () => {
		setLoading(true);
		setError('');
		try {
			const [all, todays, used] = await Promise.all([getRoutines(), getTodayRoutines(), getRoutineUsage()]);
			setRoutines(Array.isArray(all) ? all : []);
			setToday(todays || null);
			setUsage(used || null);
		} catch (e) {
			setError(errorMessage(e));
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		load();
	}, [load]);

	const onCheck = async (slot, shelfItemIds, checked) => {
		if (busy) return;
		setBusy(true);
		setCheckError('');
		try {
			setToday(await checkToday(slot, shelfItemIds, checked));
			setUsage(await getRoutineUsage());
		} catch (e) {
			setCheckError(errorMessage(e));
		} finally {
			setBusy(false);
		}
	};

	return (
		<ResponsivePage
			mobile={RoutineListMobile}
			desktop={RoutineListDesktop}
			loading={loading}
			error={error}
			onReload={load}
			routines={routines}
			today={today}
			usage={usage}
			onCheck={onCheck}
			busy={busy}
			checkError={checkError}
			saved={Boolean(location.state?.savedFromDiagnosis)}
		/>
	);
}

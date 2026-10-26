import { Link } from 'react-router-dom';

// 루틴 화면들이 함께 쓰는 이름표와 작은 부품

// 선언 순서가 권장 바르는 순서다(서버 StepType과 같다)
export const STEP_TYPES = [
	{ value: 'CLEANSER', label: '클렌저' },
	{ value: 'TONER', label: '토너' },
	{ value: 'ESSENCE', label: '에센스' },
	{ value: 'SERUM', label: '세럼' },
	{ value: 'AMPOULE', label: '앰플' },
	{ value: 'EYE', label: '아이크림' },
	{ value: 'LOTION', label: '로션' },
	{ value: 'CREAM', label: '크림' },
	{ value: 'SUNSCREEN', label: '선크림' },
	{ value: 'MASK', label: '마스크' },
	{ value: 'ETC', label: '기타' },
];

export const STEP_LABELS = Object.fromEntries(STEP_TYPES.map((t) => [t.value, t.label]));

export const DAYS = [
	{ value: 'MONDAY', label: '월' },
	{ value: 'TUESDAY', label: '화' },
	{ value: 'WEDNESDAY', label: '수' },
	{ value: 'THURSDAY', label: '목' },
	{ value: 'FRIDAY', label: '금' },
	{ value: 'SATURDAY', label: '토' },
	{ value: 'SUNDAY', label: '일' },
];

export const SLOT_LABELS = { MORNING: '아침', EVENING: '저녁' };

export const LEVEL_LABELS = { WARNING: '경고', CAUTION: '주의', INFO: '안내' };

// 비면 매일, 아니면 월요일부터 '월·수·금'
export function formatDays(days) {
	if (!Array.isArray(days) || days.length === 0 || days.length === DAYS.length) return '매일';
	return DAYS.filter((d) => days.includes(d.value))
		.map((d) => d.label)
		.join('·');
}

export function stepTitle(step) {
	return [step.brand, step.productName].filter(Boolean).join(' ');
}

export function RoutineLinks() {
	return (
		<nav className="vanity-routine-links" aria-label="루틴 메뉴">
			<Link to="/shelf">내 화장대</Link>
			<Link to="/routines">내 루틴</Link>
		</nav>
	);
}

// 루틴 카드 한 장: 이름, 시간대·요일, 단계 이름 목록
export function RoutineCard({ routine }) {
	return (
		<li className="vanity-routine-card">
			<Link to={`/routines/${routine.id}`} className="vanity-routine-card-link">
				<span className="vanity-routine-card-name">
					{routine.name}
					{routine.fromDiagnosis && <span className="vanity-routine-badge">진단에서 저장</span>}
				</span>
				<span className="vanity-routine-meta">
					{SLOT_LABELS[routine.slot] || routine.slot} · {formatDays(routine.days)} · {routine.steps.length}단계
				</span>
			</Link>
			{routine.steps.length > 0 && (
				<ol className="vanity-routine-mini-steps">
					{routine.steps.map((step) => (
						<li key={step.shelfItemId}>
							<span className="vanity-routine-step-type">{STEP_LABELS[step.stepType] || step.stepType}</span>{' '}
							{stepTitle(step)}
						</li>
					))}
				</ol>
			)}
		</li>
	);
}

// 경고 목록. names는 화장대 항목 ID → 제품 이름
export function WarningList({ warnings, names }) {
	if (!warnings || warnings.length === 0) {
		return <p className="vanity-routine-ok">함께 쓰면 안 좋은 조합이 없어요.</p>;
	}
	return (
		<ul className="vanity-routine-warnings">
			{warnings.map((w, i) => (
				<li key={`${w.code}-${i}`} className={`vanity-routine-warning is-${w.level}`}>
					<span className="vanity-routine-level">{LEVEL_LABELS[w.level] || w.level}</span>
					<p>{w.message}</p>
					{w.items.length > 0 && (
						<p className="vanity-routine-warning-items">
							{w.items.map((id) => names[id] || `항목 ${id}`).join(', ')}
						</p>
					)}
				</li>
			))}
		</ul>
	);
}

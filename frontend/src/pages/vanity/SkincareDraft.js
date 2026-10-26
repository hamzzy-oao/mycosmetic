import { STEP_LABELS, STEP_TYPES } from '../routine/routineCommon';
import tips from '../../content/tips.json';

// 화장대 진단 — 스킨케어 탭(퍼스널 탭 설계 3절 A): 아침·저녁 초안, 경고, 꼭 필요한 빈 자리, 교대 후보, 단계 고르기
const TIPS = Object.fromEntries(tips.map((t) => [t.id, t]));
export const MAKEUP_TYPES = [
	{ value: 'LIP', label: '립' },
	{ value: 'CHEEK', label: '블러셔' },
	{ value: 'EYE', label: '섀도' },
	{ value: 'BASE', label: '베이스' },
];

function itemName(byId, id) {
	const item = byId[id];
	return item ? `${item.brand} ${item.name}` : `#${id}`;
}

function DraftCard({ title, steps, byId }) {
	return (
		<section className="vanity-analysis-card" aria-label={title}>
			<h3>{title}</h3>
			{steps.length === 0 ? (
				<p className="auth-hint">넣을 제품이 없어요.</p>
			) : (
				<ol className="vanity-analysis-steps">
					{steps.map((s) => (
						<li key={s.shelfItemId}>
							<span className="vanity-analysis-step">{STEP_LABELS[s.stepType] || s.stepType}</span>
							<span>{itemName(byId, s.shelfItemId)}</span>
							{s.guessed && <span className="vanity-analysis-badge">추정</span>}
						</li>
					))}
				</ol>
			)}
		</section>
	);
}

function GapCard({ gap }) {
	const tip = TIPS[gap.contentId];
	return (
		<li className="vanity-analysis-gap">
			<p>{gap.message}</p>
			{gap.ingredientHints.length > 0 && <p className="auth-hint">찾아볼 것: {gap.ingredientHints.join(', ')}</p>}
			{tip && (
				<details>
					<summary>{tip.title}</summary>
					<p>{tip.body}</p>
				</details>
			)}
		</li>
	);
}

// current가 있으면(이름으로 추정한 값) 그 값을 먼저 보여주고, 바꾸면 저장한다
export function TypePicker({ item, onPick, busy, current = '' }) {
	const options = item.category === 'MAKEUP' ? MAKEUP_TYPES : STEP_TYPES;
	const field = item.category === 'MAKEUP' ? 'makeupType' : 'stepType';
	const id = `vanity-type-${item.shelfItemId}`;
	return (
		<li className="vanity-analysis-picker">
			<label htmlFor={id}>{`${item.name} ${item.category === 'MAKEUP' ? '종류' : '단계'}`}</label>
			<select id={id} defaultValue={current} disabled={busy} onChange={(e) => e.target.value && onPick(item.shelfItemId, { [field]: e.target.value })}>
				{!current && <option value="">골라 주세요</option>}
				{options.map((o) => (
					<option key={o.value} value={o.value}>
						{o.label}
					</option>
				))}
			</select>
		</li>
	);
}

export default function SkincareDraft({ data, onPick, onSave, busy }) {
	const byId = Object.fromEntries(data.items.map((i) => [i.shelfItemId, i]));
	// 색조 항목의 종류 고르기는 색조 탭에서 한다
	const needs = data.needsType.map((id) => byId[id]).filter((i) => i && i.category === 'SKINCARE');
	// 마스크·기타는 매일 루틴에 넣지 않는다(초안에서 빠진 제품이 사라져 보이지 않게 따로 보여준다)
	const guessed = data.items.filter((i) => i.category === 'SKINCARE' && i.guessed && i.stepType);
	const occasional = data.items.filter((i) => i.category === 'SKINCARE' && (i.stepType === 'MASK' || i.stepType === 'ETC'));
	const { morning, evening } = data.routineDraft;
	const skincareCount = data.items.filter((i) => i.category === 'SKINCARE').length;

	if (skincareCount === 0) {
		return <p className="auth-hint">내 화장대에 사용 중인 제품을 등록하면 가진 제품으로 아침·저녁 루틴 초안을 만들어 드려요.</p>;
	}

	return (
		<div className="vanity-analysis-skincare">
			<div className="vanity-analysis-drafts">
				<DraftCard title="아침 루틴" steps={morning} byId={byId} />
				<DraftCard title="저녁 루틴" steps={evening} byId={byId} />
			</div>
			{data.warnings.length > 0 && (
				<ul className="vanity-analysis-warnings">
					{data.warnings.map((w) => (
						<li key={`${w.code}-${w.items.join('-')}`} data-level={w.level}>{w.message}</li>
					))}
				</ul>
			)}
			{data.gaps.length > 0 && (
				<section aria-labelledby="vanity-gaps-title">
					<h3 id="vanity-gaps-title">꼭 필요한 단계</h3>
					<ul className="vanity-analysis-gaps">
						{data.gaps.map((g) => <GapCard key={g.role} gap={g} />)}
					</ul>
				</section>
			)}
			<p className="auth-hint">토너·에센스·세럼은 없어도 괜찮아요.</p>
			{occasional.length > 0 && (
				<p className="auth-hint">매일 루틴에 넣지 않은 제품(마스크·기타): {occasional.map((i) => `${i.brand} ${i.name}`).join(', ')}</p>
			)}
			{data.alternates.map((a) => (
				<p key={a.stepType} className="auth-hint">
					{STEP_LABELS[a.stepType] || a.stepType} 교대로 쓸 수 있어요: {a.shelfItemIds.map((id) => itemName(byId, id)).join(', ')}
				</p>
			))}
			{needs.length > 0 && (
				<section aria-labelledby="vanity-needs-title">
					<h3 id="vanity-needs-title">종류를 골라 주세요</h3>
					<p className="auth-hint">이름만으로는 어떤 단계인지 알 수 없었어요. 고르면 초안에 들어가요.</p>
					<ul className="vanity-analysis-pickers">
						{needs.map((item) => <TypePicker key={item.shelfItemId} item={item} onPick={onPick} busy={busy} />)}
					</ul>
				</section>
			)}
			{guessed.length > 0 && (
				<details className="vanity-analysis-guessed">
					<summary>이름으로 추정한 단계 확인하기({guessed.length})</summary>
					<p className="auth-hint">틀린 단계가 있으면 바꿔 주세요. 바꾸면 내 화장대에 저장돼요.</p>
					<ul className="vanity-analysis-pickers">
						{guessed.map((item) => <TypePicker key={item.shelfItemId} item={item} onPick={onPick} busy={busy} current={item.stepType} />)}
					</ul>
				</details>
			)}
			{(morning.length > 0 || evening.length > 0) && (
				<div className="vanity-analysis-save">
					<button type="button" className="auth-primary" onClick={onSave} disabled={busy}>
						루틴으로 저장
					</button>
					<p className="auth-hint">루틴 탭에서 매일 사용 체크와 성분 충돌 경고를 볼 수 있어요. 다시 저장하면 전에 진단에서 저장한 루틴이 새 초안으로 바뀌고, 직접 만든 루틴은 그대로예요.</p>
				</div>
			)}
		</div>
	);
}

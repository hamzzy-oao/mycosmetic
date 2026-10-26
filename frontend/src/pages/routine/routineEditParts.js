import { useState } from 'react';
import { Link } from 'react-router-dom';
import { DAYS, STEP_TYPES, WarningList } from './routineCommon';

// 루틴 편집 화면(폰·PC)이 함께 쓰는 부품. 상태는 RoutineEditPage가 갖고, 여기서는 보여주고 알리기만 한다

export function RoutineBasics({ name, slot, days, onName, onSlot, onToggleDay }) {
	return (
		<div className="vanity-routine-basics">
			<label htmlFor="routine-name">루틴 이름</label>
			<input id="routine-name" value={name} maxLength={50} onChange={(e) => onName(e.target.value)} />
			<span id="routine-slot-label" className="vanity-routine-field-label">시간대</span>
			<div className="vanity-routine-chips" role="group" aria-labelledby="routine-slot-label">
				{[
					['MORNING', '아침'],
					['EVENING', '저녁'],
				].map(([value, label]) => (
					<button
						key={value}
						type="button"
						className={slot === value ? 'vanity-routine-chip is-selected' : 'vanity-routine-chip'}
						aria-pressed={slot === value}
						onClick={() => onSlot(value)}
					>
						{label}
					</button>
				))}
			</div>
			<span id="routine-days-label" className="vanity-routine-field-label">요일</span>
			<div className="vanity-routine-chips is-days" role="group" aria-labelledby="routine-days-label">
				{DAYS.map(({ value, label }) => {
					const on = days.includes(value);
					return (
						<button
							key={value}
							type="button"
							className={on ? 'vanity-routine-chip is-day is-selected' : 'vanity-routine-chip is-day'}
							aria-pressed={on}
							onClick={() => onToggleDay(value)}
						>
							{label}
						</button>
					);
				})}
			</div>
			<p className="auth-hint">요일을 고르지 않으면 매일 하는 루틴이에요.</p>
		</div>
	);
}

function Chevron({ up }) {
	return (
		<svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
			<path d={up ? 'M3 10l5-5 5 5' : 'M3 6l5 5 5-5'} fill="none" stroke="currentColor" strokeWidth="2" />
		</svg>
	);
}

function StepTypeSelect({ id, label, value, onChange }) {
	return (
		<select id={id} aria-label={label} value={value} onChange={(e) => onChange(e.target.value)}>
			{STEP_TYPES.map((t) => (
				<option key={t.value} value={t.value}>
					{t.label}
				</option>
			))}
		</select>
	);
}

export function StepList({ steps, names, warnedIds, onType, onMove, onRemove }) {
	if (steps.length === 0) {
		return <p className="vanity-routine-empty-steps">아직 단계가 없어요. 아래에서 내 화장대 제품을 골라 넣어 주세요.</p>;
	}
	return (
		<ol className="vanity-routine-steps" aria-label="루틴 단계">
			{steps.map((step, index) => {
				const title = names[step.shelfItemId] || `항목 ${step.shelfItemId}`;
				return (
					<li
						key={step.shelfItemId}
						className={warnedIds.has(step.shelfItemId) ? 'vanity-routine-step is-warned' : 'vanity-routine-step'}
						aria-label={title}
					>
						<span className="vanity-routine-step-no" aria-hidden="true">
							{index + 1}
						</span>
						<span className="vanity-routine-step-title">{title}</span>
						<StepTypeSelect label={`${title} 단계 종류`} value={step.stepType} onChange={(v) => onType(index, v)} />
						<span className="vanity-routine-step-actions">
							<button type="button" aria-label={`${title} 위로`} disabled={index === 0} onClick={() => onMove(index, -1)}>
								<Chevron up />
							</button>
							<button
								type="button"
								aria-label={`${title} 아래로`}
								disabled={index === steps.length - 1}
								onClick={() => onMove(index, 1)}
							>
								<Chevron />
							</button>
							<button type="button" className="me-danger" aria-label={`${title} 빼기`} onClick={() => onRemove(index)}>
								빼기
							</button>
						</span>
					</li>
				);
			})}
		</ol>
	);
}

// 내 화장대에서 고르기. 이미 넣은 항목은 목록에서 뺀다
export function AddStep({ shelf, steps, names, onAdd }) {
	const used = new Set(steps.map((s) => s.shelfItemId));
	const available = shelf.filter((item) => !used.has(item.id));
	const [picked, setPicked] = useState('');
	const [type, setType] = useState('ETC');

	if (shelf.length === 0) {
		return (
			<p className="vanity-routine-add">
				내 화장대가 비어 있어요. <Link to="/add">제품 추가</Link>
			</p>
		);
	}
	const selected = available.some((item) => String(item.id) === picked) ? picked : '';
	const add = () => {
		if (!selected) return;
		onAdd(Number(selected), type);
		setPicked('');
	};
	return (
		<div className="vanity-routine-add">
			<label htmlFor="routine-add-item">내 화장대에서 고르기</label>
			<select id="routine-add-item" value={selected} onChange={(e) => setPicked(e.target.value)}>
				<option value="">제품 선택</option>
				{available.map((item) => (
					<option key={item.id} value={item.id}>
						{names[item.id]}
					</option>
				))}
			</select>
			<label htmlFor="routine-add-type">단계 종류</label>
			<StepTypeSelect id="routine-add-type" label="새 단계 종류" value={type} onChange={setType} />
			<button type="button" onClick={add} disabled={!selected}>
				단계 추가
			</button>
		</div>
	);
}

export function WarningPanel({ check, checkError, names, canSort, onSort }) {
	return (
		<section className="vanity-routine-check" aria-labelledby="routine-check-title" aria-live="polite">
			<h3 id="routine-check-title">함께 쓰기 확인</h3>
			{checkError ? (
				<p role="alert" className="auth-error">
					{checkError}
				</p>
			) : check ? (
				<WarningList warnings={check.warnings} names={names} />
			) : (
				<p aria-busy="true">확인하는 중...</p>
			)}
			<button type="button" className="vanity-routine-secondary" onClick={onSort} disabled={!canSort}>
				권장 순서로 정렬
			</button>
			<p className="auth-hint">성분 조합 안내는 참고용이에요. 피부에 이상이 있으면 사용을 멈춰 주세요.</p>
		</section>
	);
}

export function EditActions({ isNew, saving, saveError, notice, onSave, onDelete }) {
	return (
		<div className="vanity-routine-actions">
			{saveError && (
				<p role="alert" className="auth-error">
					{saveError}
				</p>
			)}
			{notice && (
				<p role="status" className="auth-notice">
					{notice}
				</p>
			)}
			<button type="button" className="vanity-routine-primary" onClick={onSave} disabled={saving}>
				{isNew ? '루틴 만들기' : '저장'}
			</button>
			{!isNew && (
				<button type="button" className="me-danger" onClick={onDelete} disabled={saving}>
					루틴 삭제
				</button>
			)}
		</div>
	);
}

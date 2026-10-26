import { Link } from 'react-router-dom';
import { PERSONAL_COLORS } from '../../lib/shade';
import tips from '../../content/tips.json';
import { MAKEUP_TYPES, TypePicker as SharedPicker } from './SkincareDraft';

// 화장대 진단 — 색조 탭(퍼스널 탭 설계 3절 B): 보유 립·블러셔·섀도 조합, 빈 종류의 색 범위, 색조 종류 고르기
const KIND_LABELS = { DAILY: '데일리', POINT: '포인트', TONE_ON_TONE: '톤온톤' };
const KIND_TIPS = { POINT: 'tip-point-lip', TONE_ON_TONE: 'tip-tone-on-tone', DAILY: 'tip-blusher' };
const TYPE_LABELS = Object.fromEntries(MAKEUP_TYPES.map((t) => [t.value, t.label]));
const SEASON_LABELS = Object.fromEntries(PERSONAL_COLORS.map((c) => [c.value, c.label]));
const TIPS = Object.fromEntries(tips.map((t) => [t.id, t]));

// 받침이 있으면 "이", 없으면 "가"(립이, 섀도가)
function withSubject(word) {
	const code = word.charCodeAt(word.length - 1) - 0xac00;
	return code >= 0 && code <= 11171 && code % 28 !== 0 ? `${word}이` : `${word}가`;
}

function Chip({ hex }) {
	return <span className="vanity-look-chip" style={{ background: hex }} aria-hidden="true" />;
}

function LookCard({ look, byId }) {
	const tip = TIPS[KIND_TIPS[look.kind]];
	return (
		<li className="vanity-look">
			<h4>{KIND_LABELS[look.kind] || look.kind}</h4>
			<ul className="vanity-look-items">
				{look.shelfItemIds.map((id) => {
					const item = byId[id];
					return (
						<li key={id}>
							{item && item.shadeHex && <Chip hex={item.shadeHex} />}
							<span>{item ? `${TYPE_LABELS[item.makeupType] || ''} · ${item.brand} ${item.name}` : `#${id}`}</span>
						</li>
					);
				})}
			</ul>
			<ul className="vanity-look-reasons">
				{look.reasons.map((r) => <li key={r}>{r}</li>)}
			</ul>
			{tip && <p className="auth-hint">{tip.title}</p>}
		</li>
	);
}

function TypePicker({ item, onPick, busy }) {
	const id = `vanity-makeup-type-${item.shelfItemId}`;
	return (
		<li className="vanity-analysis-picker">
			<label htmlFor={id}>{`${item.name} 종류`}</label>
			<select id={id} defaultValue="" disabled={busy} onChange={(e) => e.target.value && onPick(item.shelfItemId, { makeupType: e.target.value })}>
				<option value="">골라 주세요</option>
				{MAKEUP_TYPES.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
			</select>
		</li>
	);
}

export default function MakeupLooks({ data, onPick, busy }) {
	const byId = Object.fromEntries(data.items.map((i) => [i.shelfItemId, i]));
	const needs = data.needsType.map((id) => byId[id]).filter((i) => i && i.category === 'MAKEUP');
	const guessed = data.items.filter((i) => i.category === 'MAKEUP' && i.guessed && i.makeupType);
	const looks = data.looks || [];
	const shadeGaps = data.shadeGaps || [];

	return (
		<div className="vanity-makeup">
			{data.needsPersonalColor && (
				<p className="auth-hint">
					<Link to="/personal-color">퍼스널 컬러 진단</Link>을 하면 내 톤에 맞는 조합을 먼저 보여 드려요.
				</p>
			)}
			{looks.length === 0 ? (
				<p className="auth-hint">립·블러셔·섀도 중 두 종류 이상에 내 호수 색을 넣으면 조합을 만들어 드려요.</p>
			) : (
				<ul className="vanity-looks">
					{looks.map((look) => <LookCard key={look.kind} look={look} byId={byId} />)}
				</ul>
			)}
			{shadeGaps.length > 0 && (
				<section aria-labelledby="vanity-shade-gaps-title">
					<h3 id="vanity-shade-gaps-title">있으면 조합이 늘어요</h3>
					<ul className="vanity-shade-gaps">
						{shadeGaps.map((g) => (
							<li key={g.makeupType}>
								<p>{`${SEASON_LABELS[g.season]} ${withSubject(TYPE_LABELS[g.makeupType])} 있으면 조합을 더 만들 수 있어요.`}</p>
								<div className="vanity-shade-chips" aria-label={`${SEASON_LABELS[g.season]} 색 범위`}>
									{g.hexes.map((hex) => <Chip key={hex} hex={hex} />)}
								</div>
							</li>
						))}
					</ul>
					<p className="auth-hint">특정 제품을 추천하지 않아요. 가진 색 중에 비슷한 것이 있으면 내 화장대에 색을 넣어 주세요.</p>
				</section>
			)}
			{guessed.length > 0 && (
				<details className="vanity-analysis-guessed">
					<summary>이름으로 추정한 종류 확인하기({guessed.length})</summary>
					<ul className="vanity-analysis-pickers">
						{guessed.map((item) => <SharedPicker key={item.shelfItemId} item={item} onPick={onPick} busy={busy} current={item.makeupType} />)}
					</ul>
				</details>
			)}
			{needs.length > 0 && (
				<section aria-labelledby="vanity-makeup-needs-title">
					<h3 id="vanity-makeup-needs-title">종류를 골라 주세요</h3>
					<ul className="vanity-analysis-pickers">
						{needs.map((item) => <TypePicker key={item.shelfItemId} item={item} onPick={onPick} busy={busy} />)}
					</ul>
				</section>
			)}
		</div>
	);
}

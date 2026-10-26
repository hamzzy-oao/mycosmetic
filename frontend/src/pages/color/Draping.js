import { useState } from 'react';
import { PERSONAL_COLORS } from '../../lib/shade';

// A/B 드레이핑(퍼스널 탭 설계 2절): 1라운드 웜 대 쿨(같은 밝기), 2라운드 고른 톤 안에서 밝음 대 깊음.
// 카메라 판정은 먼저 보여줄 쪽만 정한다(ordered=false면 늘 웜·밝음을 먼저). 최종 결과는 사용자가 고른 것
const LABELS = Object.fromEntries(PERSONAL_COLORS.map((c) => [c.value, c.label]));
const IS_WARM = { SPRING_WARM: true, AUTUMN_WARM: true, SUMMER_COOL: false, WINTER_COOL: false };
const IS_LIGHT = { SPRING_WARM: true, SUMMER_COOL: true, AUTUMN_WARM: false, WINTER_COOL: false };

function seasonOf(warm, light) {
	if (warm) return light ? 'SPRING_WARM' : 'AUTUMN_WARM';
	return light ? 'SUMMER_COOL' : 'WINTER_COOL';
}

// 1라운드 두 후보(먼저 보여줄 것이 앞)
export function firstRound(first, ordered) {
	const light = IS_LIGHT[first];
	const pair = [seasonOf(true, light), seasonOf(false, light)];
	return ordered && !IS_WARM[first] ? pair.reverse() : pair;
}

// 2라운드 두 후보
export function secondRound(chosenTone, first, ordered) {
	const warm = IS_WARM[chosenTone];
	const pair = [seasonOf(warm, true), seasonOf(warm, false)];
	return ordered && !IS_LIGHT[first] ? pair.reverse() : pair;
}

function Option({ letter, photoUrl, colors, onPick }) {
	return (
		<figure className="pc-drape">
			<div className="pc-drape-frame" style={{ '--pc-swatch-count': colors.length }}>
				<div className="pc-drape-swatches" aria-hidden="true">
					{colors.map((hex) => <span key={hex} style={{ background: hex }} />)}
				</div>
				{photoUrl ? <img src={photoUrl} alt="" /> : <div className="pc-drape-noimage" aria-hidden="true" />}
				<div className="pc-drape-swatches" aria-hidden="true">
					{[...colors].reverse().map((hex) => <span key={hex} style={{ background: hex }} />)}
				</div>
			</div>
			<figcaption>
				<button type="button" className="auth-primary" onClick={onPick}>
					{letter}가 더 잘 어울려요
				</button>
			</figcaption>
		</figure>
	);
}

export default function Draping({ photoUrl, palettes, first, ordered, onChosen }) {
	const [round, setRound] = useState(1);
	const [tone, setTone] = useState(null);
	const pair = round === 1 ? firstRound(first, ordered) : secondRound(tone, first, ordered);

	const pick = (season) => {
		if (round === 1) {
			setTone(season);
			setRound(2);
		} else {
			onChosen(season);
		}
	};

	return (
		<section className="pc-draping" aria-labelledby="pc-draping-title">
			<h2 id="pc-draping-title">{round === 1 ? '1 / 2 · 어느 쪽 얼굴이 더 맑아 보여요?' : '2 / 2 · 하나만 더 골라 주세요'}</h2>
			{!ordered && round === 1 && <p className="auth-hint">판정이 애매해서 직접 비교해 주세요.</p>}
			<div className="pc-drape-pair">
				<Option letter="A" photoUrl={photoUrl} colors={palettes[pair[0]]} onPick={() => pick(pair[0])} />
				<Option letter="B" photoUrl={photoUrl} colors={palettes[pair[1]]} onPick={() => pick(pair[1])} />
			</div>
			{round === 2 && <p className="auth-hint">지금은 {IS_WARM[tone] ? '따뜻한' : '차가운'} 색 중에서 골라요.</p>}
		</section>
	);
}

export { LABELS };

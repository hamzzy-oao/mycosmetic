import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { prefersReducedMotion } from './useHeroMotion';

const STEPS = [
	{ time: 'morning', name: '클렌징' },
	{ time: 'morning', name: '토너' },
	{ time: 'morning', name: '세럼' },
	{ time: 'morning', name: '선크림' },
	{ time: 'night', name: '클렌징' },
	{ time: 'night', name: '레티놀 세럼' },
	{ time: 'night', name: '크림' },
];

function SunIcon() {
	return (
		<svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
			<circle cx="12" cy="12" r="4" />
			<path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
		</svg>
	);
}

function MoonIcon() {
	return (
		<svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
			<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z" />
		</svg>
	);
}

// 서비스 카드와 마무리 배너 사이의 아침→밤 루틴 띠. 띠가 화면에 들어와 중앙에 닿을 때까지 스크롤 진행도(--ritual, 0~1)에 따라
// 배경이 blush → peach → rose → night로 바뀌고 루틴 단계가 하나씩 체크된다. 색 전환은 HomeClub.css가 겹 투명도로 처리한다.
// 움직임 줄이기에서는 스크롤 연출 없이 모든 단계를 보여준다
export default function HomeRitual() {
	const ref = useRef(null);
	const [motion] = useState(() => !prefersReducedMotion());
	const [done, setDone] = useState(motion ? 0 : STEPS.length);
	const night = motion ? done > STEPS.filter((step) => step.time === 'morning').length : false;

	useEffect(() => {
		const band = ref.current;
		const stage = band?.querySelector('.vanity-ritual-stage');
		if (!band || !stage || !motion) {
			return undefined;
		}
		let frame = 0;
		const update = () => {
			frame = 0;
			const rect = band.getBoundingClientRect();
			const stageHeight = stage.offsetHeight;
			const start = window.innerHeight - Math.min(120, stageHeight / 3);
			const end = Math.max(0, (window.innerHeight - stageHeight) / 2);
			const span = start - end;
			const progress = span > 0 ? Math.min(Math.max((start - rect.top) / span, 0), 1) : 0;
			band.style.setProperty('--ritual', progress.toFixed(3));
			setDone(Math.floor(progress * (STEPS.length + 1)));
		};
		const schedule = () => {
			if (!frame) {
				frame = window.requestAnimationFrame(update);
			}
		};
		window.addEventListener('scroll', schedule, { passive: true });
		window.addEventListener('resize', schedule);
		schedule();
		return () => {
			window.removeEventListener('scroll', schedule);
			window.removeEventListener('resize', schedule);
			if (frame) {
				window.cancelAnimationFrame(frame);
			}
		};
	}, [motion]);

	return (
		<section ref={ref} className="vanity-ritual" data-motion={motion ? 'scroll' : 'static'} data-phase={night ? 'night' : 'morning'} aria-labelledby="vanity-ritual-title">
			<div className="vanity-ritual-stage">
				<span className="vanity-ritual-layer vanity-ritual-peach" aria-hidden="true" />
				<span className="vanity-ritual-layer vanity-ritual-rose" aria-hidden="true" />
				<span className="vanity-ritual-layer vanity-ritual-night" aria-hidden="true" />
				<div className="vanity-ritual-inner">
					<span className="vanity-ritual-icon">{night ? <MoonIcon /> : <SunIcon />}</span>
					<h2 id="vanity-ritual-title">{motion ? (night ? 'Good night.' : 'Good morning.') : 'Morning & night.'}</h2>
					<p className="vanity-ritual-copy">{motion ? (night ? '저녁 루틴 순서를 확인하세요.' : '아침 루틴 순서를 확인하세요.') : '아침과 저녁 루틴 순서를 확인하세요.'}</p>
					<ol className="vanity-ritual-steps">
						{STEPS.map((step, i) => (
							<li key={`${step.time}-${step.name}`} className={`vanity-ritual-step${i < done ? ' is-done' : ''}`} data-time={step.time}>
								<span className="vanity-ritual-check" aria-hidden="true" />{step.name}
							</li>
						))}
					</ol>
					<span className="vanity-ritual-progress" aria-hidden="true"><span /></span>
					<Link className="vanity-ritual-link" to="/routines">루틴 기록하기 <span aria-hidden="true">→</span></Link>
				</div>
			</div>
		</section>
	);
}

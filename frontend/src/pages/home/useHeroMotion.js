import { useEffect, useState } from 'react';

const PLAYED_KEY = 'vanity-intro-played';
const REDUCED_QUERY = '(prefers-reduced-motion: reduce)';
const FINE_POINTER_QUERY = '(hover: hover) and (pointer: fine)';

function matches(query) {
	return typeof window !== 'undefined' && typeof window.matchMedia === 'function' && window.matchMedia(query).matches;
}

export function prefersReducedMotion() {
	return matches(REDUCED_QUERY);
}

// 요소가 화면에 들어올 때 한 번 재생할 장면의 상태. rest: 처음부터 다 보이는 상태(움직임 줄이기, IntersectionObserver 없음),
// wait: 화면에 들어오길 기다림, play: 재생. wait일 때 무엇을 숨길지는 각 장면의 CSS가 정한다
export function usePlayWhenSeen(ref, threshold = 0.5) {
	const [state, setState] = useState('rest');

	useEffect(() => {
		const el = ref.current;
		if (!el || prefersReducedMotion() || typeof window.IntersectionObserver !== 'function') {
			return undefined;
		}
		setState('wait');
		const observer = new window.IntersectionObserver((entries) => {
			if (entries.some((entry) => entry.isIntersecting)) {
				setState('play');
				observer.disconnect();
			}
		}, { threshold });
		observer.observe(el);
		return () => observer.disconnect();
	}, [ref, threshold]);

	return state;
}

// 오프닝은 한 세션에 한 번만 재생한다. 저장소를 못 쓰는 환경(사생활 보호 창 등)에서는 매번 재생한다
function alreadyPlayed() {
	try {
		return window.sessionStorage.getItem(PLAYED_KEY) === '1';
	} catch {
		return false;
	}
}

function rememberPlayed() {
	try {
		window.sessionStorage.setItem(PLAYED_KEY, '1');
	} catch {
		// 저장 실패는 무시한다. 다음에 다시 재생될 뿐이다
	}
}

// 히어로 오프닝 재생 여부와 패럴랙스 값을 관리한다.
// 패럴랙스는 CSS 변수 --scroll(px, 단위 없는 수)과 --tilt-x·--tilt-y(-1~1)를 section에 써 주고, 실제 움직임은 HomeClub.css가 정한다.
export default function useHeroMotion(heroRef) {
	const [intro] = useState(() => (prefersReducedMotion() || alreadyPlayed() ? 'done' : 'play'));

	useEffect(() => {
		if (intro === 'play') {
			rememberPlayed();
		}
	}, [intro]);

	useEffect(() => {
		const hero = heroRef.current;
		if (!hero || prefersReducedMotion()) {
			return undefined;
		}

		let frame = 0;
		let pointer = null;
		const update = () => {
			frame = 0;
			const rect = hero.getBoundingClientRect();
			hero.style.setProperty('--scroll', Math.min(Math.max(-rect.top, 0), rect.height).toFixed(1));
			if (pointer) {
				hero.style.setProperty('--tilt-x', (((pointer.x - rect.left) / rect.width) * 2 - 1).toFixed(3));
				hero.style.setProperty('--tilt-y', (((pointer.y - rect.top) / rect.height) * 2 - 1).toFixed(3));
			}
		};
		const schedule = () => {
			if (!frame) {
				frame = window.requestAnimationFrame(update);
			}
		};
		const onPointerMove = (event) => {
			pointer = { x: event.clientX, y: event.clientY };
			schedule();
		};
		const onPointerLeave = () => {
			pointer = null;
			hero.style.setProperty('--tilt-x', '0');
			hero.style.setProperty('--tilt-y', '0');
		};

		window.addEventListener('scroll', schedule, { passive: true });
		// 마우스 기울기는 마우스가 있는 PC에서만. 터치 기기는 스크롤 패럴랙스만 쓴다
		const tilt = matches(FINE_POINTER_QUERY);
		if (tilt) {
			hero.addEventListener('pointermove', onPointerMove);
			hero.addEventListener('pointerleave', onPointerLeave);
		}
		schedule();

		return () => {
			window.removeEventListener('scroll', schedule);
			if (tilt) {
				hero.removeEventListener('pointermove', onPointerMove);
				hero.removeEventListener('pointerleave', onPointerLeave);
			}
			if (frame) {
				window.cancelAnimationFrame(frame);
			}
		};
	}, [heroRef]);

	return intro;
}

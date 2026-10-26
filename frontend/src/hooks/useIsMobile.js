import { useEffect, useState } from 'react';

// 폰·PC 분기 기준 (설계 7장)
export const MOBILE_QUERY = '(max-width: 767px)';

function getMediaQueryList() {
	if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
		return null;
	}
	return window.matchMedia(MOBILE_QUERY);
}

export default function useIsMobile() {
	const [isMobile, setIsMobile] = useState(() => {
		const mql = getMediaQueryList();
		return mql ? mql.matches : false;
	});

	useEffect(() => {
		const mql = getMediaQueryList();
		if (!mql) {
			return undefined;
		}
		const onChange = (event) => setIsMobile(event.matches);
		setIsMobile(mql.matches);
		if (typeof mql.addEventListener === 'function') {
			mql.addEventListener('change', onChange);
			return () => mql.removeEventListener('change', onChange);
		}
		// 예전 사파리는 addListener만 있다
		mql.addListener(onChange);
		return () => mql.removeListener(onChange);
	}, []);

	return isMobile;
}

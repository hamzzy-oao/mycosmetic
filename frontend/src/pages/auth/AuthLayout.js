import { useId } from 'react';
import HomeLink from '../../components/HomeLink';

// 로그인·가입·내 정보 화면의 틀 (설계 7장)
// - 폰: 화면 전체를 쓰는 한 열
// - PC: 화면 가운데 카드
// homeLink면 제목 위에 메인으로 가는 로고 링크를 둔다(로그인·가입 화면은 쓰지 않는다)
// 제목으로 이름 붙인 영역(region)이라 화면 읽기 도구와 테스트가 찾을 수 있다
export function AuthMobileLayout({ title, homeLink = false, children }) {
	const headingId = useId();
	return (
		<section data-layout="mobile" className="auth-column" aria-labelledby={headingId}>
			{homeLink && <HomeLink />}
			<h1 id={headingId}>{title}</h1>
			{children}
		</section>
	);
}

export function AuthDesktopLayout({ title, homeLink = false, children }) {
	const headingId = useId();
	return (
		<div className="auth-page">
			<section data-layout="desktop" className="auth-card" aria-labelledby={headingId}>
				{homeLink && <HomeLink />}
				<h1 id={headingId}>{title}</h1>
				{children}
			</section>
		</div>
	);
}

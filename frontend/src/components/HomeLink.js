import { Link } from 'react-router-dom';
import './HomeLink.css';

// 내부 화면 왼쪽 위의 로고 링크. 누르면 메인(/)으로 간다 (vanity-ui: VANITY + 체크 아이콘)
export default function HomeLink() {
	return (
		<Link to="/" className="vanity-home-link" aria-label="Vanity Check 홈">
			<span aria-hidden="true">VANITY</span>
			<svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true" focusable="false">
				<path d="M3 9.5l4 4 8-9" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
			</svg>
		</Link>
	);
}

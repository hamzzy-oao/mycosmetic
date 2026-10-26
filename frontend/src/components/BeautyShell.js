import BrandFooter from './BrandFooter';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import BeautyNavigation from './BeautyNavigation';
import useNotificationCount from '../hooks/useNotificationCount';
import './BeautyShell.css';

const ASSETS = `${process.env.PUBLIC_URL}/images/vanity`;
const SCREENS = {
 '/admin': ['ADMIN', '제품과 성분 정보를 관리하세요.'],
 '/admin/review': ['ADMIN', '등록된 제품 정보에 문제가 없는지 점검하세요.'],
 '/login': ['LOGIN', '계정에 로그인하세요.'],
 '/signup': ['SIGN UP', '새 계정을 만드세요.'],
 '/shelf': ['MY VANITY', '등록한 화장품을 확인하세요.'],
 '/add': ['ADD PRODUCT', '화장품을 등록하고 성분을 확인하세요.'],
 '/skin': ['PERSONAL', '피부 정보를 관리하세요.'],
 '/me': ['MY ACCOUNT', '계정 정보를 확인하세요.'],
 '/routines': ['ROUTINE', '나의 루틴을 확인하세요.'],
 '/routines/new': ['NEW ROUTINE', '새 루틴을 만드세요.'],
 '/compare': ['COMPARE', '내 제품의 성분을 비교하세요.'],
 '/advice': ['PRODUCT CHECK', '내 기록을 바탕으로 구매 적합도를 확인하세요.'],
 '/stats': ['INSIGHTS', '화장품 사용 현황을 확인하세요.'],
 '/notifications': ['NOTICE', '지금 확인할 일을 모아 보세요.'],
};

// Shared visual frame only: pages keep their own forms, API calls and responsive layouts.
export default function BeautyShell({ children }) {
	const { pathname } = useLocation();
	const { user, logout } = useAuth();
	const notificationCount = useNotificationCount(Boolean(user), pathname);
	if (pathname === '/') return children;
	const isVanity = ['/shelf', '/compare', '/stats', '/share'].includes(pathname);
 // 퍼스널 탭: 누르면 그 기능 화면이 바로 열린다(퍼스널 탭 설계 1~3절)
 const isPersonal = ['/skin', '/personal-color', '/vanity/analysis'].includes(pathname);
 // 루틴 탭: 루틴 목록, 매일 피부 기록(설계 2026-10-24 A), 새 제품 2주 테스트(B)
 const isRoutine = ['/routines', '/diary', '/trials'].includes(pathname);
 // 관리자 탭: 제품 관리와 데이터 점검함(2026-10-24)
 const isAdmin = ['/admin', '/admin/review'].includes(pathname);
 const tabs = isVanity ? [['/shelf', '내 제품'], ['/compare', '비교'], ['/stats', '현황'], ['/share', '자랑 카드']] : isPersonal ? [['/skin', '내 피부 분석'], ['/personal-color', '퍼스널 컬러 진단'], ['/vanity/analysis', '화장대 진단']] : isRoutine ? [['/routines', '루틴'], ['/diary', '피부 일기'], ['/trials', '새 제품 테스트']] : isAdmin && user?.role === 'ADMIN' ? [['/admin', '제품 관리'], ['/admin/review', '점검함']] : [];
 const tabsLabel = isVanity ? '화장대 메뉴 탭' : isPersonal ? '퍼스널 메뉴 탭' : isAdmin ? '관리자 메뉴 탭' : '루틴 메뉴 탭';
 const screen = isVanity ? ['MY VANITY', '내 제품과 성분을 확인하세요.'] : isPersonal ? ['PERSONAL', '나에게 맞는 뷰티 정보를 확인하세요.'] : pathname === '/diary' ? ['SKIN DIARY', '매일 피부 상태를 기록하세요.'] : pathname === '/trials' ? ['PRODUCT TEST', '새 제품을 2주 동안 써 보고 나에게 맞는지 평가하세요.'] : SCREENS[pathname] || (pathname.startsWith('/products/')
		? ['PRODUCT DETAILS', '제품과 성분 정보를 확인하세요.']
		: ['EDIT ROUTINE', '루틴을 수정하세요.']);
	return (
		<div className="vanity-app-shell" style={{ '--beauty-scallop': `url("${ASSETS}/club-scallop.png")` }}>
			<a className="vanity-app-skip" href="#beauty-content">본문으로 바로가기</a>
			<header className="vanity-app-header">
				<Link className="vanity-app-home" to="/">YOUR BEAUTY NOTES</Link>
				<Link to="/" className="vanity-app-logo" aria-label="Vanity Check 홈"><img src={`${process.env.PUBLIC_URL}/brandboard/vanity-primary.svg`} alt="" width="1100" height="520" /></Link>
				<div className="vanity-app-account">{user ? <>{user.role === 'ADMIN' && <Link to="/admin">ADMIN</Link>}<Link to="/notifications" aria-label={`알림 ${notificationCount}개`}>ALERTS{notificationCount > 0 && <span className="vanity-app-badge">{notificationCount}</span>}</Link><Link to="/me">MY PAGE</Link><button type="button" onClick={logout}>LOGOUT</button></> : <><Link to="/signup">SIGN UP</Link><Link to="/login">LOGIN</Link></>}</div>
			</header>
			<BeautyNavigation label="뷰티 클럽 메뉴" />
			<div className="vanity-app-banner">
				<p className="vanity-app-display">{screen[0]}</p>
				<p className="vanity-app-description">{screen[1]}</p>
			</div>
			<div className={`vanity-app-content${tabs.length ? " vanity-tabbed-content" : ""}`} id="beauty-content" tabIndex="-1">{tabs.length > 0 && <nav className="vanity-section-tabs" aria-label={tabsLabel}>{tabs.map(([to, text]) => <Link key={to} to={to} aria-current={pathname === to ? 'page' : undefined}>{text}</Link>)}{pathname === '/shelf' && <Link className="vanity-tab-add" to="/add">제품 추가 +</Link>}</nav>}{children}</div>
			<BrandFooter />
		</div>
	);
}

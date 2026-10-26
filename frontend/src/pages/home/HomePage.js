import BrandFooter from '../../components/BrandFooter';
import { Link } from 'react-router-dom';
import { useAuth } from '../../AuthContext';
import useIsMobile from '../../hooks/useIsMobile';
import BeautyNavigation from '../../components/BeautyNavigation';
import './HomePage.css';
import HomeIntro from './HomeIntro';

// 공개 메인 페이지 (/). 내 화장대(공통 기반) 위에 AI 기능 3개(성분 판단, 루틴 플래너, 가상 메이크업)가 올라간 구조를 보여준다.
// 로그인하지 않아도 보이고, 로그인한 사용자도 자동으로 다른 화면으로 보내지 않는다.
export const APP_NAME = 'Vanity Check';
export const APP_INTRO = '제품 이름이나 전성분으로 내 화장품을 담고, 나에게 맞는지 기록해요.';

// 로고 Vanity✓(public/images/vanity/logo.svg): 체크 표시가 "Check"를 대신한다. 읽는 이름은 감싼 요소의 aria-label(APP_NAME)로 준다
const LOGO = `${process.env.PUBLIC_URL}/brandboard/vanity-primary.svg`;

function Wordmark() {
	return <img className="vanity-wordmark" src={LOGO} alt="" width="1100" height="520" />;
}

export default function HomePage() {
	const { user, logout } = useAuth();
	const mobile = useIsMobile();

	return (
		<section className="vanity-page" data-layout={mobile ? 'mobile' : 'desktop'} aria-labelledby="home-title">
			<header className="vanity-header">
				<span className="vanity-header-spacer" aria-hidden="true" />
				<Link to="/" className="vanity-brand" aria-label={`${APP_NAME} 홈`}>
					<Wordmark />

				</Link>
				<div className="vanity-tools">
					{user ? (
						<>
							{user.role === 'ADMIN' && <Link to="/admin" className="vanity-header-link">ADMIN</Link>}
							<Link to="/me" className="vanity-header-link">MY PAGE</Link>
							<button type="button" className="vanity-header-link" onClick={logout}>LOGOUT</button>
						</>
					) : (
						<>
							<Link to="/signup" className="vanity-header-link">SIGN UP</Link>
							<Link to="/login" className="vanity-header-link">LOGIN</Link>
						</>
					)}
				</div>
			</header>
			<BeautyNavigation className="vanity-nav" />

			<HomeIntro />

			<BrandFooter />
		</section>
	);
}

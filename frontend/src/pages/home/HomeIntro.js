import { useRef } from 'react';
import { Link } from 'react-router-dom';
import './HomeClub.css';
import HomeRitual from './HomeRitual';
import HomeScan from './HomeScan';
import useHeroMotion, { prefersReducedMotion } from './useHeroMotion';

const ASSETS = `${process.env.PUBLIC_URL}/images/vanity`;
const COMPACT_IMAGE = `${ASSETS}/heart-compact.png`;
const COMPACT_ALT = '하트 거울이 달린 핑크 컴팩트와 리본 퍼프';
// 컴팩트가 열리는 영상이 생기면 여기에 넣는다. 예: [{ src: `${ASSETS}/compact-open.webm`, type: 'video/webm' }, { src: `${ASSETS}/compact-open.mp4`, type: 'video/mp4' }]
// 비어 있거나 움직임 줄이기를 켠 사용자에게는 이미지를 보여준다. 영상의 포스터도 같은 이미지라 첫 화면이 늦게 뜨지 않는다
const COMPACT_VIDEO = [];

function CompactArt() {
	if (COMPACT_VIDEO.length === 0 || prefersReducedMotion()) {
		return <img className="vanity-compact-art vanity-compact-media" src={COMPACT_IMAGE} alt={COMPACT_ALT} />;
	}
	return (
		<video className="vanity-compact-art vanity-compact-media" poster={COMPACT_IMAGE} autoPlay muted playsInline preload="metadata" role="img" aria-label={COMPACT_ALT}>
			{COMPACT_VIDEO.map((source) => <source key={source.src} src={source.src} type={source.type} />)}
		</video>
	);
}

export default function HomeIntro() {
	const heroRef = useRef(null);
	const intro = useHeroMotion(heroRef);

	return (
		<>
			<section ref={heroRef} className="vanity-club-hero" data-intro={intro} aria-label="서비스 소개" style={{ '--compact-mask': `url("${COMPACT_IMAGE}")` }}>
				<div className="vanity-club-frame" aria-hidden="true" />
				<div className="vanity-club-layout">
				<div className="vanity-club-copy">
					<h1 id="home-title" aria-label="Vanity Check" className="vanity-club-title"><img src={`${ASSETS}/club-headline-title.webp`} alt="GOOD SKIN. BETTER HABITS." /></h1>
					<p className="vanity-club-description">내 화장품을 확인하고, 나에게 맞는 루틴을 기록하세요.</p>
					<div className="vanity-club-actions">
						<Link className="vanity-club-button" to="/shelf">내 화장대 분석하기 <span aria-hidden="true">→</span></Link>
						<Link className="vanity-club-button vanity-club-button-outline" to="/routines">루틴 기록 둘러보기</Link>
					</div>
				</div>
				<div className="vanity-compact-stage"><CompactArt /></div>
				</div>
				{['one', 'two'].map((spot) => <img key={spot} className={`vanity-petite-sparkle vanity-petite-sparkle-${spot} vanity-depth-back`} src={`${ASSETS}/petite-sparkle-pearl.png`} alt="" aria-hidden="true" />)}
				{['one', 'two', 'three'].map((spot) => <img key={spot} className={`vanity-petite-heart vanity-petite-heart-${spot} vanity-depth-mid`} src={`${ASSETS}/petite-heart.png`} alt="" aria-hidden="true" />)}
				<img className="vanity-petite-bow vanity-depth-front" src={`${ASSETS}/petite-bow.png`} alt="" aria-hidden="true" />
			</section>
			<img className="vanity-hero-wave" src={`${ASSETS}/hero-wave.png`} alt="" aria-hidden="true" />
			<section className="vanity-club-services" id="about" aria-labelledby="vanity-services-title">
				<div className="vanity-club-services-heading"><h2 id="vanity-services-title">My vanity.</h2><span className="vanity-club-title-rule" aria-hidden="true" /></div>
				<div className="vanity-club-service-grid" style={{ '--club-stripes': `url("${ASSETS}/club-stripes.png")` }}>
					<article className="vanity-club-service">
						<div className="vanity-club-service-copy">
							<h3>MY VANITY</h3>
							<p>화장품 성분을 확인하고 사용 기록을 남겨보세요.</p>
							<Link to="/add" className="vanity-club-service-link">성분 확인하기 <span aria-hidden="true">↗</span></Link>
						</div>
						<div className="vanity-club-service-preview">
							<span className="vanity-club-preview-label">성분 확인 예시</span>
							<HomeScan />
						</div>
					</article>
				</div>
			</section>
			<HomeRitual />
			<section className="vanity-club-closing" style={{ '--club-scallop': `url("${ASSETS}/club-scallop.png")` }} aria-labelledby="vanity-closing-title">
				<div className="vanity-club-closing-frame">
					<p className="vanity-club-side">SKIN<br />PROFILE</p>
					<div>
						<p className="vanity-club-personal-label">PERSONAL</p>
						<h2 id="vanity-closing-title">Your skin.<br />Your colors.</h2>
						<p className="vanity-club-personal-copy">피부 타입과 어울리는 컬러를 확인하세요.</p>
						<div className="vanity-club-personal-actions">
							<Link className="vanity-club-button vanity-club-button-white" to="/skin">내 피부 알아보기 <span aria-hidden="true">→</span></Link>
							<Link className="vanity-club-button vanity-club-button-line" to="/personal-color">내 컬러 찾기 <span aria-hidden="true">→</span></Link>
						</div>
					</div>
					<p className="vanity-club-side">PERSONAL<br />COLOR</p>
				</div>
			</section>
		</>
	);
}





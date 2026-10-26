import useIsMobile from '../hooks/useIsMobile';

// 화면마다 폰용과 PC용 컴포넌트를 따로 두고 너비로 고른다 (설계 7장)
export default function ResponsivePage({ mobile: Mobile, desktop: Desktop, ...props }) {
	const isMobile = useIsMobile();
	return isMobile ? <Mobile {...props} /> : <Desktop {...props} />;
}

import { Link, useLocation } from 'react-router-dom';
import './BeautyNavigation.css';

// 상단 카테고리 메뉴. 카테고리를 누르면 대표 화면으로 바로 가고, 그 안의 화면은 공통 탭(BeautyShell)이 이어 준다.
// items는 그 카테고리에 속한 화면이다. 지금 화면이 items 중 하나면 카테고리를 선택 상태로 보여준다
const GROUPS = [
	{ key: 'vanity', title: 'MY VANITY', to: '/shelf', items: ['/shelf', '/compare', '/stats', '/share', '/add'] },
	{ key: 'personal', title: 'PERSONAL', to: '/skin', items: ['/skin', '/personal-color', '/vanity/analysis'] },
	{ key: 'routine', title: 'ROUTINE', to: '/routines', items: ['/routines', '/routines/new', '/diary', '/trials'] },
];

export function activeGroup(pathname) {
	if (pathname.startsWith('/routines/')) return 'routine';
	const group = GROUPS.find((g) => g.items.includes(pathname));
	return group ? group.key : null;
}

export default function BeautyNavigation({ className = 'vanity-app-nav', label = '메인 메뉴' }) {
	const { pathname } = useLocation();
	const active = activeGroup(pathname);
	return (
		<div className="vanity-category-menu">
			<nav className={className} aria-label={label}>
				{GROUPS.map((group) => (
					<div className="vanity-category-item" key={group.key}>
						<Link
							className="vanity-category-trigger"
							to={group.to}
							aria-current={active === group.key ? 'page' : undefined}
							data-active={active === group.key}
						>
							{group.title}
						</Link>
					</div>
				))}
			</nav>
		</div>
	);
}

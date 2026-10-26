import { useEffect, useRef, useState } from 'react';
import { prefersReducedMotion, usePlayWhenSeen } from './useHeroMotion';

// MY VANITY 카드의 성분 스캔 장면. 설명 글 대신 움직임으로 기능을 보여준다 (설명 글은 화면 읽기 프로그램용으로 카드에 남아 있다).
// 성분 이름은 보여주기용 예시이고 사용자 데이터가 아니다. "주의"까지만 쓰고 진단처럼 들리는 말은 쓰지 않는다 (PRD "하지 않는 것")
const GOOD = ['나이아신아마이드', '히알루론산', '세라마이드'];
const CAUTION = ['레티놀', 'AHA'];

// 카드가 처음 보일 때 한 번 재생하고, 마우스를 올리거나 키보드로 카드 안에 들어오면 다시 재생한다.
// 다시 재생은 run을 key로 써서 장면을 새로 그리는 방식이다 (CSS 애니메이션이 처음부터 다시 돈다)
export default function HomeScan() {
	const ref = useRef(null);
	const seen = usePlayWhenSeen(ref);
	const [run, setRun] = useState(0);
	const scan = run > 0 ? 'play' : seen;

	useEffect(() => {
		const card = ref.current?.closest('.vanity-club-service');
		if (!card || prefersReducedMotion()) {
			return undefined;
		}
		const replay = () => setRun((count) => count + 1);
		card.addEventListener('mouseenter', replay);
		card.addEventListener('focusin', replay);
		return () => {
			card.removeEventListener('mouseenter', replay);
			card.removeEventListener('focusin', replay);
		};
	}, []);

	return (
		<div ref={ref} className="vanity-scan" data-scan={scan} aria-hidden="true">
			<div key={run} className="vanity-scan-body">
				<span className="vanity-scan-bottle" />
				<div className="vanity-scan-groups">
					<div className="vanity-scan-group">
						<b>✓ 잘 맞아요</b>
						<div className="vanity-scan-chips">
							{GOOD.map((name, i) => <span key={name} className="vanity-scan-chip" style={{ '--i': i }}>{name}</span>)}
						</div>
					</div>
					<div className="vanity-scan-group">
						<b>! 같이 쓰면 주의</b>
						<div className="vanity-scan-chips">
							{CAUTION.map((name, i) => <span key={name} className="vanity-scan-chip vanity-scan-chip-caution" style={{ '--i': GOOD.length + i }}>{name}</span>)}
						</div>
					</div>
				</div>
			</div>
		</div>
	);
}

import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getSkinProfile, saveSkinProfile } from '../../api/skin';
import { errorMessage } from '../../lib/errorMessage';
import { PERSONAL_COLORS, TONE_NOTE } from '../../lib/shade';

// 내 정보의 피부 정보(설계 3.4, 퍼스널 탭 설계 1절). 분석 결과를 직접 고치는 곳이다.
// 기본 타입(건성·지성·복합성·수부지)은 하나만, 민감성·트러블성 태그는 함께 고른다.
// 퍼스널컬러(no-ai-features 설계 E)는 하나만 고르거나 "선택 안 함"
export const SKIN_TYPES = [
	{ value: 'SENSITIVE', label: '민감성', base: false },
	{ value: 'ACNE_PRONE', label: '트러블성', base: false },
	{ value: 'DRY', label: '건성', base: true },
	{ value: 'OILY', label: '지성', base: true },
	{ value: 'COMBINATION', label: '복합성', base: true },
	{ value: 'DEHYDRATED_OILY', label: '수부지', base: true },
];
const BASE_TYPES = SKIN_TYPES.filter((t) => t.base).map((t) => t.value);

export default function SkinInfoSection() {
	const [selected, setSelected] = useState([]);
	const [personalColor, setPersonalColor] = useState('');
	const [loading, setLoading] = useState(true);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	const [notice, setNotice] = useState('');

	useEffect(() => {
		let cancelled = false;
		(async () => {
			try {
				const data = await getSkinProfile();
				if (!cancelled) {
					setSelected(Array.isArray(data && data.skinTypes) ? data.skinTypes : []);
					setPersonalColor((data && data.personalColor) || '');
				}
			} catch (e) {
				if (!cancelled) setError(errorMessage(e));
			} finally {
				if (!cancelled) setLoading(false);
			}
		})();
		return () => {
			cancelled = true;
		};
	}, []);

	// 기본 타입을 고르면 다른 기본 타입은 풀린다
	const toggle = (value) => {
		setNotice('');
		setSelected((prev) => {
			if (prev.includes(value)) return prev.filter((v) => v !== value);
			const rest = BASE_TYPES.includes(value) ? prev.filter((v) => !BASE_TYPES.includes(v)) : prev;
			return [...rest, value];
		});
	};
	// 태그만 고르고 기본 타입이 없으면 서버가 거절하므로 저장을 막는다(빈 목록은 "모두 지우기"라 허용)
	const needsBase = selected.length > 0 && !selected.some((v) => BASE_TYPES.includes(v));

	const onSave = async () => {
		if (busy) return;
		setBusy(true);
		setError('');
		setNotice('');
		// 보기 순서대로 보낸다
		const skinTypes = SKIN_TYPES.map((t) => t.value).filter((v) => selected.includes(v));
		try {
			const data = await saveSkinProfile(skinTypes, personalColor || null);
			if (data && Array.isArray(data.skinTypes)) setSelected(data.skinTypes);
			if (data && 'personalColor' in data) setPersonalColor(data.personalColor || '');
			setNotice('피부 정보를 저장했어요.');
		} catch (e) {
			setError(errorMessage(e));
		} finally {
			setBusy(false);
		}
	};

	return (
		<section className="me-skin" aria-labelledby="me-skin-title">
			<h2 id="me-skin-title">피부 정보</h2>
			{loading ? (
				<p aria-busy="true">불러오는 중...</p>
			) : (
				<>
					<p className="auth-hint">
						피부 타입은 하나만, 민감성·트러블성은 함께 고를 수 있어요. 잘 모르겠으면{' '}
						<Link to="/skin">내 피부 분석</Link>에서 질문에 답해 보세요.
					</p>
					<div className="skin-chips" role="group" aria-label="피부 타입">
						{SKIN_TYPES.map(({ value, label }) => {
							const on = selected.includes(value);
							return (
								<button
									key={value}
									type="button"
									className={on ? 'skin-chip is-selected' : 'skin-chip'}
									aria-pressed={on}
									onClick={() => toggle(value)}
								>
									{label}
								</button>
							);
						})}
					</div>
					{needsBase && <p className="auth-hint">건성·지성·복합성·수부지 중 하나를 골라 주세요.</p>}
					<div className="skin-personal-color">
						<label htmlFor="me-personal-color">퍼스널컬러</label>
						<select
							id="me-personal-color"
							value={personalColor}
							onChange={(e) => {
								setNotice('');
								setPersonalColor(e.target.value);
							}}
						>
							<option value="">선택 안 함</option>
							{PERSONAL_COLORS.map(({ value, label }) => (
								<option key={value} value={value}>
									{label}
								</option>
							))}
						</select>
						<p className="auth-hint">
							내 화장대의 &apos;내 톤에 맞는 색조&apos; 필터에 쓰여요. {TONE_NOTE}
						</p>
					</div>
					{error && <p role="alert" className="auth-error">{error}</p>}
					{notice && <p role="status" className="auth-notice">{notice}</p>}
					<button type="button" className="auth-primary" onClick={onSave} disabled={busy || needsBase}>
						피부 정보 저장
					</button>
				</>
			)}
		</section>
	);
}

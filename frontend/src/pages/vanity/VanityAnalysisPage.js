import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { saveRoutinesFromDiagnosis } from '../../api/routines';
import { updateShelfItem } from '../../api/shelf';
import { getVanityAnalysis } from '../../api/vanity';
import ResponsivePage from '../../components/ResponsivePage';
import { errorMessage } from '../../lib/errorMessage';
import { AuthDesktopLayout, AuthMobileLayout } from '../auth/AuthLayout';
import MakeupLooks from './MakeupLooks';
import SkincareDraft from './SkincareDraft';
import './VanityAnalysisPage.css';

// 화장대 진단(퍼스널 탭 설계 3절): 내 화장대의 사용 중인 제품으로 스킨케어 루틴 초안과 색조 조합을 보여준다
const TABS = [
	['skincare', '스킨케어'],
	['makeup', '색조'],
];

function VanityAnalysisContent() {
	const navigate = useNavigate();
	const [data, setData] = useState(null);
	const [tab, setTab] = useState('skincare');
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');

	const load = useCallback(async () => {
		try {
			setData(await getVanityAnalysis());
			setError('');
		} catch (e) {
			setError(errorMessage(e));
		}
	}, []);

	useEffect(() => {
		load();
	}, [load]);

	const pickType = async (shelfItemId, changes) => {
		setBusy(true);
		try {
			await updateShelfItem(shelfItemId, changes);
			await load();
		} catch (e) {
			setError(errorMessage(e));
		} finally {
			setBusy(false);
		}
	};

	// 서버가 지금 화장대로 초안을 다시 계산해 매일 도는 아침·저녁 루틴으로 저장한다(전에 진단에서 저장한 루틴은 바뀐다)
	const saveDraft = async () => {
		if (busy) return;
		setBusy(true);
		setError('');
		try {
			await saveRoutinesFromDiagnosis();
			navigate('/routines', { state: { savedFromDiagnosis: true } });
		} catch (e) {
			setError(errorMessage(e));
			setBusy(false);
		}
	};

	if (!data) {
		return error ? <p role="alert" className="auth-error">{error}</p> : <p aria-busy="true">불러오는 중...</p>;
	}

	return (
		<div className="vanity-analysis">
			<div className="vanity-analysis-tabs" role="tablist" aria-label="화장대 진단 종류">
				{TABS.map(([key, label]) => (
					<button
						key={key}
						type="button"
						role="tab"
						id={`vanity-tab-${key}`}
						aria-selected={tab === key}
						aria-controls={`vanity-panel-${key}`}
						onClick={() => setTab(key)}
					>
						{label}
					</button>
				))}
			</div>
			<div role="tabpanel" id={`vanity-panel-${tab}`} aria-labelledby={`vanity-tab-${tab}`}>
				{tab === 'skincare' ? (
					<SkincareDraft data={data} onPick={pickType} onSave={saveDraft} busy={busy} />
				) : (
					<MakeupLooks data={data} onPick={pickType} busy={busy} />
				)}
			</div>
			{error && <p role="alert" className="auth-error">{error}</p>}
			<p className="vanity-analysis-disclaimer">가진 제품과 성분 규칙으로 만든 참고용 초안이에요. 의학적 판단이 아니에요.</p>
		</div>
	);
}

function VanityAnalysisMobile() {
	return (
		<AuthMobileLayout title="화장대 진단" homeLink>
			<VanityAnalysisContent />
		</AuthMobileLayout>
	);
}

function VanityAnalysisDesktop() {
	return (
		<AuthDesktopLayout title="화장대 진단" homeLink>
			<VanityAnalysisContent />
		</AuthDesktopLayout>
	);
}

export default function VanityAnalysisPage() {
	return <ResponsivePage mobile={VanityAnalysisMobile} desktop={VanityAnalysisDesktop} />;
}

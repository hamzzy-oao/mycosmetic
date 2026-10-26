import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getSkinProfile, saveSkinProfile } from '../../api/skin';
import { analyzeSkin, getSkinAnalysisHistory, getSkinQuestions } from '../../api/skinAnalysis';
import ResponsivePage from '../../components/ResponsivePage';
import { errorMessage } from '../../lib/errorMessage';
import { AuthDesktopLayout, AuthMobileLayout } from '../auth/AuthLayout';
import './SkinAnalysisPage.css';

// 내 피부 분석(퍼스널 탭 설계 1절): 안내 → Q1(피부 유형) → (잘 모르겠어요면) Q1-1(한낮 느낌) → Q2(고민 0~3개) → 결과.
// 문항 문구는 서버(GET /api/skin-analysis/questions)가 준다. 판정은 서버가 하고 화면은 결과와 근거만 보여준다
export const SKIN_TYPE_LABELS = {
	DRY: '건성',
	OILY: '지성',
	COMBINATION: '복합성',
	DEHYDRATED_OILY: '수부지',
	SENSITIVE: '민감성',
	ACNE_PRONE: '트러블성',
};
// 서버 enum 선언 순서(저장할 때 이 순서로 보낸다)
const SKIN_TYPE_ORDER = ['SENSITIVE', 'ACNE_PRONE', 'DRY', 'OILY', 'COMBINATION', 'DEHYDRATED_OILY'];
const CONCERN_LABELS = {
	DRYNESS: '건조함',
	OILINESS: '유분',
	PORES: '모공',
	WRINKLES: '주름',
	PIGMENTATION: '색소침착',
	SENSITIVITY: '민감함',
	ACNE: '여드름',
};
export const DISCLAIMER = '참고용 분석이며 의학적 판단이 아닙니다.';

// "복합성 · 민감성" 같은 한 줄 이름
export function resultTitle(baseType, tags = []) {
	return [baseType, ...tags].filter(Boolean).map((t) => SKIN_TYPE_LABELS[t] || t).join(' · ');
}

function question(questions, id) {
	return questions && questions.questions ? questions.questions.find((q) => q.id === id) : null;
}

function SingleChoice({ q, value, onChange }) {
	return (
		<fieldset className="skin-analysis-question" role="radiogroup" aria-label={q.text}>
			<legend>{q.text}</legend>
			{q.options.map((o) => (
				<label key={o.code} className={value === o.code ? 'skin-analysis-option is-selected' : 'skin-analysis-option'}>
					<input type="radio" name={q.id} value={o.code} checked={value === o.code} onChange={() => onChange(o.code)} />
					{o.label}
				</label>
			))}
		</fieldset>
	);
}

function ConcernChoice({ q, selected, onToggle }) {
	const full = selected.length >= q.maxSelect;
	return (
		<div className="skin-analysis-question" role="group" aria-label={q.text}>
			<p className="skin-analysis-legend">{q.text}</p>
			<p className="auth-hint">고민이 없으면 고르지 않고 넘어가도 돼요.</p>
			<div className="skin-chips">
				{q.options.map((o) => {
					const on = selected.includes(o.code);
					return (
						<button
							key={o.code}
							type="button"
							className={on ? 'skin-chip is-selected' : 'skin-chip'}
							aria-pressed={on}
							disabled={!on && full}
							onClick={() => onToggle(o.code)}
						>
							{o.label}
						</button>
					);
				})}
			</div>
			{full && <p className="auth-hint">최대 {q.maxSelect}개까지 고를 수 있어요.</p>}
		</div>
	);
}

function History({ history }) {
	if (!history || !history.items || history.items.length === 0) return null;
	return (
		<section className="skin-analysis-history" aria-labelledby="skin-history-title">
			<h2 id="skin-history-title">최근 분석</h2>
			{history.changes.length > 0 && (
				<ul className="skin-analysis-changes">
					{history.changes.map((c) => <li key={c}>{c}</li>)}
				</ul>
			)}
			<ol>
				{history.items.map((item) => (
					<li key={item.id}>
						<span>{resultTitle(item.baseType, item.tags)}</span>
						<time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleDateString('ko-KR')}</time>
					</li>
				))}
			</ol>
		</section>
	);
}

function Result({ result, onSensitive, busy, onRestart }) {
	return (
		<section className="skin-analysis-result" aria-labelledby="skin-result-title">
			<p className="skin-analysis-eyebrow">분석 결과</p>
			<h2 id="skin-result-title">{resultTitle(result.baseType, result.tags)}</h2>
			<ul className="skin-analysis-reasons">
				{result.reasons.map((r) => <li key={r}>{r}</li>)}
			</ul>
			{result.concerns.length > 0 && (
				<p className="skin-analysis-concerns">관심 고민: {result.concerns.map((c) => CONCERN_LABELS[c] || c).join(' · ')}</p>
			)}
			{result.candidateIngredients.length > 0 && (
				<div className="skin-analysis-candidates">
					<h3>확인해 볼 후보 성분</h3>
					<p className="auth-hint">안맞아요 제품 2개 이상에 함께 들어 있던 성분이에요. 원인이라고 단정할 수는 없어요.</p>
					<ul>
						{result.candidateIngredients.map((name) => <li key={name}>{name}</li>)}
					</ul>
				</div>
			)}
			{result.suggestSensitive && (
				<div className="skin-analysis-suggest">
					<p>안맞아요 기록이 많아요. 민감성을 추가할까요?</p>
					<button type="button" className="skin-analysis-secondary" onClick={onSensitive} disabled={busy}>
						민감성 추가하기
					</button>
				</div>
			)}
			<p className="skin-analysis-disclaimer">{DISCLAIMER}</p>
			<div className="skin-analysis-actions">
				<button type="button" className="auth-primary" onClick={onRestart}>
					다시 분석하기
				</button>
				<Link to="/me">내 정보에서 직접 고치기</Link>
			</div>
		</section>
	);
}

function SkinAnalysisContent() {
	const [questions, setQuestions] = useState(null);
	const [history, setHistory] = useState(null);
	const [step, setStep] = useState('intro');
	const [selfType, setSelfType] = useState('');
	const [middayFeel, setMiddayFeel] = useState('');
	const [concerns, setConcerns] = useState([]);
	const [result, setResult] = useState(null);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	const [notice, setNotice] = useState('');

	useEffect(() => {
		let cancelled = false;
		getSkinQuestions()
			.then((data) => !cancelled && setQuestions(data))
			.catch((e) => !cancelled && setError(errorMessage(e)));
		// 이력은 없어도 분석은 할 수 있으니 실패해도 안내하지 않는다
		getSkinAnalysisHistory()
			.then((data) => !cancelled && setHistory(data))
			.catch(() => {});
		return () => {
			cancelled = true;
		};
	}, []);

	if (!questions) {
		return error ? <p role="alert" className="auth-error">{error}</p> : <p aria-busy="true">불러오는 중...</p>;
	}

	const toggleConcern = (code) =>
		setConcerns((prev) => (prev.includes(code) ? prev.filter((c) => c !== code) : [...prev, code]));

	const restart = () => {
		setSelfType('');
		setMiddayFeel('');
		setConcerns([]);
		setResult(null);
		setError('');
		setNotice('');
		setStep('selfType');
	};

	const submit = async () => {
		if (busy) return;
		setBusy(true);
		setError('');
		// 보기 순서대로 보낸다
		const ordered = question(questions, 'concerns').options.map((o) => o.code).filter((c) => concerns.includes(c));
		try {
			const data = await analyzeSkin({
				selfType,
				middayFeel: selfType === 'UNSURE' ? middayFeel : null,
				concerns: ordered,
			});
			setResult(data);
			setStep('result');
			getSkinAnalysisHistory().then(setHistory).catch(() => {});
		} catch (e) {
			setError(errorMessage(e));
		} finally {
			setBusy(false);
		}
	};

	// 민감성 제안에 동의: 지금 저장된 타입에 SENSITIVE를 더해 저장한다(퍼스널컬러는 그대로 보낸다)
	const addSensitive = async () => {
		if (busy) return;
		setBusy(true);
		setError('');
		try {
			const profile = await getSkinProfile();
			const types = new Set([...(profile.skinTypes || []), 'SENSITIVE']);
			await saveSkinProfile(SKIN_TYPE_ORDER.filter((t) => types.has(t)), profile.personalColor || null);
			setResult((prev) => ({ ...prev, tags: SKIN_TYPE_ORDER.filter((t) => t === 'SENSITIVE' || prev.tags.includes(t)), suggestSensitive: false }));
			setNotice('민감성을 추가했어요.');
		} catch (e) {
			setError(errorMessage(e));
		} finally {
			setBusy(false);
		}
	};

	const selfQ = question(questions, 'selfType');
	const middayQ = question(questions, 'middayFeel');
	const concernQ = question(questions, 'concerns');

	return (
		<div className="skin-analysis">
			{step === 'intro' && (
				<>
					<div className="skin-analysis-start">
					<p className="skin-analysis-intro">{questions.intro}</p>
					<button type="button" className="auth-primary" onClick={() => setStep('selfType')}>
						시작하기
					</button>
					</div>
					<History history={history} />
				</>
			)}
			{step === 'selfType' && (
				<>
					<p className="skin-analysis-step">1 / 2</p>
					<SingleChoice q={selfQ} value={selfType} onChange={setSelfType} />
					<div className="skin-analysis-nav">
						<button type="button" className="skin-analysis-secondary" onClick={() => setStep('intro')}>이전</button>
						<button
							type="button"
							className="auth-primary"
							disabled={!selfType}
							onClick={() => setStep(selfType === 'UNSURE' ? 'middayFeel' : 'concerns')}
						>
							다음
						</button>
					</div>
				</>
			)}
			{step === 'middayFeel' && (
				<>
					<p className="skin-analysis-step">1 / 2 · 추가 질문</p>
					<SingleChoice q={middayQ} value={middayFeel} onChange={setMiddayFeel} />
					<div className="skin-analysis-nav">
						<button type="button" className="skin-analysis-secondary" onClick={() => setStep('selfType')}>이전</button>
						<button type="button" className="auth-primary" disabled={!middayFeel} onClick={() => setStep('concerns')}>
							다음
						</button>
					</div>
				</>
			)}
			{step === 'concerns' && (
				<>
					<p className="skin-analysis-step">2 / 2</p>
					<ConcernChoice q={concernQ} selected={concerns} onToggle={toggleConcern} />
					<div className="skin-analysis-nav">
						<button
							type="button"
							className="skin-analysis-secondary"
							onClick={() => setStep(selfType === 'UNSURE' ? 'middayFeel' : 'selfType')}
						>
							이전
						</button>
						<button type="button" className="auth-primary" onClick={submit} disabled={busy}>
							결과 보기
						</button>
					</div>
				</>
			)}
			{step === 'result' && result && (
				<>
					<Result result={result} onSensitive={addSensitive} busy={busy} onRestart={restart} />
					<History history={history} />
				</>
			)}
			{error && <p role="alert" className="auth-error">{error}</p>}
			{notice && <p role="status" className="auth-notice">{notice}</p>}
		</div>
	);
}

function SkinAnalysisMobile() {
	return (
		<AuthMobileLayout title="내 피부 분석" homeLink>
			<SkinAnalysisContent />
		</AuthMobileLayout>
	);
}

function SkinAnalysisDesktop() {
	return (
		<AuthDesktopLayout title="내 피부 분석" homeLink>
			<SkinAnalysisContent />
		</AuthDesktopLayout>
	);
}

export default function SkinAnalysisPage() {
	return <ResponsivePage mobile={SkinAnalysisMobile} desktop={SkinAnalysisDesktop} />;
}

import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getShelf } from '../../api/shelf';
import { cancelTrial, finishTrial, getTrials, startTrial } from '../../api/trials';
import { errorMessage } from '../../lib/errorMessage';
import { MOOD_LABELS, dayLabel, summaryText } from '../diary/diaryCommon';
import { productTitle } from '../shelf/shelfCommon';
import './TrialsPage.css';

// 새 제품 2주 테스트 (설계 2026-10-24 B). 매일 기록은 피부 일기(/diary)에서 하고, 여기서는 기간 동안의 기록을 모아 평가한다
const FINISH_CHOICES = [
	['GOOD', '맞음'],
	['BAD', '안 맞음'],
];

const GROUPS = [
	['READY', '평가를 기다려요', '2주가 지났어요. 잘 맞았는지 평가해 주세요.'],
	['ACTIVE', '진행 중', null],
	['DONE', '끝난 테스트', null],
];

const DONE_SHOWN = 5;

// 테스트 기간 14일 칸: 그날 일기의 기분 점, 기록 안 한 날은 빈 칸
function DayStrip({ trial, title }) {
	const today = new Date();
	return (
		<ol className="trial-days" aria-label={`${title} 14일 기록`}>
			{(trial.days || []).map((d, i) => {
				const label = `${i + 1}일째 ${dayLabel(d.date)} ${d.mood ? MOOD_LABELS[d.mood] : '기록 없음'}`;
				const isFuture = new Date(`${d.date}T23:59:59`) > today && !d.mood;
				return (
					<li
						key={d.date}
						className={`trial-day${d.mood ? ` is-${d.mood}` : ''}${isFuture ? ' is-future' : ''}`}
						aria-label={label}
						title={label}
					>
						<span aria-hidden="true">{i + 1}</span>
					</li>
				);
			})}
		</ol>
	);
}

function TrialCard({ trial, onChanged, onError }) {
	const [busy, setBusy] = useState(false);
	const title = productTitle(trial.product);
	const run = async (action) => {
		setBusy(true);
		onError('');
		try {
			await action();
			await onChanged();
		} catch (e) {
			onError(errorMessage(e));
		} finally {
			setBusy(false);
		}
	};
	const open = trial.status !== 'DONE';
	return (
		<li className={`trial-card is-${trial.status}`}>
			<div className="trial-card-head">
				<strong>{title}</strong>
				<span className="trial-card-meta">
					{trial.startedOn} ~ {trial.finishedOn && trial.finishedOn < trial.endsOn ? trial.finishedOn : trial.endsOn}
					{trial.status === 'ACTIVE' && ` · ${trial.day}/${trial.totalDays}일째`}
				</span>
			</div>
			<DayStrip trial={trial} title={title} />
			<p className="trial-summary">{summaryText(trial.summary)}</p>
			{trial.status === 'DONE' && (
				<p className="trial-result">
					평가: <strong>{trial.verdict === 'GOOD' ? '맞음' : trial.verdict === 'BAD' ? '안 맞음' : '평가 전'}</strong>
				</p>
			)}
			{open && (
				<div className="trial-actions">
					{trial.status === 'ACTIVE' && (
						<Link className="trial-record-link" to="/diary">
							오늘 피부 기록하기
						</Link>
					)}
					<span className="trial-ask">{trial.status === 'READY' ? '나에게 맞았나요?' : '일찍 평가하고 끝내기'}</span>
					{FINISH_CHOICES.map(([verdict, label]) => (
						<button
							key={verdict}
							type="button"
							disabled={busy}
							aria-label={`${title} ${label}으로 평가`}
							onClick={() => run(() => finishTrial(trial.id, verdict))}
						>
							{label}
						</button>
					))}
					<button type="button" className="trial-link-button" disabled={busy} onClick={() => run(() => cancelTrial(trial.id))}>
						테스트 취소
					</button>
				</div>
			)}
		</li>
	);
}

function StartForm({ trials, shelf, onChanged }) {
	const [pick, setPick] = useState('');
	const [error, setError] = useState('');
	const [starting, setStarting] = useState(false);
	const tested = new Set(trials.map((t) => t.shelfItemId));
	const candidates = shelf.filter((item) => !tested.has(item.id) && !item.endedOn);
	const activeCount = trials.filter((t) => t.status === 'ACTIVE').length;

	const start = async (e) => {
		e.preventDefault();
		if (!pick) return;
		setStarting(true);
		setError('');
		try {
			await startTrial(Number(pick));
			setPick('');
			await onChanged();
		} catch (err) {
			setError(errorMessage(err));
		} finally {
			setStarting(false);
		}
	};

	return (
		<section className="trial-panel" aria-labelledby="trial-start-title">
			<h2 id="trial-start-title">테스트 시작하기</h2>
			{error && (
				<p role="alert" className="trial-error">
					{error}
				</p>
			)}
			{candidates.length === 0 ? (
				<p className="trial-hint">
					테스트할 수 있는 화장대 제품이 없어요. <Link to="/add">제품 추가하기</Link>
				</p>
			) : (
				<form className="trial-start" onSubmit={start}>
					<label htmlFor="trial-pick">테스트할 제품</label>
					<select id="trial-pick" value={pick} onChange={(e) => setPick(e.target.value)}>
						<option value="">화장대에서 고르기</option>
						{candidates.map((item) => (
							<option key={item.id} value={item.id}>
								{productTitle(item.product)}
							</option>
						))}
					</select>
					<button type="submit" className="trial-primary" disabled={!pick || starting}>
						오늘부터 2주 테스트
					</button>
				</form>
			)}
			{activeCount > 0 && (
				<p className="trial-hint">한 번에 하나씩 테스트하면 어느 제품 때문인지 더 정확히 알 수 있어요.</p>
			)}
		</section>
	);
}

export default function TrialsPage() {
	const [trials, setTrials] = useState(null);
	const [shelf, setShelf] = useState([]);
	const [loadError, setLoadError] = useState('');
	const [actionError, setActionError] = useState('');

	const load = useCallback(async () => {
		const [t, s] = await Promise.all([getTrials(), getShelf()]);
		setTrials(t || []);
		setShelf(s || []);
	}, []);

	useEffect(() => {
		load().catch((e) => setLoadError(errorMessage(e)));
	}, [load]);

	return (
		<section className="trial-page" aria-labelledby="trial-title">
			<h1 id="trial-title" className="visually-hidden">
				새 제품 2주 테스트
			</h1>
			<p className="trial-intro">
				새 제품을 2주 동안 써 보면서 <Link to="/diary">피부 일기</Link>를 매일 쓰면, 그 기간의 기록을 모아 나에게 맞는지
				평가해요. 평가는 화장대에 남아요.
			</p>
			{loadError && (
				<p role="alert" className="trial-error">
					{loadError}
				</p>
			)}
			{!trials && !loadError && <p aria-busy="true">불러오는 중...</p>}
			{trials && (
				<>
					<StartForm trials={trials} shelf={shelf} onChanged={load} />
					{actionError && (
						<p role="alert" className="trial-error">
							{actionError}
						</p>
					)}
					{GROUPS.map(([status, heading, hint]) => {
						const list = trials.filter((t) => t.status === status);
						const shown = status === 'DONE' ? list.slice(0, DONE_SHOWN) : list;
						if (shown.length === 0) return null;
						return (
							<section key={status} className="trial-panel" aria-labelledby={`trial-${status}`}>
								<h2 id={`trial-${status}`}>
									{heading} <span className="trial-count">{list.length}개</span>
								</h2>
								{hint && <p className="trial-hint">{hint}</p>}
								<ul className="trial-list">
									{shown.map((t) => (
										<TrialCard key={t.id} trial={t} onChanged={load} onError={setActionError} />
									))}
								</ul>
							</section>
						);
					})}
					{trials.length === 0 && <p className="trial-hint">아직 시작한 테스트가 없어요.</p>}
					<p className="trial-legend" aria-hidden="true">
						{['GOOD', 'OK', 'BAD'].map((m) => (
							<span key={m} className={`trial-legend-item is-${m}`}>
								<span className="trial-dot" />
								{MOOD_LABELS[m]}
							</span>
						))}
						<span className="trial-legend-item">
							<span className="trial-dot is-empty" />
							기록 없음
						</span>
					</p>
				</>
			)}
		</section>
	);
}

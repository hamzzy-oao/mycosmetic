import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { deleteDiaryEntry, getDiaryMonth, putDiaryEntry } from '../../api/diary';
import { getTrials } from '../../api/trials';
import { errorMessage } from '../../lib/errorMessage';
import { todayString } from '../shelf/shelfCommon';
import {
	MOODS,
	MOOD_LABELS,
	TAGS,
	TAG_LABELS,
	WEEKDAYS,
	addMonths,
	calendarCells,
	dayLabel,
	monthOf,
	trialDates,
} from './diaryCommon';
import './DiaryPage.css';

// 피부 일기 (설계 2026-10-24 A): 매일 피부 기록 전용. 월 달력에서 날짜를 누르고 기분·태그·메모를 남긴다.
// 새 제품 테스트(B)는 /trials에서 하고, 여기서는 테스트 기간인 날만 달력에 표시한다
const EMPTY_FORM = { mood: '', tags: [], memo: '' };

function Calendar({ month, today, entries, testDays, selected, onSelect, onMonth }) {
	return (
		<section className="diary-card" aria-labelledby="diary-month-title">
			<div className="diary-month-head">
				<button type="button" aria-label="이전 달" onClick={() => onMonth(addMonths(month, -1))}>
					<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
						<path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" strokeWidth="2" />
					</svg>
				</button>
				<h2 id="diary-month-title">{month.replace('-', '년 ')}월</h2>
				<button
					type="button"
					aria-label="다음 달"
					disabled={month >= monthOf(today)}
					onClick={() => onMonth(addMonths(month, 1))}
				>
					<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
						<path d="M9 5l7 7-7 7" fill="none" stroke="currentColor" strokeWidth="2" />
					</svg>
				</button>
			</div>
			<div className="diary-grid">
				<div className="diary-row" aria-hidden="true">
					{WEEKDAYS.map((w) => (
						<span key={w} className="diary-weekday">
							{w}
						</span>
					))}
				</div>
				<div className="diary-days">
					{calendarCells(month).map((date, i) => {
						if (!date) return <span key={`blank-${i}`} className="diary-day is-blank" aria-hidden="true" />;
						const entry = entries[date];
						const testing = testDays.has(date);
						const label = `${dayLabel(date)}${entry ? ` ${MOOD_LABELS[entry.mood]}` : ''}${testing ? ' 새 제품 테스트 중' : ''}${date === today ? ' 오늘' : ''}`;
						return (
							<button
								key={date}
								type="button"
								className={`diary-day${entry ? ` is-${entry.mood}` : ''}${testing ? ' is-testing' : ''}${date === today ? ' is-today' : ''}`}
								aria-label={label}
								aria-pressed={selected === date}
								disabled={date > today}
								onClick={() => onSelect(date)}
							>
								<span>{Number(date.slice(8))}</span>
								{entry && <span className="diary-dot" aria-hidden="true" />}
								{testing && <span className="diary-test-mark" aria-hidden="true" />}
							</button>
						);
					})}
				</div>
			</div>
			<p className="diary-legend" aria-hidden="true">
				{MOODS.map((m) => (
					<span key={m} className={`diary-legend-item is-${m}`}>
						<span className="diary-dot" />
						{MOOD_LABELS[m]}
					</span>
				))}
				<span className="diary-legend-item">
					<span className="diary-test-mark" />
					새 제품 테스트 중인 날
				</span>
			</p>
		</section>
	);
}

function EntryEditor({ date, entry, onSaved, onDeleted }) {
	const [form, setForm] = useState(EMPTY_FORM);
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState('');
	const [notice, setNotice] = useState('');

	useEffect(() => {
		setForm(entry ? { mood: entry.mood, tags: entry.tags || [], memo: entry.memo || '' } : EMPTY_FORM);
	}, [date, entry]);

	// 저장·삭제 뒤 entry가 바뀌어도 안내는 남기고, 다른 날을 고르면 지운다
	useEffect(() => {
		setError('');
		setNotice('');
	}, [date]);

	const toggleTag = (tag) =>
		setForm((f) => ({ ...f, tags: f.tags.includes(tag) ? f.tags.filter((t) => t !== tag) : [...f.tags, tag] }));

	const save = async (e) => {
		e.preventDefault();
		if (!form.mood) {
			setError('오늘 피부가 어땠는지 골라 주세요.');
			return;
		}
		setSaving(true);
		setError('');
		try {
			onSaved(await putDiaryEntry(date, form));
			setNotice('저장했어요.');
		} catch (err) {
			setError(errorMessage(err));
		} finally {
			setSaving(false);
		}
	};

	const remove = async () => {
		setSaving(true);
		setError('');
		try {
			await deleteDiaryEntry(date);
			onDeleted(date);
			setNotice('지웠어요.');
		} catch (err) {
			setError(errorMessage(err));
		} finally {
			setSaving(false);
		}
	};

	return (
		<form className="diary-card diary-editor" onSubmit={save} aria-labelledby="diary-editor-title">
			<h2 id="diary-editor-title">{dayLabel(date)} 피부</h2>
			<fieldset>
				<legend>피부 상태</legend>
				<div className="diary-choices">
					{MOODS.map((m) => (
						<button
							key={m}
							type="button"
							className={`diary-mood is-${m}`}
							aria-pressed={form.mood === m}
							onClick={() => setForm((f) => ({ ...f, mood: m }))}
						>
							<span className="diary-dot" aria-hidden="true" />
							{MOOD_LABELS[m]}
						</button>
					))}
				</div>
			</fieldset>
			<fieldset>
				<legend>있었던 일 (여러 개)</legend>
				<div className="diary-choices">
					{TAGS.map((t) => (
						<button
							key={t}
							type="button"
							className="diary-tag"
							aria-pressed={form.tags.includes(t)}
							onClick={() => toggleTag(t)}
						>
							{TAG_LABELS[t]}
						</button>
					))}
				</div>
			</fieldset>
			<label htmlFor="diary-memo">메모</label>
			<textarea
				id="diary-memo"
				maxLength={200}
				rows={3}
				value={form.memo}
				onChange={(e) => setForm((f) => ({ ...f, memo: e.target.value }))}
				placeholder="예: 새 세럼 바른 날, 턱에 트러블"
			/>
			{error && (
				<p role="alert" className="diary-error">
					{error}
				</p>
			)}
			{notice && (
				<p role="status" className="diary-notice">
					{notice}
				</p>
			)}
			<div className="diary-editor-actions">
				<button type="submit" className="diary-primary" disabled={saving}>
					저장
				</button>
				{entry && (
					<button type="button" className="diary-link-button" disabled={saving} onClick={remove}>
						이 날 기록 지우기
					</button>
				)}
			</div>
			<p className="diary-hint">내 기록을 정리한 참고용이에요. 증상이 심하면 피부과 진료를 받아 보세요.</p>
		</form>
	);
}

export default function DiaryPage() {
	const today = todayString();
	const [month, setMonth] = useState(monthOf(today));
	const [selected, setSelected] = useState(today);
	const [entries, setEntries] = useState({});
	const [trials, setTrials] = useState([]);
	const [loading, setLoading] = useState(true);
	const [loadError, setLoadError] = useState('');
	const testDays = useMemo(() => trialDates(trials), [trials]);

	const loadMonth = useCallback(async (m) => {
		const data = await getDiaryMonth(m);
		setEntries(Object.fromEntries((data.entries || []).map((e) => [e.date, e])));
	}, []);

	useEffect(() => {
		let cancelled = false;
		setLoading(true);
		setLoadError('');
		loadMonth(month)
			.catch((e) => !cancelled && setLoadError(errorMessage(e)))
			.finally(() => !cancelled && setLoading(false));
		return () => {
			cancelled = true;
		};
	}, [month, loadMonth]);

	// 테스트 기간 표시는 덤이라 실패해도 일기 쓰기는 막지 않는다
	useEffect(() => {
		let cancelled = false;
		getTrials()
			.then((t) => !cancelled && setTrials(t || []))
			.catch(() => {});
		return () => {
			cancelled = true;
		};
	}, []);

	const changeMonth = (m) => {
		setMonth(m);
		setSelected(m === monthOf(today) ? today : `${m}-01`);
	};

	const saved = (entry) => setEntries((all) => ({ ...all, [entry.date]: entry }));
	const deleted = (date) =>
		setEntries((all) => {
			const next = { ...all };
			delete next[date];
			return next;
		});

	return (
		<section className="diary-page" aria-labelledby="diary-title">
			<h1 id="diary-title" className="visually-hidden">
				피부 일기
			</h1>
			{loadError && (
				<p role="alert" className="diary-error">
					{loadError}
				</p>
			)}
			<p className="diary-intro">
				매일 피부 상태를 남기는 곳이에요. 날짜를 누르고 그날 피부가 어땠는지 기록하세요. 새 제품이 맞는지 보고 싶다면{' '}
				<Link to="/trials">새 제품 테스트</Link>를 시작하세요.
			</p>
			{loading && <p aria-busy="true">불러오는 중...</p>}
			<div className="diary-layout">
				<Calendar
					month={month}
					today={today}
					entries={entries}
					testDays={testDays}
					selected={selected}
					onSelect={setSelected}
					onMonth={changeMonth}
				/>
				<EntryEditor date={selected} entry={entries[selected]} onSaved={saved} onDeleted={deleted} />
			</div>
		</section>
	);
}

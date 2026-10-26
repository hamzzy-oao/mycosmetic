import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { checkRoutineDraft, createRoutine, deleteRoutine, getRoutine, updateRoutine } from '../../api/routines';
import { getShelf } from '../../api/shelf';
import HomeLink from '../../components/HomeLink';
import ResponsivePage from '../../components/ResponsivePage';
import { errorMessage } from '../../lib/errorMessage';
import { productTitle } from '../shelf/shelfCommon';
import { DAYS } from './routineCommon';
import { AddStep, EditActions, RoutineBasics, StepList, WarningPanel } from './routineEditParts';
import './RoutinePage.css';

const DELETE_CONFIRM = '이 루틴을 삭제할까요?';
// 검사는 이름과 관계없다. 이름을 비워 둔 채 편집해도 검사가 400이 되지 않게 고정 이름을 보낸다
const CHECK_NAME = '편집 중';

function orderedDays(days) {
	return DAYS.map((d) => d.value).filter((v) => days.includes(v));
}

function Editor(props) {
	const { steps, names, shelf, check, onAdd, onType, onMove, onRemove } = props;
	const warnedIds = useMemo(
		() => new Set((check ? check.warnings : []).filter((w) => w.level !== 'INFO').flatMap((w) => w.items)),
		[check]
	);
	return (
		<div className="vanity-routine-editor-flow">
			<section className="vanity-routine-editor-section" aria-labelledby="routine-basics-title">
				<h2 id="routine-basics-title"><span className="vanity-routine-section-number" aria-hidden="true">1</span>기본 설정</h2>
				<RoutineBasics {...props} />
			</section>
			<section className="vanity-routine-editor-section" aria-labelledby="routine-products-title">
				<h2 id="routine-products-title"><span className="vanity-routine-section-number" aria-hidden="true">2</span>제품 추가·순서 <small className="vanity-routine-section-count">{steps.length}개</small></h2>
				<StepList steps={steps} names={names} warnedIds={warnedIds} onType={onType} onMove={onMove} onRemove={onRemove} />
				<AddStep shelf={shelf} steps={steps} names={names} onAdd={onAdd} />
			</section>
			<section className="vanity-routine-editor-section" aria-labelledby="routine-save-title">
				<h2 id="routine-save-title"><span className="vanity-routine-section-number" aria-hidden="true">3</span>루틴 저장</h2>
				<WarningPanel {...props} />
				<EditActions {...props} />
			</section>
		</div>
	);
}

function Head({ isNew }) {
	return (
		<>
			<HomeLink />
			<div className="vanity-routine-head">
				<h1 id="routine-edit-title">{isNew ? '새 루틴' : '루틴 편집'}</h1>

			</div>
		</>
	);
}

function Loading({ loading, loadError }) {
	if (loading) return <p aria-busy="true">불러오는 중...</p>;
	if (loadError) {
		return (
			<p role="alert" className="auth-error">
				{loadError}
			</p>
		);
	}
	return null;
}

function RoutineEditMobile(props) {
	const ready = !props.loading && !props.loadError;
	return (
		<section data-layout="mobile" className="vanity-routine vanity-routine-mobile" aria-labelledby="routine-edit-title">
			<Head isNew={props.isNew} />
			<Loading loading={props.loading} loadError={props.loadError} />
			{ready && <Editor {...props} />}
		</section>
	);
}

function RoutineEditDesktop(props) {
	const ready = !props.loading && !props.loadError;
	return (
		<section data-layout="desktop" className="vanity-routine vanity-routine-desktop" aria-labelledby="routine-edit-title">
			<Head isNew={props.isNew} />
			<Loading loading={props.loading} loadError={props.loadError} />
			{ready && <Editor {...props} />}
		</section>
	);
}

// 루틴 만들기·편집 (/routines/new, /routines/:id). 단계가 바뀔 때마다 서버에 미리 보기 검사를 요청해 경고를 바로 보여준다
export default function RoutineEditPage() {
	const { id } = useParams();
	const isNew = id == null;
	const routineId = isNew ? null : Number(id);
	const navigate = useNavigate();

	const [shelf, setShelf] = useState([]);
	const [name, setName] = useState('');
	const [slot, setSlot] = useState('MORNING');
	const [days, setDays] = useState([]);
	const [steps, setSteps] = useState([]);
	const [loading, setLoading] = useState(true);
	const [loadError, setLoadError] = useState('');
	const [check, setCheck] = useState(null);
	const [checkError, setCheckError] = useState('');
	const [saving, setSaving] = useState(false);
	const [saveError, setSaveError] = useState('');
	const [notice, setNotice] = useState('');
	const checkSeq = useRef(0);
	// 화장대에 없는(지운) 항목도 이름을 보여주려고 루틴 응답의 이름을 기억한다
	const [routineNames, setRoutineNames] = useState({});

	const applyRoutine = useCallback((routine) => {
		setName(routine.name);
		setSlot(routine.slot);
		setDays(routine.days || []);
		setSteps(routine.steps.map((s) => ({ shelfItemId: s.shelfItemId, stepType: s.stepType })));
		setRoutineNames(
			Object.fromEntries(routine.steps.map((s) => [s.shelfItemId, [s.brand, s.productName].filter(Boolean).join(' ')]))
		);
	}, []);

	useEffect(() => {
		let cancelled = false;
		(async () => {
			try {
				const [shelfData, routine] = await Promise.all([getShelf(), isNew ? null : getRoutine(routineId)]);
				if (cancelled) return;
				setShelf(Array.isArray(shelfData) ? shelfData : []);
				if (routine) applyRoutine(routine);
			} catch (e) {
				if (!cancelled) setLoadError(errorMessage(e));
			} finally {
				if (!cancelled) setLoading(false);
			}
		})();
		return () => {
			cancelled = true;
		};
	}, [isNew, routineId, applyRoutine]);

	// 시간대·요일·단계가 바뀔 때마다 검사한다. 늦게 온 이전 응답은 버린다
	useEffect(() => {
		if (loading || loadError) return;
		const seq = ++checkSeq.current;
		checkRoutineDraft({ name: CHECK_NAME, slot, days: orderedDays(days), steps }, routineId ?? undefined)
			.then((result) => {
				if (seq !== checkSeq.current) return;
				setCheck(result);
				setCheckError('');
			})
			.catch((e) => {
				if (seq !== checkSeq.current) return;
				setCheckError(errorMessage(e));
			});
	}, [loading, loadError, slot, days, steps, routineId]);

	const names = useMemo(() => {
		const map = { ...routineNames };
		shelf.forEach((item) => {
			map[item.id] = productTitle(item.product);
		});
		return map;
	}, [shelf, routineNames]);

	const edit = (fn) => {
		setNotice('');
		fn();
	};
	const onName = (v) => edit(() => setName(v));
	const onSlot = (v) => edit(() => setSlot(v));
	const onToggleDay = (v) => edit(() => setDays((prev) => (prev.includes(v) ? prev.filter((d) => d !== v) : [...prev, v])));
	const onAdd = (shelfItemId, stepType) => edit(() => setSteps((prev) => [...prev, { shelfItemId, stepType }]));
	const onType = (index, stepType) =>
		edit(() => setSteps((prev) => prev.map((s, i) => (i === index ? { ...s, stepType } : s))));
	const onRemove = (index) => edit(() => setSteps((prev) => prev.filter((_, i) => i !== index)));
	const onMove = (index, delta) =>
		edit(() =>
			setSteps((prev) => {
				const target = index + delta;
				if (target < 0 || target >= prev.length) return prev;
				const next = [...prev];
				[next[index], next[target]] = [next[target], next[index]];
				return next;
			})
		);

	const order = check ? check.suggestedOrder : [];
	const inSuggestedOrder =
		order.length === steps.length && order.every((shelfItemId, i) => steps[i].shelfItemId === shelfItemId);
	const canSort = Boolean(check) && steps.length > 1 && order.length === steps.length && !inSuggestedOrder;
	const onSort = () => {
		if (!canSort) return;
		edit(() => setSteps((prev) => [...prev].sort((a, b) => order.indexOf(a.shelfItemId) - order.indexOf(b.shelfItemId))));
	};

	const onSave = async () => {
		if (saving) return;
		setNotice('');
		if (name.trim() === '') {
			setSaveError('루틴 이름을 적어 주세요.');
			return;
		}
		setSaving(true);
		setSaveError('');
		const draft = { name: name.trim(), slot, days: orderedDays(days), steps };
		try {
			if (isNew) {
				const created = await createRoutine(draft);
				navigate(`/routines/${created.id}`, { replace: true });
				return;
			}
			applyRoutine(await updateRoutine(routineId, draft));
			setNotice('저장했어요.');
		} catch (e) {
			setSaveError(errorMessage(e));
		} finally {
			setSaving(false);
		}
	};

	const onDelete = async () => {
		if (!window.confirm(DELETE_CONFIRM)) return;
		setSaving(true);
		setSaveError('');
		try {
			await deleteRoutine(routineId);
			navigate('/routines', { replace: true });
		} catch (e) {
			setSaveError(errorMessage(e));
			setSaving(false);
		}
	};

	return (
		<ResponsivePage
			mobile={RoutineEditMobile}
			desktop={RoutineEditDesktop}
			isNew={isNew}
			loading={loading}
			loadError={loadError}
			shelf={shelf}
			names={names}
			name={name}
			slot={slot}
			days={days}
			steps={steps}
			check={check}
			checkError={checkError}
			canSort={canSort}
			saving={saving}
			saveError={saveError}
			notice={notice}
			onName={onName}
			onSlot={onSlot}
			onToggleDay={onToggleDay}
			onAdd={onAdd}
			onType={onType}
			onMove={onMove}
			onRemove={onRemove}
			onSort={onSort}
			onSave={onSave}
			onDelete={onDelete}
		/>
	);
}

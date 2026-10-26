import { useCallback, useEffect, useMemo, useState } from 'react';
import { getShelf, getToneMatchedShelf, removeShelfItem, searchShelfByIngredient, updateShelfItem } from '../../api/shelf';
import ResponsivePage from '../../components/ResponsivePage';
import { errorMessage } from '../../lib/errorMessage';
import { isMakeup } from '../../lib/shade';
import ShelfDesktop from './ShelfDesktop';
import ShelfMobile from './ShelfMobile';
import { matchesProductName } from './shelfCommon';
import './ShelfPage.css';

const REMOVE_CONFIRM = '이 제품을 화장대에서 뺄까요?\n평가와 메모도 함께 지워져요.';

// 내 화장대 (설계 7장). 목록 상태와 수정·삭제·성분 검색은 여기서 하고, 폰·PC 화면은 보여주기만 한다
export default function ShelfPage() {
	const [items, setItems] = useState([]);
	const [loading, setLoading] = useState(true);
	const [loadError, setLoadError] = useState('');
	const [actionError, setActionError] = useState('');
	// 성분으로 찾기 결과: { query, matches: { [shelfItemId]: [성분 이름] } }. 검색 중이 아니면 null
	const [found, setFound] = useState(null);
	const [searchBusy, setSearchBusy] = useState(false);
	const [searchError, setSearchError] = useState('');
	const [nameQuery, setNameQuery] = useState('');
	// 내 톤에 맞는 색조(설계 E): 맞는 항목 ID 집합. 필터를 끄면 null
	const [toneIds, setToneIds] = useState(null);
	const [toneBusy, setToneBusy] = useState(false);
	const [toneError, setToneError] = useState('');
	const [toneNeedsColor, setToneNeedsColor] = useState(false);

	const load = useCallback(async () => {
		setLoading(true);
		setLoadError('');
		try {
			const data = await getShelf();
			setItems(Array.isArray(data) ? data : []);
		} catch (e) {
			setLoadError(errorMessage(e));
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		load();
	}, [load]);

	// 실패하면 오류 문구를, 성공하면 빈 값을 돌려준다.
	// 오류를 자기 자리에 보여주는 곳(메모·기간 편집)은 showError: false로 위쪽 오류 표시를 끈다
	const onUpdate = useCallback(async (id, changes, { showError = true } = {}) => {
		setActionError('');
		try {
			const updated = await updateShelfItem(id, changes);
			setItems((prev) =>
				prev.map((item) => (item.id === id ? (updated && updated.id === id ? updated : { ...item, ...changes }) : item))
			);
			return '';
		} catch (e) {
			const message = errorMessage(e);
			if (showError) setActionError(message);
			return message;
		}
	}, []);

	const onRemove = useCallback(async (id) => {
		if (!window.confirm(REMOVE_CONFIRM)) {
			return;
		}
		setActionError('');
		try {
			await removeShelfItem(id);
			setItems((prev) => prev.filter((item) => item.id !== id));
		} catch (e) {
			setActionError(errorMessage(e));
		}
	}, []);

	const onSearch = useCallback(async (query) => {
		setSearchBusy(true);
		setSearchError('');
		try {
			const data = await searchShelfByIngredient(query);
			const matches = {};
			(Array.isArray(data) ? data : []).forEach((hit) => {
				matches[hit.id] = Array.isArray(hit.matchedIngredients) ? hit.matchedIngredients : [];
			});
			setFound({ query, matches });
		} catch (e) {
			setSearchError(errorMessage(e));
		} finally {
			setSearchBusy(false);
		}
	}, []);

	const onToggleTone = useCallback(async () => {
		setToneError('');
		setToneNeedsColor(false);
		if (toneIds) {
			setToneIds(null);
			return;
		}
		setToneBusy(true);
		try {
			const data = await getToneMatchedShelf();
			setToneIds(new Set((Array.isArray(data) ? data : []).map((hit) => hit.id)));
		} catch (e) {
			const code = e && e.response && e.response.data && e.response.data.code;
			if (code === 'PERSONAL_COLOR_REQUIRED') {
				setToneNeedsColor(true);
			} else {
				setToneError(errorMessage(e));
			}
		} finally {
			setToneBusy(false);
		}
	}, [toneIds]);

	const onClearSearch = useCallback(() => {
		setFound(null);
		setSearchError('');
	}, []);

	// 검색 중이면 화장대 목록에서 일치한 항목만, 일치한 성분을 붙여 보여준다(평가 수정 등이 그대로 반영된다).
	// 내 톤 필터를 켜면 그중 톤이 맞는 색조만 남긴다
	const visible = useMemo(() => {
		let result = items.filter((item) => matchesProductName(item, nameQuery));
		if (found) {
			result = result
				.filter((item) => found.matches[item.id])
				.map((item) => ({ ...item, matchedIngredients: found.matches[item.id] }));
		}
		if (toneIds) {
			result = result.filter((item) => toneIds.has(item.id));
		}
		return result;
	}, [items, found, toneIds, nameQuery]);

	const tone = {
		available: items.some(isMakeup),
		active: Boolean(toneIds),
		busy: toneBusy,
		error: toneError,
		needsColor: toneNeedsColor,
		onToggle: onToggleTone,
	};

	const search = {
		query: found ? found.query : '',
		active: Boolean(found),
		busy: searchBusy,
		error: searchError,
		onSearch,
		onClear: onClearSearch,
	};

	return (
		<ResponsivePage
			mobile={ShelfMobile}
			desktop={ShelfDesktop}
			items={visible}
			allCount={items.length}
			search={search}
			nameFilter={{ query: nameQuery, onChange: setNameQuery }}
			tone={tone}
			loading={loading}
			loadError={loadError}
			actionError={actionError}
			onReload={load}
			onUpdate={onUpdate}
			onRemove={onRemove}
		/>
	);
}

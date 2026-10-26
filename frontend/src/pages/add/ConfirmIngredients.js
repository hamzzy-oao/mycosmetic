import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
	checkProductName,
	confirmProduct,
	getBrands,
	matchIngredientText,
	nameCheckNeedsAttention,
	previewProductLink,
	searchProducts,
} from '../../api/products';
import ResponsivePage from '../../components/ResponsivePage';
import { clearDraft, loadDraft, saveDraft } from '../../lib/draftStorage';
import { errorMessage } from '../../lib/errorMessage';
import ConfirmDesktop from './ConfirmDesktop';
import ConfirmMobile from './ConfirmMobile';
import { MAX_ITEMS, itemsFromResult, itemsFromText } from './confirmCommon';

// 개봉 후 사용기한 기본값(개월). 대부분의 스킨케어·메이크업이 12M이고, 상세페이지에서 읽으면 그 값으로 바꾼다
export const DEFAULT_PAO_MONTHS = '12';

// 성분 확인 화면 (설계 4.1의 8·9단계, 6.3, 6.4)
// 상태: items [{ key, rawText, ingredientId, standardName, matchType, candidates }], 제품 정보 form,
//       고른 기존 제품(chosenProduct), 고를 옵션 전성분(sections). 바뀔 때마다 localStorage에 임시 보관하고
//       새로고침하면 되살린다. 용량은 입력칸 없이 상세페이지에서 읽은 값만 보낸다
function initialDraft(job, pasteMode) {
	return {
		items: pasteMode ? [] : itemsFromResult(job && job.result),
		form: { brand: '', name: '', volume: '', category: 'SKINCARE', paoMonths: DEFAULT_PAO_MONTHS },
		chosenProduct: null,
		linkImageUrl: null,
		sections: null,
	};
}

// 서버 매칭 결과(MatchResult 목록)를 화면 항목으로
function itemsFromMatches(matches) {
	return itemsFromResult({ items: matches });
}

// 비어 있으면 null, 1~60 정수가 아니면 NaN
function parsePao(value) {
	const text = String(value == null ? '' : value).trim();
	if (!text) return null;
	const months = Number(text);
	return Number.isInteger(months) && months >= 1 && months <= 60 ? months : NaN;
}

function isValidDraft(draft) {
	return draft && Array.isArray(draft.items) && draft.form && typeof draft.form === 'object';
}

// 같은 브랜드를 먼저 보여준다
function rankByBrand(results, brand) {
	const wanted = brand.trim().toLowerCase();
	const list = Array.isArray(results) ? results : [];
	if (!wanted) return list;
	const same = list.filter((p) => String(p.brand || '').toLowerCase().includes(wanted));
	return [...same, ...list.filter((p) => !same.includes(p))];
}

function validate(draft, jobId) {
	if (jobId == null) return '확인할 성분 읽기 결과가 없어요. 처음부터 다시 시도해 주세요.';
	if (draft.items.length === 0) return '성분을 한 개 이상 넣어 주세요.';
	if (draft.items.length > MAX_ITEMS) return `성분은 ${MAX_ITEMS}개까지 저장할 수 있어요.`;
	if (draft.items.some((item) => !String(item.rawText || '').trim())) return '이름이 비어 있는 성분이 있어요.';
	if (!draft.chosenProduct && (!draft.form.brand.trim() || !draft.form.name.trim())) {
		return '브랜드와 제품명을 입력해 주세요.';
	}
	if (Number.isNaN(parsePao(draft.form.paoMonths))) return '개봉 후 사용기한은 1~60개월로 넣어 주세요.';
	return '';
}

function buildRequest(draft, jobId, differentProduct = false) {
	const items = draft.items.map((item) => ({
		ingredientId: item.ingredientId == null ? null : item.ingredientId,
		rawText: item.rawText.trim(),
	}));
	const paoMonths = parsePao(draft.form.paoMonths);
	if (draft.chosenProduct) {
		return { jobId, productId: draft.chosenProduct.id, items, paoMonths };
	}
	const { brand, name, volume, category } = draft.form;
	return {
		jobId,
		newProduct: {
			barcode: null,
			brand: brand.trim(),
			name: name.trim(),
			volume: String(volume || '').trim() || null,
			category,
			sourceUrl: draft.linkSourceUrl || null,
			differentProduct,
		},
		items,
		paoMonths,
	};
}

export default function ConfirmIngredients({ job, pasteMode, previewUrl, initialImage, onRetake, onAlreadyConfirmed }) {
	const navigate = useNavigate();
	const jobId = job && job.jobId != null ? job.jobId : null;
	const [restored] = useState(() => {
		const saved = loadDraft(jobId);
		return isValidDraft(saved) ? saved : null;
	});
	const [draft, setDraft] = useState(() => restored || initialDraft(job, pasteMode));
	const [search, setSearch] = useState(null);
	const [link, setLink] = useState(null);
	const [busy, setBusy] = useState(false);
	const [pasting, setPasting] = useState(false);
	const [error, setError] = useState('');
	const [nameCheck, setNameCheck] = useState(null); // null | { loading } | { result }
	const [brands, setBrands] = useState([]);
	const mountedRef = useRef(true);
	const linkRequestRef = useRef(null);

	useEffect(() => {
		mountedRef.current = true;
		return () => {
			mountedRef.current = false;
			linkRequestRef.current?.abort();
		};
	}, []);

	useEffect(() => {
		saveDraft(jobId, draft);
	}, [jobId, draft]);

	// 브랜드 입력 자동완성. 못 불러오면 자동완성 없이 쓴다
	useEffect(() => {
		getBrands()
			.then((list) => {
				if (mountedRef.current && Array.isArray(list)) setBrands(list);
			})
			.catch(() => {});
	}, []);

	const updateItems = useCallback((fn) => {
		setDraft((prev) => ({ ...prev, items: fn(prev.items) }));
	}, []);

	const onPick = useCallback((key, value) => {
		updateItems((items) =>
			items.map((item) => {
				if (item.key !== key) return item;
				const candidate = item.candidates.find((c) => String(c.ingredientId) === value);
				return candidate
					? { ...item, ingredientId: candidate.ingredientId, standardName: candidate.standardName }
					: { ...item, ingredientId: null, standardName: null };
			})
		);
	}, [updateItems]);

	const onEdit = useCallback((key, text) => {
		updateItems((items) => items.map((item) => (item.key === key ? { ...item, rawText: text } : item)));
	}, [updateItems]);

	const onMove = useCallback((key, delta) => {
		updateItems((items) => {
			const from = items.findIndex((item) => item.key === key);
			const to = from + delta;
			if (from < 0 || to < 0 || to >= items.length) return items;
			const next = [...items];
			[next[from], next[to]] = [next[to], next[from]];
			return next;
		});
	}, [updateItems]);

	const onRemove = useCallback((key) => {
		updateItems((items) => items.filter((item) => item.key !== key));
	}, [updateItems]);

	const onAddText = useCallback((text) => {
		updateItems((items) => [...items, ...itemsFromText(text)]);
	}, [updateItems]);

	// 붙여넣은 전성분은 서버가 옵션별로 나눠 사전과 맞춘다. 옵션이 여럿이면 고르게 하고, 서버를 못 쓰면 글자 그대로 나눈다.
	// 바로 넣었으면 { keys, total, matched }(넣은 항목, 그중 사전과 맞은 수)를 돌려준다. 글자 읽기가 틀렸는지 볼 때 쓴다
	const onPasteText = useCallback(async (text) => {
		setPasting(true);
		const summary = (added) => ({
			keys: added.map((item) => item.key),
			total: added.length,
			matched: added.filter((item) => item.matchType === 'EXACT' || item.matchType === 'ALIAS').length,
		});
		try {
			const { sections } = await matchIngredientText(text);
			if (!mountedRef.current) return null;
			const list = Array.isArray(sections) ? sections : [];
			if (list.length === 1) {
				const added = itemsFromMatches(list[0].items);
				updateItems((items) => [...items, ...added]);
				return summary(added);
			}
			if (list.length > 1) setDraft((prev) => ({ ...prev, sections: list }));
			return null;
		} catch (e) {
			if (!mountedRef.current) return null;
			const added = itemsFromText(text);
			updateItems((items) => [...items, ...added]);
			return summary(added);
		} finally {
			if (mountedRef.current) setPasting(false);
		}
	}, [updateItems]);

	const onApplySection = useCallback((section) => {
		setDraft((prev) => ({ ...prev, items: itemsFromMatches(section.items), sections: null }));
	}, []);

	const onDismissSections = useCallback(() => {
		setDraft((prev) => ({ ...prev, sections: null }));
	}, []);

	const onField = useCallback((name, value) => {
		setDraft((prev) => ({ ...prev, form: { ...prev.form, [name]: value } }));
		if (name === 'brand' || name === 'name') {
			setSearch(null);
			setNameCheck(null);
		}
	}, []);

	const onSearch = useCallback(async () => {
		const { brand, name } = draft.form;
		setSearch({ loading: true });
		try {
			const results = await searchProducts(name.trim());
			if (mountedRef.current) setSearch({ results: rankByBrand(results, brand) });
		} catch (e) {
			if (mountedRef.current) setSearch({ error: errorMessage(e) });
		}
	}, [draft.form]);

	// 상세페이지에서 가져온 항목만 채운다. 전성분은 목록이 비어 있고 옵션이 하나면 바로 넣고, 아니면 고르게 한다
	const onLinkChange = useCallback(() => {
		linkRequestRef.current?.abort();
		linkRequestRef.current = null;
		setLink(null);
	}, []);

	const onLinkImport = useCallback(async (url) => {
		linkRequestRef.current?.abort();
		const request = new AbortController();
		linkRequestRef.current = request;
		setLink({ loading: true });
		try {
			const preview = await previewProductLink(url, { signal: request.signal });
			if (!mountedRef.current || linkRequestRef.current !== request) return;
			const sections = Array.isArray(preview.ingredientSections) ? preview.ingredientSections : [];
			setDraft((prev) => {
				const fillNow = sections.length === 1 && prev.items.length === 0;
				return {
					...prev,
					form: {
						...prev.form,
						name: String(preview.name || '').slice(0, 200),
						...(preview.volume ? { volume: String(preview.volume).slice(0, 50) } : {}),
						...(preview.paoMonths ? { paoMonths: String(preview.paoMonths) } : {}),
					},
					items: fillNow ? itemsFromMatches(sections[0].items) : prev.items,
					sections: fillNow || sections.length === 0 ? prev.sections : sections,
					chosenProduct: null,
					linkImageUrl: preview.imageUrl || null,
					// 링크 이름을 그대로 저장하면 서버가 출처를 올리브영 링크로 남긴다
					linkSourceUrl: preview.sourceUrl || null,
				};
			});
			setSearch(null);
			setLink({ done: true, notice: preview.notice });
		} catch (e) {
			if (mountedRef.current && linkRequestRef.current === request) {
				setLink({ error: e.code === 'ECONNABORTED'
					? '응답이 늦어 가져오기를 중단했어요. 다시 시도하거나 직접 입력해 주세요.'
					: errorMessage(e) });
			}
		}
	}, []);

	// 후보를 고르면 그 제품(productId)으로, "목록에 없어요"면 새 제품으로 저장한다
	const onChoose = useCallback((product) => {
		setSearch(null);
		if (!product) return;
		setDraft((prev) => ({
			...prev,
			chosenProduct: { id: product.id, brand: product.brand, name: product.name, volume: product.volume || null },
		}));
	}, []);

	const onClearChoice = useCallback(() => {
		setDraft((prev) => ({ ...prev, chosenProduct: null }));
	}, []);

	// 새 제품 이름 점검(2026-10-26). 보여 줄 게 있으면 패널을 열고 false, 없으면 true
	const runNameCheck = useCallback(async (form) => {
		setNameCheck({ loading: true });
		try {
			const result = await checkProductName({ brand: form.brand.trim(), name: form.name.trim(), volume: form.volume });
			if (!mountedRef.current) return false;
			if (nameCheckNeedsAttention(result)) {
				setNameCheck({ result });
				return false;
			}
			setNameCheck(null);
			return true;
		} catch (e) {
			// 점검을 못 해도 저장은 막지 않는다(서버가 같은 이름은 다시 막는다)
			if (mountedRef.current) setNameCheck(null);
			return true;
		}
	}, []);

	// 9단계: 저장 성공하면 임시 보관을 지우고 내 화장대로.
	// 이미 저장한 job(409 EXTRACTION_ALREADY_CONFIRMED)이면 임시 보관을 지우고 제품 추가 화면이 안내한다.
	// differentProduct: 같은 이름 제품이 있어도 다른 제품이라고 사용자가 확인함(서버가 같은 이름이면 409로 막는다)
	const save = useCallback(async (differentProduct) => {
		setBusy(true);
		try {
			await confirmProduct(buildRequest(draft, jobId, differentProduct));
			clearDraft(jobId);
			navigate('/shelf');
		} catch (e) {
			const code = e && e.response && e.response.data && e.response.data.code;
			if (code === 'EXTRACTION_ALREADY_CONFIRMED') {
				clearDraft(jobId);
				if (onAlreadyConfirmed) {
					onAlreadyConfirmed(jobId);
					return;
				}
			}
			if (!mountedRef.current) return;
			setBusy(false);
			if (code === 'PRODUCT_SAME_NAME_EXISTS') {
				// 점검한 사이에 같은 이름 제품이 생겼다. 다시 점검해 고르게 한다
				runNameCheck(draft.form);
				return;
			}
			setError(errorMessage(e));
		}
	}, [draft, jobId, navigate, onAlreadyConfirmed, runNameCheck]);


	const onSave = useCallback(async () => {
		if (busy || pasting || (link && link.loading) || (nameCheck && nameCheck.loading)) return;
		const problem = validate(draft, jobId);
		if (problem) {
			setError(problem);
			return;
		}
		setError('');
		if (draft.chosenProduct) {
			save(false);
			return;
		}
		if (await runNameCheck(draft.form)) save(false);
	}, [busy, pasting, link, nameCheck, draft, jobId, runNameCheck, save]);

	// 점검 패널: 입력한 그대로 저장. 비슷한 제품이 있었으면 다른 제품이라고 확인한 것이다
	const onSaveAnyway = useCallback(() => {
		const different = Boolean(nameCheck && nameCheck.result && nameCheck.result.similar.length > 0);
		setNameCheck(null);
		save(different);
	}, [nameCheck, save]);

	// 점검 패널: 제안대로 고치고 다시 점검한다
	const applyAndRecheck = useCallback((changes) => {
		const form = { ...draft.form, ...changes };
		setDraft((prev) => ({ ...prev, form: { ...prev.form, ...changes } }));
		runNameCheck(form);
	}, [draft.form, runNameCheck]);

	const onApplyBrand = useCallback((brand) => applyAndRecheck({ brand }), [applyAndRecheck]);
	const onApplyName = useCallback((result) => applyAndRecheck({
		name: result.name,
		...(result.volume ? { volume: result.volume } : {}),
	}), [applyAndRecheck]);

	// 점검 패널: 이미 있는 제품을 고르면 그 제품에 저장한다
	const onChooseChecked = useCallback((product) => {
		setNameCheck(null);
		onChoose(product);
	}, [onChoose]);

	return (
		<ResponsivePage
			mobile={ConfirmMobile}
			desktop={ConfirmDesktop}
			result={pasteMode ? null : job && job.result}
			pasteMode={pasteMode}
			initialImage={pasteMode ? initialImage : null}
			previewUrl={previewUrl || draft.linkImageUrl || ''}
			productImageUrl={draft.linkImageUrl || ''}
			restored={Boolean(restored)}
			items={draft.items}
			form={draft.form}
			chosenProduct={draft.chosenProduct}
			search={search}
			brands={brands}
			nameCheck={nameCheck}
			onApplyBrand={onApplyBrand}
			onApplyName={onApplyName}
			onSaveAnyway={onSaveAnyway}
			onChooseChecked={onChooseChecked}
			busy={busy || pasting || Boolean(link && link.loading)}
			pasting={pasting}
			sections={draft.sections}
			onApplySection={onApplySection}
			onDismissSections={onDismissSections}
			error={error}
			onRetake={onRetake}
			onPick={onPick}
			onEdit={onEdit}
			onMove={onMove}
			onRemove={onRemove}
			onAdd={onAddText}
			onPaste={onPasteText}
			onField={onField}
			onSearch={onSearch}
			onChoose={onChoose}
			onClearChoice={onClearChoice}
			onSave={onSave}
			link={link}
			onLinkImport={onLinkImport}
			onLinkChange={onLinkChange}
		/>
	);
}

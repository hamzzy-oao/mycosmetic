import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { createExtraction, createManualExtraction, getExtraction } from '../../api/extractions';
import { getProduct, searchProducts } from '../../api/products';
import { addToShelf } from '../../api/shelf';
import ResponsivePage from '../../components/ResponsivePage';
import { errorMessage } from '../../lib/errorMessage';
import { isOcrImage } from '../../lib/ocr';
import { clearDraft } from '../../lib/draftStorage';
import { pollJob } from '../../lib/pollJob';
import { resizeToJpeg } from '../../lib/resizeImage';
import AddProductDesktop from './AddProductDesktop';
import AddProductMobile from './AddProductMobile';

export const AI_BUSY_MESSAGE = '지금 분석 요청이 많아요. 잠시 후 다시 시도해 주세요.';
export const UPLOAD_NETWORK_MESSAGE = '사진을 올리지 못했어요. 사진이 너무 크면 다시 찍어 주세요.';

// 접수(POST /api/extractions) 오류 안내. 응답이 없으면 네트워크 오류, AI_BUSY는 고정 문구,
// 나머지(EXTRACTION_IN_PROGRESS·DAILY_LIMIT·IMAGE_DIMENSIONS_TOO_LARGE·UNSUPPORTED_IMAGE 등)는 서버 메시지를 쓴다
function uploadErrorMessage(e) {
	if (!e || !e.response) return UPLOAD_NETWORK_MESSAGE;
	if (e.response.data && e.response.data.code === 'AI_BUSY') return AI_BUSY_MESSAGE;
	return errorMessage(e);
}

export const ALREADY_CONFIRMED_MESSAGE = '이미 저장한 성분 읽기 결과예요. 내 화장대에서 확인해 주세요.';

// 이미 저장(확인)한 job인가. 서버의 confirmed가 기준이고, 예전 계약(DONE인데 result가 null)도 같이 본다
function isConfirmedJob(job) {
	return Boolean(job && (job.confirmed || (job.status === 'DONE' && !job.result)));
}

export const UNSUPPORTED_IMAGE_MESSAGE = '이 사진 형식은 읽을 수 없어요. JPEG로 찍거나 다른 사진을 골라 주세요.';

// 제품 추가 (설계 4.1의 1~3, 7단계)
// step: search → found(있는 제품 담기) | photo → waiting → failed | confirm
// 등록 방법 탭으로 제품 찾기(이미 등록된 제품, v-6)·사진(AI)·상세페이지 붙여넣기(AI 없이 바로 confirm) 중 고른다.
// 바코드 등록은 v-5로 뺐다.
// 흐름 상태와 원본 사진·미리보기 주소는 여기서 가지고, 폰·PC 화면은 보여주기만 한다.
// confirm 단계는 성분 확인 화면(ConfirmIngredients)이 원본 미리보기 URL·job을 넘겨받는다.
// 접수한 jobId는 주소(?job=, 붙여넣기면 &paste=1)에도 남겨서 새로고침해도 기다리기·확인 단계로 돌아온다.
// 확인 화면의 편집 내용은 jobId별로 임시 보관된다(lib/draftStorage)
export default function AddProductPage() {
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const resumeRef = useRef({ job: searchParams.get('job'), paste: searchParams.get('paste') === '1' });
	const [step, setStep] = useState('search');
	const [search, setSearch] = useState({ query: '', loading: false, done: false, results: [] });
	const [product, setProduct] = useState(null);
	const [representative, setRepresentative] = useState(null);
	const [file, setFile] = useState(null);
	const [previewUrl, setPreviewUrl] = useState('');
	const [crop, setCrop] = useState(null);
	const [jobId, setJobId] = useState(null);
	const [job, setJob] = useState(null);
	const [timedOut, setTimedOut] = useState(false);
	const [pasteMode, setPasteMode] = useState(false);
	// 오른쪽 칸에 올린 전성분 캡처 이미지. 붙여넣기 확인 화면이 받아서 글자를 읽는다
	const [pasteImage, setPasteImage] = useState(null);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState('');
	const pollRef = useRef(null);
	const previewRef = useRef('');
	const mountedRef = useRef(true);

	const stopPolling = useCallback(() => {
		if (pollRef.current) {
			pollRef.current.abort();
			pollRef.current = null;
		}
	}, []);

	const replacePreview = useCallback((url) => {
		if (previewRef.current && typeof URL.revokeObjectURL === 'function') {
			URL.revokeObjectURL(previewRef.current);
		}
		previewRef.current = url;
		setPreviewUrl(url);
	}, []);

	useEffect(() => {
		mountedRef.current = true;
		return () => {
			mountedRef.current = false;
			stopPolling();
			if (previewRef.current && typeof URL.revokeObjectURL === 'function') {
				URL.revokeObjectURL(previewRef.current);
			}
		};
	}, [stopPolling]);

	// 1단계: 이미 등록된 제품을 이름으로 찾는다(서버는 2글자 미만이면 빈 목록)
	const onSearchProducts = useCallback(async (query) => {
		setError('');
		setSearch({ query, loading: true, done: false, results: [] });
		try {
			const results = await searchProducts(query);
			if (mountedRef.current) setSearch({ query, loading: false, done: true, results: Array.isArray(results) ? results : [] });
		} catch (e) {
			if (mountedRef.current) {
				setSearch({ query, loading: false, done: false, results: [] });
				setError(errorMessage(e));
			}
		}
	}, []);

	// 찾은 제품을 고르면 대표 성분을 보여주고 담기를 묻는다
	const onChooseProduct = useCallback(async (found) => {
		setError('');
		setProduct(found);
		setRepresentative(null);
		setStep('found');
		try {
			const detail = await getProduct(found.id);
			if (mountedRef.current) setRepresentative((detail && detail.representative) || null);
		} catch (e) {
			// 성분 목록을 못 불러와도 담기는 할 수 있다
		}
	}, []);

	const onPhotoMethod = useCallback(() => {
		setError('');
		setProduct(null);
		setStep('photo');
	}, []);

	const onAddFound = useCallback(async () => {
		if (!product || busy) return;
		setError('');
		setBusy(true);
		try {
			await addToShelf(product.representativeListId);
			navigate('/shelf');
		} catch (e) {
			if (mountedRef.current) {
				setError(errorMessage(e));
				setBusy(false);
			}
		}
	}, [product, busy, navigate]);

	const onRestart = useCallback(() => {
		stopPolling();
		setError('');
		setProduct(null);
		setRepresentative(null);
		setJob(null);
		setJobId(null);
		setTimedOut(false);
		setPasteMode(false);
		setStep('search');
		setSearchParams({}, { replace: true });
	}, [stopPolling, setSearchParams]);

	// 이미 저장한 job(새로고침·저장 중 409 EXTRACTION_ALREADY_CONFIRMED): 임시 보관을 지우고 안내와 함께 제품 찾기 단계로
	const onAlreadyConfirmed = useCallback((id) => {
		stopPolling();
		if (id != null) clearDraft(id);
		setJob(null);
		setJobId(null);
		setTimedOut(false);
		setPasteMode(false);
		setStep('search');
		setError(ALREADY_CONFIRMED_MESSAGE);
		setSearchParams({}, { replace: true });
	}, [stopPolling, setSearchParams]);

	// 2단계: 사진 고르기·자르기
	const onFile = useCallback((nextFile) => {
		setError('');
		setCrop(null);
		setFile(nextFile || null);
		replacePreview(nextFile && typeof URL.createObjectURL === 'function' ? URL.createObjectURL(nextFile) : '');
	}, [replacePreview]);

	// 7단계: 1.5초마다 결과 조회, 90초가 지나면 멈추고 [다시 확인]
	const startPolling = useCallback(async (id) => {
		stopPolling();
		const controller = new AbortController();
		pollRef.current = controller;
		setTimedOut(false);
		setError('');
		setStep('waiting');
		try {
			const result = await pollJob(id, { fetchJob: getExtraction, signal: controller.signal });
			if (result.cancelled || !mountedRef.current) return;
			if (result.timedOut) {
				setTimedOut(true);
				return;
			}
			if (isConfirmedJob(result)) {
				onAlreadyConfirmed(id);
				return;
			}
			setJob(result);
			setStep(result.status === 'DONE' ? 'confirm' : 'failed');
		} catch (e) {
			if (controller.signal.aborted || !mountedRef.current) return;
			setError(errorMessage(e));
			setTimedOut(true);
		} finally {
			if (pollRef.current === controller) pollRef.current = null;
		}
	}, [stopPolling, onAlreadyConfirmed]);

	// 3단계: 긴 변 2000px 이하 JPEG로 바꿔 접수 → 202 jobId
	const onSubmitPhoto = useCallback(async () => {
		if (!file || busy) return;
		setError('');
		setBusy(true);
		let blob;
		try {
			blob = await resizeToJpeg(file, crop);
		} catch (e) {
			if (mountedRef.current) {
				setError(e && e.message === 'UNSUPPORTED_IMAGE' ? UNSUPPORTED_IMAGE_MESSAGE : errorMessage(e));
				setBusy(false);
			}
			return;
		}
		try {
			const accepted = await createExtraction(blob);
			if (!mountedRef.current) return;
			setBusy(false);
			setJob(null);
			setPasteMode(false);
			setJobId(accepted.jobId);
			setSearchParams({ job: String(accepted.jobId) }, { replace: true });
			startPolling(accepted.jobId);
		} catch (e) {
			if (mountedRef.current) {
				setError(uploadErrorMessage(e));
				setBusy(false);
			}
		}
	}, [file, crop, busy, startPolling, setSearchParams]);

	const onRecheck = useCallback(() => {
		if (jobId != null) startPolling(jobId);
	}, [jobId, startPolling]);

	const onRetake = useCallback(() => {
		stopPolling();
		setError('');
		setJob(null);
		setJobId(null);
		setTimedOut(false);
		setPasteMode(false);
		setStep('photo');
		setSearchParams({}, { replace: true });
	}, [stopPolling, setSearchParams]);

	// AI가 실패하면 성분을 직접 붙여넣는다(설계 6.3)
	const onPaste = useCallback(() => {
		setError('');
		setPasteMode(true);
		setStep('confirm');
		if (jobId != null) setSearchParams({ job: String(jobId), paste: '1' }, { replace: true });
	}, [jobId, setSearchParams]);

	// 사진 없이 상세페이지 등의 전성분을 바로 붙여넣는다. 서버에 직접 입력 job을 만들고 붙여넣기 확인 화면으로.
	// image(전성분 캡처)를 주면 확인 화면에서 그 이미지의 글자를 바로 읽는다
	const onPasteDirect = useCallback(async (image) => {
		if (busy) return;
		setPasteImage(isOcrImage(image) ? image : null);
		stopPolling();
		setError('');
		setBusy(true);
		try {
			const created = await createManualExtraction();
			if (!mountedRef.current) return;
			setJob({ jobId: created.jobId, status: 'FAILED', errorCode: 'MANUAL_INPUT', result: null, confirmed: false });
			setJobId(created.jobId);
			setTimedOut(false);
			setPasteMode(true);
			setStep('confirm');
			setSearchParams({ job: String(created.jobId), paste: '1' }, { replace: true });
		} catch (e) {
			if (mountedRef.current) setError(errorMessage(e));
		} finally {
			if (mountedRef.current) setBusy(false);
		}
	}, [busy, stopPolling, setSearchParams]);

	// 새로고침: 주소의 jobId로 결과를 다시 불러온다. 진행 중이면 기다리기, 끝났으면 확인 화면으로
	useEffect(() => {
		const { job: jobParam, paste } = resumeRef.current;
		resumeRef.current = {};
		const id = Number(jobParam);
		if (!jobParam || !Number.isInteger(id) || id <= 0) return;
		setJobId(id);
		setStep('waiting');
		getExtraction(id)
			.then((found) => {
				if (!mountedRef.current) return;
				if (isConfirmedJob(found)) {
					// 이미 저장한 job(DONE이든 직접 붙여넣어 저장한 FAILED든)
					onAlreadyConfirmed(id);
				} else if (found.status === 'DONE') {
					setJob(found);
					setStep('confirm');
				} else if (found.status === 'FAILED') {
					setJob(found);
					setPasteMode(paste);
					setStep(paste ? 'confirm' : 'failed');
				} else {
					startPolling(id);
				}
			})
			.catch((e) => {
				if (!mountedRef.current) return;
				setJobId(null);
				setStep('search');
				setError(errorMessage(e));
				setSearchParams({}, { replace: true });
			});
	}, [startPolling, onAlreadyConfirmed, setSearchParams]);

	return (
		<ResponsivePage
			mobile={AddProductMobile}
			desktop={AddProductDesktop}
			step={step}
			search={search}
			product={product}
			representative={representative}
			file={file}
			previewUrl={previewUrl}
			job={job}
			timedOut={timedOut}
			pasteMode={pasteMode}
			pasteImage={pasteImage}
			busy={busy}
			error={error}
			onSearchProducts={onSearchProducts}
			onChooseProduct={onChooseProduct}
			onPhotoMethod={onPhotoMethod}
			onAddFound={onAddFound}
			onRestart={onRestart}
			onFile={onFile}
			onCrop={setCrop}
			onSubmitPhoto={onSubmitPhoto}
			onRecheck={onRecheck}
			onRetake={onRetake}
			onPaste={onPaste}
			onPasteDirect={onPasteDirect}
			onAlreadyConfirmed={onAlreadyConfirmed}
		/>
	);
}

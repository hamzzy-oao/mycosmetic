import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { getAdvice } from '../../api/advice';
import { addToShelf } from '../../api/shelf';
import HomeLink from '../../components/HomeLink';
import ResponsivePage from '../../components/ResponsivePage';
import { errorMessage } from '../../lib/errorMessage';
import { InputPanel, InputTabs, MEDICAL_NOTE, ResultCard } from './adviceCommon';
import './AdvicePage.css';

// "이거 사도 돼?" 규칙 버전 (no-ai-features 설계 F). 성분 붙여넣기로
// POST /api/advice를 부르고 결과 카드를 보여준다. 제품 상세의 "나에게 맞을까?"는 ?product=로 들어온다.
// 사진(AI) 판단은 비용 대비 효율이 낮아 넣지 않는다(2026-10-23 사용자 결정). 사진 AI는 제품 등록에서만 쓴다

function AdviceHead() {
	return (
		<div className="advice-head">
			<h1 id="advice-title" className="visually-hidden">화장대 진단</h1>
			<nav aria-label="판단 메뉴" className="advice-links">
				<Link to="/shelf">내 화장대</Link>
				<Link to="/skin">피부 상태</Link>
			</nav>
		</div>
	);
}

function ResultArea({ advice, judging, ...props }) {
	if (judging) return <p aria-busy="true" className="advice-waiting">판단하는 중...</p>;
	if (!advice) {
		return (
			<div className="advice-empty">
				<p>전성분을 붙여넣으면 내 기록과 비교해 볼게요.</p>
				<p className="advice-note">{MEDICAL_NOTE}</p>
			</div>
		);
	}
	return <ResultCard advice={advice} {...props} />;
}

function AdviceMobile(props) {
	return (
		<section data-layout="mobile" className="advice-page advice-mobile" aria-labelledby="advice-title">
			<HomeLink />
			<AdviceHead />
			<InputTabs tab={props.tab} onTab={props.onTab} />
			<InputPanel {...props} />
			<ResultArea {...props} />
		</section>
	);
}

function AdviceDesktop(props) {
	return (
		<section data-layout="desktop" className="advice-page advice-desktop" aria-labelledby="advice-title">
			<HomeLink />
			<AdviceHead />
			<div className="advice-columns">
				<div className="advice-input">
					<InputTabs tab={props.tab} onTab={props.onTab} />
					<InputPanel {...props} />
				</div>
				<div className="advice-output">
					<ResultArea {...props} />
				</div>
			</div>
		</section>
	);
}

export default function AdvicePage() {
	const navigate = useNavigate();
	const [params, setParams] = useSearchParams();
	const productParam = params.get('product');
	const [tab, setTab] = useState('paste');
	const [advice, setAdvice] = useState(null);
	const [judging, setJudging] = useState(false);
	const [error, setError] = useState('');
	const [adding, setAdding] = useState(false);
	const [addError, setAddError] = useState('');
	const mountedRef = useRef(true);

	useEffect(() => {
		mountedRef.current = true;
		return () => {
			mountedRef.current = false;
		};
	}, []);

	const judge = useCallback(async (body) => {
		setError('');
		setAddError('');
		setJudging(true);
		setAdvice(null);
		try {
			const result = await getAdvice(body);
			if (mountedRef.current) setAdvice(result);
		} catch (e) {
			if (mountedRef.current) setError(errorMessage(e));
		} finally {
			if (mountedRef.current) setJudging(false);
		}
	}, []);

	// 주소의 ?product=로 들어오면 그 제품으로 판단한다(새로고침해도 그대로)
	useEffect(() => {
		const id = Number(productParam);
		if (!productParam || !Number.isInteger(id) || id <= 0) return;
		judge({ productId: id });
	}, [productParam, judge]);

	const onTab = useCallback((next) => {
		setTab(next);
		setError('');
	}, []);

	const onPaste = useCallback(
		(text) => {
			if (productParam) setParams({}, { replace: true });
			judge({ ingredientsText: text });
		},
		[judge, productParam, setParams]
	);

	const onAdd = useCallback(async () => {
		if (!advice || !advice.product || adding) return;
		setAddError('');
		setAdding(true);
		try {
			await addToShelf(advice.product.representativeListId);
			navigate('/shelf');
		} catch (e) {
			if (mountedRef.current) {
				setAddError(errorMessage(e));
				setAdding(false);
			}
		}
	}, [advice, adding, navigate]);

	return (
		<ResponsivePage
			mobile={AdviceMobile}
			desktop={AdviceDesktop}
			tab={tab}
			onTab={onTab}
			advice={advice}
			judging={judging}
			busy={judging}
			error={error}
			onPaste={onPaste}
			adding={adding}
			addError={addError}
			onAdd={onAdd}
		/>
	);
}

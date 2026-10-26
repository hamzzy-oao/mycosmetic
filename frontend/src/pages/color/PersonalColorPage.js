import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { estimateColor, getPalettes, savePersonalColor } from '../../api/personalColor';
import ResponsivePage from '../../components/ResponsivePage';
import { errorMessage } from '../../lib/errorMessage';
import { averageHex, clippedRatio, issueMessage, qualityIssues, sampleRegions, stillSamples, toSamplesPayload } from '../../lib/faceColor';
import { detectFaces } from '../../lib/faceLandmarker';
import { PERSONAL_COLORS } from '../../lib/shade';
import { AuthDesktopLayout, AuthMobileLayout } from '../auth/AuthLayout';
import CameraCapture from './CameraCapture';
import CaptureGuide from './CaptureGuide';
import Draping, { LABELS } from './Draping';
import { grabFileFrame, toPhotoUrl } from './captureFrames';
import './PersonalColorPage.css';

// 퍼스널 컬러 진단(퍼스널 탭 설계 2절): 안내 → 카메라(또는 갤러리 사진) → 판정 → A/B 드레이핑 → 저장.
// 카메라를 쓸 수 없으면 갤러리 사진, 그것도 안 되면 직접 고르기
function PersonalColorContent() {
	const [step, setStep] = useState('guide');
	const [palettes, setPalettes] = useState(null);
	const [payload, setPayload] = useState(null);
	const [photoUrl, setPhotoUrl] = useState(null);
	const [estimate, setEstimate] = useState(null);
	const [manual, setManual] = useState('');
	const [saved, setSaved] = useState(null);
	const [busy, setBusy] = useState(false);
	const [cameraDenied, setCameraDenied] = useState(false);
	const [error, setError] = useState('');

	useEffect(() => {
		getPalettes()
			.then((data) => setPalettes(data.palettes))
			.catch((e) => setError(errorMessage(e)));
	}, []);

	const runEstimate = async (nextPayload, nextPhoto) => {
		setBusy(true);
		setError('');
		setPayload(nextPayload);
		setPhotoUrl(nextPhoto);
		try {
			setEstimate(await estimateColor(nextPayload));
			setStep('result');
		} catch (e) {
			setError(errorMessage(e));
		} finally {
			setBusy(false);
		}
	};

	const onUnavailable = useCallback(() => {
		setCameraDenied(true);
		setStep('fallback');
	}, []);

	const onFile = async (e) => {
		const file = e.target.files && e.target.files[0];
		if (!file) return;
		setError('');
		try {
			const frame = await grabFileFrame(file);
			const faces = await detectFaces(frame.canvas);
			const frames = faces.length === 1 ? stillSamples(frame.image, faces[0]) : null;
			if (!frames) {
				setError(issueMessage(faces.length > 1 ? 'MULTIPLE_FACES' : 'NO_FACE'));
				return;
			}
			// 사진도 카메라와 같은 검사를 거친다. 옆얼굴이면 서버가 저장을 거절하도록 그대로 알린다
			const issues = qualityIssues({ faces, samples: sampleRegions(frame.image, faces[0]), clipped: clippedRatio(frame.image) });
			// 서버가 다시 보지 않는 것(너무 멂·날아간 픽셀 등)도 있으니 사진 단계에서 거른다
			if (issues.length > 0) {
				setError(issues.map(issueMessage).join(' '));
				return;
			}
			await runEstimate(
				{ samples: toSamplesPayload(frames), client: { faceCount: faces.length, frontal: !issues.includes('NOT_FRONTAL') } },
				toPhotoUrl(frame.canvas)
			);
		} catch (err) {
			setError('이 사진은 읽을 수 없어요. 다른 사진을 골라 주세요.');
		}
	};

	const save = async (body) => {
		setBusy(true);
		setError('');
		try {
			setSaved(await savePersonalColor(body));
			setStep('saved');
		} catch (e) {
			setError(errorMessage(e));
		} finally {
			setBusy(false);
		}
	};

	const skinHex = payload
		? averageHex(Object.fromEntries(payload.samples.map((s) => [s.region, s.frames])))
		: null;

	const restart = () => {
		setEstimate(null);
		setPayload(null);
		setPhotoUrl(null);
		setStep('camera');
	};

	return (
		<div className="pc">
			{step === 'guide' && <CaptureGuide onStart={() => setStep('camera')} />}
			{step === 'camera' && (
				<>
					<CameraCapture onCaptured={runEstimate} onUnavailable={onUnavailable} />
					<button type="button" className="pc-link" onClick={() => setStep('fallback')}>
						카메라 대신 사진 고르기
					</button>
				</>
			)}
			{step === 'fallback' && (
				<section className="pc-fallback" aria-labelledby="pc-fallback-title">
					<h2 id="pc-fallback-title">{cameraDenied ? '카메라를 쓸 수 없어요' : '사진으로 진단하기'}</h2>
					<label htmlFor="pc-file">갤러리에서 사진 고르기</label>
					<input id="pc-file" type="file" accept="image/*" onChange={onFile} disabled={busy} />
					<p className="auth-hint">사진도 브라우저 안에서만 읽어요.</p>
					<button type="button" className="pc-link" onClick={() => setStep('manual')}>
						이미 알고 있는 퍼스널컬러 직접 고르기
					</button>
				</section>
			)}
			{step === 'manual' && (
				<section className="pc-manual" aria-labelledby="pc-manual-title">
					<h2 id="pc-manual-title">퍼스널컬러 직접 고르기</h2>
					<label htmlFor="pc-manual-select">퍼스널컬러</label>
					<select id="pc-manual-select" value={manual} onChange={(e) => setManual(e.target.value)}>
						<option value="">골라 주세요</option>
						{PERSONAL_COLORS.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
					</select>
					<button type="button" className="auth-primary" disabled={!manual || busy} onClick={() => save({ method: 'MANUAL', chosen: manual })}>
						저장
					</button>
				</section>
			)}
			{step === 'result' && estimate && (
				<section className="pc-result" aria-labelledby="pc-result-title">
					<h2 id="pc-result-title">읽은 피부색</h2>
					{estimate.quality.passed ? (
						<>
							<p>
								카메라로 본 후보: <strong>{estimate.candidates.map((c) => LABELS[c]).join(', ')}</strong>
							</p>
							<p className="pc-skin">
								<span className="pc-skin-chip" style={{ background: skinHex }} aria-hidden="true" />
								읽은 피부색 {skinHex}
							</p>
							<p className="auth-hint">
								신뢰도 {Math.round(estimate.confidence * 100)}% · 색만 보고 고른 후보라, 다음 화면에서 직접 비교해 확정해요.
							</p>
							<button type="button" className="auth-primary" onClick={() => setStep('draping')} disabled={!palettes}>
								팔레트 비교하기
							</button>
						</>
					) : (
						<>
							<ul className="pc-issues">
								{estimate.quality.issues.map((code) => <li key={code}>{issueMessage(code)}</li>)}
							</ul>
							<button type="button" className="auth-primary" onClick={restart}>
								다시 찍기
							</button>
						</>
					)}
				</section>
			)}
			{step === 'draping' && estimate && palettes && (
				<Draping
					photoUrl={photoUrl}
					palettes={palettes}
					first={estimate.candidates[0]}
					ordered={estimate.ordered}
					onChosen={(chosen) => save({ method: 'CAMERA', samples: payload, chosen })}
				/>
			)}
			{step === 'saved' && saved && (
				<section className="pc-saved" aria-labelledby="pc-saved-title">
					{/* "웜"은 받침이 있어 "으로", "쿨"은 ㄹ 받침이라 "로" */}
					<h2 id="pc-saved-title">{`${LABELS[saved.chosen]}${saved.chosen.endsWith('WARM') ? '으로' : '로'} 저장했어요`}</h2>
					{saved.agreed === false && <p className="auth-hint">카메라 후보와 다르게 골랐어요. 직접 비교한 결과를 따랐어요.</p>}
					<p className="auth-hint">
						내 화장대의 색조 필터와 <Link to="/vanity/analysis">화장대 진단</Link>의 색조 조합에 쓰여요. 언제든 <Link to="/me">내 정보</Link>에서 바꿀 수 있어요.
					</p>
					<button type="button" className="auth-primary" onClick={() => setStep('guide')}>
						다시 진단하기
					</button>
				</section>
			)}
			{busy && step !== 'camera' && <p aria-busy="true">처리하는 중...</p>}
			{error && <p role="alert" className="auth-error">{error}</p>}
			<p className="pc-disclaimer">색만 보고 나눈 참고용 결과예요. 조명과 카메라에 따라 달라질 수 있어요.</p>
		</div>
	);
}

function PersonalColorMobile() {
	return (
		<AuthMobileLayout title="퍼스널 컬러 진단" homeLink>
			<PersonalColorContent />
		</AuthMobileLayout>
	);
}

function PersonalColorDesktop() {
	return (
		<AuthDesktopLayout title="퍼스널 컬러 진단" homeLink>
			<PersonalColorContent />
		</AuthDesktopLayout>
	);
}

export default function PersonalColorPage() {
	return <ResponsivePage mobile={PersonalColorMobile} desktop={PersonalColorDesktop} />;
}

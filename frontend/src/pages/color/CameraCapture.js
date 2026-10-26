import { useEffect, useRef, useState } from 'react';
import { boxAverage, clippedRatio, issueMessage, qualityIssues, sampleRegions, toSamplesPayload } from '../../lib/faceColor';
import { detectFaces } from '../../lib/faceLandmarker';
import { grabVideoFrame, toPhotoUrl } from './captureFrames';

// 카메라 촬영(퍼스널 탭 설계 2절): 실시간 품질 검사 → 통과하면 촬영 → 약 2초 동안 10프레임의 영역 평균색을 모은다.
// 화면을 떠나면 카메라를 끈다. 카메라를 쓸 수 없으면 onUnavailable
const CHECK_MS = 500;
const FRAMES = 10;
const FRAME_GAP_MS = 200;

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export default function CameraCapture({ onCaptured, onUnavailable }) {
	const videoRef = useRef(null);
	const [ready, setReady] = useState(false);
	const [issues, setIssues] = useState(['NO_FACE']);
	const [capturing, setCapturing] = useState(false);
	const [whitePaper, setWhitePaper] = useState(false);
	const capturingRef = useRef(false);

	useEffect(() => {
		let stream = null;
		let timer = null;
		let cancelled = false;
		(async () => {
			try {
				if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) throw new Error('no camera');
				stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user', width: { ideal: 1280 } }, audio: false });
				if (cancelled) {
					stream.getTracks().forEach((t) => t.stop());
					return;
				}
				const video = videoRef.current;
				video.srcObject = stream;
				await video.play();
				setReady(true);
				timer = setInterval(async () => {
					if (capturingRef.current || !video.videoWidth) return;
					try {
						const frame = grabVideoFrame(video);
						const faces = await detectFaces(frame.canvas);
						const samples = faces.length === 1 ? sampleRegions(frame.image, faces[0]) : null;
						if (!cancelled) setIssues(qualityIssues({ faces, samples, clipped: clippedRatio(frame.image) }));
					} catch (e) {
						// 인식 도구(wasm·모델)를 받지 못하면 얼굴이 없다고 하지 않고 따로 알린다
						if (!cancelled) setIssues(['LOAD_FAILED']);
					}
				}, CHECK_MS);
			} catch (e) {
				if (!cancelled) onUnavailable();
			}
		})();
		return () => {
			cancelled = true;
			if (timer) clearInterval(timer);
			if (stream) stream.getTracks().forEach((t) => t.stop());
		};
	}, [onUnavailable]);

	const capture = async () => {
		const video = videoRef.current;
		capturingRef.current = true;
		setCapturing(true);
		const frames = { FOREHEAD: [], LEFT_CHEEK: [], RIGHT_CHEEK: [] };
		let faceCount = 1;
		let frontal = true;
		let photoUrl = null;
		let whiteRef = null;
		try {
			for (let i = 0; i < FRAMES; i += 1) {
				const frame = grabVideoFrame(video);
				const faces = await detectFaces(frame.canvas);
				// 모으는 동안 한 프레임이라도 얼굴 수가 1이 아니거나 옆을 보면 서버에 그대로 알린다
				if (faces.length !== 1) faceCount = faces.length;
				const samples = faces.length === 1 ? sampleRegions(frame.image, faces[0]) : null;
				if (samples) {
					if (qualityIssues({ faces, samples, clipped: 0 }).includes('NOT_FRONTAL')) frontal = false;
					for (const region of Object.keys(frames)) frames[region].push(samples[region]);
					photoUrl = toPhotoUrl(frame.canvas);
					if (whitePaper) whiteRef = boxAverage(frame.image);
				}
				if (i < FRAMES - 1) await wait(FRAME_GAP_MS);
			}
		} catch (e) {
			setIssues(['LOAD_FAILED']);
			return;
		} finally {
			// 인식이 실패해도 "색을 읽는 중"에 멈춰 있지 않게 한다
			capturingRef.current = false;
			setCapturing(false);
		}
		if (frames.FOREHEAD.length === 0) {
			setIssues(['NO_FACE']);
			return;
		}
		const payload = { samples: toSamplesPayload(frames), client: { faceCount, frontal } };
		onCaptured(whiteRef ? { ...payload, whiteRef } : payload, photoUrl);
	};

	return (
		<section className="pc-camera" aria-label="카메라">
			<div className="pc-video-wrap">
				<video ref={videoRef} className="pc-video" playsInline muted aria-label="카메라 미리보기" />
				{whitePaper && <div className="pc-white-box" aria-hidden="true" />}
			</div>
			<label className="pc-white-toggle">
				<input type="checkbox" checked={whitePaper} onChange={(e) => setWhitePaper(e.target.checked)} />
				흰 종이로 조명 색 보정하기(점선 칸에 흰 종이를 들어 주세요)
			</label>
			{!ready && <p aria-busy="true">카메라를 켜는 중...</p>}
			{ready && issues.length > 0 && (
				<ul className="pc-issues" aria-live="polite">
					{issues.map((code) => <li key={code}>{issueMessage(code)}</li>)}
				</ul>
			)}
			{ready && issues.length === 0 && !capturing && <p className="auth-hint" aria-live="polite">좋아요. 가만히 있다가 촬영을 눌러 주세요.</p>}
			{capturing && <p aria-busy="true">색을 읽는 중이에요. 2초만 가만히 있어 주세요.</p>}
			<button type="button" className="auth-primary" onClick={capture} disabled={!ready || issues.length > 0 || capturing}>
				촬영
			</button>
		</section>
	);
}

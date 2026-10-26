// MediaPipe Face Landmarker를 처음 쓸 때 한 번만 만든다(퍼스널 탭 설계 2절).
// 사진·영상은 브라우저 밖으로 나가지 않는다.
// 라이브러리와 wasm(실행 코드)은 우리 출처에서 제공한다(scripts/copy-mediapipe.js가 npm 패키지에서 public/mediapipe로 복사).
// 라이브러리는 webpack이 묶지 않고 브라우저가 처음 쓸 때 직접 불러온다(첫 화면 번들에도 들어가지 않는다).
// 모델 파일은 실행 코드가 아닌 데이터라 공식 배포처에서 받는다(버전 경로 고정, 저장소에 수 MB 파일을 두지 않으려고).
export const LIBRARY_URL = `${process.env.PUBLIC_URL}/mediapipe/vision_bundle.mjs`;
export const WASM_URL = `${process.env.PUBLIC_URL}/mediapipe/wasm`;
const MODEL_URL = 'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task';

let landmarkerPromise = null;

export function loadFaceLandmarker() {
	if (!landmarkerPromise) {
		landmarkerPromise = (async () => {
			const { FaceLandmarker, FilesetResolver } = await import(/* webpackIgnore: true */ LIBRARY_URL);
			const fileset = await FilesetResolver.forVisionTasks(WASM_URL);
			return FaceLandmarker.createFromOptions(fileset, {
				baseOptions: { modelAssetPath: MODEL_URL },
				runningMode: 'IMAGE',
				numFaces: 2,
			});
		})().catch((e) => {
			landmarkerPromise = null; // 다음에 다시 시도할 수 있게
			throw e;
		});
	}
	return landmarkerPromise;
}

// 캔버스(또는 이미지) 한 장에서 얼굴마다 랜드마크 배열
export async function detectFaces(source) {
	const landmarker = await loadFaceLandmarker();
	return landmarker.detect(source).faceLandmarks || [];
}

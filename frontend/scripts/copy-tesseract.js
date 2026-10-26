// 브라우저 글자 읽기(tesseract.js)의 worker·wasm과 한국어·영어 글자 데이터를 npm 패키지에서 public/tesseract로 복사한다.
// 전성분 캡처 이미지를 서버로 보내지 않고 브라우저 안에서 읽는다(2026-10-26 사용자 결정: 비용 0원, 이미지 서버 전송 없음).
// 외부 CDN 대신 우리 출처에서 제공해 package-lock의 무결성 값이 그대로 적용되게 한다(copy-mediapipe.js와 같은 방식).
// npm start·build 전에 자동으로 돈다(prestart·prebuild). 복사본은 깃에 올리지 않는다(.gitignore)
const fs = require('fs');
const path = require('path');

const modules = path.join(__dirname, '..', 'node_modules');
const out = path.join(__dirname, '..', 'public', 'tesseract');

// LSTM 엔진만 쓴다(OEM 1). simd가 되는 브라우저는 simd 판을, 아니면 일반 판을 tesseract.js가 고른다
const files = [
	['tesseract.js/dist/worker.min.js', 'worker.min.js'],
	['tesseract.js-core/tesseract-core-lstm.wasm.js', 'core/tesseract-core-lstm.wasm.js'],
	['tesseract.js-core/tesseract-core-simd-lstm.wasm.js', 'core/tesseract-core-simd-lstm.wasm.js'],
	// best_int: 정확도는 best와 비슷하고 파일이 작다(한국어 약 1.5MB)
	['@tesseract.js-data/kor/4.0.0_best_int/kor.traineddata.gz', 'lang/kor.traineddata.gz'],
	['@tesseract.js-data/eng/4.0.0_best_int/eng.traineddata.gz', 'lang/eng.traineddata.gz'],
];

const missing = files.map(([from]) => path.join(modules, from)).filter((p) => !fs.existsSync(p));
if (missing.length) {
	console.error(`글자 읽기 파일이 없어요(npm install을 먼저 해 주세요):\n${missing.join('\n')}`);
	process.exit(1);
}
fs.rmSync(out, { recursive: true, force: true });
for (const [from, to] of files) {
	const target = path.join(out, to);
	fs.mkdirSync(path.dirname(target), { recursive: true });
	fs.copyFileSync(path.join(modules, from), target);
}
console.log(`tesseract 복사: ${path.relative(process.cwd(), out)}`);

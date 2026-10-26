// MediaPipe 라이브러리(vision_bundle.mjs)와 wasm을 npm 패키지에서 public/mediapipe로 복사한다(퍼스널 탭 설계 2절).
// 실행 코드를 외부 CDN이 아니라 우리 출처에서 제공해 package-lock의 무결성 값이 그대로 적용되게 한다.
// 라이브러리를 webpack이 묶지 않게 하려고 브라우저가 직접 불러온다(묶으면 동적 import·빠진 소스맵 경고가 CI 빌드를 깬다).
// npm start·build 전에 자동으로 돈다(prestart·prebuild). 복사본은 깃에 올리지 않는다(.gitignore)
const fs = require('fs');
const path = require('path');

const pkg = path.join(__dirname, '..', 'node_modules', '@mediapipe', 'tasks-vision');
const from = path.join(pkg, 'wasm');
const out = path.join(__dirname, '..', 'public', 'mediapipe');
const to = path.join(out, 'wasm');

if (!fs.existsSync(from)) {
	console.error(`MediaPipe wasm이 없어요: ${from} (npm install을 먼저 해 주세요)`);
	process.exit(1);
}
fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(to, { recursive: true });
fs.cpSync(from, to, { recursive: true });
fs.copyFileSync(path.join(pkg, 'vision_bundle.mjs'), path.join(out, 'vision_bundle.mjs'));
console.log(`MediaPipe 복사: ${path.relative(process.cwd(), out)}`);

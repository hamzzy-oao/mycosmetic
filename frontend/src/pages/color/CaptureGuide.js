// 촬영 전 안내(퍼스널 탭 설계 2절)
export const GUIDE = [
	'창가처럼 빛이 고르게 드는 곳에서 찍어 주세요.',
	'직사광선, 노란 조명, 여러 조명이 섞인 곳은 피해 주세요.',
	'카메라 앱의 뷰티 필터·보정은 꺼 주세요.',
	'색조 화장과 컬러렌즈는 빼 주세요.',
	'앞머리를 넘겨 이마가 보이게 해 주세요.',
	'화면을 정면으로 봐 주세요.',
	'(선택) 흰 종이를 화면 아래 점선 칸에 들면 조명 색을 보정해요.',
	'사진은 서버로 보내지 않아요. 얼굴 색의 평균값만 보내요.',
];

export default function CaptureGuide({ onStart }) {
	return (
		<section className="pc-guide" aria-labelledby="pc-guide-title">
			<h2 id="pc-guide-title">찍기 전에 확인해 주세요</h2>
			<ul>
				{GUIDE.map((g) => <li key={g}>{g}</li>)}
			</ul>
			<button type="button" className="auth-primary" onClick={onStart}>
				시작하기
			</button>
		</section>
	);
}

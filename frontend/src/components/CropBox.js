import { useEffect, useRef, useState } from 'react';

// 전성분 영역 자르기 (설계 4.1-2). 사진 위에 포인터(손가락·마우스)로 사각형을 그린다.
// onChange에는 사진 크기에 대한 비율 { x, y, width, height }(0~1)을, 전체를 쓰면 null을 준다(기본값)
const MIN_SIZE = 0.03;

function clamp01(value) {
	return Number.isFinite(value) ? Math.min(1, Math.max(0, value)) : 0;
}

function toRect(a, b) {
	return {
		x: Math.min(a.x, b.x),
		y: Math.min(a.y, b.y),
		width: Math.abs(a.x - b.x),
		height: Math.abs(a.y - b.y),
	};
}

export default function CropBox({ imageUrl, onChange }) {
	const areaRef = useRef(null);
	const startRef = useRef(null);
	const [rect, setRect] = useState(null);

	// 사진이 바뀌면 다시 전체로
	useEffect(() => {
		setRect(null);
		startRef.current = null;
	}, [imageUrl]);

	const pointAt = (event) => {
		const box = areaRef.current.getBoundingClientRect();
		if (!box.width || !box.height) return { x: 0, y: 0 };
		return {
			x: clamp01((event.clientX - box.left) / box.width),
			y: clamp01((event.clientY - box.top) / box.height),
		};
	};

	const onPointerDown = (event) => {
		event.preventDefault();
		if (event.currentTarget.setPointerCapture && event.pointerId !== undefined) {
			try {
				event.currentTarget.setPointerCapture(event.pointerId);
			} catch (e) {
				// 포인터 캡처가 안 되는 환경은 그냥 진행한다
			}
		}
		startRef.current = pointAt(event);
		setRect(null);
	};

	const onPointerMove = (event) => {
		if (!startRef.current) return;
		setRect(toRect(startRef.current, pointAt(event)));
	};

	const onPointerUp = (event) => {
		if (!startRef.current) return;
		const next = toRect(startRef.current, pointAt(event));
		startRef.current = null;
		if (next.width < MIN_SIZE || next.height < MIN_SIZE) {
			setRect(null);
			onChange(null);
			return;
		}
		setRect(next);
		onChange(next);
	};

	const onReset = () => {
		setRect(null);
		onChange(null);
	};

	return (
		<div className="crop-box">
			<p className="auth-hint">전성분이 있는 부분을 손가락이나 마우스로 끌어서 고르세요. 고르지 않으면 사진 전체를 읽어요.</p>
			<div
				ref={areaRef}
				className="crop-area"
				data-testid="crop-area"
				onPointerDown={onPointerDown}
				onPointerMove={onPointerMove}
				onPointerUp={onPointerUp}
				onPointerCancel={onPointerUp}
			>
				<img src={imageUrl} alt="고른 사진" draggable={false} />
				{rect && (
					<div
						className="crop-rect"
						data-testid="crop-rect"
						style={{
							left: `${rect.x * 100}%`,
							top: `${rect.y * 100}%`,
							width: `${rect.width * 100}%`,
							height: `${rect.height * 100}%`,
						}}
					/>
				)}
			</div>
			<button type="button" onClick={onReset} disabled={!rect}>
				사진 전체 쓰기
			</button>
		</div>
	);
}

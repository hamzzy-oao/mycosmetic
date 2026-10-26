// 카메라 영상·사진을 캔버스에 그려 픽셀을 읽는다. 결과는 브라우저 메모리에만 있다(서버로 보내지 않음)
const MAX_SIDE = 1280;

// source: <video> 또는 ImageBitmap. { canvas, image(ImageData) }
export function grabFrame(source, width, height) {
	const scale = Math.min(1, MAX_SIDE / Math.max(width, height));
	const canvas = document.createElement('canvas');
	canvas.width = Math.round(width * scale);
	canvas.height = Math.round(height * scale);
	const ctx = canvas.getContext('2d', { willReadFrequently: true });
	ctx.drawImage(source, 0, 0, canvas.width, canvas.height);
	return { canvas, image: ctx.getImageData(0, 0, canvas.width, canvas.height) };
}

export function grabVideoFrame(video) {
	return grabFrame(video, video.videoWidth, video.videoHeight);
}

export async function grabFileFrame(file) {
	const bitmap = await createImageBitmap(file);
	const frame = grabFrame(bitmap, bitmap.width, bitmap.height);
	bitmap.close?.();
	return frame;
}

// 드레이핑에 쓸 사진(메모리 안의 data URL)
export function toPhotoUrl(canvas) {
	return canvas.toDataURL('image/jpeg', 0.85);
}

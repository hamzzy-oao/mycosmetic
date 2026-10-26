// 사진 준비 (설계 4.1-2): 자른 영역을 긴 변 2000px 이하 JPEG로 바꾼다.
// HEIC처럼 브라우저가 읽을 수 있는 형식은 이 과정에서 JPEG가 된다.
// 브라우저가 읽지 못하면 Error('UNSUPPORTED_IMAGE')를 던진다
export const MAX_SIDE = 2000;
const JPEG_QUALITY = 0.9;

// 비율을 지키며 긴 변을 max 이하로 줄인다. 이미 작으면 그대로
export function fitWithin(width, height, max = MAX_SIDE) {
	const longest = Math.max(width, height);
	if (longest <= max) {
		return { width, height };
	}
	const scale = max / longest;
	return {
		width: Math.max(1, Math.round(width * scale)),
		height: Math.max(1, Math.round(height * scale)),
	};
}

function loadWithImgElement(file) {
	return new Promise((resolve, reject) => {
		if (typeof URL.createObjectURL !== 'function') {
			reject(new Error('NO_OBJECT_URL'));
			return;
		}
		const url = URL.createObjectURL(file);
		const img = new Image();
		img.onload = () => {
			URL.revokeObjectURL(url);
			resolve({ source: img, width: img.naturalWidth, height: img.naturalHeight, close: () => {} });
		};
		img.onerror = () => {
			URL.revokeObjectURL(url);
			reject(new Error('IMG_DECODE_FAILED'));
		};
		img.src = url;
	});
}

async function decode(file) {
	if (typeof createImageBitmap === 'function') {
		try {
			// 사진의 회전 정보(EXIF)를 반영해 읽는다
			const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
			return { source: bitmap, width: bitmap.width, height: bitmap.height, close: () => bitmap.close && bitmap.close() };
		} catch (e) {
			// <img>로 다시 시도한다
		}
	}
	try {
		return await loadWithImgElement(file);
	} catch (e) {
		throw new Error('UNSUPPORTED_IMAGE');
	}
}

function clamp01(value) {
	return Math.min(1, Math.max(0, Number(value) || 0));
}

// crop은 사진 크기에 대한 비율 { x, y, width, height } (0~1). 없으면 전체
function cropToPixels(crop, width, height) {
	if (!crop) {
		return { sx: 0, sy: 0, sw: width, sh: height };
	}
	const sx = Math.round(clamp01(crop.x) * width);
	const sy = Math.round(clamp01(crop.y) * height);
	const sw = Math.max(1, Math.min(width - sx, Math.round(clamp01(crop.width) * width)));
	const sh = Math.max(1, Math.min(height - sy, Math.round(clamp01(crop.height) * height)));
	return { sx, sy, sw, sh };
}

export async function resizeToJpeg(file, crop) {
	const image = await decode(file);
	try {
		const { sx, sy, sw, sh } = cropToPixels(crop, image.width, image.height);
		const size = fitWithin(sw, sh);
		const canvas = document.createElement('canvas');
		canvas.width = size.width;
		canvas.height = size.height;
		const context = canvas.getContext('2d');
		if (!context) {
			throw new Error('UNSUPPORTED_IMAGE');
		}
		context.drawImage(image.source, sx, sy, sw, sh, 0, 0, size.width, size.height);
		return await new Promise((resolve, reject) => {
			canvas.toBlob(
				(blob) => (blob ? resolve(blob) : reject(new Error('UNSUPPORTED_IMAGE'))),
				'image/jpeg',
				JPEG_QUALITY
			);
		});
	} finally {
		image.close();
	}
}

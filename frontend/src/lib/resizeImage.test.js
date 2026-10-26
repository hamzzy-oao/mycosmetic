import { fitWithin, resizeToJpeg } from './resizeImage';

test('fitWithin: 4000x3000 → 2000x1500, 1000x800은 그대로', () => {
	expect(fitWithin(4000, 3000)).toEqual({ width: 2000, height: 1500 });
	expect(fitWithin(1000, 800)).toEqual({ width: 1000, height: 800 });
});

test('fitWithin: 세로가 긴 사진과 max 지정', () => {
	expect(fitWithin(3024, 4032)).toEqual({ width: 1500, height: 2000 });
	expect(fitWithin(2000, 2000)).toEqual({ width: 2000, height: 2000 });
	expect(fitWithin(800, 400, 400)).toEqual({ width: 400, height: 200 });
});

describe('resizeToJpeg', () => {
	const originalCreateImageBitmap = global.createImageBitmap;
	const originalImage = global.Image;
	const originalCreateObjectURL = URL.createObjectURL;
	const originalRevokeObjectURL = URL.revokeObjectURL;
	let drawImage;
	let canvases;

	beforeEach(() => {
		drawImage = jest.fn();
		canvases = [];
		jest.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(function getContext() {
			canvases.push(this);
			return { drawImage };
		});
		jest.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback, type, quality) => {
			callback(new Blob(['jpeg'], { type }));
			canvases.quality = quality;
		});
		URL.createObjectURL = jest.fn(() => 'blob:fake');
		URL.revokeObjectURL = jest.fn();
	});

	afterEach(() => {
		jest.restoreAllMocks();
		global.createImageBitmap = originalCreateImageBitmap;
		global.Image = originalImage;
		URL.createObjectURL = originalCreateObjectURL;
		URL.revokeObjectURL = originalRevokeObjectURL;
	});

	test('자른 영역을 긴 변 2000px 이하 JPEG로 만든다', async () => {
		const close = jest.fn();
		const bitmap = { width: 4000, height: 3000, close };
		global.createImageBitmap = jest.fn().mockResolvedValue(bitmap);
		const file = new File(['x'], 'a.png', { type: 'image/png' });

		const blob = await resizeToJpeg(file, { x: 0.5, y: 0, width: 0.5, height: 1 });

		expect(blob.type).toBe('image/jpeg');
		expect(canvases.quality).toBe(0.9);
		// 오른쪽 절반 2000x3000 → 1333x2000
		expect(drawImage).toHaveBeenCalledWith(bitmap, 2000, 0, 2000, 3000, 0, 0, 1333, 2000);
		expect(canvases[0].width).toBe(1333);
		expect(canvases[0].height).toBe(2000);
		expect(close).toHaveBeenCalled();
	});

	test('crop이 없으면 전체를 쓴다', async () => {
		const bitmap = { width: 1000, height: 800, close: jest.fn() };
		global.createImageBitmap = jest.fn().mockResolvedValue(bitmap);

		await resizeToJpeg(new File(['x'], 'a.jpg', { type: 'image/jpeg' }), null);

		expect(drawImage).toHaveBeenCalledWith(bitmap, 0, 0, 1000, 800, 0, 0, 1000, 800);
	});

	test('createImageBitmap이 실패하면 <img>로 읽는다', async () => {
		global.createImageBitmap = jest.fn().mockRejectedValue(new Error('decode'));
		global.Image = class {
			constructor() {
				this.naturalWidth = 3000;
				this.naturalHeight = 3000;
			}

			set src(value) {
				this.loadedSrc = value;
				setTimeout(() => this.onload());
			}
		};

		await resizeToJpeg(new File(['x'], 'a.heic', { type: 'image/heic' }), null);

		expect(drawImage).toHaveBeenCalledWith(expect.any(global.Image), 0, 0, 3000, 3000, 0, 0, 2000, 2000);
		expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:fake');
	});

	test('두 방법 모두 읽지 못하면 UNSUPPORTED_IMAGE', async () => {
		global.createImageBitmap = jest.fn().mockRejectedValue(new Error('decode'));
		global.Image = class {
			set src(value) {
				setTimeout(() => this.onerror(new Error('bad')));
			}
		};

		await expect(resizeToJpeg(new File(['x'], 'a.heic', { type: 'image/heic' }), null)).rejects.toThrow(
			'UNSUPPORTED_IMAGE'
		);
		expect(drawImage).not.toHaveBeenCalled();
	});
});

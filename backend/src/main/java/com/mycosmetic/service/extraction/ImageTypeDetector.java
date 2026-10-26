package com.mycosmetic.service.extraction;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * 업로드된 사진의 형식을 파일 이름이나 Content-Type이 아니라 앞 바이트(시그니처)로 판별한다.
 * 받는 형식은 JPEG와 PNG뿐이다(HEIC 등은 화면이 JPEG로 바꿔서 보낸다).
 * 가로·세로는 머리 부분만 읽어 확인한다(픽셀을 풀지 않는다).
 */
public final class ImageTypeDetector {

	public static final String JPEG = "image/jpeg";
	public static final String PNG = "image/png";

	private static final byte[] JPEG_SIGNATURE = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
	private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };

	/** 사진의 가로·세로(px) */
	public record Dimensions(int width, int height) {
	}

	private ImageTypeDetector() {
	}

	/**
	 * 머리 부분(PNG IHDR, JPEG SOF)에서 가로·세로만 읽는다. 픽셀은 풀지 않는다.
	 * 디스크 임시 파일을 만들지 않도록 메모리 캐시 스트림을 쓴다. 읽을 수 없으면 empty
	 */
	public static Optional<Dimensions> readDimensions(byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			return Optional.empty();
		}
		try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				return Optional.empty();
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				return Optional.of(new Dimensions(reader.getWidth(0), reader.getHeight(0)));
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException e) {
			// 머리가 깨졌거나 잘린 사진
			return Optional.empty();
		}
	}

	/** JPEG면 {@code image/jpeg}, PNG면 {@code image/png}, 그 외에는 empty */
	public static Optional<String> detect(byte[] bytes) {
		if (startsWith(bytes, JPEG_SIGNATURE)) {
			return Optional.of(JPEG);
		}
		if (startsWith(bytes, PNG_SIGNATURE)) {
			return Optional.of(PNG);
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] bytes, byte[] signature) {
		if (bytes == null || bytes.length < signature.length) {
			return false;
		}
		for (int i = 0; i < signature.length; i++) {
			if (bytes[i] != signature[i]) {
				return false;
			}
		}
		return true;
	}
}

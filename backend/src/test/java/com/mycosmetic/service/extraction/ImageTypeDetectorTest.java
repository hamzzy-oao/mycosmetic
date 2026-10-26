package com.mycosmetic.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mycosmetic.support.TestImages;

class ImageTypeDetectorTest {

	static byte[] bytes(int... values) {
		byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = (byte) values[i];
		}
		return result;
	}

	@Test
	@DisplayName("JPEG·PNG 시그니처를 알아본다")
	void detectsJpegAndPng() {
		assertThat(ImageTypeDetector.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10))).contains("image/jpeg");
		assertThat(ImageTypeDetector.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)))
				.contains("image/png");
	}

	@Test
	@DisplayName("fakeJpeg_returns415")
	void fakeJpeg_returns415() {
		// Review Focus 4: 이름만 .jpg인 PDF는 형식을 알 수 없다(컨트롤러 테스트에서 415)
		byte[] pdf = "%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII);

		assertThat(ImageTypeDetector.detect(pdf)).isEmpty();
	}

	@Test
	@DisplayName("시그니처가 잘렸거나 비어 있으면 알 수 없다")
	void truncatedOrEmpty_isEmpty() {
		assertThat(ImageTypeDetector.detect(new byte[0])).isEmpty();
		assertThat(ImageTypeDetector.detect(null)).isEmpty();
		assertThat(ImageTypeDetector.detect(bytes(0xFF, 0xD8))).isEmpty();
		assertThat(ImageTypeDetector.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A))).isEmpty();
		// HEIC(ftyp 상자)는 받지 않는다. 화면이 JPEG로 바꿔서 보낸다
		assertThat(ImageTypeDetector.detect(bytes(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70))).isEmpty();
	}

	@Test
	@DisplayName("머리 부분에서 가로·세로를 읽는다(픽셀 없이 머리만 있어도)")
	void readsDimensionsFromHeader() {
		assertThat(ImageTypeDetector.readDimensions(TestImages.jpeg(3, 5)))
				.contains(new ImageTypeDetector.Dimensions(3, 5));
		assertThat(ImageTypeDetector.readDimensions(TestImages.pngHeaderOnly(1, 20000)))
				.contains(new ImageTypeDetector.Dimensions(1, 20000));
		assertThat(ImageTypeDetector.readDimensions(bytes(0xFF, 0xD8, 0xFF, 0xE0))).isEmpty();
		assertThat(ImageTypeDetector.readDimensions(new byte[0])).isEmpty();
	}
}

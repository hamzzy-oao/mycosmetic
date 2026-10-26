package com.mycosmetic.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

/** 업로드 테스트용 사진 바이트. 서버가 머리 부분에서 가로·세로를 읽으므로 실제로 열리는 사진이어야 한다 */
public final class TestImages {

	private TestImages() {
	}

	/** 가로·세로가 width×height인 JPEG */
	public static byte[] jpeg(int width, int height) {
		return write(width, height, "jpg");
	}

	/** 가로·세로가 width×height인 PNG */
	public static byte[] png(int width, int height) {
		return write(width, height, "png");
	}

	/** 작은 JPEG(2×2) */
	public static byte[] jpeg() {
		return jpeg(2, 2);
	}

	/**
	 * 머리(IHDR)에만 width×height라고 적힌 PNG. 픽셀 데이터는 없다.
	 * 큰 픽셀 배열을 만들지 않고 "머리만 보고 거절하는지"를 확인할 때 쓴다.
	 */
	public static byte[] pngHeaderOnly(int width, int height) {
		ByteBuffer ihdr = ByteBuffer.allocate(13);
		ihdr.putInt(width).putInt(height);
		ihdr.put((byte) 8); // bit depth
		ihdr.put((byte) 2); // color type: truecolor
		ihdr.put((byte) 0).put((byte) 0).put((byte) 0); // compression, filter, interlace
		byte[] type = "IHDR".getBytes(StandardCharsets.US_ASCII);
		CRC32 crc = new CRC32();
		crc.update(type);
		crc.update(ihdr.array());

		ByteBuffer png = ByteBuffer.allocate(8 + 4 + 4 + 13 + 4);
		png.put(new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A });
		png.putInt(13).put(type).put(ihdr.array()).putInt((int) crc.getValue());
		return png.array();
	}

	private static byte[] write(int width, int height, String format) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			if (!ImageIO.write(image, format, out)) {
				throw new IllegalStateException("이미지 writer 없음: " + format);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}
}

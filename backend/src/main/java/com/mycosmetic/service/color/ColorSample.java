package com.mycosmetic.service.color;

import java.util.List;

/** 한 영역에서 프레임마다 뽑은 평균색 */
public record ColorSample(Region region, List<Rgb> frames) {

	public ColorSample {
		frames = List.copyOf(frames);
	}
}

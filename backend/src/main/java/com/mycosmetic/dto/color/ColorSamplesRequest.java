package com.mycosmetic.dto.color;

import java.util.List;

import com.mycosmetic.service.color.Region;

/**
 * POST /api/personal-color/estimate(퍼스널 탭 설계 2절). 사진이 아니라 브라우저가 얼굴 영역에서 뽑은 평균색만 받는다.
 * 값 검사(세 영역이 한 번씩, 영역마다 프레임 1~30개, 0~255)는 서비스가 INVALID_COLOR_SAMPLES로 한다
 *
 * @param whiteRef 흰 종이 보정 기준색(선택)
 * @param client   브라우저가 본 얼굴 수·정면 여부(서버가 확인할 수 없어 그대로 믿는다)
 */
public record ColorSamplesRequest(List<Sample> samples, RgbValue whiteRef, ClientCheck client) {

	public record Sample(Region region, List<RgbValue> frames) {
	}

	public record RgbValue(Integer r, Integer g, Integer b) {
	}

	public record ClientCheck(Integer faceCount, Boolean frontal) {
	}
}

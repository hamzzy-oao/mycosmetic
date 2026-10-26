package com.mycosmetic.service.skin;

import com.mycosmetic.entity.SkinType;

/** Q1-1. 보습제 없이 한낮이 되면 피부가 어떤가요(Q1이 UNSURE일 때만, 퍼스널 탭 설계 1절) */
public enum MiddayFeel {
	/** 얼굴 전체가 당기고 건조해요 */
	ALL_TIGHT(SkinType.DRY),
	/** 볼은 당기고 T존은 괜찮아요 */
	CHEEK_TIGHT(SkinType.COMBINATION),
	/** 볼은 괜찮고 T존이 번들거려요 */
	TZONE_OILY(SkinType.COMBINATION),
	/** 얼굴 전체가 번들거려요 */
	ALL_OILY(SkinType.OILY);

	private final SkinType type;

	MiddayFeel(SkinType type) {
		this.type = type;
	}

	public SkinType type() {
		return type;
	}
}

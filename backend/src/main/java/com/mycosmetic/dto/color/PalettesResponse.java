package com.mycosmetic.dto.color;

import java.util.List;
import java.util.Map;

import com.mycosmetic.entity.PersonalColor;

/** 계절별 팔레트 hex(드레이핑·색 범위 추천용) */
public record PalettesResponse(Map<PersonalColor, List<String>> palettes, String ruleVersion) {
}

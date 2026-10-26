package com.mycosmetic.service.routine;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.mycosmetic.service.ingredient.IngredientNameNormalizer;

/**
 * 루틴 충돌 판정에 쓰는 성분 그룹(코드 상수, no-ai-features 설계 A).
 * 성분 이름(표준명 또는 원문)을 정규화하고 공백을 뺀 문자열에 키워드가 들어 있으면 그 그룹이다.
 * 설계의 한국어 키워드에 같은 뜻의 영문 INCI 키워드를 더했다(원문이 영문인 전성분 대비).
 */
public enum IngredientGroup {

	RETINOID("레티노이드", List.of("레티놀", "레티날", "레티닐", "트레티노인", "아다팔렌",
			"retinol", "retinal", "retinyl", "tretinoin", "adapalene")),
	AHA("AHA", List.of("글라이콜릭애씨드", "락틱애씨드", "만델릭애씨드",
			"glycolicacid", "lacticacid", "mandelicacid")),
	BHA("BHA", List.of("살리실릭애씨드", "베타인살리실레이트", "salicylicacid", "betainesalicylate")),
	VITAMIN_C("비타민C", List.of("아스코빅애씨드", "아스코빌", "에칠아스코빌", "ascorbicacid", "ascorbyl")),
	BENZOYL_PEROXIDE("벤조일퍼옥사이드", List.of("벤조일퍼옥사이드", "benzoylperoxide")),
	NIACINAMIDE("나이아신아마이드", List.of("나이아신아마이드", "niacinamide"));

	private final String label;
	private final List<String> keywords;

	IngredientGroup(String label, List<String> keywords) {
		this.label = label;
		this.keywords = keywords;
	}

	/** 화면 문구에 쓰는 이름 */
	public String label() {
		return label;
	}

	/** 성분 이름 하나가 속한 그룹들 */
	public static Set<IngredientGroup> of(String name) {
		Set<IngredientGroup> groups = EnumSet.noneOf(IngredientGroup.class);
		if (name == null || name.isBlank()) {
			return groups;
		}
		String key = squash(name);
		for (IngredientGroup group : values()) {
			if (group.keywords.stream().anyMatch(key::contains)) {
				groups.add(group);
			}
		}
		return groups;
	}

	/** 여러 성분 이름(한 제품의 전성분)이 속한 그룹들 */
	public static Set<IngredientGroup> detect(Collection<String> names) {
		Set<IngredientGroup> groups = EnumSet.noneOf(IngredientGroup.class);
		for (String name : names) {
			groups.addAll(of(name));
		}
		return groups;
	}

	private static String squash(String name) {
		return IngredientNameNormalizer.normalize(name).value().replace(" ", "");
	}
}

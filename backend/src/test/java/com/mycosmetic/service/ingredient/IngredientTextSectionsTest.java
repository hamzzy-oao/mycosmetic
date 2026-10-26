package com.mycosmetic.service.ingredient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mycosmetic.service.ingredient.IngredientTextSections.Section;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 올리브영 상품정보 제공고시에서 받은 실제 전성분 글(src/test/resources/oliveyoung/article-*.json)로 확인한다 */
class IngredientTextSectionsTest {

	static String ingredientsOf(String fixture) throws IOException {
		try (InputStream in = IngredientTextSectionsTest.class.getResourceAsStream("/oliveyoung/" + fixture)) {
			JsonNode root = JsonMapper.builder().build().readTree(in);
			for (JsonNode item : root.path("data").path("articleInfoList").path(0).path("items")) {
				if (item.path("title").asText("").contains("모든 성분")) {
					return item.path("content").asText();
				}
			}
		}
		throw new IllegalStateException("전성분 항목 없음: " + fixture);
	}

	@Test
	@DisplayName("구역 이름 줄(1. 제품명)이 하나뿐이면 구역 없이 성분만, 숫자 사이 쉼표는 나누지 않는다")
	void sunscreen_singleSection() throws IOException {
		List<Section> sections = IngredientTextSections.parse(ingredientsOf("article-sunscreen-numbered-header.json"));

		assertThat(sections).hasSize(1);
		assertThat(sections.get(0).label()).isNull();
		assertThat(sections.get(0).names()).startsWith("정제수", "징크옥사이드").contains("1,2-헥산다이올")
				.endsWith("루틴").hasSize(36);
	}

	@Test
	@DisplayName("[호수] 성분… 형식은 호수마다 구역, 괄호 안 CI 번호는 이름에 남긴다")
	void cushion_shades() throws IOException {
		List<Section> sections = IngredientTextSections.parse(ingredientsOf("article-cushion-12-shades.json"));

		assertThat(sections).extracting(Section::label).startsWith("크림", "페어").contains("미디엄딥").hasSize(12);
		assertThat(sections.get(0).names()).startsWith("정제수", "사이클로펜타실록세인", "티타늄디옥사이드 (CI 77891)")
				.endsWith("마이카 (CI 77019)");
	}

	@Test
	@DisplayName("[이름]성분… 처럼 띄어쓰기가 없어도 나눈다")
	void set_bracketWithoutSpace() throws IOException {
		List<Section> sections = IngredientTextSections.parse(ingredientsOf("article-set-bracket-no-space.json"));

		assertThat(sections).extracting(Section::label).containsExactly("자음수", "자음유액", "윤조에센스");
		assertThat(sections.get(1).names()).contains("하이드로제네이티드폴리(C6-14올레핀)");
	}

	@Test
	@DisplayName("이름 ㅣ 성분 형식")
	void lip_pipe() throws IOException {
		List<Section> sections = IngredientTextSections.parse(ingredientsOf("article-lip-pipe-label.json"));

		assertThat(sections.get(0).label()).isEqualTo("퍼펙트트윈립#901트루피치");
		assertThat(sections.get(0).names()).startsWith("아이소도데케인");
	}

	@Test
	@DisplayName("팔레트: [이름] 아래 번호 줄·이름 줄마다 구역을 만들고 최대 30개까지")
	void palettes() throws IOException {
		List<Section> numbered = IngredientTextSections.parse(ingredientsOf("article-palette-numbered-pans.json"));
		List<Section> plain = IngredientTextSections.parse(ingredientsOf("article-palette-plain-labels.json"));

		assertThat(numbered.get(0).label()).isEqualTo("01 데이지 · 1번");
		assertThat(numbered.get(0).names()).startsWith("탤크", "마이카 (CI 77019)");
		assertThat(numbered).hasSizeLessThanOrEqualTo(IngredientTextSections.MAX_SECTIONS);
		assertThat(plain.get(0).label()).isEqualTo("소프트 스모키 · SS 1");
		assertThat(plain.get(1).label()).isEqualTo("소프트 스모키 · SS 2");
	}

	@Test
	@DisplayName("붙여넣은 글: 머리말·줄바꿈·전각 쉼표·+/- 표기·끝 마침표를 정리한다")
	void pasted() {
		List<Section> sections = IngredientTextSections.parse(
				"전성분: 정제수, 글리세린，1,2-헥산다이올\n부틸렌글라이콜, [+/- 마이카 (CI 77019), 적색산화철]. ");

		assertThat(sections).hasSize(1);
		assertThat(sections.get(0).names())
				.containsExactly("정제수", "글리세린", "1,2-헥산다이올", "부틸렌글라이콜", "마이카 (CI 77019)", "적색산화철");
	}

	@Test
	@DisplayName("쉼표가 없는 한 성분짜리 글과 빈 글")
	void singleAndEmpty() {
		assertThat(IngredientTextSections.parse("호호바씨오일").get(0).names()).containsExactly("호호바씨오일");
		assertThat(IngredientTextSections.parse("  \n ")).isEmpty();
		assertThat(IngredientTextSections.parse(null)).isEmpty();
	}
}

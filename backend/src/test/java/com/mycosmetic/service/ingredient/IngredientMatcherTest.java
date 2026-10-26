package com.mycosmetic.service.ingredient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientAlias;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.repository.IngredientAliasRepository;
import com.mycosmetic.repository.IngredientRepository;
import com.mycosmetic.service.ingredient.MatchResult.Candidate;
import com.mycosmetic.support.IntegrationTestSupport;

/**
 * 설계 4.1의 5단계. InnoDB FULLTEXT는 커밋된 행만 찾으므로 시드를 커밋하고 검색한 뒤, 끝나면 지운다.
 */
class IngredientMatcherTest extends IntegrationTestSupport {

	@Autowired
	IngredientMatcher matcher;

	@Autowired
	IngredientRepository ingredientRepository;

	@Autowired
	IngredientAliasRepository aliasRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private final List<Ingredient> seeded = new ArrayList<>();

	Ingredient water;
	Ingredient glycerin;
	Ingredient niacinamide;
	Ingredient butyleneGlycol;
	Ingredient sodiumHyaluronate;
	Ingredient glycyrrhizate;

	@BeforeEach
	void seed() {
		water = seed("정제수", "물");
		glycerin = seed("글리세린");
		niacinamide = seed("나이아신아마이드", "니코틴아마이드");
		butyleneGlycol = seed("부틸렌글라이콜");
		sodiumHyaluronate = seed("소듐하이알루로네이트");
		glycyrrhizate = seed("다이포타슘글리시리제이트추출물에센스액");
	}

	@AfterEach
	void cleanUp() {
		for (Ingredient ingredient : seeded) {
			jdbcTemplate.update("DELETE FROM ingredient_aliases WHERE ingredient_id = ?", ingredient.getId());
			jdbcTemplate.update("DELETE FROM ingredients WHERE id = ?", ingredient.getId());
		}
	}

	@Test
	@DisplayName("표준명과 같으면 EXACT")
	void exact() {
		MatchResult result = matcher.match(List.of("글리세린")).get(0);

		assertThat(result.type()).isEqualTo(MatchType.EXACT);
		assertThat(result.ingredientId()).isEqualTo(glycerin.getId());
		assertThat(result.standardName()).isEqualTo("글리세린");
		assertThat(result.candidates()).isEmpty();
	}

	@Test
	@DisplayName("이명과 같으면 ALIAS이고 표준명을 돌려준다")
	void alias() {
		MatchResult result = matcher.match(List.of("니코틴아마이드")).get(0);

		assertThat(result.type()).isEqualTo(MatchType.ALIAS);
		assertThat(result.rawText()).isEqualTo("니코틴아마이드");
		assertThat(result.ingredientId()).isEqualTo(niacinamide.getId());
		assertThat(result.standardName()).isEqualTo("나이아신아마이드");
	}

	@Test
	@DisplayName("한 글자 틀리면 FUZZY 후보에 정답이 1순위")
	void fuzzy() {
		MatchResult result = matcher.match(List.of("나이아신아마이트")).get(0);

		assertThat(result.type()).isEqualTo(MatchType.FUZZY);
		assertThat(result.ingredientId()).isNull();
		assertThat(result.standardName()).isNull();
		assertThat(result.candidates()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
		Candidate first = result.candidates().get(0);
		assertThat(first.ingredientId()).isEqualTo(niacinamide.getId());
		assertThat(first.standardName()).isEqualTo("나이아신아마이드");
		assertThat(first.similarity()).isEqualTo(0.875);
	}

	@Test
	@DisplayName("유사도 0.8 미만만 있으면 UNMATCHED")
	void belowThresholdIsUnmatched() {
		// 경계값: 0.8은 FUZZY, 0.79는 UNMATCHED
		String at08 = "소듐하이알루로나이드";
		String at079 = "다이포타슘글리시리제이트추출물오일크림";
		assertThat(NameSimilarity.of(at08, "소듐하이알루로네이트")).isCloseTo(0.8, within(1e-9));
		assertThat(NameSimilarity.of(at079, "다이포타슘글리시리제이트추출물에센스액")).isCloseTo(0.789, within(0.001));

		List<MatchResult> results = matcher.match(List.of(at08, at079, "전혀없는성분이름"));

		assertThat(results.get(0).type()).isEqualTo(MatchType.FUZZY);
		assertThat(results.get(0).candidates()).extracting(Candidate::ingredientId).containsExactly(sodiumHyaluronate.getId());
		assertThat(results.get(1).type()).isEqualTo(MatchType.UNMATCHED);
		assertThat(results.get(1).candidates()).isEmpty();
		assertThat(results.get(1).ingredientId()).isNull();
		assertThat(results.get(2).type()).isEqualTo(MatchType.UNMATCHED);
	}

	@Test
	@DisplayName("괄호 표기를 떼고 매칭하고 position은 입력 순서")
	void parenthesesAndPosition() {
		List<MatchResult> results = matcher.match(List.of("정제수(Aqua)", " 부틸렌글라이콜 ", "니코틴아마이드"));

		assertThat(results).extracting(MatchResult::position).containsExactly(1, 2, 3);
		assertThat(results).extracting(MatchResult::rawText)
				.containsExactly("정제수(Aqua)", " 부틸렌글라이콜 ", "니코틴아마이드");
		assertThat(results).extracting(MatchResult::type)
				.containsExactly(MatchType.EXACT, MatchType.EXACT, MatchType.ALIAS);
		assertThat(results.get(0).ingredientId()).isEqualTo(water.getId());
		assertThat(results.get(1).ingredientId()).isEqualTo(butyleneGlycol.getId());
	}

	@Test
	@DisplayName("같은 이명이 여러 성분에 걸리면 ALIAS가 아니라 FUZZY로 후보 제시")
	void ambiguousAliasBecomesFuzzy() {
		Ingredient other = seed("미네랄워터");
		aliasRepository.save(IngredientAlias.create(other, "물", "물"));

		MatchResult result = matcher.match(List.of("물")).get(0);

		assertThat(result.type()).isEqualTo(MatchType.FUZZY);
		assertThat(result.ingredientId()).isNull();
		assertThat(result.candidates()).extracting(Candidate::ingredientId)
				.containsExactlyInAnyOrder(water.getId(), other.getId());
		assertThat(result.candidates()).extracting(Candidate::similarity).containsOnly(1.0);
	}

	@Test
	@DisplayName("빈 이름은 UNMATCHED")
	void blankIsUnmatched() {
		MatchResult result = matcher.match(List.of("  ")).get(0);

		assertThat(result.type()).isEqualTo(MatchType.UNMATCHED);
		assertThat(result.position()).isEqualTo(1);
	}

	/** 표준명과 이명을 alias로 넣고 커밋한다 */
	private Ingredient seed(String standardName, String... synonyms) {
		Ingredient ingredient = ingredientRepository.save(Ingredient.create(standardName, null, null));
		seeded.add(ingredient);
		aliasRepository.save(IngredientAlias.create(ingredient, standardName,
				IngredientNameNormalizer.normalize(standardName).value()));
		for (String synonym : synonyms) {
			aliasRepository.save(IngredientAlias.create(ingredient, synonym,
					IngredientNameNormalizer.normalize(synonym).value()));
		}
		return ingredient;
	}
}

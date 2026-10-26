package com.mycosmetic.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mycosmetic.entity.SkinAnalysis;
import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.entity.SkinProfile;
import com.mycosmetic.entity.SkinType;
import com.mycosmetic.entity.User;
import com.mycosmetic.service.user.UserService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** 피부 분석 이력·고민 저장과 V10 데이터 정리(퍼스널 탭 설계 1절) */
class SkinAnalysisRepositoryTest extends IntegrationTestSupport {

	/** V10__skin_analysis.sql의 기본 타입 정리 문장과 같다. 이미 마이그레이션이 돈 DB에서 같은 규칙을 다시 확인한다 */
	static final List<String> MERGE_SQL = List.of("""
			UPDATE skin_profiles
			SET skin_types = JSON_ARRAY_APPEND(skin_types, '$', 'COMBINATION')
			WHERE JSON_CONTAINS(skin_types, '"DRY"') AND JSON_CONTAINS(skin_types, '"OILY"')
			  AND NOT JSON_CONTAINS(skin_types, '"COMBINATION"') AND user_id = ?""", """
			UPDATE skin_profiles
			SET skin_types = JSON_REMOVE(skin_types, JSON_UNQUOTE(JSON_SEARCH(skin_types, 'one', 'DRY')))
			WHERE JSON_CONTAINS(skin_types, '"COMBINATION"') AND JSON_CONTAINS(skin_types, '"DRY"') AND user_id = ?""",
			"""
					UPDATE skin_profiles
					SET skin_types = JSON_REMOVE(skin_types, JSON_UNQUOTE(JSON_SEARCH(skin_types, 'one', 'OILY')))
					WHERE JSON_CONTAINS(skin_types, '"COMBINATION"') AND JSON_CONTAINS(skin_types, '"OILY"') AND user_id = ?""");

	@Autowired
	SkinAnalysisRepository skinAnalysisRepository;

	@Autowired
	SkinProfileRepository skinProfileRepository;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	UserService userService;

	@Autowired
	JdbcTemplate jdbc;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private SkinAnalysis save(User user, SkinType base) {
		return skinAnalysisRepository.save(SkinAnalysis.create(user.getId(), "{}", EnumSet.of(base),
				EnumSet.of(SkinConcern.PORES), "skin-v1"));
	}

	@Test
	@DisplayName("최신순 10건만, 남의 이력은 없다")
	void latestTenOwnOnly() {
		User user = newUser();
		User other = newUser();
		for (int i = 0; i < 11; i++) {
			save(user, SkinType.DRY);
		}
		SkinAnalysis last = save(user, SkinType.OILY);
		save(other, SkinType.COMBINATION);

		List<SkinAnalysis> found = skinAnalysisRepository.findTop10ByUserIdOrderByCreatedAtDescIdDesc(user.getId());

		assertThat(found).hasSize(10).allMatch(a -> a.getUserId().equals(user.getId()));
		assertThat(found.get(0).getId()).isEqualTo(last.getId());
		assertThat(found.get(0).getResultTypes()).containsExactly(SkinType.OILY);
		assertThat(found.get(0).getConcerns()).containsExactly(SkinConcern.PORES);
	}

	@Test
	@DisplayName("피부 정보에 고민을 저장하고 읽는다(처음엔 빈 집합)")
	void profileConcerns() {
		User user = newUser();
		SkinProfile profile = skinProfileRepository.save(SkinProfile.create(user.getId(), Set.of(SkinType.DRY)));
		assertThat(skinProfileRepository.findById(user.getId()).orElseThrow().getConcerns()).isEmpty();

		profile.changeConcerns(Set.of(SkinConcern.WRINKLES, SkinConcern.DRYNESS));
		skinProfileRepository.save(profile);

		assertThat(skinProfileRepository.findById(user.getId()).orElseThrow().getConcerns())
				.containsExactly(SkinConcern.DRYNESS, SkinConcern.WRINKLES);
	}

	@Test
	@DisplayName("V10 정리: DRY와 OILY가 함께 있으면 COMBINATION 하나가 되고 태그는 남는다")
	void migration_mergesDryOily() {
		User both = newUser();
		User withCombination = newUser();
		User single = newUser();
		jdbc.update("INSERT INTO skin_profiles (user_id, skin_types, concerns, updated_at) VALUES (?, ?, '[]', NOW(6))",
				both.getId(), "[\"SENSITIVE\",\"DRY\",\"OILY\"]");
		jdbc.update("INSERT INTO skin_profiles (user_id, skin_types, concerns, updated_at) VALUES (?, ?, '[]', NOW(6))",
				withCombination.getId(), "[\"DRY\",\"COMBINATION\"]");
		jdbc.update("INSERT INTO skin_profiles (user_id, skin_types, concerns, updated_at) VALUES (?, ?, '[]', NOW(6))",
				single.getId(), "[\"ACNE_PRONE\",\"OILY\"]");

		for (User user : List.of(both, withCombination, single)) {
			MERGE_SQL.forEach(sql -> jdbc.update(sql, user.getId()));
		}

		assertThat(skinProfileRepository.findById(both.getId()).orElseThrow().getSkinTypes())
				.containsExactly(SkinType.SENSITIVE, SkinType.COMBINATION);
		assertThat(skinProfileRepository.findById(withCombination.getId()).orElseThrow().getSkinTypes())
				.containsExactly(SkinType.COMBINATION);
		assertThat(skinProfileRepository.findById(single.getId()).orElseThrow().getSkinTypes())
				.containsExactly(SkinType.ACNE_PRONE, SkinType.OILY);
	}

	@Test
	@DisplayName("탈퇴하면 분석 이력도 지워진다")
	void withdraw_deletesAnalyses() {
		User user = newUser();
		save(user, SkinType.DRY);

		userService.withdraw(user.getId());

		assertThat(skinAnalysisRepository.findTop10ByUserIdOrderByCreatedAtDescIdDesc(user.getId())).isEmpty();
	}
}

package com.mycosmetic.service.skin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.shelf.UpdateShelfRequest;
import com.mycosmetic.dto.skin.SkinAnalysisHistoryResponse;
import com.mycosmetic.dto.skin.SkinAnalysisRequest;
import com.mycosmetic.dto.skin.SkinAnalysisResponse;
import com.mycosmetic.dto.skin.SkinQuestionsResponse;
import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.entity.SkinType;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.SkinAnalysisRepository;
import com.mycosmetic.repository.SkinProfileRepository;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 피부 분석 서비스(퍼스널 탭 설계 1절) */
class SkinAnalysisServiceTest extends IntegrationTestSupport {

	@Autowired
	SkinAnalysisService skinAnalysisService;

	@Autowired
	SkinProfileService skinProfileService;

	@Autowired
	SkinProfileRepository skinProfileRepository;

	@Autowired
	SkinAnalysisRepository skinAnalysisRepository;

	@Autowired
	ShelfService shelfService;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ProductFixtures fixtures;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private Long shelfItem(User user, Verdict verdict, Object... lines) {
		Long id = shelfService.add(user.getId(), fixtures.list(fixtures.product(), 1, lines).getId()).id();
		if (verdict != Verdict.UNRATED) {
			shelfService.update(user.getId(), id, new UpdateShelfRequest(verdict, null, null, null));
		}
		return id;
	}

	private static SkinAnalysisRequest request(SelfType self, SkinConcern... concerns) {
		return new SkinAnalysisRequest(self, null, List.of(concerns));
	}

	@Test
	@DisplayName("분석하면 결과를 돌려주고, 피부 정보(타입·고민)와 이력을 저장한다")
	void analyze_savesProfileAndHistory() {
		User user = newUser();

		SkinAnalysisResponse response = skinAnalysisService.analyze(user.getId(),
				request(SelfType.OILY, SkinConcern.DRYNESS, SkinConcern.ACNE));

		assertThat(response.baseType()).isEqualTo(SkinType.DEHYDRATED_OILY);
		assertThat(response.tags()).containsExactly(SkinType.ACNE_PRONE);
		assertThat(response.concerns()).containsExactly(SkinConcern.DRYNESS, SkinConcern.ACNE);
		assertThat(response.reasons()).isNotEmpty();
		assertThat(response.ruleVersion()).isEqualTo(SkinAnalysisRules.RULE_VERSION);
		var profile = skinProfileRepository.findById(user.getId()).orElseThrow();
		assertThat(profile.getSkinTypes()).containsExactly(SkinType.ACNE_PRONE, SkinType.DEHYDRATED_OILY);
		assertThat(profile.getConcerns()).containsExactly(SkinConcern.DRYNESS, SkinConcern.ACNE);
		assertThat(skinAnalysisRepository.findTop10ByUserIdOrderByCreatedAtDescIdDesc(user.getId()))
				.singleElement().satisfies(a -> {
					assertThat(a.getResultTypes()).containsExactly(SkinType.ACNE_PRONE, SkinType.DEHYDRATED_OILY);
					// MySQL JSON 컬럼은 공백·키 순서를 바꿔 저장한다
					assertThat(a.getAnswers()).contains("selfType").contains("OILY").contains("DRYNESS");
				});
	}

	@Test
	@DisplayName("잘 모르겠어요가 아니면 한낮 답은 저장하지 않는다(판정에 쓰지 않으므로)")
	void analyze_dropsUnusedMiddayFeel() {
		User user = newUser();

		skinAnalysisService.analyze(user.getId(), new SkinAnalysisRequest(SelfType.DRY, MiddayFeel.ALL_OILY, List.of()));

		assertThat(skinAnalysisRepository.findTop10ByUserIdOrderByCreatedAtDescIdDesc(user.getId()))
				.singleElement().satisfies(a -> assertThat(a.getAnswers()).doesNotContain("ALL_OILY"));
	}

	@Test
	@DisplayName("분석해도 퍼스널컬러는 그대로 둔다")
	void analyze_keepsPersonalColor() {
		User user = newUser();
		skinProfileService.put(user.getId(), Set.of(SkinType.DRY), PersonalColor.AUTUMN_WARM);

		skinAnalysisService.analyze(user.getId(), request(SelfType.COMBINATION));

		var profile = skinProfileRepository.findById(user.getId()).orElseThrow();
		assertThat(profile.getPersonalColor()).isEqualTo(PersonalColor.AUTUMN_WARM);
		assertThat(profile.getSkinTypes()).containsExactly(SkinType.COMBINATION);
	}

	@Test
	@DisplayName("안맞아요 2개에 공통인 성분을 후보로 주고, 안맞아요가 많으면 민감성을 제안한다")
	void analyze_candidatesAndSuggestion() {
		User user = newUser();
		String x = "자극후보" + ProductFixtures.unique();
		shelfItem(user, Verdict.BAD, "정제수", x);
		shelfItem(user, Verdict.BAD, x, "글리세린");
		shelfItem(user, Verdict.GOOD, "판테놀");

		SkinAnalysisResponse response = skinAnalysisService.analyze(user.getId(), request(SelfType.DRY));

		assertThat(response.candidateIngredients()).containsExactly(x);
		assertThat(response.suggestSensitive()).isTrue();
		assertThat(response.tags()).isEmpty();
	}

	@Test
	@DisplayName("잘못된 답변은 INVALID_SKIN_ANSWERS이고 아무것도 저장하지 않는다")
	void invalidAnswers() {
		User user = newUser();

		assertThatThrownBy(() -> skinAnalysisService.analyze(user.getId(),
				new SkinAnalysisRequest(SelfType.UNSURE, null, List.of())))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_SKIN_ANSWERS));
		assertThat(skinProfileRepository.findById(user.getId())).isEmpty();
		assertThat(skinAnalysisRepository.findTop10ByUserIdOrderByCreatedAtDescIdDesc(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("이력: 최신순, 바로 전 결과와 달라진 점을 문장으로 준다")
	void history_changes() {
		User user = newUser();
		skinAnalysisService.analyze(user.getId(), request(SelfType.COMBINATION, SkinConcern.ACNE, SkinConcern.PORES));
		skinAnalysisService.analyze(user.getId(), request(SelfType.OILY, SkinConcern.DRYNESS, SkinConcern.SENSITIVITY));

		SkinAnalysisHistoryResponse history = skinAnalysisService.history(user.getId());

		assertThat(history.items()).hasSize(2);
		assertThat(history.items().get(0).baseType()).isEqualTo(SkinType.DEHYDRATED_OILY);
		assertThat(history.changes()).contains("피부 타입이 복합성에서 수부지로 바뀌었어요.", "민감성이 새로 붙었어요.",
				"트러블성이 빠졌어요.", "고민에 건조함이 더해졌어요.", "고민에서 모공이 빠졌어요.");
	}

	@Test
	@DisplayName("타입 변경 문장의 조사: 받침이 있으면 '으로'(건성으로), 없으면 '로'(수부지로)")
	void history_particle() {
		User user = newUser();
		skinAnalysisService.analyze(user.getId(), request(SelfType.OILY));
		skinAnalysisService.analyze(user.getId(), request(SelfType.DRY));

		assertThat(skinAnalysisService.history(user.getId()).changes())
				.containsExactly("피부 타입이 지성에서 건성으로 바뀌었어요.");
		assertThat(SkinAnalysisService.withRo("복합성")).isEqualTo("복합성으로");
		assertThat(SkinAnalysisService.withRo("수부지")).isEqualTo("수부지로");
		assertThat(SkinAnalysisService.withRo("서울")).isEqualTo("서울로");
	}

	@Test
	@DisplayName("이력이 하나뿐이면 달라진 점이 없다")
	void history_single() {
		User user = newUser();
		skinAnalysisService.analyze(user.getId(), request(SelfType.DRY));

		assertThat(skinAnalysisService.history(user.getId()).changes()).isEmpty();
		assertThat(skinAnalysisService.history(newUser().getId()).items()).isEmpty();
	}

	@Test
	@DisplayName("문항: Q1·Q1-1·Q2와 첫 안내 문구")
	void questions() {
		SkinQuestionsResponse questions = skinAnalysisService.questions();

		assertThat(questions.ruleVersion()).isEqualTo(SkinAnalysisRules.RULE_VERSION);
		assertThat(questions.intro()).contains("15~30분");
		assertThat(questions.questions()).extracting(SkinQuestionsResponse.Question::id)
				.containsExactly("selfType", "middayFeel", "concerns");
		assertThat(questions.questions().get(0).options()).extracting(SkinQuestionsResponse.Option::code)
				.containsExactly("DRY", "OILY", "COMBINATION", "UNSURE");
		assertThat(questions.questions().get(2).maxSelect()).isEqualTo(3);
		assertThat(questions.questions().get(2).options()).hasSize(7);
	}
}

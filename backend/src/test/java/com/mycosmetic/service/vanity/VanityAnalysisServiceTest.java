package com.mycosmetic.service.vanity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.shelf.UpdateShelfRequest;
import com.mycosmetic.dto.vanity.VanityAnalysisResponse;
import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.MakeupType;
import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.entity.SkinType;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.service.skin.SkinProfileService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 화장대 분석 서비스(퍼스널 탭 설계 3절) */
class VanityAnalysisServiceTest extends IntegrationTestSupport {

	@Autowired
	VanityAnalysisService vanityAnalysisService;

	@Autowired
	ShelfService shelfService;

	@Autowired
	SkinProfileService skinProfileService;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ProductFixtures fixtures;

	@Autowired
	ProductRepository productRepository;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private Long add(User user, String name, Object... lines) {
		Product product = fixtures.product(ProductFixtures.newBarcode(), "브랜드", name + " " + ProductFixtures.unique());
		return shelfService.add(user.getId(), fixtures.list(product, 1, lines.length == 0 ? new Object[] { "정제수" } : lines)
				.getId()).id();
	}

	private void update(User user, Long id, UpdateShelfRequest request) {
		shelfService.update(user.getId(), id, request);
	}

	private static List<Long> morningIds(VanityAnalysisResponse r) {
		return r.routineDraft().morning().stream().map(s -> s.shelfItemId()).toList();
	}

	private static List<Long> eveningIds(VanityAnalysisResponse r) {
		return r.routineDraft().evening().stream().map(s -> s.shelfItemId()).toList();
	}

	@Test
	@DisplayName("이름으로 단계를 추정해 아침·저녁 초안을 만들고, 제품 이름을 함께 준다")
	void draftFromNames() {
		User user = newUser();
		Long cleanser = add(user, "약산성 클렌징폼");
		Long cream = add(user, "세라 크림");
		Long sun = add(user, "톤업 선크림");

		VanityAnalysisResponse response = vanityAnalysisService.analyze(user.getId());

		assertThat(morningIds(response)).containsExactly(cleanser, cream, sun);
		assertThat(eveningIds(response)).containsExactly(cleanser, cream);
		assertThat(response.gaps()).isEmpty();
		assertThat(response.routineDraft().morning()).allMatch(s -> s.guessed());
		assertThat(response.items()).extracting(VanityAnalysisResponse.Item::shelfItemId)
				.containsExactlyInAnyOrder(cleanser, cream, sun);
		assertThat(response.items()).anySatisfy(i -> {
			assertThat(i.shelfItemId()).isEqualTo(cream);
			assertThat(i.name()).startsWith("세라 크림");
			assertThat(i.stepType()).isEqualTo(StepType.CREAM);
			assertThat(i.guessed()).isTrue();
		});
	}

	@Test
	@DisplayName("안맞아요·유통기한 지남·다 쓴 제품은 뺀다")
	void excludesBadExpiredEnded() {
		User user = newUser();
		Long bad = add(user, "나쁜 크림");
		update(user, bad, new UpdateShelfRequest(Verdict.BAD, null, null, null));
		Long expired = add(user, "지난 토너");
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		update(user, expired, new UpdateShelfRequest(null, null, null, null, today.minusMonths(13), 12));
		Long ended = add(user, "다쓴 세럼");
		update(user, ended, new UpdateShelfRequest(null, null, today.minusDays(30), today.minusDays(1)));
		Long ok = add(user, "괜찮은 로션");

		VanityAnalysisResponse response = vanityAnalysisService.analyze(user.getId());

		assertThat(morningIds(response)).containsExactly(ok);
		assertThat(response.items()).extracting(VanityAnalysisResponse.Item::shelfItemId).containsExactly(ok);
	}

	@Test
	@DisplayName("저장한 단계가 이름 추정보다 먼저다")
	void usesSavedTypeOverGuess() {
		User user = newUser();
		Long id = add(user, "수분 크림");
		update(user, id, new UpdateShelfRequest(null, null, null, null, null, null, null, null, "SERUM", null));

		VanityAnalysisResponse response = vanityAnalysisService.analyze(user.getId());

		assertThat(response.routineDraft().morning()).singleElement().satisfies(s -> {
			assertThat(s.stepType()).isEqualTo(StepType.SERUM);
			assertThat(s.guessed()).isFalse();
		});
	}

	@Test
	@DisplayName("이름으로 단계를 알 수 없으면 needsType으로 알려 준다")
	void needsType() {
		User user = newUser();
		Long unknown = add(user, "멀티 밤");

		VanityAnalysisResponse response = vanityAnalysisService.analyze(user.getId());

		assertThat(response.needsType()).containsExactly(unknown);
		assertThat(response.routineDraft().morning()).isEmpty();
	}

	@Test
	@DisplayName("레티놀이 든 제품은 저녁에만 들어가고, 피부 고민이 빈 자리 성분 안내에 반영된다")
	void retinolAndConcerns() {
		User user = newUser();
		Long retinol = add(user, "나이트 세럼", "정제수", "레티놀");
		skinProfileService.applyAnalysis(user.getId(), Set.of(SkinType.DRY), Set.of(SkinConcern.DRYNESS));

		VanityAnalysisResponse response = vanityAnalysisService.analyze(user.getId());

		assertThat(morningIds(response)).isEmpty();
		assertThat(eveningIds(response)).containsExactly(retinol);
		assertThat(response.gaps()).anySatisfy(g -> {
			assertThat(g.role()).isEqualTo("MOISTURIZER");
			assertThat(g.ingredientHints()).contains("세라마이드");
		});
	}

	private Long addMakeup(User user, String name, String hex) {
		Product product = productRepository.save(Product.create(ProductFixtures.newBarcode(), "브랜드",
				name + " " + ProductFixtures.unique(), "3g", Category.MAKEUP, null));
		Long id = shelfService.add(user.getId(), fixtures.list(product, 1, "정제수").getId()).id();
		if (hex != null) {
			update(user, id, new UpdateShelfRequest(null, null, null, null, null, null, "내 호수", hex));
		}
		return id;
	}

	@Test
	@DisplayName("색 있는 립·블러셔로 조합을 만들고, 퍼스널컬러가 있으면 빈 종류의 색 범위를 준다")
	void makeupLooks() {
		User user = newUser();
		Long lip = addMakeup(user, "코랄 립스틱", "#D9A08C");
		Long cheek = addMakeup(user, "피치 블러셔", "#E8B9A0");
		addMakeup(user, "커버 쿠션", "#E8C9A8"); // 베이스는 조합에 쓰지 않는다
		Long noColor = addMakeup(user, "누드 틴트", null);

		VanityAnalysisResponse none = vanityAnalysisService.analyze(user.getId());
		assertThat(none.needsPersonalColor()).isTrue();
		assertThat(none.looks()).anySatisfy(l -> {
			assertThat(l.kind()).isEqualTo("DAILY");
			assertThat(l.shelfItemIds()).containsExactly(lip, cheek);
		});
		assertThat(none.looks()).allMatch(l -> !l.shelfItemIds().contains(noColor));
		assertThat(none.shadeGaps()).isEmpty();

		skinProfileService.changePersonalColorOnly(user.getId(), PersonalColor.SPRING_WARM);
		VanityAnalysisResponse withColor = vanityAnalysisService.analyze(user.getId());
		assertThat(withColor.needsPersonalColor()).isFalse();
		assertThat(withColor.shadeGaps()).singleElement().satisfies(g -> assertThat(g.makeupType()).isEqualTo(MakeupType.EYE));
	}

	@Test
	@DisplayName("남의 화장대 항목은 섞이지 않는다")
	void analysis_onlyOwnItems() {
		User a = newUser();
		User b = newUser();
		add(a, "A의 크림");
		Long mine = add(b, "B의 토너");

		VanityAnalysisResponse response = vanityAnalysisService.analyze(b.getId());

		assertThat(response.items()).extracting(VanityAnalysisResponse.Item::shelfItemId).containsExactly(mine);
	}
}

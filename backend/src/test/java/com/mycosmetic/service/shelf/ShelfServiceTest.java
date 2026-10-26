package com.mycosmetic.service.shelf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.shelf.ExpiryStatus;
import com.mycosmetic.dto.shelf.ShadeSeason;
import com.mycosmetic.dto.shelf.ShadeTone;
import com.mycosmetic.dto.shelf.ShelfItemResponse;
import com.mycosmetic.dto.shelf.ShelfStatsResponse;
import com.mycosmetic.dto.shelf.UpdateShelfRequest;
import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.MakeupType;
import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.ShelfItem;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.service.skin.SkinProfileService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 내 화장대 (설계 3.4, 3.5-5, 4.6) */
class ShelfServiceTest extends IntegrationTestSupport {

	@Autowired
	ShelfService shelfService;

	@Autowired
	ShelfItemRepository shelfItemRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	SkinProfileService skinProfileService;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ProductFixtures fixtures;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private static ErrorCode codeOf(Throwable e) {
		return ((BusinessException) e).errorCode();
	}

	@Test
	@DisplayName("담은 항목은 UNRATED로 시작한다")
	void add_startsUnrated() {
		User user = newUser();
		Product product = fixtures.product();
		IngredientList list = fixtures.list(product, 1, "정제수");

		ShelfItemResponse added = shelfService.add(user.getId(), list.getId());

		assertThat(added.id()).isNotNull();
		assertThat(added.verdict()).isEqualTo(Verdict.UNRATED);
		assertThat(added.ingredientListId()).isEqualTo(list.getId());
		assertThat(added.listIsRepresentative()).isTrue();
		assertThat(added.product().id()).isEqualTo(product.getId());
		assertThat(added.product().representativeListId()).isEqualTo(list.getId());
		assertThat(added.memo()).isNull();
		assertThat(added.updatedAt()).isNotNull();
		assertThat(shelfService.list(user.getId())).extracting(ShelfItemResponse::id).containsExactly(added.id());
	}

	@Test
	@DisplayName("같은 버전을 두 번 담으면 SHELF_ITEM_DUPLICATE")
	void add_twice_duplicate() {
		User user = newUser();
		IngredientList list = fixtures.list(fixtures.product(), 1, "정제수");
		shelfService.add(user.getId(), list.getId());

		assertThatThrownBy(() -> shelfService.add(user.getId(), list.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting(ShelfServiceTest::codeOf).isEqualTo(ErrorCode.SHELF_ITEM_DUPLICATE);
		assertThat(shelfItemRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())).hasSize(1);
	}

	@Test
	@DisplayName("없는 성분 버전을 담으면 NOT_FOUND")
	void add_missingList_notFound() {
		User user = newUser();

		assertThatThrownBy(() -> shelfService.add(user.getId(), Long.MAX_VALUE))
				.isInstanceOf(BusinessException.class)
				.extracting(ShelfServiceTest::codeOf).isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("PATCH는 보낸 필드만 바꾼다")
	void update_onlySentFields() {
		User user = newUser();
		IngredientList list = fixtures.list(fixtures.product(), 1, "정제수");
		Long id = shelfService.add(user.getId(), list.getId()).id();
		shelfService.update(user.getId(), id,
				new UpdateShelfRequest(Verdict.GOOD, "촉촉해요", LocalDate.of(2026, 10, 1), null));

		ShelfItemResponse updated = shelfService.update(user.getId(), id,
				new UpdateShelfRequest(null, null, null, LocalDate.of(2026, 10, 20)));

		assertThat(updated.verdict()).isEqualTo(Verdict.GOOD);
		assertThat(updated.memo()).isEqualTo("촉촉해요");
		assertThat(updated.startedOn()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(updated.endedOn()).isEqualTo(LocalDate.of(2026, 10, 20));

		ShelfItem saved = shelfItemRepository.findById(id).orElseThrow();
		assertThat(saved.getVerdict()).isEqualTo(Verdict.GOOD);
		assertThat(saved.getMemo()).isEqualTo("촉촉해요");
		assertThat(saved.getEndedOn()).isEqualTo(LocalDate.of(2026, 10, 20));
	}

	@Test
	@DisplayName("memo를 빈 문자열로 보내면 메모를 지운다")
	void update_emptyMemo_clears() {
		User user = newUser();
		IngredientList list = fixtures.list(fixtures.product(), 1, "정제수");
		Long id = shelfService.add(user.getId(), list.getId()).id();
		shelfService.update(user.getId(), id, new UpdateShelfRequest(null, "메모", null, null));

		ShelfItemResponse cleared = shelfService.update(user.getId(), id, new UpdateShelfRequest(null, "", null, null));

		assertThat(cleared.memo()).isNull();
		assertThat(shelfItemRepository.findById(id).orElseThrow().getMemo()).isNull();
	}

	@Test
	@DisplayName("평가 '보통'(NEUTRAL)은 더 이상 고를 수 없어 INVALID_REQUEST, 평가는 그대로")
	void update_neutral_invalid() {
		User user = newUser();
		Long id = shelfService.add(user.getId(), fixtures.list(fixtures.product(), 1, "정제수").getId()).id();
		shelfService.update(user.getId(), id, new UpdateShelfRequest(Verdict.GOOD, null, null, null));

		assertThatThrownBy(() -> shelfService.update(user.getId(), id,
				new UpdateShelfRequest(Verdict.NEUTRAL, null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(ShelfServiceTest::codeOf).isEqualTo(ErrorCode.INVALID_REQUEST);
		assertThat(shelfItemRepository.findById(id).orElseThrow().getVerdict()).isEqualTo(Verdict.GOOD);
	}

	@Test
	@DisplayName("사용 종료일이 시작일보다 앞이면 INVALID_REQUEST")
	void update_endBeforeStart_invalid() {
		User user = newUser();
		IngredientList list = fixtures.list(fixtures.product(), 1, "정제수");
		Long id = shelfService.add(user.getId(), list.getId()).id();
		shelfService.update(user.getId(), id, new UpdateShelfRequest(null, null, LocalDate.of(2026, 10, 10), null));

		assertThatThrownBy(() -> shelfService.update(user.getId(), id,
				new UpdateShelfRequest(null, null, null, LocalDate.of(2026, 10, 9))))
				.isInstanceOf(BusinessException.class)
				.extracting(ShelfServiceTest::codeOf).isEqualTo(ErrorCode.INVALID_REQUEST);
		assertThat(shelfItemRepository.findById(id).orElseThrow().getEndedOn()).isNull();
	}

	@Test
	@DisplayName("대표 버전이 바뀌어도 내 항목은 내가 확인한 버전 그대로")
	void representativeChanges_itemKeepsMyList() {
		User user = newUser();
		Product product = fixtures.product();
		IngredientList mine = fixtures.list(product, 1, "정제수");
		shelfService.add(user.getId(), mine.getId());

		IngredientList newer = fixtures.list(product, 2, "정제수", "글리세린");

		List<ShelfItemResponse> shelf = shelfService.list(user.getId());
		assertThat(shelf).hasSize(1);
		assertThat(shelf.get(0).ingredientListId()).isEqualTo(mine.getId());
		assertThat(shelf.get(0).listIsRepresentative()).isFalse();
		assertThat(shelf.get(0).product().representativeListId()).isEqualTo(newer.getId());
	}

	@Test
	@DisplayName("남의 항목은 수정·삭제할 수 없고 NOT_FOUND, 원래 항목은 그대로")
	void otherUsersItem_notFound() {
		User a = newUser();
		User b = newUser();
		IngredientList list = fixtures.list(fixtures.product(), 1, "정제수");
		Long id = shelfService.add(a.getId(), list.getId()).id();

		assertThatThrownBy(() -> shelfService.update(b.getId(), id, new UpdateShelfRequest(Verdict.BAD, null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(ShelfServiceTest::codeOf).isEqualTo(ErrorCode.NOT_FOUND);
		assertThatThrownBy(() -> shelfService.remove(b.getId(), id))
				.isInstanceOf(BusinessException.class)
				.extracting(ShelfServiceTest::codeOf).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(shelfItemRepository.findById(id).orElseThrow().getVerdict()).isEqualTo(Verdict.UNRATED);
	}

	@Test
	@DisplayName("빼면 목록에서 사라진다")
	void remove_deletes() {
		User user = newUser();
		IngredientList list = fixtures.list(fixtures.product(), 1, "정제수");
		Long id = shelfService.add(user.getId(), list.getId()).id();

		shelfService.remove(user.getId(), id);

		assertThat(shelfItemRepository.findById(id)).isEmpty();
		assertThat(shelfService.list(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("성분으로 찾기: 내 버전의 표준명·원문에 검색어(정규화, 공백 무시)가 있는 항목만, 일치한 성분과 함께")
	void searchByIngredient_matchesMyVersion() {
		User user = newUser();
		Ingredient retinol = fixtures.ingredient("레티놀");
		Product withRetinol = fixtures.product();
		IngredientList mine = fixtures.list(withRetinol, 1, "정제수", retinol, "레티닐 팔미테이트");
		Long hit = shelfService.add(user.getId(), mine.getId()).id();
		// 대표 버전에만 들어간 성분은 내 버전이 아니므로 찾지 않는다
		fixtures.list(withRetinol, 2, "정제수", "나이아신아마이드");
		IngredientList other = fixtures.list(fixtures.product(), 1, "정제수", "Glycolic Acid");
		Long acid = shelfService.add(user.getId(), other.getId()).id();

		List<ShelfItemResponse> retinoids = shelfService.searchByIngredient(user.getId(), " 레티 ");
		assertThat(retinoids).extracting(ShelfItemResponse::id).containsExactly(hit);
		assertThat(retinoids.get(0).matchedIngredients())
				.containsExactly(retinol.getStandardName(), "레티닐 팔미테이트");
		assertThat(retinoids.get(0).listIsRepresentative()).isFalse();

		assertThat(shelfService.searchByIngredient(user.getId(), "레티닐팔미")).extracting(ShelfItemResponse::id)
				.containsExactly(hit);
		assertThat(shelfService.searchByIngredient(user.getId(), "GLYCOLIC acid"))
				.extracting(ShelfItemResponse::id).containsExactly(acid);
		assertThat(shelfService.searchByIngredient(user.getId(), "나이아신아마이드")).isEmpty();
		assertThat(shelfService.searchByIngredient(user.getId(), "정제수")).hasSize(2);
		assertThat(shelfService.list(user.getId())).allSatisfy(i -> assertThat(i.matchedIngredients()).isNull());
	}

	@Test
	@DisplayName("성분으로 찾기: 남의 항목은 나오지 않고, 화장대가 비면 빈 목록")
	void searchByIngredient_ownOnly() {
		User a = newUser();
		User b = newUser();
		shelfService.add(a.getId(), fixtures.list(fixtures.product(), 1, "정제수").getId());

		assertThat(shelfService.searchByIngredient(b.getId(), "정제수")).isEmpty();
	}

	@Test
	@DisplayName("성분으로 찾기: 검색어가 비었거나 50자를 넘으면 INVALID_REQUEST")
	void searchByIngredient_invalidQuery() {
		User user = newUser();

		for (String q : new String[] { null, "", "   ", "()", "가".repeat(51) }) {
			assertThatThrownBy(() -> shelfService.searchByIngredient(user.getId(), q))
					.isInstanceOf(BusinessException.class)
					.extracting(ShelfServiceTest::codeOf).isEqualTo(ErrorCode.INVALID_REQUEST);
		}
		assertThat(shelfService.searchByIngredient(user.getId(), "가".repeat(50))).isEmpty();
	}

	private static LocalDate seoulToday() {
		return LocalDate.now(ZoneId.of("Asia/Seoul"));
	}

	private Long addItem(User user, Product product) {
		return shelfService.add(user.getId(), fixtures.list(product, 1, "정제수").getId()).id();
	}

	private Long addItem(User user) {
		return addItem(user, fixtures.product());
	}

	private static UpdateShelfRequest opening(LocalDate openedOn, Integer paoMonths) {
		return new UpdateShelfRequest(null, null, null, null, openedOn, paoMonths);
	}

	@Test
	@DisplayName("개봉일·사용기한을 저장하면 유통기한과 상태를 계산하고, null은 무시한다")
	void update_opening_computesExpiry() {
		User user = newUser();
		Long id = addItem(user);
		LocalDate opened = seoulToday().minusMonths(12).plusDays(10);

		ShelfItemResponse updated = shelfService.update(user.getId(), id, opening(opened, 12));

		assertThat(updated.openedOn()).isEqualTo(opened);
		assertThat(updated.paoMonths()).isEqualTo(12);
		assertThat(updated.expiresOn()).isEqualTo(opened.plusMonths(12));
		assertThat(updated.expiryStatus()).isEqualTo(ExpiryStatus.EXPIRING);
		ShelfItem saved = shelfItemRepository.findById(id).orElseThrow();
		assertThat(saved.getOpenedOn()).isEqualTo(opened);
		assertThat(saved.getPaoMonths()).isEqualTo(12);

		ShelfItemResponse kept = shelfService.update(user.getId(), id,
				new UpdateShelfRequest(Verdict.GOOD, null, null, null));
		assertThat(kept.openedOn()).isEqualTo(opened);
		assertThat(kept.paoMonths()).isEqualTo(12);

		ShelfItemResponse longer = shelfService.update(user.getId(), id, opening(null, 24));
		assertThat(longer.openedOn()).isEqualTo(opened);
		assertThat(longer.expiryStatus()).isEqualTo(ExpiryStatus.OK);
	}

	@Test
	@DisplayName("값이 없으면 UNKNOWN이고 사용 일수는 시작일이 있을 때만 계산한다")
	void response_unknownAndUsageDays() {
		User user = newUser();
		Long id = addItem(user);

		ShelfItemResponse fresh = shelfService.list(user.getId()).get(0);
		assertThat(fresh.expiryStatus()).isEqualTo(ExpiryStatus.UNKNOWN);
		assertThat(fresh.expiresOn()).isNull();
		assertThat(fresh.usageDays()).isNull();

		ShelfItemResponse started = shelfService.update(user.getId(), id,
				new UpdateShelfRequest(null, null, seoulToday().minusDays(40), null));
		assertThat(started.usageDays()).isEqualTo(40L);
		ShelfItemResponse ended = shelfService.update(user.getId(), id,
				new UpdateShelfRequest(null, null, null, seoulToday().minusDays(10)));
		assertThat(ended.usageDays()).isEqualTo(30L);
	}

	@Test
	@DisplayName("개봉일이 오늘(서울)보다 뒤면 INVALID_REQUEST, 오늘은 된다")
	void update_futureOpenedOn_invalid() {
		User user = newUser();
		Long id = addItem(user);

		assertThatThrownBy(() -> shelfService.update(user.getId(), id, opening(seoulToday().plusDays(2), 6)))
				.isInstanceOf(BusinessException.class)
				.extracting(ShelfServiceTest::codeOf).isEqualTo(ErrorCode.INVALID_REQUEST);
		assertThat(shelfItemRepository.findById(id).orElseThrow().getOpenedOn()).isNull();

		assertThat(shelfService.update(user.getId(), id, opening(seoulToday().minusDays(1), 6)).expiryStatus())
				.isEqualTo(ExpiryStatus.OK);
	}

	@Test
	@DisplayName("통계: 개수·분류·평가별 수, 쓰는 중인 항목의 임박·지남 목록, 다 쓴 항목의 평균 사용 일수")
	void stats() {
		User user = newUser();
		LocalDate today = seoulToday();
		Long expiring = addItem(user);
		shelfService.update(user.getId(), expiring, opening(today.minusMonths(6).plusDays(5), 6));
		Long expired = addItem(user);
		shelfService.update(user.getId(), expired, opening(today.minusMonths(13), 12));
		Long finishedExpired = addItem(user);
		shelfService.update(user.getId(), finishedExpired, new UpdateShelfRequest(Verdict.GOOD, null,
				today.minusDays(100), today.minusDays(90), today.minusMonths(13), 12));
		Long finished = addItem(user, productRepository.save(Product.create(ProductFixtures.newBarcode(), "브랜드",
				"립 " + ProductFixtures.unique(), null, Category.MAKEUP, null)));
		shelfService.update(user.getId(), finished,
				new UpdateShelfRequest(Verdict.BAD, null, today.minusDays(30), today.minusDays(25)));
		Long finishedNoStart = addItem(user);
		shelfService.update(user.getId(), finishedNoStart, new UpdateShelfRequest(null, null, null, today));
		shelfService.add(newUser().getId(), fixtures.list(fixtures.product(), 1, "정제수").getId());

		ShelfStatsResponse stats = shelfService.stats(user.getId());

		assertThat(stats.total()).isEqualTo(5);
		assertThat(stats.inUse()).isEqualTo(2);
		assertThat(stats.finished()).isEqualTo(3);
		assertThat(stats.byCategory()).containsEntry(Category.SKINCARE, 4L).containsEntry(Category.MAKEUP, 1L);
		assertThat(stats.byVerdict()).containsEntry(Verdict.GOOD, 1L).containsEntry(Verdict.BAD, 1L)
				.containsEntry(Verdict.UNRATED, 3L).doesNotContainKey(Verdict.NEUTRAL);
		assertThat(stats.expiring()).extracting(ShelfItemResponse::id).containsExactly(expiring);
		assertThat(stats.expired()).extracting(ShelfItemResponse::id).containsExactly(expired);
		assertThat(stats.avgUsageDaysFinished()).isEqualTo(7.5);
	}

	@Test
	@DisplayName("통계: 화장대가 비면 0과 빈 목록, 평균은 null")
	void stats_empty() {
		ShelfStatsResponse stats = shelfService.stats(newUser().getId());

		assertThat(stats.total()).isZero();
		assertThat(stats.byCategory()).containsEntry(Category.SKINCARE, 0L).containsEntry(Category.MAKEUP, 0L);
		// 평가 '보통'은 없앴다: 맞음·안 맞음·평가 전
		assertThat(stats.byVerdict()).hasSize(3);
		assertThat(stats.expiring()).isEmpty();
		assertThat(stats.expired()).isEmpty();
		assertThat(stats.avgUsageDaysFinished()).isNull();
	}
	// ---- 색상과 내 톤(no-ai-features 설계 E) ----

	private static UpdateShelfRequest shade(String name, String hex) {
		return new UpdateShelfRequest(null, null, null, null, null, null, name, hex);
	}

	@Test
	@DisplayName("호수 이름·색을 저장하면 색은 대문자로 바꾸고 웜/쿨·계절을 붙인다. null은 무시하고 빈 문자열은 지운다")
	void update_shade_savesAndClears() {
		User user = newUser();
		Long id = addItem(user, fixtures.makeupProduct());

		ShelfItemResponse saved = shelfService.update(user.getId(), id, shade("  코랄 21호 ", "#ff7f50"));

		assertThat(saved.shadeName()).isEqualTo("코랄 21호");
		assertThat(saved.shadeHex()).isEqualTo("#FF7F50");
		assertThat(saved.shadeTone()).isEqualTo(ShadeTone.WARM);
		assertThat(saved.shadeSeason()).isEqualTo(ShadeSeason.SPRING);
		assertThat(saved.shadeMatchesMe()).isNull();
		ShelfItem entity = shelfItemRepository.findById(id).orElseThrow();
		assertThat(entity.getShadeHex()).isEqualTo("#FF7F50");

		ShelfItemResponse kept = shelfService.update(user.getId(), id, new UpdateShelfRequest(Verdict.GOOD, null, null, null));
		assertThat(kept.shadeName()).isEqualTo("코랄 21호");
		assertThat(kept.shadeHex()).isEqualTo("#FF7F50");

		ShelfItemResponse cleared = shelfService.update(user.getId(), id, shade(" ", ""));
		assertThat(cleared.shadeName()).isNull();
		assertThat(cleared.shadeHex()).isNull();
		assertThat(cleared.shadeTone()).isNull();
		assertThat(cleared.shadeSeason()).isNull();
	}

	@Test
	@DisplayName("색조가 아닌 항목에 호수를 넣으면 INVALID_REQUEST, 지우는 것은 된다")
	void update_shade_nonMakeup_rejected() {
		User user = newUser();
		Long id = addItem(user);

		assertThatThrownBy(() -> shelfService.update(user.getId(), id, shade(null, "#FF0000")))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REQUEST));
		assertThatThrownBy(() -> shelfService.update(user.getId(), id, shade("21호", null)))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REQUEST));
		assertThat(shelfService.update(user.getId(), id, shade("", "")).shadeHex()).isNull();
		assertThat(shelfItemRepository.findById(id).orElseThrow().getShadeName()).isNull();
	}

	@Test
	@DisplayName("요청 검증을 거치지 않은 잘못된 색 형식도 INVALID_REQUEST")
	void update_shade_invalidHex_rejected() {
		User user = newUser();
		Long id = addItem(user, fixtures.makeupProduct());

		assertThatThrownBy(() -> shelfService.update(user.getId(), id, shade(null, "#12345")))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REQUEST));
		assertThat(shelfItemRepository.findById(id).orElseThrow().getShadeHex()).isNull();
	}

	@Test
	@DisplayName("퍼스널컬러를 고르면 목록의 shadeMatchesMe가 웜/쿨 일치 여부가 된다")
	void list_shadeMatchesMe() {
		User user = newUser();
		Long warm = addItem(user, fixtures.makeupProduct());
		Long cool = addItem(user, fixtures.makeupProduct());
		Long none = addItem(user, fixtures.makeupProduct());
		shelfService.update(user.getId(), warm, shade("코랄", "#FF7F50"));
		shelfService.update(user.getId(), cool, shade("핑크", "#FFB6C1"));

		assertThat(shelfService.list(user.getId())).allSatisfy(i -> assertThat(i.shadeMatchesMe()).isNull());

		skinProfileService.put(user.getId(), java.util.Set.of(), PersonalColor.SUMMER_COOL);
		List<ShelfItemResponse> items = shelfService.list(user.getId());
		assertThat(items).filteredOn(i -> i.id().equals(warm)).singleElement()
				.satisfies(i -> assertThat(i.shadeMatchesMe()).isFalse());
		assertThat(items).filteredOn(i -> i.id().equals(cool)).singleElement()
				.satisfies(i -> assertThat(i.shadeMatchesMe()).isTrue());
		assertThat(items).filteredOn(i -> i.id().equals(none)).singleElement()
				.satisfies(i -> assertThat(i.shadeMatchesMe()).isNull());
	}

	@Test
	@DisplayName("내 톤에 맞는 색조: 퍼스널컬러가 없으면 PERSONAL_COLOR_REQUIRED")
	void toneMatch_noPersonalColor_rejected() {
		User user = newUser();
		addItem(user, fixtures.makeupProduct());

		assertThatThrownBy(() -> shelfService.toneMatch(user.getId()))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PERSONAL_COLOR_REQUIRED));

		skinProfileService.put(user.getId(), java.util.Set.of());
		assertThatThrownBy(() -> shelfService.toneMatch(user.getId()))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PERSONAL_COLOR_REQUIRED));
	}

	@Test
	@DisplayName("내 톤에 맞는 색조: MAKEUP 항목 중 웜/쿨이 같은 것만(중립·색 없음·스킨케어 제외)")
	void toneMatch_filters() {
		User user = newUser();
		Long coral = addItem(user, fixtures.makeupProduct());
		Long brown = addItem(user, fixtures.makeupProduct());
		Long pink = addItem(user, fixtures.makeupProduct());
		Long grey = addItem(user, fixtures.makeupProduct());
		addItem(user, fixtures.makeupProduct());
		addItem(user);
		shelfService.update(user.getId(), coral, shade("코랄", "#FF7F50"));
		shelfService.update(user.getId(), brown, shade("브라운", "#8B4513"));
		shelfService.update(user.getId(), pink, shade("핑크", "#FFB6C1"));
		shelfService.update(user.getId(), grey, shade("그레이", "#808080"));
		skinProfileService.put(user.getId(), java.util.Set.of(), PersonalColor.AUTUMN_WARM);

		List<ShelfItemResponse> warm = shelfService.toneMatch(user.getId());

		assertThat(warm).extracting(ShelfItemResponse::id).containsExactlyInAnyOrder(coral, brown);
		assertThat(warm).allSatisfy(i -> assertThat(i.shadeMatchesMe()).isTrue());

		skinProfileService.put(user.getId(), java.util.Set.of(), PersonalColor.WINTER_COOL);
		assertThat(shelfService.toneMatch(user.getId())).extracting(ShelfItemResponse::id).containsExactly(pink);

		User other = newUser();
		skinProfileService.put(other.getId(), java.util.Set.of(), PersonalColor.AUTUMN_WARM);
		assertThat(shelfService.toneMatch(other.getId())).isEmpty();
	}

	@Test
	@DisplayName("성분으로 찾기 결과에도 호수 정보가 그대로 붙는다")
	void searchByIngredient_keepsShade() {
		User user = newUser();
		Long id = shelfService.add(user.getId(), fixtures.list(fixtures.makeupProduct(), 1, "정제수").getId()).id();
		shelfService.update(user.getId(), id, shade("코랄", "#FF7F50"));

		assertThat(shelfService.searchByIngredient(user.getId(), "정제수")).singleElement()
				.satisfies(i -> {
					assertThat(i.shadeHex()).isEqualTo("#FF7F50");
					assertThat(i.matchedIngredients()).isNotEmpty();
				});
	}

	// ---- 제품 종류(퍼스널 탭 설계 3절) ----

	private static UpdateShelfRequest types(String stepType, String makeupType) {
		return new UpdateShelfRequest(null, null, null, null, null, null, null, null, stepType, makeupType);
	}

	@Test
	@DisplayName("스킨케어 항목에 단계를 저장하고, null은 무시하고 빈 문자열은 지운다")
	void update_stepType_savesAndClears() {
		User user = newUser();
		Long id = addItem(user);

		assertThat(shelfService.update(user.getId(), id, types("SERUM", null)).stepType()).isEqualTo(StepType.SERUM);
		assertThat(shelfService.update(user.getId(), id, new UpdateShelfRequest(Verdict.GOOD, null, null, null))
				.stepType()).isEqualTo(StepType.SERUM);
		assertThat(shelfService.update(user.getId(), id, types("", null)).stepType()).isNull();
		assertThat(shelfItemRepository.findById(id).orElseThrow().getStepType()).isNull();
	}

	@Test
	@DisplayName("색조 항목에 색조 종류를 저장한다")
	void update_makeupType() {
		User user = newUser();
		Long id = addItem(user, fixtures.makeupProduct());

		ShelfItemResponse saved = shelfService.update(user.getId(), id, types(null, "LIP"));

		assertThat(saved.makeupType()).isEqualTo(MakeupType.LIP);
		assertThat(shelfItemRepository.findById(id).orElseThrow().getMakeupType()).isEqualTo(MakeupType.LIP);
	}

	@Test
	@DisplayName("분류와 맞지 않거나 모르는 값이면 INVALID_ITEM_TYPE이고 저장하지 않는다(지우기는 된다)")
	void update_types_rejected() {
		User user = newUser();
		Long skincare = addItem(user);
		Long makeup = addItem(user, fixtures.makeupProduct());

		for (Runnable call : List.<Runnable>of(
				() -> shelfService.update(user.getId(), skincare, types(null, "LIP")),
				() -> shelfService.update(user.getId(), makeup, types("TONER", null)),
				() -> shelfService.update(user.getId(), skincare, types("FOO", null)),
				() -> shelfService.update(user.getId(), makeup, types(null, "lip")))) {
			assertThatThrownBy(call::run).satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_ITEM_TYPE));
		}
		assertThat(shelfItemRepository.findById(skincare).orElseThrow().getMakeupType()).isNull();
		assertThat(shelfItemRepository.findById(makeup).orElseThrow().getStepType()).isNull();
		assertThat(shelfService.update(user.getId(), skincare, types(null, "")).makeupType()).isNull();
	}

	@Test
	@DisplayName("종류 값의 앞뒤 공백은 빼고, 공백만 있으면 지운다(다른 필드와 같다)")
	void update_types_blankAndStrip() {
		User user = newUser();
		Long id = addItem(user);

		assertThat(shelfService.update(user.getId(), id, types(" SERUM ", null)).stepType()).isEqualTo(StepType.SERUM);
		assertThat(shelfService.update(user.getId(), id, types("  ", null)).stepType()).isNull();
	}
}

package com.mycosmetic.service.ingredient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.mycosmetic.client.mfds.MfdsClientException;
import com.mycosmetic.client.mfds.MfdsIngredientClient;
import com.mycosmetic.client.mfds.MfdsIngredientRow;
import com.mycosmetic.client.mfds.MfdsPage;
import com.mycosmetic.config.MfdsProperties;
import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.repository.IngredientAliasRepository;
import com.mycosmetic.repository.IngredientRepository;
import com.mycosmetic.service.ingredient.IngredientDictionaryImportService.ImportReport;
import com.mycosmetic.support.IntegrationTestSupport;

/**
 * 가짜 식약처 client로 사전 가져오기를 확인한다. 가져오기는 커밋되므로, 이름에 고유값을 붙이고 끝나면 지운다.
 */
class IngredientDictionaryImportServiceTest extends IntegrationTestSupport {

	@Autowired
	IngredientRepository ingredientRepository;

	@Autowired
	IngredientAliasRepository aliasRepository;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	IngredientListRematchService rematchService;

	private final String tag = UUID.randomUUID().toString().substring(0, 8);
	private final List<String> names = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		for (String name : names) {
			ingredientRepository.findByStandardName(name).ifPresent(i -> {
				jdbcTemplate.update("DELETE FROM ingredient_aliases WHERE ingredient_id = ?", i.getId());
				jdbcTemplate.update("DELETE FROM ingredients WHERE id = ?", i.getId());
			});
		}
	}

	@Test
	@DisplayName("표준명과 이명을 모두 ingredient_aliases에 정규화해서 넣는다")
	void insertsStandardNameAndSynonymsAsAliases() {
		String name = name("나이아신아마이드");
		FakeClient client = new FakeClient(List.of(
				List.of(row(name, "Niacinamide", "98-92-0", "니코틴아마이드" + tag, " Nicotin–Amide" + tag))));

		ImportReport report = service(client).importAll();

		Ingredient ingredient = ingredientRepository.findByStandardName(name).orElseThrow();
		assertThat(ingredient.getEnglishName()).isEqualTo("Niacinamide");
		assertThat(ingredient.getCasNo()).isEqualTo("98-92-0");
		assertThat(normalizedAliases(ingredient)).containsExactlyInAnyOrder(
				IngredientNameNormalizer.normalize(name).value(),
				"니코틴아마이드" + tag,
				"nicotin-amide" + tag);
		assertThat(report).isEqualTo(new ImportReport(1, 3));
	}

	@Test
	@DisplayName("두 번 실행해도 중복이 생기지 않는다")
	void runningTwiceDoesNotDuplicate() {
		String a = name("글리세린");
		String b = name("정제수");
		FakeClient client = new FakeClient(List.of(
				List.of(row(a, "Glycerin", "56-81-5", "글리세롤" + tag)),
				List.of(row(b, "Water", "7732-18-5", "물" + tag, "아쿠아" + tag))));

		service(client).importAll();
		ImportReport second = service(client).importAll();

		assertThat(second.aliasesAdded()).isZero();
		assertThat(countIngredients()).isEqualTo(2);
		assertThat(normalizedAliases(ingredientRepository.findByStandardName(a).orElseThrow())).hasSize(2);
		assertThat(normalizedAliases(ingredientRepository.findByStandardName(b).orElseThrow())).hasSize(3);
	}

	@Test
	@DisplayName("기존 성분의 영문명·CAS가 바뀌면 갱신한다")
	void updatesChangedDetails() {
		String name = name("부틸렌글라이콜");
		service(new FakeClient(List.of(List.of(row(name, "Butylene Glycol", "107-88-0"))))).importAll();

		service(new FakeClient(List.of(List.of(row(name, "1,3-Butylene Glycol", "107-88-0; 6290-03-5"))))).importAll();

		Ingredient ingredient = ingredientRepository.findByStandardName(name).orElseThrow();
		assertThat(ingredient.getEnglishName()).isEqualTo("1,3-Butylene Glycol");
		assertThat(ingredient.getCasNo()).isEqualTo("107-88-0; 6290-03-5");
		assertThat(countIngredients()).isEqualTo(1);
	}

	@Test
	@DisplayName("중간 페이지에서 실패하면 그 실행 전체를 되돌리고 기존 사전을 유지한다")
	void failureInMiddlePageKeepsExistingDictionary() {
		String existing = name("정제수");
		service(new FakeClient(List.of(List.of(row(existing, "Water", "7732-18-5"))))).importAll();

		String fresh = name("글리세린");
		FakeClient failing = new FakeClient(List.of(
				List.of(row(existing, "Aqua", "0000-00-0", "아쿠아" + tag)),
				List.of(row(fresh, "Glycerin", "56-81-5")),
				List.of(row(name("스쿠알란"), "Squalane", "111-01-3"))));
		failing.failOnPage = 3;

		assertThatThrownBy(() -> service(failing).importAll()).isInstanceOf(MfdsClientException.class);

		Ingredient kept = ingredientRepository.findByStandardName(existing).orElseThrow();
		assertThat(kept.getEnglishName()).isEqualTo("Water");
		assertThat(normalizedAliases(kept)).hasSize(1);
		assertThat(ingredientRepository.findByStandardName(fresh)).isEmpty();
	}

	@Test
	@DisplayName("같은 응답 안의 같은 표준명은 하나로 합치고 빈 이명은 건너뛴다")
	void mergesDuplicateRows() {
		String name = name("히알루론산");
		FakeClient client = new FakeClient(List.of(List.of(
				row(name, "Hyaluronic Acid", null, "  ", "하이알루로닉애씨드" + tag),
				row(name, "Hyaluronic Acid", null, "하이알루로닉애씨드" + tag, "히알루론산(저분자)" + tag))));

		ImportReport report = service(client).importAll();

		assertThat(report.ingredients()).isEqualTo(1);
		assertThat(normalizedAliases(ingredientRepository.findByStandardName(name).orElseThrow()))
				.containsExactlyInAnyOrder(
						IngredientNameNormalizer.normalize(name).value(),
						"하이알루로닉애씨드" + tag,
						IngredientNameNormalizer.normalize("히알루론산(저분자)" + tag).value());
	}

	@Test
	@DisplayName("DB collation(악센트·전각·대소문자 무시)에서 같은 표준명은 한 성분으로 합친다")
	void mergesNamesEqualUnderCollation() {
		String plain = name("Glycerine");
		String accented = name("GLYCÉRINE");
		String fullWidth = name("Ｇｌｙｃｅｒｉｎｅ");
		FakeClient client = new FakeClient(List.of(List.of(
				row(plain, "Glycerin", "56-81-5"),
				row(accented, null, null, "글리세롤" + tag),
				row(fullWidth, null, null))));

		ImportReport report = service(client).importAll();

		assertThat(report.ingredients()).isEqualTo(1);
		assertThat(countIngredients()).isEqualTo(1);

		// 다음 실행에서 다른 표기로 와도 기존 성분을 갱신한다(새로 넣다가 중복 키로 실패하지 않는다)
		service(new FakeClient(List.of(List.of(row(accented, "Glycerol", "56-81-5"))))).importAll();

		assertThat(countIngredients()).isEqualTo(1);
		assertThat(ingredientRepository.findByStandardName(plain).orElseThrow().getEnglishName()).isEqualTo("Glycerol");
	}

	@Test
	@DisplayName("같은 표준명의 뒤 행에 영문명·CAS가 비어 있으면 앞 행의 값을 지킨다")
	void laterEmptyDetailsDoNotOverwrite() {
		String name = name("세라마이드엔피");
		FakeClient client = new FakeClient(List.of(List.of(
				row(name, "Ceramide NP", "100403-19-8"),
				row(name, null, "  ", "세라마이드3" + tag))));

		service(client).importAll();

		Ingredient ingredient = ingredientRepository.findByStandardName(name).orElseThrow();
		assertThat(ingredient.getEnglishName()).isEqualTo("Ceramide NP");
		assertThat(ingredient.getCasNo()).isEqualTo("100403-19-8");
	}

	private IngredientDictionaryImportService service(MfdsIngredientClient client) {
		MfdsProperties properties = new MfdsProperties("http://unused", "", Duration.ofSeconds(1), 1, Duration.ZERO);
		return new IngredientDictionaryImportService(client, ingredientRepository, aliasRepository,
				transactionManager, properties, rematchService);
	}

	private String name(String base) {
		String name = base + "-" + tag;
		names.add(name);
		return name;
	}

	private static MfdsIngredientRow row(String korean, String english, String cas, String... synonyms) {
		return new MfdsIngredientRow(korean, english, cas, List.of(synonyms));
	}

	private List<String> normalizedAliases(Ingredient ingredient) {
		return jdbcTemplate.queryForList(
				"SELECT normalized_alias FROM ingredient_aliases WHERE ingredient_id = ?", String.class, ingredient.getId());
	}

	private int countIngredients() {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ingredients WHERE standard_name LIKE ?", Integer.class, "%-" + tag);
	}

	/** 페이지 크기 1 기준으로 pages[i]를 i+1페이지로 돌려준다 */
	static class FakeClient implements MfdsIngredientClient {

		final List<List<MfdsIngredientRow>> pages;
		int failOnPage = -1;

		FakeClient(List<List<MfdsIngredientRow>> pages) {
			this.pages = pages;
		}

		@Override
		public MfdsPage fetchPage(int pageNo, int numOfRows) {
			if (pageNo == failOnPage) {
				throw new MfdsClientException("테스트 실패");
			}
			int total = pages.stream().mapToInt(List::size).sum();
			return new MfdsPage(total, pageNo <= pages.size() ? pages.get(pageNo - 1) : List.of());
		}
	}
}

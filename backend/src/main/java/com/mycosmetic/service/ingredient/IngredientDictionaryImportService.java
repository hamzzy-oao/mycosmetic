package com.mycosmetic.service.ingredient;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mycosmetic.client.mfds.MfdsClientException;
import com.mycosmetic.client.mfds.MfdsIngredientClient;
import com.mycosmetic.client.mfds.MfdsIngredientRow;
import com.mycosmetic.client.mfds.MfdsPage;
import com.mycosmetic.config.MfdsProperties;
import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientAlias;
import com.mycosmetic.repository.IngredientAliasRepository;
import com.mycosmetic.repository.IngredientRepository;

/**
 * 식약처 원료성분 사전 가져오기 (설계 4.5). 주 1회 배치, 또는 {@code --import-ingredients}로 한 번 실행.
 *
 * <ul>
 * <li>모든 페이지를 먼저 받은 뒤 한 트랜잭션으로 반영한다. 어느 페이지든 실패하면 아무것도 바꾸지 않아 기존 사전을 그대로 쓴다.
 * 식약처 호출 중에는 DB 트랜잭션을 열어 두지 않는다</li>
 * <li>표준명도 alias 한 줄로 넣는다(설계 3.3). alias는 (성분, 정규화 값)마다 한 번만 넣는다</li>
 * <li>기존 성분은 영문명·CAS가 바뀌었으면 갱신한다. 응답에서 사라진 성분·이명은 지우지 않는다(성분 버전이 참조한다)</li>
 * <li>반영한 뒤 저장된 성분 버전의 "사전에 없음" 줄을 다시 매칭한다({@link IngredientListRematchService})</li>
 * </ul>
 */
@Service
public class IngredientDictionaryImportService {

	private static final Logger log = LoggerFactory.getLogger(IngredientDictionaryImportService.class);

	private static final int STANDARD_NAME_MAX = 200;
	private static final int ENGLISH_NAME_MAX = 300;
	private static final int CAS_NO_MAX = 100;
	private static final int ALIAS_MAX = 300;
	private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

	/**
	 * @param ingredients  이번 응답에서 처리한 성분 수(새로 만든 것 + 기존 것)
	 * @param aliasesAdded 새로 넣은 alias 수
	 */
	public record ImportReport(int ingredients, int aliasesAdded) {
	}

	private final MfdsIngredientClient client;
	private final IngredientRepository ingredientRepository;
	private final IngredientAliasRepository aliasRepository;
	private final TransactionTemplate transactionTemplate;
	private final MfdsProperties properties;
	private final IngredientListRematchService rematchService;

	public IngredientDictionaryImportService(MfdsIngredientClient client, IngredientRepository ingredientRepository,
			IngredientAliasRepository aliasRepository, PlatformTransactionManager transactionManager,
			MfdsProperties properties, IngredientListRematchService rematchService) {
		this.client = client;
		this.ingredientRepository = ingredientRepository;
		this.aliasRepository = aliasRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.properties = properties;
		this.rematchService = rematchService;
	}

	/** 주 1회 배치. 실패하면 기존 사전을 그대로 쓰고 경고 로그만 남긴다 */
	@Scheduled(cron = "0 0 4 * * MON", zone = "Asia/Seoul")
	public void scheduledImport() {
		try {
			importAll();
		} catch (RuntimeException e) {
			log.warn("식약처 성분 사전 가져오기 실패. 기존 사전을 유지한다. 이유={}", e.getMessage());
		}
	}

	/**
	 * 전체를 가져와 반영한다.
	 *
	 * @throws MfdsClientException 식약처 호출 실패(반영 없음)
	 */
	public ImportReport importAll() {
		List<MfdsIngredientRow> rows = fetchAll();
		ImportReport report = transactionTemplate.execute(status -> apply(rows));
		log.info("식약처 성분 사전 가져오기 완료. 받은 행={}, {}", rows.size(), report);
		// 사전은 이미 반영됐으므로 다시 매칭이 실패해도 가져오기는 성공으로 둔다. 다음 가져오기 때 다시 시도한다
		try {
			rematchService.rematchUnmatched();
		} catch (RuntimeException e) {
			log.warn("성분 버전 다시 매칭 실패. 이유={}", e.getMessage());
		}
		return report;
	}

	private List<MfdsIngredientRow> fetchAll() {
		int pageSize = properties.pageSize();
		MfdsPage first = client.fetchPage(1, pageSize);
		List<MfdsIngredientRow> rows = new ArrayList<>(first.rows());
		int pages = (int) Math.ceil(first.totalCount() / (double) pageSize);
		for (int pageNo = 2; pageNo <= pages; pageNo++) {
			pause(properties.pageDelay());
			MfdsPage page = client.fetchPage(pageNo, pageSize);
			if (page.rows().isEmpty()) {
				break;
			}
			rows.addAll(page.rows());
		}
		return rows;
	}

	private ImportReport apply(List<MfdsIngredientRow> rows) {
		Map<String, Merged> merged = merge(rows);

		Map<String, Ingredient> existing = new HashMap<>();
		for (Ingredient ingredient : ingredientRepository.findAll()) {
			existing.put(key(ingredient.getStandardName()), ingredient);
		}
		Set<String> existingAliases = new HashSet<>();
		for (IngredientAlias alias : aliasRepository.findAll()) {
			existingAliases.add(aliasKey(alias.getIngredient().getId(), alias.getNormalizedAlias()));
		}

		int aliasesAdded = 0;
		for (Merged row : merged.values()) {
			Ingredient ingredient = existing.get(key(row.standardName));
			if (ingredient == null) {
				ingredient = ingredientRepository.save(Ingredient.create(row.standardName, row.englishName, row.casNo));
			} else if (!Objects.equals(ingredient.getEnglishName(), row.englishName)
					|| !Objects.equals(ingredient.getCasNo(), row.casNo)) {
				ingredient.updateDetails(row.englishName, row.casNo);
			}
			for (String alias : row.aliases) {
				String normalized = IngredientNameNormalizer.normalize(alias).value();
				if (normalized.isEmpty() || normalized.length() > ALIAS_MAX
						|| !existingAliases.add(aliasKey(ingredient.getId(), normalized))) {
					continue;
				}
				aliasRepository.save(IngredientAlias.create(ingredient, alias, normalized));
				aliasesAdded++;
			}
		}
		return new ImportReport(merged.size(), aliasesAdded);
	}

	/** 같은 표준명 행을 합친다. 표준명이 없거나 너무 긴 행은 건너뛴다 */
	private static Map<String, Merged> merge(List<MfdsIngredientRow> rows) {
		Map<String, Merged> merged = new LinkedHashMap<>();
		int skipped = 0;
		for (MfdsIngredientRow row : rows) {
			String name = row.koreanName() == null ? "" : row.koreanName().strip();
			if (name.isEmpty() || name.length() > STANDARD_NAME_MAX) {
				skipped++;
				continue;
			}
			Merged m = merged.computeIfAbsent(key(name), k -> new Merged(name));
			// 뒤 행의 값이 비어 있으면 앞 행에서 받은 값을 지킨다
			String englishName = cut(row.englishName(), ENGLISH_NAME_MAX);
			if (englishName != null) {
				m.englishName = englishName;
			}
			String casNo = cut(row.casNo(), CAS_NO_MAX);
			if (casNo != null) {
				m.casNo = casNo;
			}
			for (String synonym : row.synonyms()) {
				String s = synonym == null ? "" : synonym.strip();
				if (!s.isEmpty() && s.length() <= ALIAS_MAX) {
					m.aliases.add(s);
				}
			}
		}
		if (skipped > 0) {
			log.warn("식약처 성분 사전에서 표준명이 없거나 너무 긴 행 {}건을 건너뛰었다", skipped);
		}
		return merged;
	}

	/**
	 * standard_name은 utf8mb4_0900_ai_ci(악센트·대소문자 무시, 전각·반각 같음)라 메모리에서도 비슷한 기준으로 같은 이름을 판정한다.
	 * DB가 같다고 보는 두 이름을 다르게 보면 INSERT가 중복 키로 실패해 가져오기 전체가 되돌려진다.
	 * NFKD로 호환 문자(전각 등)를 풀고 결합 부호(악센트)를 지운 뒤 소문자로 바꾼다.
	 */
	private static String key(String standardName) {
		String decomposed = Normalizer.normalize(standardName.strip(), Normalizer.Form.NFKD);
		String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
		return Normalizer.normalize(withoutMarks, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
	}

	private static String aliasKey(Long ingredientId, String normalized) {
		return ingredientId + "|" + normalized;
	}

	private static String cut(String value, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String s = value.strip();
		return s.length() <= max ? s : s.substring(0, max);
	}

	private static void pause(Duration delay) {
		if (delay == null || delay.isZero() || delay.isNegative()) {
			return;
		}
		try {
			Thread.sleep(delay.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new MfdsClientException("식약처 성분 사전 가져오기가 중단되었다");
		}
	}

	private static final class Merged {
		final String standardName;
		String englishName;
		String casNo;
		final Set<String> aliases = new LinkedHashSet<>();

		Merged(String standardName) {
			this.standardName = standardName;
			this.aliases.add(standardName);
		}
	}
}

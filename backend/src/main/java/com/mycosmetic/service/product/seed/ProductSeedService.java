package com.mycosmetic.service.product.seed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mycosmetic.dto.ingredient.IngredientSection;
import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.service.ingredient.IngredientTextService;
import com.mycosmetic.service.ingredient.MatchResult;
import com.mycosmetic.service.product.seed.ProductSeedTxService.Outcome;
import com.mycosmetic.service.product.seed.ProductSeedTxService.SeedItem;
import com.mycosmetic.service.product.seed.ProductSeedTxService.SeedProduct;

/**
 * 손으로 모은 시드 제품 CSV를 성분 사전과 맞춰 넣는다 (seed-products 스킬 2번).
 *
 * <ul>
 * <li>전성분 글은 {@link IngredientTextService#matchSections}로 나눈다. 구역이 2개 이상(옵션·호수·세트)이면 넣지 않는다</li>
 * <li>EXACT·ALIAS만 성분에 잇는다. FUZZY 후보는 사람이 골라야 하므로 원문만 UNMATCHED로 저장한다
 * (나중에 사전이 늘면 {@code IngredientListRematchService}가 다시 잇는다)</li>
 * <li>제품은 브랜드+제품명(연속 공백은 하나로)으로 찾고, 성분 버전은 fingerprint로 찾아 다시 돌려도 중복이 생기지 않는다</li>
 * <li>매칭은 쓰기 트랜잭션 밖에서 하고, 쓰기는 줄마다 {@link ProductSeedTxService}의 새 트랜잭션으로 한다</li>
 * </ul>
 */
@Service
public class ProductSeedService {

	private static final Logger log = LoggerFactory.getLogger(ProductSeedService.class);

	static final int UNMATCHED_TOP = 50;
	/** ConfirmRequest·FinalItem과 같은 제한 */
	static final int BRAND_MAX = 100;
	static final int NAME_MAX = 200;
	static final int VOLUME_MAX = 50;
	static final int TEXT_MAX = 30000;
	static final int ITEMS_MAX = 200;
	static final int RAW_TEXT_MAX = 500;
	static final Set<String> SOURCE_TYPES = Set.of("PACKAGE", "OFFICIAL_SITE", "OBF");
	static final String NO_KIND = "미분류";

	private final IngredientTextService textService;
	private final ProductSeedTxService txService;

	public ProductSeedService(IngredientTextService textService, ProductSeedTxService txService) {
		this.textService = textService;
		this.txService = txService;
	}

	public SeedReport seed(List<SeedRow> rows, boolean dryRun) {
		List<SeedReport.LineReason> skipped = new ArrayList<>();
		List<SeedReport.LineReason> failed = new ArrayList<>();
		Map<String, Integer> items = new LinkedHashMap<>();
		for (MatchType type : MatchType.values()) {
			items.put(type.name(), 0);
		}
		Map<String, Integer> unmatched = new HashMap<>();
		Map<String, Integer> byKind = new TreeMap<>();
		Map<String, Integer> byCategory = new TreeMap<>();
		Set<String> seenKeys = new HashSet<>();
		int created = 0;
		int createdProducts = 0;
		int alreadyExists = 0;

		for (SeedRow row : rows) {
			String invalid = invalidReason(row);
			if (invalid != null) {
				failed.add(new SeedReport.LineReason(row.line(), invalid));
				continue;
			}
			SeedProduct info = new SeedProduct(collapse(row.brand()), collapse(row.name()),
					row.volume().isBlank() ? null : collapse(row.volume()), Category.valueOf(row.category()));
			if (!seenKeys.add(info.brand().toLowerCase(Locale.ROOT) + "\n" + info.name().toLowerCase(Locale.ROOT))) {
				skipped.add(new SeedReport.LineReason(row.line(), "DUPLICATE_IN_FILE"));
				continue;
			}

			List<IngredientSection> sections = textService.matchSections(row.ingredients());
			if (sections.isEmpty() || sections.get(0).items().isEmpty()) {
				failed.add(new SeedReport.LineReason(row.line(), "NO_INGREDIENTS"));
				continue;
			}
			if (sections.size() > 1) {
				skipped.add(new SeedReport.LineReason(row.line(), "MULTI_SECTION"));
				continue;
			}
			List<MatchResult> matches = sections.get(0).items();
			if (matches.size() > ITEMS_MAX) {
				failed.add(new SeedReport.LineReason(row.line(), "TOO_MANY_INGREDIENTS"));
				continue;
			}
			if (matches.stream().anyMatch(m -> m.rawText().strip().length() > RAW_TEXT_MAX)) {
				failed.add(new SeedReport.LineReason(row.line(), "INGREDIENT_TOO_LONG"));
				continue;
			}
			List<SeedItem> seedItems = matches.stream().map(ProductSeedService::toSeedItem).toList();

			Outcome outcome;
			try {
				outcome = dryRun ? txService.preview(info, seedItems) : txService.insert(info, seedItems);
			} catch (RuntimeException e) {
				log.warn("시드 줄 저장 실패 line={} reason={}", row.line(), e.getClass().getSimpleName());
				failed.add(new SeedReport.LineReason(row.line(), "DB_ERROR"));
				continue;
			}
			if (outcome == Outcome.ARCHIVED) {
				skipped.add(new SeedReport.LineReason(row.line(), "ARCHIVED"));
				continue;
			}
			if (outcome == Outcome.ALREADY_EXISTS) {
				alreadyExists++;
			} else {
				created++;
				if (outcome == Outcome.CREATED_PRODUCT) {
					createdProducts++;
				}
				for (MatchResult m : matches) {
					items.merge(m.type().name(), 1, Integer::sum);
					if (m.type() == MatchType.FUZZY || m.type() == MatchType.UNMATCHED) {
						unmatched.merge(m.rawText().strip(), 1, Integer::sum);
					}
				}
			}
			byKind.merge(row.kind().isBlank() ? NO_KIND : row.kind(), 1, Integer::sum);
			byCategory.merge(info.category().name(), 1, Integer::sum);
		}

		List<SeedReport.NameCount> top = unmatched.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
						.thenComparing(Map.Entry.comparingByKey()))
				.limit(UNMATCHED_TOP)
				.map(e -> new SeedReport.NameCount(e.getKey(), e.getValue()))
				.toList();
		SeedReport report = new SeedReport(dryRun, rows.size(), created, createdProducts, alreadyExists, skipped,
				failed, items, top, byKind, byCategory);
		log.info("시드 넣기 완료 dryRun={} rows={} created={} alreadyExists={} skipped={} failed={}", dryRun,
				rows.size(), created, alreadyExists, skipped.size(), failed.size());
		return report;
	}

	/** 형식 오류 이유. 문제없으면 null */
	static String invalidReason(SeedRow row) {
		if (row.brand().isBlank() || row.name().isBlank() || row.category().isBlank() || row.ingredients().isBlank()
				|| row.sourceType().isBlank()) {
			return "MISSING_FIELD";
		}
		if (row.brand().length() > BRAND_MAX || row.name().length() > NAME_MAX || row.volume().length() > VOLUME_MAX
				|| row.ingredients().length() > TEXT_MAX) {
			return "TOO_LONG";
		}
		if (!row.category().equals(Category.SKINCARE.name()) && !row.category().equals(Category.MAKEUP.name())) {
			return "INVALID_CATEGORY";
		}
		if (!SOURCE_TYPES.contains(row.sourceType())) {
			return "INVALID_SOURCE_TYPE";
		}
		return null;
	}

	private static SeedItem toSeedItem(MatchResult m) {
		boolean linked = (m.type() == MatchType.EXACT || m.type() == MatchType.ALIAS) && m.ingredientId() != null;
		return linked ? new SeedItem(m.ingredientId(), m.rawText().strip(), m.type())
				: new SeedItem(null, m.rawText().strip(), MatchType.UNMATCHED);
	}

	private static String collapse(String value) {
		return value.strip().replaceAll("\\s+", " ");
	}
}

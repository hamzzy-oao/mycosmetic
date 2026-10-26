package com.mycosmetic.service.ingredient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientAlias;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.repository.IngredientAliasRepository;
import com.mycosmetic.service.ingredient.MatchResult.Candidate;

/**
 * 성분 사전 매칭 (설계 4.1 5단계). 이름마다 정규화한 뒤 순서대로 시도한다.
 *
 * <ol>
 * <li>EXACT: 정규화 값과 같은 alias가 있고, 그 alias가 표준명(정규화한 표준명과 같음)</li>
 * <li>ALIAS: 정규화 값과 같은 이명이 한 성분에만 있음</li>
 * <li>FUZZY: ngram FULLTEXT 후보 20개 → 유사도 0.8 이상을 성분 기준으로 중복 제거해 상위 3개.
 * 같은 이명이 여러 성분에 걸린 경우도 그 성분들을 후보로 제시한다. ingredientId는 null(사용자가 고른다)</li>
 * <li>UNMATCHED: 그 외</li>
 * </ol>
 */
@Service
public class IngredientMatcher {

	static final int FULLTEXT_CANDIDATES = 20;
	static final int MAX_CANDIDATES = 3;
	static final double SIMILARITY_THRESHOLD = 0.8;
	/** 1 - 2/10 같은 계산의 부동소수점 오차로 경계값 0.8이 떨어지지 않게 한다 */
	private static final double EPSILON = 1e-9;

	private static final Comparator<Candidate> BY_SIMILARITY = Comparator.comparingDouble(Candidate::similarity)
			.reversed()
			.thenComparing(Candidate::standardName);

	private final IngredientAliasRepository aliasRepository;

	public IngredientMatcher(IngredientAliasRepository aliasRepository) {
		this.aliasRepository = aliasRepository;
	}

	/** rawNames 순서대로 결과를 돌려준다. position은 1부터 */
	@Transactional(readOnly = true)
	public List<MatchResult> match(List<String> rawNames) {
		List<MatchResult> results = new ArrayList<>(rawNames.size());
		for (int i = 0; i < rawNames.size(); i++) {
			results.add(matchOne(i + 1, rawNames.get(i)));
		}
		return results;
	}

	private MatchResult matchOne(int position, String rawText) {
		String normalized = IngredientNameNormalizer.normalize(rawText).value();
		if (normalized.isEmpty()) {
			return unmatched(position, rawText);
		}

		List<Ingredient> sameName = distinctIngredients(aliasRepository.findByNormalizedAlias(normalized));
		if (!sameName.isEmpty()) {
			List<Ingredient> standard = sameName.stream()
					.filter(ingredient -> normalized.equals(
							IngredientNameNormalizer.normalize(ingredient.getStandardName()).value()))
					.toList();
			if (standard.size() == 1) {
				return resolved(position, rawText, MatchType.EXACT, standard.get(0));
			}
			if (standard.isEmpty() && sameName.size() == 1) {
				return resolved(position, rawText, MatchType.ALIAS, sameName.get(0));
			}
			// 같은 이름이 여러 성분에 걸림 → 사용자가 고르도록 후보로 제시
			List<Ingredient> ambiguous = standard.isEmpty() ? sameName : standard;
			List<Candidate> candidates = ambiguous.stream()
					.map(ingredient -> new Candidate(ingredient.getId(), ingredient.getStandardName(), 1.0))
					.sorted(BY_SIMILARITY)
					.limit(MAX_CANDIDATES)
					.toList();
			return new MatchResult(position, rawText, MatchType.FUZZY, null, null, candidates);
		}

		List<Candidate> candidates = fuzzyCandidates(normalized);
		if (candidates.isEmpty()) {
			return unmatched(position, rawText);
		}
		return new MatchResult(position, rawText, MatchType.FUZZY, null, null, candidates);
	}

	private List<Candidate> fuzzyCandidates(String normalized) {
		Map<Long, Candidate> best = new LinkedHashMap<>();
		for (IngredientAlias alias : aliasRepository.searchCandidates(normalized, FULLTEXT_CANDIDATES)) {
			double similarity = NameSimilarity.of(normalized, alias.getNormalizedAlias());
			if (similarity + EPSILON < SIMILARITY_THRESHOLD) {
				continue;
			}
			Ingredient ingredient = alias.getIngredient();
			Candidate candidate = new Candidate(ingredient.getId(), ingredient.getStandardName(), similarity);
			best.merge(ingredient.getId(), candidate, (a, b) -> a.similarity() >= b.similarity() ? a : b);
		}
		return best.values().stream()
				.sorted(BY_SIMILARITY)
				.limit(MAX_CANDIDATES)
				.toList();
	}

	private static List<Ingredient> distinctIngredients(List<IngredientAlias> aliases) {
		Map<Long, Ingredient> byId = new LinkedHashMap<>();
		for (IngredientAlias alias : aliases) {
			byId.putIfAbsent(alias.getIngredient().getId(), alias.getIngredient());
		}
		return List.copyOf(byId.values());
	}

	private static MatchResult resolved(int position, String rawText, MatchType type, Ingredient ingredient) {
		return new MatchResult(position, rawText, type, ingredient.getId(), ingredient.getStandardName(), List.of());
	}

	private static MatchResult unmatched(int position, String rawText) {
		return new MatchResult(position, rawText, MatchType.UNMATCHED, null, null, List.of());
	}
}

package com.mycosmetic.service.ingredient;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.repository.IngredientListItemRepository;
import com.mycosmetic.repository.IngredientListRepository;
import com.mycosmetic.repository.IngredientRepository;
import com.mycosmetic.service.product.ListFingerprint;

/**
 * 사전을 가져온 뒤 저장된 성분 버전의 "사전에 없음" 줄을 다시 매칭한다(설계 4.5, 2026-10-24 사용자 결정).
 *
 * <ul>
 * <li>EXACT·ALIAS로 하나에 정해지는 줄만 성분에 잇는다. FUZZY(후보 고르기)는 사람이 골라야 하므로 그대로 둔다</li>
 * <li>원문·순서·확인 수·상태는 바꾸지 않는다. 바뀐 줄이 있으면 fingerprint를 다시 계산한다.
 * 그래야 같은 제품을 새로 저장할 때 같은 버전으로 찾아진다</li>
 * <li>다시 계산한 fingerprint가 같은 제품의 다른 버전과 같으면 그 버전은 건드리지 않는다(두 버전을 합치지 않는다)</li>
 * <li>버전마다 따로 트랜잭션을 열고 행을 잠근다. 확인·저장과 겹쳐도 한 버전만 실패하고 나머지는 반영된다</li>
 * </ul>
 */
@Service
public class IngredientListRematchService {

	private static final Logger log = LoggerFactory.getLogger(IngredientListRematchService.class);

	/**
	 * @param lists 성분 줄이 바뀐 버전 수
	 * @param items 성분에 이은 줄 수
	 * @param skipped fingerprint가 같은 제품의 다른 버전과 겹쳐 건너뛴 버전 수
	 */
	public record RematchReport(int lists, int items, int skipped) {
	}

	private record ListResult(int items, boolean skipped) {
	}

	private final IngredientListRepository listRepository;
	private final IngredientListItemRepository itemRepository;
	private final IngredientRepository ingredientRepository;
	private final IngredientMatcher matcher;
	private final TransactionTemplate transactionTemplate;

	public IngredientListRematchService(IngredientListRepository listRepository,
			IngredientListItemRepository itemRepository, IngredientRepository ingredientRepository,
			IngredientMatcher matcher, PlatformTransactionManager transactionManager) {
		this.listRepository = listRepository;
		this.itemRepository = itemRepository;
		this.ingredientRepository = ingredientRepository;
		this.matcher = matcher;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public RematchReport rematchUnmatched() {
		return rematchAll(UnaryOperator.identity());
	}

	/**
	 * 관리자 점검함의 띄어쓰기 고치기: "글리세 린"처럼 붙여넣다 단어 중간에 생긴 공백을 빼고 다시 매칭한다.
	 * 원문은 그대로 두고, 공백을 뺀 이름이 사전에 하나로 정해지는(EXACT·ALIAS) 줄만 성분에 잇는다
	 */
	public RematchReport rematchWithoutSpaces() {
		return rematchAll(IngredientListRematchService::withoutSpaces);
	}

	/** 공백을 뺀 이름으로 매칭한 결과(줄 순서대로). 점검함이 고칠 수 있는 줄을 미리 셀 때 쓴다 */
	public List<MatchResult> matchWithoutSpaces(List<String> rawNames) {
		return matcher.match(rawNames.stream().map(IngredientListRematchService::withoutSpaces).toList());
	}

	static String withoutSpaces(String raw) {
		return raw.replaceAll("\\s+", "");
	}

	private RematchReport rematchAll(UnaryOperator<String> textForMatching) {
		int lists = 0;
		int items = 0;
		int skipped = 0;
		for (Long listId : itemRepository.findListIdsWithUnmatched()) {
			try {
				ListResult result = transactionTemplate.execute(status -> rematchList(listId, status, textForMatching));
				if (result.skipped()) {
					skipped++;
				} else if (result.items() > 0) {
					lists++;
					items += result.items();
				}
			} catch (RuntimeException e) {
				log.warn("성분 버전 다시 매칭 실패. 이 버전은 그대로 둔다. listId={}, 이유={}", listId, e.getMessage());
			}
		}
		RematchReport report = new RematchReport(lists, items, skipped);
		log.info("성분 버전 다시 매칭 완료. {}", report);
		return report;
	}

	private ListResult rematchList(Long listId, TransactionStatus status, UnaryOperator<String> textForMatching) {
		IngredientList list = listRepository.findWithLockById(listId).orElseThrow();
		List<IngredientListItem> rows = itemRepository.findWithIngredientByListId(listId);
		List<IngredientListItem> unmatched = rows.stream()
				.filter(row -> row.getMatchType() == MatchType.UNMATCHED)
				.toList();
		List<MatchResult> results = matcher
				.match(unmatched.stream().map(IngredientListItem::getRawText).map(textForMatching).toList());

		int linked = 0;
		for (int i = 0; i < unmatched.size(); i++) {
			MatchResult result = results.get(i);
			if (result.ingredientId() == null) {
				continue;
			}
			unmatched.get(i).linkIngredient(ingredientRepository.getReferenceById(result.ingredientId()),
					result.type());
			linked++;
		}
		if (linked == 0) {
			return new ListResult(0, false);
		}

		String fingerprint = ListFingerprint.of(rows.stream()
				.map(row -> new FinalItem(row.getIngredient() == null ? null : row.getIngredient().getId(),
						row.getRawText()))
				.toList());
		Optional<Long> same = listRepository.findIdByProductIdAndFingerprint(list.getProduct().getId(), fingerprint);
		if (same.isPresent() && !same.get().equals(listId)) {
			status.setRollbackOnly();
			log.warn("다시 매칭한 성분 버전이 같은 제품의 다른 버전과 같아 건너뛴다. listId={}, 같은 버전={}", listId, same.get());
			return new ListResult(0, true);
		}
		list.changeFingerprint(fingerprint);
		return new ListResult(linked, false);
	}
}

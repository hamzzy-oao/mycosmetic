package com.mycosmetic.service.vanity;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.dto.shelf.ExpiryStatus;
import com.mycosmetic.dto.vanity.VanityAnalysisResponse;
import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.MakeupType;
import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.entity.ShelfItem;
import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.entity.SkinProfile;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.repository.SkinProfileRepository;
import com.mycosmetic.service.advice.AdviceIngredient;
import com.mycosmetic.service.advice.ShelfEntryLoader;
import com.mycosmetic.service.routine.IngredientGroup;
import com.mycosmetic.service.shelf.ShelfUsage;

/**
 * 화장대 분석(퍼스널 탭 설계 3절). 내 화장대의 사용 중인 제품으로 루틴 초안과 색조 조합을 만든다. 저장하지 않는다.
 * 🔒 화장대·피부 정보는 인증 사용자 ID로만 읽는다
 */
@Service
public class VanityAnalysisService {

	static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final ShelfItemRepository shelfItemRepository;
	private final SkinProfileRepository skinProfileRepository;
	private final ShelfEntryLoader shelfEntryLoader;
	private final Clock clock;

	public VanityAnalysisService(ShelfItemRepository shelfItemRepository, SkinProfileRepository skinProfileRepository,
			ShelfEntryLoader shelfEntryLoader, Clock clock) {
		this.shelfItemRepository = shelfItemRepository;
		this.skinProfileRepository = skinProfileRepository;
		this.shelfEntryLoader = shelfEntryLoader;
		this.clock = clock;
	}

	/** 대상: 다 쓰지 않았고(종료일 없음) 안맞아요가 아니며 유통기한이 지나지 않은 항목 */
	@Transactional(readOnly = true)
	public VanityAnalysisResponse analyze(Long userId) {
		LocalDate today = LocalDate.now(clock.withZone(SEOUL));
		List<ShelfItem> active = shelfItemRepository.findWithProductByUserId(userId).stream()
				.filter(i -> i.getEndedOn() == null && i.getVerdict() != Verdict.BAD)
				.filter(i -> ShelfUsage.status(ShelfUsage.expiresOn(i.getOpenedOn(), i.getPaoMonths()),
						today) != ExpiryStatus.EXPIRED)
				.toList();
		Map<Long, List<AdviceIngredient>> ingredientsByList = shelfEntryLoader.load(active, null).ingredientsByList();
		Optional<SkinProfile> profile = skinProfileRepository.findById(userId);
		Set<SkinConcern> concerns = profile.map(SkinProfile::getConcerns).orElse(Set.of());
		PersonalColor personalColor = profile.map(SkinProfile::getPersonalColor).orElse(null);

		List<DraftItem> drafts = new ArrayList<>();
		List<Long> needsType = new ArrayList<>();
		List<VanityAnalysisResponse.Item> items = new ArrayList<>();
		List<LookBuilder.LookItem> lookItems = new ArrayList<>();
		for (ShelfItem item : active) {
			String name = item.getProduct().getName();
			Category category = item.getProduct().getCategory();
			StepType step = null;
			MakeupType makeup = null;
			boolean guessed;
			if (category == Category.SKINCARE) {
				step = item.getStepType() != null ? item.getStepType() : ProductTypeGuesser.guessStep(name);
				guessed = item.getStepType() == null && step != null;
				if (step == null) {
					needsType.add(item.getId());
				} else {
					List<String> names = ingredientsByList.getOrDefault(item.getIngredientList().getId(), List.of())
							.stream().flatMap(i -> i.names().stream()).toList();
					drafts.add(new DraftItem(item.getId(), step, guessed, item.getVerdict(), item.getStartedOn(),
							IngredientGroup.detect(names)));
				}
			} else {
				makeup = item.getMakeupType() != null ? item.getMakeupType() : ProductTypeGuesser.guessMakeup(name);
				guessed = item.getMakeupType() == null && makeup != null;
				if (makeup == null) {
					needsType.add(item.getId());
				} else if (makeup != MakeupType.BASE && item.getShadeHex() != null) {
					lookItems.add(new LookBuilder.LookItem(item.getId(), makeup, item.getShadeHex(), item.getVerdict()));
				}
			}
			items.add(new VanityAnalysisResponse.Item(item.getId(), item.getProduct().getBrand(), name, category,
					item.getVerdict(), step, makeup, guessed, item.getShadeHex()));
		}

		RoutineDraft draft = RoutineDraftBuilder.build(drafts, concerns);
		LookBuilder.Looks looks = LookBuilder.build(lookItems, personalColor);
		return new VanityAnalysisResponse(new VanityAnalysisResponse.Draft(draft.morning(), draft.evening()),
				draft.warnings(), draft.gaps(), draft.alternates(), needsType, items, looks.looks(),
				looks.needsPersonalColor(), looks.shadeGaps());
	}
}

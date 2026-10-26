package com.mycosmetic.service.skin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.dto.skin.SkinAnalysisHistoryResponse;
import com.mycosmetic.dto.skin.SkinAnalysisRequest;
import com.mycosmetic.dto.skin.SkinAnalysisResponse;
import com.mycosmetic.dto.skin.SkinQuestionsResponse;
import com.mycosmetic.dto.skin.SkinQuestionsResponse.Option;
import com.mycosmetic.dto.skin.SkinQuestionsResponse.Question;
import com.mycosmetic.entity.ShelfItem;
import com.mycosmetic.entity.SkinAnalysis;
import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.entity.SkinType;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.repository.SkinAnalysisRepository;
import com.mycosmetic.service.advice.AdviceRules;
import com.mycosmetic.service.advice.ShelfEntryLoader;

import tools.jackson.databind.json.JsonMapper;

/**
 * 피부 분석(퍼스널 탭 설계 1절). 문진 답으로 타입·태그를 정하고({@link SkinAnalysisRules}), 내 화장대의 안맞아요 기록으로
 * 확인해 볼 후보 성분을 붙인다. 🔒 화장대·피부 정보·이력은 인증 사용자 ID로만 읽고 쓴다
 */
@Service
public class SkinAnalysisService {

	static final String INTRO = "세안 후 15~30분 동안 아무것도 바르지 않은 상태를 떠올리며 답해 주세요.";

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final SkinAnalysisRepository skinAnalysisRepository;
	private final SkinProfileService skinProfileService;
	private final ShelfItemRepository shelfItemRepository;
	private final ShelfEntryLoader shelfEntryLoader;

	public SkinAnalysisService(SkinAnalysisRepository skinAnalysisRepository, SkinProfileService skinProfileService,
			ShelfItemRepository shelfItemRepository, ShelfEntryLoader shelfEntryLoader) {
		this.skinAnalysisRepository = skinAnalysisRepository;
		this.skinProfileService = skinProfileService;
		this.shelfItemRepository = shelfItemRepository;
		this.shelfEntryLoader = shelfEntryLoader;
	}

	/** 판정 → 피부 정보(타입·고민) 갱신, 퍼스널컬러는 그대로 → 이력 저장 */
	@Transactional
	public SkinAnalysisResponse analyze(Long userId, SkinAnalysisRequest request) {
		List<ShelfItem> shelf = shelfItemRepository.findWithProductByUserId(userId);
		int good = (int) shelf.stream().filter(i -> i.getVerdict() == Verdict.GOOD).count();
		int bad = (int) shelf.stream().filter(i -> i.getVerdict() == Verdict.BAD).count();
		SkinAnalysisResult result = SkinAnalysisRules.analyze(request.selfType(), request.middayFeel(),
				request.concerns(), good, bad);
		List<String> candidates = bad == 0 ? List.of()
				: AdviceRules.badCandidates(shelfEntryLoader.load(shelf, null).entries());

		skinProfileService.applyAnalysis(userId, result.types(), result.concerns());
		SkinAnalysis saved = skinAnalysisRepository.save(SkinAnalysis.create(userId, answersJson(request),
				result.types(), result.concerns(), SkinAnalysisRules.RULE_VERSION));
		skinAnalysisRepository.flush();
		return new SkinAnalysisResponse(result.baseType(), List.copyOf(result.tags()),
				List.copyOf(result.concerns()), result.reasons(), result.suggestSensitive(), candidates,
				SkinAnalysisRules.RULE_VERSION, saved.getCreatedAt());
	}

	@Transactional(readOnly = true)
	public SkinAnalysisHistoryResponse history(Long userId) {
		List<SkinAnalysis> analyses = skinAnalysisRepository.findTop10ByUserIdOrderByCreatedAtDescIdDesc(userId);
		List<SkinAnalysisHistoryResponse.Item> items = analyses.stream()
				.map(a -> new SkinAnalysisHistoryResponse.Item(a.getId(), SkinTypeRules.baseOf(a.getResultTypes()),
						tagsOf(a.getResultTypes()), List.copyOf(a.getConcerns()), a.getRuleVersion(),
						a.getCreatedAt()))
				.toList();
		List<String> changes = analyses.size() < 2 ? List.of() : changes(analyses.get(1), analyses.get(0));
		return new SkinAnalysisHistoryResponse(items, changes);
	}

	public SkinQuestionsResponse questions() {
		return new SkinQuestionsResponse(SkinAnalysisRules.RULE_VERSION, INTRO, List.of(
				new Question("selfType", "본인이 생각하는 피부 유형은?", false, 1, null, List.of(
						new Option("DRY", "건성"), new Option("OILY", "지성"), new Option("COMBINATION", "복합성"),
						new Option("UNSURE", "잘 모르겠어요"))),
				new Question("middayFeel", "보습제 없이 한낮이 되면 피부가 어떤가요?", false, 1, "selfType=UNSURE",
						List.of(new Option("ALL_TIGHT", "얼굴 전체가 당기고 건조해요"),
								new Option("CHEEK_TIGHT", "볼은 당기고 T존은 괜찮아요"),
								new Option("TZONE_OILY", "볼은 괜찮고 T존이 번들거려요"),
								new Option("ALL_OILY", "얼굴 전체가 번들거려요"))),
				new Question("concerns", "현재 가장 고민되는 피부 상태는? (최대 3개)", true,
						SkinAnalysisRules.MAX_CONCERNS, null, concernOptions())));
	}

	private static List<Option> concernOptions() {
		List<Option> options = new ArrayList<>();
		for (SkinConcern concern : SkinConcern.values()) {
			options.add(new Option(concern.name(), SkinAnalysisRules.CONCERN_LABELS.get(concern)));
		}
		return options;
	}

	/** 바로 전 결과(before) 대비 최근 결과(after)가 달라진 점 */
	static List<String> changes(SkinAnalysis before, SkinAnalysis after) {
		List<String> changes = new ArrayList<>();
		SkinType from = SkinTypeRules.baseOf(before.getResultTypes());
		SkinType to = SkinTypeRules.baseOf(after.getResultTypes());
		if (from != null && to != null && from != to) {
			changes.add("피부 타입이 " + label(from) + "에서 " + withRo(label(to)) + " 바뀌었어요.");
		}
		for (SkinType tag : tagsOf(after.getResultTypes())) {
			if (!before.getResultTypes().contains(tag)) {
				changes.add(label(tag) + "이 새로 붙었어요.");
			}
		}
		for (SkinType tag : tagsOf(before.getResultTypes())) {
			if (!after.getResultTypes().contains(tag)) {
				changes.add(label(tag) + "이 빠졌어요.");
			}
		}
		for (SkinConcern concern : after.getConcerns()) {
			if (!before.getConcerns().contains(concern)) {
				changes.add("고민에 " + SkinAnalysisRules.CONCERN_LABELS.get(concern) + "이 더해졌어요.");
			}
		}
		for (SkinConcern concern : before.getConcerns()) {
			if (!after.getConcerns().contains(concern)) {
				changes.add("고민에서 " + SkinAnalysisRules.CONCERN_LABELS.get(concern) + "이 빠졌어요.");
			}
		}
		return changes;
	}

	private static List<SkinType> tagsOf(Set<SkinType> types) {
		return types.stream().filter(t -> !t.isBase()).toList();
	}

	/** 받침이 없거나 ㄹ 받침이면 "로", 그 밖의 받침이면 "으로"(건성으로, 수부지로) */
	static String withRo(String word) {
		char last = word.charAt(word.length() - 1);
		if (last < '가' || last > '힣') {
			return word + "로";
		}
		int jong = (last - '가') % 28;
		return word + (jong == 0 || jong == 8 ? "로" : "으로");
	}

	private static String label(SkinType type) {
		return SkinAnalysisRules.TYPE_LABELS.get(type);
	}

	private static String answersJson(SkinAnalysisRequest request) {
		Map<String, Object> answers = new LinkedHashMap<>();
		answers.put("selfType", request.selfType());
		// 판정이 쓰는 답만 남긴다(잘 모르겠어요가 아니면 한낮 문항은 무시한다)
		answers.put("middayFeel", request.selfType() == SelfType.UNSURE ? request.middayFeel() : null);
		answers.put("concerns", request.concerns());
		return MAPPER.writeValueAsString(answers);
	}
}

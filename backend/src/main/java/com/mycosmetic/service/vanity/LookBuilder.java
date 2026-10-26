package com.mycosmetic.service.vanity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mycosmetic.dto.shelf.ShadeTone;
import com.mycosmetic.entity.MakeupType;
import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.service.color.SeasonPalette;
import com.mycosmetic.service.shade.ShadeClassifier;

/**
 * 보유한 립·블러셔·섀도로 색조 조합을 만든다(퍼스널 탭 설계 3절 B). 순수 함수라 DB 없이 단위 테스트한다.
 * <ul>
 * <li>조합: 립·볼·눈 중 서로 다른 종류 2~3개(종류당 1개). 베이스는 쓰지 않는다. 종류마다 점수 상위 5개만 조합한다</li>
 * <li>데일리: 모두 부드러움(크로마 0.45 미만) / 포인트: 정확히 하나만 선명 / 톤온톤: 모든 쌍의 색상각 차이 30° 이내</li>
 * <li>점수: 내 퍼스널컬러와 웜/쿨 같음 +2(항목당), 계절까지 같음 +1, 잘맞아요 +1, 조합 안 웜·쿨 섞임 −2</li>
 * <li>조합 종류마다 최고점 하나(동점은 잘맞아요 많은 쪽, 그다음 id 작은 쪽)</li>
 * <li>색 범위 추천: 내 퍼스널컬러가 있을 때, 보유 0개인 종류마다 그 계절 팔레트 색. 제품은 추천하지 않는다</li>
 * </ul>
 */
public final class LookBuilder {

	public static final double VIVID_CHROMA = 0.45;
	static final double TONE_ON_TONE_HUE = 30;
	static final int TOP_PER_TYPE = 5;
	static final List<MakeupType> LOOK_TYPES = List.of(MakeupType.LIP, MakeupType.CHEEK, MakeupType.EYE);
	static final List<String> KINDS = List.of("DAILY", "POINT", "TONE_ON_TONE");
	static final Map<String, String> KIND_REASONS = Map.of("DAILY", "모두 부드러운 색이라 매일 쓰기 좋아요.", "POINT",
			"한 곳만 선명하게, 나머지는 차분하게 맞췄어요.", "TONE_ON_TONE", "비슷한 색 계열이라 자연스럽게 이어져요.");

	/** @param hex #RRGGBB */
	public record LookItem(Long shelfItemId, MakeupType makeupType, String hex, Verdict verdict) {
	}

	/** @param shelfItemIds 립 → 볼 → 눈 순서 */
	public record Look(String kind, List<Long> shelfItemIds, int score, List<String> reasons) {

		public Look {
			shelfItemIds = List.copyOf(shelfItemIds);
			reasons = List.copyOf(reasons);
		}
	}

	public record ShadeGap(MakeupType makeupType, PersonalColor season, List<String> hexes) {

		public ShadeGap {
			hexes = List.copyOf(hexes);
		}
	}

	public record Looks(List<Look> looks, boolean needsPersonalColor, List<ShadeGap> shadeGaps) {

		public Looks {
			looks = List.copyOf(looks);
			shadeGaps = List.copyOf(shadeGaps);
		}
	}

	private LookBuilder() {
	}

	/** @param personalColor 없으면 null */
	public static Looks build(List<LookItem> items, PersonalColor personalColor) {
		Map<MakeupType, List<LookItem>> byType = new EnumMap<>(MakeupType.class);
		for (MakeupType type : LOOK_TYPES) {
			byType.put(type, items.stream().filter(i -> i.makeupType() == type)
					.sorted(Comparator.comparingInt((LookItem i) -> -itemScore(i, personalColor))
							.thenComparing(LookItem::shelfItemId))
					.limit(TOP_PER_TYPE).toList());
		}

		List<List<LookItem>> combos = new ArrayList<>();
		combine(byType, 0, new ArrayList<>(), combos);

		List<Look> looks = new ArrayList<>();
		for (String kind : KINDS) {
			best(combos.stream().filter(c -> matchesKind(kind, c)).toList(), personalColor)
					.ifPresent(c -> looks.add(toLook(kind, c, personalColor)));
		}

		List<ShadeGap> gaps = new ArrayList<>();
		if (personalColor != null) {
			for (MakeupType type : LOOK_TYPES) {
				if (byType.get(type).isEmpty()) {
					gaps.add(new ShadeGap(type, personalColor, SeasonPalette.of(personalColor)));
				}
			}
		}
		return new Looks(looks, personalColor == null, gaps);
	}

	/** 원형 색상각 차이(0~180) */
	static double hueDistance(double a, double b) {
		double d = Math.abs(a - b) % 360;
		return d > 180 ? 360 - d : d;
	}

	private static void combine(Map<MakeupType, List<LookItem>> byType, int index, List<LookItem> current,
			List<List<LookItem>> out) {
		if (index == LOOK_TYPES.size()) {
			if (current.size() >= 2) {
				out.add(List.copyOf(current));
			}
			return;
		}
		combine(byType, index + 1, current, out); // 이 종류는 빼고
		for (LookItem item : byType.get(LOOK_TYPES.get(index))) {
			current.add(item);
			combine(byType, index + 1, current, out);
			current.remove(current.size() - 1);
		}
	}

	private static boolean matchesKind(String kind, List<LookItem> combo) {
		long vivid = combo.stream().filter(i -> ShadeClassifier.chromaOf(i.hex()) >= VIVID_CHROMA).count();
		return switch (kind) {
		case "DAILY" -> vivid == 0;
		case "POINT" -> vivid == 1;
		case "TONE_ON_TONE" -> toneOnTone(combo);
		default -> false;
		};
	}

	/** 무채색이 섞이면 톤온톤으로 보지 않는다 */
	private static boolean toneOnTone(List<LookItem> combo) {
		if (combo.stream().anyMatch(i -> ShadeClassifier.classify(i.hex()).tone() == ShadeTone.NEUTRAL)) {
			return false;
		}
		for (int a = 0; a < combo.size(); a++) {
			for (int b = a + 1; b < combo.size(); b++) {
				if (hueDistance(ShadeClassifier.hueOf(combo.get(a).hex()),
						ShadeClassifier.hueOf(combo.get(b).hex())) > TONE_ON_TONE_HUE) {
					return false;
				}
			}
		}
		return true;
	}

	private static Optional<List<LookItem>> best(List<List<LookItem>> combos, PersonalColor personalColor) {
		return combos.stream().min(Comparator.comparingInt((List<LookItem> c) -> -score(c, personalColor))
				.thenComparingLong(c -> -c.stream().filter(i -> i.verdict() == Verdict.GOOD).count())
				.thenComparing(LookBuilder::idKey));
	}

	private static String idKey(List<LookItem> combo) {
		StringBuilder sb = new StringBuilder();
		combo.stream().map(LookItem::shelfItemId).sorted()
				.forEach(id -> sb.append("%020d,".formatted(id)));
		return sb.toString();
	}

	private static int itemScore(LookItem item, PersonalColor personalColor) {
		int score = item.verdict() == Verdict.GOOD ? 1 : 0;
		if (personalColor != null && Boolean.TRUE.equals(ShadeClassifier.matches(item.hex(), personalColor))) {
			score += 2;
			ShadeClassifier.Shade shade = ShadeClassifier.classify(item.hex());
			if (shade.season() != null && personalColor.name().startsWith(shade.season().name())) {
				score += 1;
			}
		}
		return score;
	}

	private static boolean mixed(List<LookItem> combo) {
		boolean warm = combo.stream().anyMatch(i -> ShadeClassifier.classify(i.hex()).tone() == ShadeTone.WARM);
		boolean cool = combo.stream().anyMatch(i -> ShadeClassifier.classify(i.hex()).tone() == ShadeTone.COOL);
		return warm && cool;
	}

	private static int score(List<LookItem> combo, PersonalColor personalColor) {
		int score = combo.stream().mapToInt(i -> itemScore(i, personalColor)).sum();
		return mixed(combo) ? score - 2 : score;
	}

	private static Look toLook(String kind, List<LookItem> combo, PersonalColor personalColor) {
		List<String> reasons = new ArrayList<>();
		reasons.add(KIND_REASONS.get(kind));
		if (personalColor != null) {
			long matching = combo.stream()
					.filter(i -> Boolean.TRUE.equals(ShadeClassifier.matches(i.hex(), personalColor))).count();
			if (matching > 0) {
				reasons.add("내 퍼스널컬러와 같은 톤의 색이 %d개예요.".formatted(matching));
			}
		}
		long good = combo.stream().filter(i -> i.verdict() == Verdict.GOOD).count();
		if (good > 0) {
			reasons.add("잘맞아요 제품이 %d개 들어 있어요.".formatted(good));
		}
		if (mixed(combo)) {
			reasons.add("웜톤과 쿨톤이 섞여 있어요.");
		}
		return new Look(kind, combo.stream().map(LookItem::shelfItemId).toList(), score(combo, personalColor),
				reasons);
	}
}

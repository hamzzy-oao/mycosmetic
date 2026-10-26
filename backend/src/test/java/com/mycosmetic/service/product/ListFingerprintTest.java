package com.mycosmetic.service.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mycosmetic.dto.product.FinalItem;

/** 설계 3.5-1 */
class ListFingerprintTest {

	@Test
	@DisplayName("같은 성분·같은 순서면 같은 fingerprint")
	void sameItemsSameFingerprint() {
		List<FinalItem> a = List.of(new FinalItem(1L, "정제수"), new FinalItem(2L, "글리세린"));
		List<FinalItem> b = List.of(new FinalItem(1L, "물"), new FinalItem(2L, "글리세린"));

		String fp = ListFingerprint.of(a);

		assertThat(fp).isEqualTo(ListFingerprint.of(b));
		assertThat(fp).matches("[0-9a-f]{64}");
	}

	@Test
	@DisplayName("순서가 바뀌면 다른 fingerprint")
	void orderMatters() {
		List<FinalItem> a = List.of(new FinalItem(1L, null), new FinalItem(2L, null));
		List<FinalItem> b = List.of(new FinalItem(2L, null), new FinalItem(1L, null));

		assertThat(ListFingerprint.of(a)).isNotEqualTo(ListFingerprint.of(b));
	}

	@Test
	@DisplayName("매칭 안 된 성분은 정규화한 raw 텍스트로 계산한다")
	void unmatchedUsesNormalizedRaw() {
		List<FinalItem> a = List.of(new FinalItem(1L, null), new FinalItem(null, "Foo  Bar"));
		List<FinalItem> b = List.of(new FinalItem(1L, null), new FinalItem(null, "foo bar"));
		List<FinalItem> c = List.of(new FinalItem(1L, null), new FinalItem(null, "foo baz"));

		assertThat(ListFingerprint.of(a)).isEqualTo(ListFingerprint.of(b));
		assertThat(ListFingerprint.of(a)).isNotEqualTo(ListFingerprint.of(c));
	}

	@Test
	@DisplayName("ingredientId 12와 raw '12'는 다르다")
	void idAndRawDiffer() {
		List<FinalItem> byId = List.of(new FinalItem(12L, null));
		List<FinalItem> byRaw = List.of(new FinalItem(null, "12"));

		assertThat(ListFingerprint.of(byId)).isNotEqualTo(ListFingerprint.of(byRaw));
	}
}

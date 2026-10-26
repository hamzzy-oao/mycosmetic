package com.mycosmetic.service.product.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeedCsvReaderTest {

	private static final String HEADER = "brand,name,volume,category,kind,ingredients,source_type,source_url\r\n";

	@Test
	@DisplayName("큰따옴표 안의 쉼표·줄바꿈·\"\"를 한 칸으로 읽고, 줄 번호는 그 줄이 시작하는 곳이다")
	void readsQuotedFieldsAndKeepsStartLine() {
		String csv = "﻿" + HEADER
				+ "브랜드A,\"크림 \"\"수분\"\"\",50ml,SKINCARE,크림,\"정제수, 글리세린\n판테놀\",PACKAGE,\r\n"
				+ "브랜드B,토너,,SKINCARE,,정제수,OFFICIAL_SITE,https://brand.example/toner\r\n";

		List<SeedRow> rows = SeedCsvReader.parse(csv);

		assertThat(rows).hasSize(2);
		assertThat(rows.get(0).name()).isEqualTo("크림 \"수분\"");
		assertThat(rows.get(0).ingredients()).isEqualTo("정제수, 글리세린\n판테놀");
		assertThat(rows.get(0).line()).isEqualTo(2);
		assertThat(rows.get(1).line()).isEqualTo(4);
		assertThat(rows.get(1).volume()).isEmpty();
		assertThat(rows.get(1).sourceUrl()).isEqualTo("https://brand.example/toner");
	}

	@Test
	@DisplayName("선택 열(volume, kind, source_url)이 없어도 빈 문자열로 읽고, 빈 줄은 건너뛴다")
	void optionalColumnsMayBeAbsent() {
		String csv = "brand,name,category,ingredients,source_type\n\n브랜드,세럼,MAKEUP,정제수,OBF\n,,,,\n";

		List<SeedRow> rows = SeedCsvReader.parse(csv);

		assertThat(rows).singleElement().satisfies(row -> {
			assertThat(row.line()).isEqualTo(3);
			assertThat(row.volume()).isEmpty();
			assertThat(row.kind()).isEmpty();
		});
	}

	@Test
	@DisplayName("필수 열이 없거나 큰따옴표가 닫히지 않으면 IllegalArgumentException")
	void rejectsMissingColumnAndUnclosedQuote() {
		assertThatThrownBy(() -> SeedCsvReader.parse("brand,name,category\nA,B,SKINCARE\n"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ingredients");
		assertThatThrownBy(() -> SeedCsvReader.parse(HEADER + "A,\"B,,SKINCARE,,정제수,PACKAGE,\n"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("2행");
	}
}

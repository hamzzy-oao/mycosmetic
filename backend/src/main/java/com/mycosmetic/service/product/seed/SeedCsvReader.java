package com.mycosmetic.service.product.seed;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 시드 CSV 읽기 (RFC 4180: 쉼표 구분, 큰따옴표로 감싼 칸 안의 쉼표·줄바꿈·{@code ""} 허용). UTF-8이고 BOM이 있어도 된다.
 * 첫 줄은 머리글이다. 필수 열(brand, name, category, ingredients, source_type)이 없으면 IllegalArgumentException.
 */
public final class SeedCsvReader {

	static final List<String> REQUIRED = List.of("brand", "name", "category", "ingredients", "source_type");

	private SeedCsvReader() {
	}

	public static List<SeedRow> read(Path file) throws IOException {
		return parse(Files.readString(file, StandardCharsets.UTF_8));
	}

	static List<SeedRow> parse(String text) {
		if (text.startsWith("﻿")) {
			text = text.substring(1);
		}
		List<Record> records = records(text);
		if (records.isEmpty()) {
			throw new IllegalArgumentException("CSV가 비어 있다");
		}
		Map<String, Integer> header = new HashMap<>();
		List<String> names = records.get(0).fields();
		for (int i = 0; i < names.size(); i++) {
			header.put(names.get(i).strip().toLowerCase(Locale.ROOT), i);
		}
		List<String> missing = REQUIRED.stream().filter(c -> !header.containsKey(c)).toList();
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException("CSV 필수 열이 없다: " + missing);
		}

		List<SeedRow> rows = new ArrayList<>();
		for (Record record : records.subList(1, records.size())) {
			List<String> f = record.fields();
			if (f.stream().allMatch(String::isBlank)) {
				continue;
			}
			rows.add(new SeedRow(record.line(), get(f, header, "brand"), get(f, header, "name"),
					get(f, header, "volume"), get(f, header, "category"), get(f, header, "kind"),
					get(f, header, "ingredients"), get(f, header, "source_type"), get(f, header, "source_url")));
		}
		return rows;
	}

	private static String get(List<String> fields, Map<String, Integer> header, String column) {
		Integer index = header.get(column);
		return index == null || index >= fields.size() ? "" : fields.get(index).strip();
	}

	private record Record(int line, List<String> fields) {
	}

	private static List<Record> records(String text) {
		List<Record> records = new ArrayList<>();
		List<String> fields = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		int line = 1;
		int recordLine = 1;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quoted) {
				if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
					field.append('"');
					i++;
				} else if (c == '"') {
					quoted = false;
				} else {
					if (c == '\n') {
						line++;
					}
					field.append(c);
				}
			} else if (c == '"') {
				quoted = true;
			} else if (c == ',') {
				fields.add(field.toString());
				field.setLength(0);
			} else if (c == '\r') {
				// \r\n의 \r은 무시한다
			} else if (c == '\n') {
				fields.add(field.toString());
				field.setLength(0);
				records.add(new Record(recordLine, fields));
				fields = new ArrayList<>();
				line++;
				recordLine = line;
			} else {
				field.append(c);
			}
		}
		if (quoted) {
			throw new IllegalArgumentException(recordLine + "행: 큰따옴표가 닫히지 않았다");
		}
		if (field.length() > 0 || !fields.isEmpty()) {
			fields.add(field.toString());
			records.add(new Record(recordLine, fields));
		}
		return records;
	}
}

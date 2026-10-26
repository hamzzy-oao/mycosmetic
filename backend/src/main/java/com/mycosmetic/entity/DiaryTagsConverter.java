package com.mycosmetic.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Set&lt;DiaryTag&gt; ↔ JSON 배열 문자열(예: ["TROUBLE","DRYNESS"]). enum 선언 순서로 저장한다. */
@Converter
public class DiaryTagsConverter implements AttributeConverter<Set<DiaryTag>, String> {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final TypeReference<List<DiaryTag>> LIST_TYPE = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(Set<DiaryTag> attribute) {
		Set<DiaryTag> ordered = attribute == null || attribute.isEmpty()
				? EnumSet.noneOf(DiaryTag.class)
				: EnumSet.copyOf(attribute);
		return MAPPER.writeValueAsString(ordered);
	}

	@Override
	public Set<DiaryTag> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return EnumSet.noneOf(DiaryTag.class);
		}
		List<DiaryTag> values = MAPPER.readValue(dbData, LIST_TYPE);
		return values.isEmpty() ? EnumSet.noneOf(DiaryTag.class) : EnumSet.copyOf(values);
	}
}

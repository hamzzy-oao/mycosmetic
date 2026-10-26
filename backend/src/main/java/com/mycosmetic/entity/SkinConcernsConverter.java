package com.mycosmetic.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Set&lt;SkinConcern&gt; ↔ JSON 배열 문자열(예: ["DRYNESS","PORES"]). enum 선언 순서로 저장한다. */
@Converter
public class SkinConcernsConverter implements AttributeConverter<Set<SkinConcern>, String> {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final TypeReference<List<SkinConcern>> LIST_TYPE = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(Set<SkinConcern> attribute) {
		Set<SkinConcern> ordered = attribute == null || attribute.isEmpty()
				? EnumSet.noneOf(SkinConcern.class)
				: EnumSet.copyOf(attribute);
		return MAPPER.writeValueAsString(ordered);
	}

	@Override
	public Set<SkinConcern> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return EnumSet.noneOf(SkinConcern.class);
		}
		List<SkinConcern> values = MAPPER.readValue(dbData, LIST_TYPE);
		return values.isEmpty() ? EnumSet.noneOf(SkinConcern.class) : EnumSet.copyOf(values);
	}
}

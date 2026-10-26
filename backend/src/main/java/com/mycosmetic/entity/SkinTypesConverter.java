package com.mycosmetic.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Set&lt;SkinType&gt; ↔ JSON 배열 문자열(예: ["SENSITIVE","DRY"]). enum 선언 순서로 저장한다. */
@Converter
public class SkinTypesConverter implements AttributeConverter<Set<SkinType>, String> {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final TypeReference<List<SkinType>> LIST_TYPE = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(Set<SkinType> attribute) {
		Set<SkinType> ordered = attribute == null || attribute.isEmpty()
				? EnumSet.noneOf(SkinType.class)
				: EnumSet.copyOf(attribute);
		return MAPPER.writeValueAsString(ordered);
	}

	@Override
	public Set<SkinType> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return EnumSet.noneOf(SkinType.class);
		}
		List<SkinType> values = MAPPER.readValue(dbData, LIST_TYPE);
		return values.isEmpty() ? EnumSet.noneOf(SkinType.class) : EnumSet.copyOf(values);
	}
}

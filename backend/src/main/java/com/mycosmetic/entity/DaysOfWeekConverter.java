package com.mycosmetic.entity;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Set&lt;DayOfWeek&gt; ↔ JSON 배열 문자열(예: ["MONDAY","FRIDAY"]). 월요일부터 순서대로 저장한다 */
@Converter
public class DaysOfWeekConverter implements AttributeConverter<Set<DayOfWeek>, String> {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final TypeReference<List<DayOfWeek>> LIST_TYPE = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(Set<DayOfWeek> attribute) {
		Set<DayOfWeek> ordered = attribute == null || attribute.isEmpty()
				? EnumSet.noneOf(DayOfWeek.class)
				: EnumSet.copyOf(attribute);
		return MAPPER.writeValueAsString(ordered);
	}

	@Override
	public Set<DayOfWeek> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return EnumSet.noneOf(DayOfWeek.class);
		}
		List<DayOfWeek> values = MAPPER.readValue(dbData, LIST_TYPE);
		return values.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(values);
	}
}

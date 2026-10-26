package com.mycosmetic.dto.routine;

import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.RoutineStep;
import com.mycosmetic.entity.StepType;

/** 루틴 단계. 화면이 바로 그릴 수 있도록 제품 이름을 함께 준다 */
public record RoutineStepResponse(Long shelfItemId, StepType stepType, int position, Long productId, String brand,
		String productName, Category category) {

	public static RoutineStepResponse of(RoutineStep step) {
		var product = step.getShelfItem().getProduct();
		return new RoutineStepResponse(step.getShelfItem().getId(), step.getStepType(), step.getPosition(),
				product.getId(), product.getBrand(), product.getName(), product.getCategory());
	}
}

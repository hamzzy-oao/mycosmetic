package com.mycosmetic.service.ingredient;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * {@code --import-ingredients} 인자가 있으면 시작할 때 사전 가져오기를 한 번 실행한다.
 * 실패하면 예외를 그대로 던져 알린다(기존 사전은 바뀌지 않는다).
 *
 * <pre>./gradlew bootRun --args='--import-ingredients'</pre>
 */
@Component
public class IngredientDictionaryImportRunner implements ApplicationRunner {

	static final String OPTION = "import-ingredients";

	private final IngredientDictionaryImportService importService;

	public IngredientDictionaryImportRunner(IngredientDictionaryImportService importService) {
		this.importService = importService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (args.containsOption(OPTION)) {
			importService.importAll();
		}
	}
}

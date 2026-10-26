package com.mycosmetic.service.product.seed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code --seed-products=<CSV>} 인자가 있으면 시작할 때 시드 제품을 한 번 넣고 보고서 JSON을 쓴다 (seed-products 스킬).
 * {@code --seed-dry-run}이면 DB에 쓰지 않는다. 보고서 기본 위치는 {@code build/seed/<오늘>.json}.
 * 일회성 작업이라 보고서를 쓰면 앱을 끝낸다(스케줄러 스레드 때문에 그냥 두면 프로세스가 남는다).
 * {@code --seed-keep-running}이면 끝내지 않는다.
 *
 * <pre>./gradlew bootRun -PskipFrontend --args='--seed-products=C:/seed/skincare.csv --seed-dry-run'</pre>
 */
@Component
public class ProductSeedRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ProductSeedRunner.class);

	static final String OPTION = "seed-products";
	static final String REPORT_OPTION = "seed-report";
	static final String DRY_RUN_OPTION = "seed-dry-run";
	static final String KEEP_RUNNING_OPTION = "seed-keep-running";

	private final ProductSeedService seedService;
	private final Clock clock;
	private final ConfigurableApplicationContext context;
	private final JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

	public ProductSeedRunner(ProductSeedService seedService, Clock clock, ConfigurableApplicationContext context) {
		this.seedService = seedService;
		this.clock = clock;
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		Optional<String> csv = option(args, OPTION);
		if (csv.isEmpty()) {
			return;
		}
		boolean dryRun = args.containsOption(DRY_RUN_OPTION);
		Path reportPath = Path.of(option(args, REPORT_OPTION)
				.orElse("build/seed/" + LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))) + ".json"));

		SeedReport report = seedService.seed(SeedCsvReader.read(Path.of(csv.get())), dryRun);
		if (reportPath.getParent() != null) {
			Files.createDirectories(reportPath.getParent());
		}
		mapper.writeValue(reportPath.toFile(), report);
		log.info("시드 보고서 저장 {}", reportPath.toAbsolutePath());
		if (!args.containsOption(KEEP_RUNNING_OPTION)) {
			System.exit(SpringApplication.exit(context, () -> 0));
		}
	}

	private static Optional<String> option(ApplicationArguments args, String name) {
		List<String> values = args.getOptionValues(name);
		return values == null || values.isEmpty() || values.get(0).isBlank() ? Optional.empty()
				: Optional.of(values.get(0));
	}
}

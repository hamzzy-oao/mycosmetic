package com.mycosmetic.config;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * E2E(계획 Task 26)는 띄울 때마다 빈 DB에서 시작한다: Flyway clean → migrate(+ classpath:e2e 시드).
 * 실수로 개발·운영 DB를 지우지 않도록 DB 이름이 {@code _e2e} 또는 {@code _test}로 끝날 때만 지운다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("e2e")
public class E2eDatabaseConfig {

	@Bean
	FlywayMigrationStrategy e2eCleanMigrateStrategy() {
		return flyway -> {
			String catalog = currentCatalog(flyway.getConfiguration().getDataSource());
			requireDisposable(catalog);
			flyway.clean();
			flyway.migrate();
		};
	}

	/** 지워도 되는 DB(이름이 _e2e·_test로 끝남)가 아니면 시작을 막는다 */
	static void requireDisposable(String catalog) {
		if (catalog == null || !(catalog.endsWith("_e2e") || catalog.endsWith("_test"))) {
			throw new IllegalStateException("E2E는 _e2e 또는 _test DB에서만 실행한다: " + catalog);
		}
	}

	private static String currentCatalog(DataSource dataSource) {
		try (Connection connection = dataSource.getConnection()) {
			return connection.getCatalog();
		} catch (SQLException e) {
			throw new IllegalStateException("E2E DB에 연결할 수 없다", e);
		}
	}
}

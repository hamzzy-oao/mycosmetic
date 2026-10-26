package com.mycosmetic.support;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 테스트 컨텍스트마다 깨끗한 스키마로 시작한다. 실수로 개발 DB를 지우지 않도록 DB 이름을 확인한다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("test")
class TestDatabaseConfig {

	@Bean
	FlywayMigrationStrategy cleanMigrateStrategy() {
		return flyway -> {
			String catalog = currentCatalog(flyway.getConfiguration().getDataSource());
			if (!"mycosmetic_test".equals(catalog)) {
				throw new IllegalStateException("테스트는 mycosmetic_test DB에서만 실행한다: " + catalog);
			}
			flyway.clean();
			flyway.migrate();
		};
	}

	private static String currentCatalog(DataSource dataSource) {
		try (Connection connection = dataSource.getConnection()) {
			return connection.getCatalog();
		} catch (SQLException e) {
			throw new IllegalStateException("테스트 DB에 연결할 수 없다", e);
		}
	}
}

package com.mycosmetic.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.ListConfirmation;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.User;
import com.mycosmetic.support.IntegrationTestSupport;

/**
 * V1 스키마가 설계 3장대로 만들어졌는지 확인한다. 각 테스트는 고유한 값으로 자기 데이터를 만든다(커밋된 행이 남아도 서로 영향 없음).
 */
class SchemaIntegrationTest extends IntegrationTestSupport {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	UserRepository userRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	IngredientListRepository ingredientListRepository;

	@Autowired
	ListConfirmationRepository listConfirmationRepository;

	@Test
	@DisplayName("설계 3장의 테이블 12개와 루틴 테이블 2개(no-ai-features 설계 A)가 있다")
	void allTablesExist() {
		List<String> tables = jdbcTemplate.queryForList(
				"SELECT TABLE_NAME FROM information_schema.TABLES "
						+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'",
				String.class);

		assertThat(tables).containsExactlyInAnyOrder(
				"users", "social_accounts", "refresh_tokens",
				"ingredients", "ingredient_aliases", "products", "ingredient_lists",
				"ingredient_list_items", "list_confirmations",
				"shelf_items", "skin_profiles", "extraction_jobs",
				"routines", "routine_steps", "skin_analyses", "personal_color_analyses",
				// V13 루틴 체크, V14 피부 일기·새 제품 테스트, V15 관리자 점검함
				"routine_checks", "skin_diary_entries", "product_trials", "admin_review_dismissals");
	}

	@Test
	@DisplayName("users.email은 null 여러 개를 허용하고 같은 값은 거절한다")
	void emailUniqueAllowsMultipleNulls() {
		userRepository.saveAndFlush(User.ofSocial(null, "카카오1"));
		userRepository.saveAndFlush(User.ofSocial(null, "카카오2"));

		String email = unique() + "@example.com";
		userRepository.saveAndFlush(User.ofEmail(email, "hash", "첫번째"));

		assertThatThrownBy(() -> userRepository.saveAndFlush(User.ofEmail(email, "hash", "두번째")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("(product_id, fingerprint)와 (list_id, user_id)는 중복 불가")
	void listAndConfirmationUniqueness() {
		User user = userRepository.saveAndFlush(User.ofEmail(unique() + "@example.com", "hash", "확인자"));
		Product product = productRepository.saveAndFlush(
				Product.create(null, "브랜드", "토너 " + unique(), "200ml", Category.SKINCARE, user));
		String fingerprint = "f".repeat(64);
		IngredientList list = ingredientListRepository.saveAndFlush(IngredientList.create(product, fingerprint, user));

		assertThatThrownBy(() -> ingredientListRepository.saveAndFlush(
				IngredientList.create(product, fingerprint, user)))
				.isInstanceOf(DataIntegrityViolationException.class);

		listConfirmationRepository.saveAndFlush(ListConfirmation.create(list, user));

		assertThatThrownBy(() -> listConfirmationRepository.saveAndFlush(ListConfirmation.create(list, user)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("ingredient_aliases.normalized_alias에 ngram FULLTEXT 인덱스가 있다")
	void ngramFulltextIndexExists() {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT COLUMN_NAME, INDEX_TYPE FROM information_schema.STATISTICS "
						+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ingredient_aliases' AND INDEX_NAME = 'ft_alias'");

		assertThat(rows).hasSize(1);
		assertThat(rows.get(0)).containsEntry("COLUMN_NAME", "normalized_alias").containsEntry("INDEX_TYPE", "FULLTEXT");

		String ddl = jdbcTemplate.queryForMap("SHOW CREATE TABLE ingredient_aliases").get("Create Table").toString();
		assertThat(ddl).contains("WITH PARSER `ngram`");
	}

	private static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}

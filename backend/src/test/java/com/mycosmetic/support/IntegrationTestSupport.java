package com.mycosmetic.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

/**
 * 통합 테스트 공통 부모. 로컬 MySQL의 mycosmetic_test DB를 쓰고,
 * 컨텍스트가 뜰 때 {@link TestDatabaseConfig}가 스키마를 지우고 다시 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {
}

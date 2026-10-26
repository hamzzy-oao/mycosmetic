package com.mycosmetic.service.skin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.entity.SkinType;
import com.mycosmetic.entity.User;
import com.mycosmetic.repository.SkinProfileRepository;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** 내 피부 상태 (설계 3.4) */
class SkinProfileServiceTest extends IntegrationTestSupport {

	@Autowired
	SkinProfileService skinProfileService;

	@Autowired
	SkinProfileRepository skinProfileRepository;

	@Autowired
	TestAuthHelper auth;

	@Test
	@DisplayName("저장한 적이 없으면 빈 목록")
	void get_none_empty() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		assertThat(skinProfileService.get(user.getId()).skinTypes()).isEmpty();
	}

	@Test
	@DisplayName("피부 상태는 여러 개를 저장하고 다시 PUT하면 덮어쓴다")
	void put_savesAndOverwrites() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		assertThat(skinProfileService.put(user.getId(), Set.of(SkinType.DRY, SkinType.SENSITIVE)).skinTypes())
				.containsExactly(SkinType.SENSITIVE, SkinType.DRY);
		assertThat(skinProfileService.get(user.getId()).skinTypes())
				.containsExactly(SkinType.SENSITIVE, SkinType.DRY);

		assertThat(skinProfileService.put(user.getId(), Set.of(SkinType.OILY)).skinTypes())
				.containsExactly(SkinType.OILY);
		assertThat(skinProfileRepository.findById(user.getId()).orElseThrow().getSkinTypes())
				.containsExactly(SkinType.OILY);

		assertThat(skinProfileService.put(user.getId(), Set.of()).skinTypes()).isEmpty();
		assertThat(skinProfileService.get(user.getId()).skinTypes()).isEmpty();
	}

	@Test
	@DisplayName("처음 저장을 여러 요청이 동시에 해도 중복 키 오류 없이 모두 성공하고 행은 하나다")
	void concurrentFirstPut_allSucceed() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		int threads = 4;
		CyclicBarrier barrier = new CyclicBarrier(threads);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					barrier.await(10, TimeUnit.SECONDS);
					return skinProfileService.put(user.getId(), Set.of(SkinType.DRY));
				}));
			}
			for (Future<?> future : futures) {
				future.get(30, TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}

		assertThat(skinProfileService.get(user.getId()).skinTypes()).containsExactly(SkinType.DRY);
	}
	@Test
	@DisplayName("퍼스널컬러를 함께 저장하고, null로 PUT하면 지운다(선택 안 함)")
	void put_personalColor() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		assertThat(skinProfileService.get(user.getId()).personalColor()).isNull();
		assertThat(skinProfileService.personalColor(user.getId())).isNull();

		assertThat(skinProfileService.put(user.getId(), Set.of(SkinType.DRY), PersonalColor.SPRING_WARM).personalColor())
				.isEqualTo(PersonalColor.SPRING_WARM);
		assertThat(skinProfileService.get(user.getId()).personalColor()).isEqualTo(PersonalColor.SPRING_WARM);
		assertThat(skinProfileService.personalColor(user.getId())).isEqualTo(PersonalColor.SPRING_WARM);
		assertThat(skinProfileRepository.findById(user.getId()).orElseThrow().getPersonalColor())
				.isEqualTo(PersonalColor.SPRING_WARM);

		assertThat(skinProfileService.put(user.getId(), Set.of(SkinType.DRY), null).personalColor()).isNull();
		assertThat(skinProfileService.personalColor(user.getId())).isNull();
	}
}

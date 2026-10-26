package com.mycosmetic.service.skin;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.dto.skin.SkinProfileResponse;
import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.entity.SkinProfile;
import com.mycosmetic.entity.SkinType;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.SkinProfileRepository;
import com.mycosmetic.repository.UserRepository;

/** 내 피부 상태 (설계 3.4). 🔒 PK가 인증 사용자 ID라 남의 행에 닿을 수 없다 */
@Service
public class SkinProfileService {

	private final SkinProfileRepository skinProfileRepository;
	private final UserRepository userRepository;

	public SkinProfileService(SkinProfileRepository skinProfileRepository, UserRepository userRepository) {
		this.skinProfileRepository = skinProfileRepository;
		this.userRepository = userRepository;
	}

	/** 저장한 적이 없으면 빈 목록, 퍼스널컬러 null */
	@Transactional(readOnly = true)
	public SkinProfileResponse get(Long userId) {
		return skinProfileRepository.findById(userId)
				.map(SkinProfileService::toResponse)
				.orElseGet(() -> new SkinProfileResponse(List.of(), null));
	}

	/** 내 퍼스널컬러. 저장한 적이 없거나 선택 안 했으면 null */
	@Transactional(readOnly = true)
	public PersonalColor personalColor(Long userId) {
		return skinProfileRepository.findById(userId).map(SkinProfile::getPersonalColor).orElse(null);
	}

	/** 피부 상태만 덮어쓰고 퍼스널컬러는 지운다(PUT 의미: 보내지 않은 값은 없음). 피부 고민은 PUT 대상이 아니라 그대로 둔다 */
	@Transactional
	public SkinProfileResponse put(Long userId, Set<SkinType> skinTypes) {
		return put(userId, skinTypes, null);
	}

	/**
	 * 보낸 값으로 덮어쓴다(personalColor가 null이면 지운다). 피부 타입은 기본 타입 1개 + 태그여야 한다(SkinTypeRules). 처음 저장을 두 요청이 동시에 하면 둘 다 "없음"을 보고 INSERT해 중복 키 오류(500)가 나므로,
	 * 사용자 행을 먼저 잠가 같은 사용자의 저장을 줄 세운다. 뒤에 온 요청은 앞 요청이 만든 행을 고친다
	 */
	@Transactional
	public SkinProfileResponse put(Long userId, Set<SkinType> skinTypes, PersonalColor personalColor) {
		SkinTypeRules.validate(skinTypes);
		userRepository.findWithLockById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		SkinProfile profile = skinProfileRepository.findById(userId)
				.map(existing -> {
					existing.changeSkinTypes(skinTypes);
					return existing;
				})
				.orElseGet(() -> skinProfileRepository.save(SkinProfile.create(userId, skinTypes)));
		profile.changePersonalColor(personalColor);
		skinProfileRepository.flush();
		return toResponse(profile);
	}

	/**
	 * 피부 분석 결과로 타입·고민을 덮어쓴다. 퍼스널컬러는 그대로 둔다(퍼스널 탭 설계 1절).
	 * 처음 저장이 겹치지 않게 put과 같이 사용자 행을 먼저 잠근다
	 */
	@Transactional
	public SkinProfileResponse applyAnalysis(Long userId, Set<SkinType> skinTypes, Set<SkinConcern> concerns) {
		SkinTypeRules.validate(skinTypes);
		SkinProfile profile = lockAndGet(userId, skinTypes);
		profile.changeSkinTypes(skinTypes);
		profile.changeConcerns(concerns);
		skinProfileRepository.flush();
		return toResponse(profile);
	}

	/** 퍼스널컬러만 바꾼다. 피부 타입·고민은 그대로 둔다(처음이면 빈 타입으로 만든다) */
	@Transactional
	public SkinProfileResponse changePersonalColorOnly(Long userId, PersonalColor personalColor) {
		SkinProfile profile = lockAndGet(userId, Set.of());
		profile.changePersonalColor(personalColor);
		skinProfileRepository.flush();
		return toResponse(profile);
	}

	private SkinProfile lockAndGet(Long userId, Set<SkinType> initialTypes) {
		userRepository.findWithLockById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		return skinProfileRepository.findById(userId)
				.orElseGet(() -> skinProfileRepository.save(SkinProfile.create(userId, initialTypes)));
	}

	private static SkinProfileResponse toResponse(SkinProfile profile) {
		// getSkinTypes()·getConcerns()는 EnumSet을 감싼 것이라 enum 선언 순서다
		return new SkinProfileResponse(List.copyOf(profile.getSkinTypes()), profile.getPersonalColor(),
				List.copyOf(profile.getConcerns()));
	}
}

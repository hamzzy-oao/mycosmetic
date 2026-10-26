package com.mycosmetic.service.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.dto.user.MeResponse;
import com.mycosmetic.entity.Provider;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ExtractionJobRepository;
import com.mycosmetic.repository.PersonalColorAnalysisRepository;
import com.mycosmetic.repository.RefreshTokenRepository;
import com.mycosmetic.repository.RoutineCheckRepository;
import com.mycosmetic.repository.RoutineRepository;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.repository.SkinAnalysisRepository;
import com.mycosmetic.repository.SkinProfileRepository;
import com.mycosmetic.repository.SocialAccountRepository;
import com.mycosmetic.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final ShelfItemRepository shelfItemRepository;
	private final SkinProfileRepository skinProfileRepository;
	private final ExtractionJobRepository extractionJobRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final RoutineRepository routineRepository;
	private final SkinAnalysisRepository skinAnalysisRepository;
	private final PersonalColorAnalysisRepository personalColorAnalysisRepository;
	private final RoutineCheckRepository routineCheckRepository;

	public UserService(UserRepository userRepository, SocialAccountRepository socialAccountRepository,
			ShelfItemRepository shelfItemRepository, SkinProfileRepository skinProfileRepository,
			ExtractionJobRepository extractionJobRepository, RefreshTokenRepository refreshTokenRepository,
			RoutineRepository routineRepository, SkinAnalysisRepository skinAnalysisRepository,
			PersonalColorAnalysisRepository personalColorAnalysisRepository,
			RoutineCheckRepository routineCheckRepository) {
		this.userRepository = userRepository;
		this.socialAccountRepository = socialAccountRepository;
		this.shelfItemRepository = shelfItemRepository;
		this.skinProfileRepository = skinProfileRepository;
		this.extractionJobRepository = extractionJobRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.routineRepository = routineRepository;
		this.skinAnalysisRepository = skinAnalysisRepository;
		this.personalColorAnalysisRepository = personalColorAnalysisRepository;
		this.routineCheckRepository = routineCheckRepository;
	}

	/** 탈퇴한 사용자의 남은 access_token을 거절할 때 쓴다 */
	public boolean exists(Long userId) {
		return userRepository.existsById(userId);
	}

	public MeResponse me(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		boolean kakaoLinked = socialAccountRepository.findByUserIdAndProvider(userId, Provider.KAKAO).isPresent();
		return new MeResponse(user.getId(), user.getEmail(), user.getNickname(), user.getPasswordHash() != null,
				kakaoLinked, user.getRole());
	}

	/** 닉네임 변경. 앞뒤 공백을 지우고, 지운 뒤 비면 INVALID_REQUEST */
	@Transactional
	public MeResponse changeNickname(Long userId, String nickname) {
		String trimmed = nickname == null ? "" : nickname.strip();
		if (trimmed.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		user.changeNickname(trimmed);
		return me(userId);
	}

	/**
	 * 탈퇴(설계 5.5). 하나의 트랜잭션에서 본인 데이터를 자식 테이블부터 명시적으로 지우고 마지막에 users 행을 지운다.
	 * 외래 키의 ON DELETE CASCADE에 기대지 않아 지우는 대상이 코드에 드러난다.
	 * 공유 데이터(products·ingredient_lists의 created_by, list_confirmations.user_id)는 지우지 않고
	 * 외래 키 ON DELETE SET NULL로 익명이 된다. confirm_count는 바꾸지 않는다.
	 * 이미 없는 사용자면 아무것도 하지 않는다(같은 요청이 두 번 와도 안전).
	 */
	@Transactional
	public void withdraw(Long userId) {
		routineCheckRepository.deleteAllByUserId(userId);   // 사용 기록도 shelf_items를 가리킨다
		routineRepository.deleteAllByUserId(userId);   // routine_steps가 shelf_items를 가리키므로 먼저
		shelfItemRepository.deleteAllByUserId(userId);
		skinProfileRepository.deleteAllByUserId(userId);
		skinAnalysisRepository.deleteAllByUserId(userId);
		personalColorAnalysisRepository.deleteAllByUserId(userId);
		extractionJobRepository.deleteAllByUserId(userId);
		refreshTokenRepository.deleteAllByUserId(userId);
		socialAccountRepository.deleteAllByUserId(userId);
		userRepository.deleteById(userId);
	}
}

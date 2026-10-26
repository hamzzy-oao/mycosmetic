package com.mycosmetic.service.extraction;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import com.mycosmetic.dto.extraction.ExtractionJobResponse;
import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.JobStatus;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ExtractionJobRepository;
import com.mycosmetic.repository.UserRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * 성분 추출 접수와 결과 조회 (설계 4.1 3단계, 4.4).
 */
@Service
public class ExtractionService {

	private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

	/** 사용자당 하루(Asia/Seoul 0시~24시) 추출 횟수. 실패해서 한도에서 뺀 job은 세지 않는다 */
	public static final int DAILY_LIMIT = 20;
	/** spring.servlet.multipart.max-file-size와 같은 값. 서블릿 설정을 거치지 않는 경로에서도 지킨다 */
	static final long MAX_FILE_BYTES = DataSize.ofMegabytes(5).toBytes();
	/** 사용자당 동시에 진행(QUEUED·RUNNING)할 수 있는 job 수. 스레드풀·대기열을 한 사람이 다 차지하지 못하게 한다 */
	static final int MAX_IN_PROGRESS = 2;
	/** 사진 가로·세로 최대(px). 화면은 긴 변 2000px로 줄여 보낸다(설계 4.1 2단계) */
	static final int MAX_DIMENSION = 4000;
	/** 사진 없이 직접 붙여넣기로 시작한 job의 error_code. AI를 부르지 않으므로 API 키 없이도 쓸 수 있다 */
	public static final String MANUAL_INPUT = "MANUAL_INPUT";
	private static final Set<JobStatus> IN_PROGRESS = EnumSet.of(JobStatus.QUEUED, JobStatus.RUNNING);
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final ExtractionJobRepository jobRepository;
	private final UserRepository userRepository;
	private final ExtractionWorker worker;
	private final TransactionTemplate tx;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	public ExtractionService(ExtractionJobRepository jobRepository, UserRepository userRepository,
			ExtractionWorker worker, TransactionTemplate tx, JsonMapper jsonMapper, Clock clock) {
		this.jobRepository = jobRepository;
		this.userRepository = userRepository;
		this.worker = worker;
		this.tx = tx;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	/**
	 * 형식·크기·가로세로 확인 → 하루 한도와 동시 작업 수 확인(사용자 행 잠금으로 같은 사용자의 동시 접수를 줄 세움) → QUEUED job 저장.
	 * 트랜잭션이 커밋된 <b>뒤에</b> worker에 사진을 넘긴다(worker가 아직 커밋되지 않은 job을 찾지 못하는 일을 막는다).
	 *
	 * @return 접수된 job ID
	 */
	public Long accept(Long userId, MultipartFile file) {
		if (file.getSize() > MAX_FILE_BYTES) {
			throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
		}
		byte[] image = readBytes(file);
		String mediaType = ImageTypeDetector.detect(image)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_IMAGE));
		ImageTypeDetector.Dimensions size = ImageTypeDetector.readDimensions(image)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_IMAGE));
		if (size.width() > MAX_DIMENSION || size.height() > MAX_DIMENSION) {
			throw new BusinessException(ErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);
		}

		Long jobId = tx.execute(status -> queueWithinLimit(userId));

		dispatch(jobId, image, mediaType);
		return jobId;
	}

	private Long queueWithinLimit(Long userId) {
		User user = userRepository.findWithLockById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		Instant now = Instant.now(clock);
		long usedToday = jobRepository.countByUserIdAndCountsTowardLimitTrueAndCreatedAtGreaterThanEqual(userId,
				startOfToday(now));
		if (usedToday >= DAILY_LIMIT) {
			throw new BusinessException(ErrorCode.EXTRACTION_DAILY_LIMIT);
		}
		if (jobRepository.countByUserIdAndStatusIn(userId, IN_PROGRESS) >= MAX_IN_PROGRESS) {
			throw new BusinessException(ErrorCode.EXTRACTION_IN_PROGRESS);
		}
		return jobRepository.save(ExtractionJob.queue(user, now)).getId();
	}

	/**
	 * 사진 없이 전성분을 직접 붙여넣는 job을 만든다. AI를 부르지 않고 바로 FAILED({@link #MANUAL_INPUT}, 한도 미차감)로 두어
	 * 기존 "AI 실패 → 직접 붙여넣기" 저장 흐름을 그대로 쓴다. 저장할 때 하루 한도에 1회로 센다(ProductConfirmTxService).
	 *
	 * @return 만든 job ID
	 */
	public Long startManual(Long userId) {
		return tx.execute(status -> {
			User user = userRepository.findWithLockById(userId)
					.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
			Instant now = Instant.now(clock);
			long usedToday = jobRepository.countByUserIdAndCountsTowardLimitTrueAndCreatedAtGreaterThanEqual(userId,
					startOfToday(now));
			if (usedToday >= DAILY_LIMIT) {
				throw new BusinessException(ErrorCode.EXTRACTION_DAILY_LIMIT);
			}
			ExtractionJob job = ExtractionJob.queue(user, now);
			job.fail(MANUAL_INPUT, false, now);
			return jobRepository.save(job).getId();
		});
	}

	/** 작업 큐가 가득 차면 접수를 AI_BUSY로 거절하고, 만들어 둔 job은 한도에서 빼고 FAILED로 둔다 */
	private void dispatch(Long jobId, byte[] image, String mediaType) {
		try {
			worker.run(jobId, image, mediaType);
		} catch (TaskRejectedException e) {
			log.warn("성분 추출 작업 큐가 가득 참 jobId={}", jobId);
			tx.executeWithoutResult(status -> jobRepository.findById(jobId)
					.ifPresent(job -> job.fail(ErrorCode.AI_BUSY.name(), false, Instant.now(clock))));
			throw new BusinessException(ErrorCode.AI_BUSY);
		}
	}

	/** 본인 job만 조회한다. 남의 job이나 없는 job은 똑같이 NOT_FOUND */
	@Transactional(readOnly = true)
	public ExtractionJobResponse get(Long userId, Long jobId) {
		ExtractionJob job = jobRepository.findByIdAndUserId(jobId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		return new ExtractionJobResponse(job.getId(), job.getStatus(), job.getErrorCode(),
				job.getResultJson() == null ? null : jsonMapper.readTree(job.getResultJson()), job.isConfirmed());
	}

	/** now가 속한 날(Asia/Seoul)의 0시. 하루 한도의 시작 */
	public static Instant startOfToday(Instant now) {
		return LocalDate.ofInstant(now, SEOUL).atStartOfDay(SEOUL).toInstant();
	}

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
	}
}

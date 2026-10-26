package com.mycosmetic.service.color;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.dto.color.ColorSamplesRequest;
import com.mycosmetic.dto.color.ColorSamplesRequest.RgbValue;
import com.mycosmetic.dto.color.EstimateResponse;
import com.mycosmetic.dto.color.EstimateResponse.LabValue;
import com.mycosmetic.dto.color.EstimateResponse.QualityValue;
import com.mycosmetic.dto.color.PalettesResponse;
import com.mycosmetic.dto.color.PersonalColorAnalysisResponse;
import com.mycosmetic.dto.color.SavePersonalColorRequest;
import com.mycosmetic.entity.ColorMethod;
import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.entity.PersonalColorAnalysis;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.PersonalColorAnalysisRepository;
import com.mycosmetic.service.skin.SkinProfileService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 퍼스널컬러(퍼스널 탭 설계 2절). 브라우저가 보낸 평균색으로 판정하고({@link PersonalColorRules}), 사용자가 드레이핑으로 고른 결과를
 * 저장한다. 판정은 저장할 때도 서버가 다시 계산한다(브라우저가 보낸 판정은 받지 않는다). 🔒 결과·피부 정보는 인증 사용자 ID로만
 */
@Service
public class PersonalColorService {

	static final int MAX_FRAMES = 30;

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final TypeReference<List<PersonalColor>> COLOR_LIST = new TypeReference<>() {
	};

	private final PersonalColorAnalysisRepository analysisRepository;
	private final SkinProfileService skinProfileService;

	public PersonalColorService(PersonalColorAnalysisRepository analysisRepository,
			SkinProfileService skinProfileService) {
		this.analysisRepository = analysisRepository;
		this.skinProfileService = skinProfileService;
	}

	public PalettesResponse palettes() {
		Map<PersonalColor, List<String>> palettes = new EnumMap<>(PersonalColor.class);
		for (PersonalColor color : PersonalColor.values()) {
			palettes.put(color, SeasonPalette.of(color));
		}
		return new PalettesResponse(palettes, PersonalColorRules.RULE_VERSION);
	}

	/** 저장하지 않는다. 품질이 낮아도 결과와 사유를 돌려준다 */
	public EstimateResponse estimate(ColorSamplesRequest request) {
		Judged judged = judge(request);
		return toEstimate(judged);
	}

	/** CAMERA는 다시 판정해서 품질이 낮으면 PERSONAL_COLOR_LOW_QUALITY. 피부 정보의 퍼스널컬러도 바꾼다(피부 타입은 그대로) */
	@Transactional
	public PersonalColorAnalysisResponse save(Long userId, SavePersonalColorRequest request) {
		PersonalColorAnalysis analysis;
		if (request.method() == ColorMethod.MANUAL) {
			analysis = PersonalColorAnalysis.manual(userId, request.chosen(), PersonalColorRules.RULE_VERSION);
		} else {
			if (request.samples() == null) {
				throw new BusinessException(ErrorCode.INVALID_COLOR_SAMPLES);
			}
			Judged judged = judge(request.samples());
			if (!judged.passed()) {
				throw new BusinessException(ErrorCode.PERSONAL_COLOR_LOW_QUALITY);
			}
			PersonalColorRules.Estimate e = judged.estimate();
			List<PersonalColor> candidates = e.judgment().candidates();
			Map<String, Object> quality = new LinkedHashMap<>();
			quality.put("passed", true);
			quality.put("issues", judged.issues());
			quality.put("score", e.quality().score());
			analysis = PersonalColorAnalysis.camera(userId, new double[] { e.lab().l(), e.lab().a(), e.lab().b() },
					e.ita(), e.hue(), e.judgment().confidence(), MAPPER.writeValueAsString(quality),
					MAPPER.writeValueAsString(candidates), request.chosen(), candidates.get(0) == request.chosen(),
					PersonalColorRules.RULE_VERSION);
		}
		skinProfileService.changePersonalColorOnly(userId, request.chosen());
		PersonalColorAnalysis saved = analysisRepository.saveAndFlush(analysis);
		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public Optional<PersonalColorAnalysisResponse> latest(Long userId) {
		return analysisRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId).map(PersonalColorService::toResponse);
	}

	/** 판정 + 브라우저 확인값(얼굴 수·정면)을 합친 품질 */
	private record Judged(PersonalColorRules.Estimate estimate, List<String> issues) {

		boolean passed() {
			return issues.isEmpty();
		}
	}

	private static Judged judge(ColorSamplesRequest request) {
		List<ColorSample> samples = validate(request);
		Rgb white = request.whiteRef() == null ? null : rgb(request.whiteRef());
		PersonalColorRules.Estimate estimate = PersonalColorRules.estimate(samples, white);
		List<String> issues = new ArrayList<>(estimate.quality().issues());
		// 브라우저 값이 없거나 이상하면 실패로 본다(얼굴은 정확히 1개, 정면은 true일 때만 통과)
		ColorSamplesRequest.ClientCheck client = request.client();
		Integer faces = client == null ? null : client.faceCount();
		if (faces == null || faces < 1) {
			issues.add("NO_FACE");
		} else if (faces > 1) {
			issues.add("MULTIPLE_FACES");
		}
		if (client == null || !Boolean.TRUE.equals(client.frontal())) {
			issues.add("NOT_FRONTAL");
		}
		return new Judged(estimate, issues);
	}

	/** 세 영역이 한 번씩, 영역마다 프레임 1~30개, 채널 0~255. 아니면 INVALID_COLOR_SAMPLES */
	static List<ColorSample> validate(ColorSamplesRequest request) {
		if (request == null || request.samples() == null || request.samples().size() != Region.values().length) {
			throw new BusinessException(ErrorCode.INVALID_COLOR_SAMPLES);
		}
		Set<Region> seen = EnumSet.noneOf(Region.class);
		List<ColorSample> samples = new ArrayList<>();
		for (ColorSamplesRequest.Sample sample : request.samples()) {
			if (sample == null || sample.region() == null || !seen.add(sample.region()) || sample.frames() == null
					|| sample.frames().isEmpty() || sample.frames().size() > MAX_FRAMES) {
				throw new BusinessException(ErrorCode.INVALID_COLOR_SAMPLES);
			}
			samples.add(new ColorSample(sample.region(), sample.frames().stream().map(PersonalColorService::rgb).toList()));
		}
		return samples;
	}

	private static Rgb rgb(RgbValue value) {
		if (value == null || !channel(value.r()) || !channel(value.g()) || !channel(value.b())) {
			throw new BusinessException(ErrorCode.INVALID_COLOR_SAMPLES);
		}
		return new Rgb(value.r(), value.g(), value.b());
	}

	private static boolean channel(Integer v) {
		return v != null && v >= 0 && v <= 255;
	}

	private static EstimateResponse toEstimate(Judged judged) {
		PersonalColorRules.Estimate e = judged.estimate();
		PersonalColorRules.Judgment j = e.judgment();
		return new EstimateResponse(new LabValue(e.lab().l(), e.lab().a(), e.lab().b()), e.ita(), e.hue(), e.chroma(),
				j.candidates(), j.confidence(), j.ordered(),
				new QualityValue(judged.passed(), judged.issues(), e.quality().score()), PersonalColorRules.RULE_VERSION);
	}

	private static PersonalColorAnalysisResponse toResponse(PersonalColorAnalysis a) {
		LabValue lab = a.getLabL() == null ? null : new LabValue(a.getLabL(), a.getLabA(), a.getLabB());
		List<PersonalColor> candidates = a.getCandidates() == null ? null
				: MAPPER.readValue(a.getCandidates(), COLOR_LIST);
		return new PersonalColorAnalysisResponse(a.getId(), a.getMethod(), a.getChosen(), candidates,
				a.getConfidence(), a.getAgreed(), lab, a.getRuleVersion(), a.getCreatedAt());
	}
}

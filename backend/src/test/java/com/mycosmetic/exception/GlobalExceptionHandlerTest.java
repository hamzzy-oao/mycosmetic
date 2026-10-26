package com.mycosmetic.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.mycosmetic.support.IntegrationTestSupport;

@WithMockUser
class GlobalExceptionHandlerTest extends IntegrationTestSupport {

	private static final String TRACE_ID_PATTERN = "[0-9a-f]{8}";

	@Autowired
	MockMvc mockMvc;

	@Test
	@DisplayName("BusinessException은 ErrorCode의 상태·코드·메시지와 traceId로 응답한다")
	void businessException_usesErrorCode() throws Exception {
		mockMvc.perform(get("/api/test-errors/business"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.message").value(ErrorCode.NOT_FOUND.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern(TRACE_ID_PATTERN)));
	}

	@Test
	@DisplayName("@Valid 실패는 400 INVALID_REQUEST")
	void validationFailure_returns400() throws Exception {
		mockMvc.perform(post("/api/test-errors/valid").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_REQUEST.message()));
	}

	@Test
	@DisplayName("예상하지 못한 예외는 500 일반 메시지, 응답에 예외 클래스명·스택이 없다")
	void unexpectedException_hidesDetails() throws Exception {
		mockMvc.perform(get("/api/test-errors/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.message()))
				.andExpect(content().string(not(containsString("Exception"))))
				.andExpect(content().string(not(containsString("SELECT"))));
	}

	@Test
	@DisplayName("응답의 traceId와 X-Trace-Id 헤더가 같다")
	void traceId_matchesHeader() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/test-errors/business"))
				.andExpect(header().string("X-Trace-Id", matchesPattern(TRACE_ID_PATTERN)))
				.andReturn();
		String bodyTraceId = JsonPath.read(result.getResponse().getContentAsString(), "$.traceId");
		assertThat(bodyTraceId).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
	}

	@Test
	@DisplayName("업로드 크기 초과는 413 FILE_TOO_LARGE")
	void maxUploadSizeExceeded_returns413() throws Exception {
		mockMvc.perform(get("/api/test-errors/too-large"))
				.andExpect(status().isContentTooLarge())
				.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"))
				.andExpect(jsonPath("$.message").value(ErrorCode.FILE_TOO_LARGE.message()));
	}

	@Test
	@DisplayName("없는 경로는 404 NOT_FOUND JSON")
	void unknownPath_returns404Json() throws Exception {
		mockMvc.perform(get("/api/nope"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.traceId").value(matchesPattern(TRACE_ID_PATTERN)));
	}
}

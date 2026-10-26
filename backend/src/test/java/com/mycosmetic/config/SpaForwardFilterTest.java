package com.mycosmetic.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.mycosmetic.support.IntegrationTestSupport;

class SpaForwardFilterTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Test
	@DisplayName("화면 주소 GET은 index.html로 전달한다")
	void screenPath_forwardsToIndex() throws Exception {
		mockMvc.perform(get("/shelf/3"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("/index.html"));
	}

	@Test
	@DisplayName("루트 GET도 index.html로 전달한다")
	void root_forwardsToIndex() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(forwardedUrl("/index.html"));
	}

	@Test
	@DisplayName("없는 API는 index.html로 전달하지 않는다")
	void api_isNotForwarded() throws Exception {
		mockMvc.perform(get("/api/nope"))
				.andExpect(forwardedUrl(null));
	}

	@Test
	@DisplayName("정적 파일 경로는 전달하지 않는다")
	void staticFile_isNotForwarded() throws Exception {
		mockMvc.perform(get("/static/js/none.js"))
				.andExpect(status().isNotFound())
				.andExpect(forwardedUrl(null));
	}

	@Test
	@DisplayName("POST는 전달하지 않는다")
	void post_isNotForwarded() throws Exception {
		mockMvc.perform(post("/shelf"))
				.andExpect(forwardedUrl(null));
	}
}

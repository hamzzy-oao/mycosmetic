package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.mycosmetic.entity.User;
import com.mycosmetic.security.TokenCookies;
import com.mycosmetic.support.TestAuthHelper;
import com.mycosmetic.support.TestImages;

import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 Tomcat으로 6MB(max-request-size)를 넘는 본문을 보낸다.
 * server.tomcat.max-swallow-size가 작으면 Tomcat이 남은 본문을 버리지 못하고 연결을 끊어 화면이 413 JSON을 받지 못한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UploadSizeLimitTest {

	@LocalServerPort
	int port;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	JsonMapper jsonMapper;

	private final HttpClient http = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	@Test
	@DisplayName("7MB 본문은 연결이 끊기지 않고 413 FILE_TOO_LARGE JSON을 받는다")
	void sevenMegabyteBody_returns413Json() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		String accessToken = auth.accessTokenCookie(user).getValue();
		String csrf = fetchCsrfToken();

		String boundary = "----test" + UUID.randomUUID();
		byte[] body = multipartBody(boundary, 7 * 1024 * 1024);
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/extractions"))
				.timeout(Duration.ofSeconds(30))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.header("Cookie", TokenCookies.ACCESS_TOKEN + "=" + accessToken + "; XSRF-TOKEN=" + csrf)
				.header("X-XSRF-TOKEN", csrf)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body))
				.build();

		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		assertThat(response.statusCode()).isEqualTo(413);
		assertThat(jsonMapper.readTree(response.body()).get("code").asString()).isEqualTo("FILE_TOO_LARGE");
	}

	private String fetchCsrfToken() throws Exception {
		HttpResponse<Void> response = http.send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/csrf")).GET().build(),
				HttpResponse.BodyHandlers.discarding());
		return response.headers().allValues("Set-Cookie").stream()
				.filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
				.map(cookie -> cookie.substring("XSRF-TOKEN=".length()).split(";", 2)[0])
				.findFirst()
				.orElseThrow();
	}

	private static byte[] multipartBody(String boundary, int fileSize) {
		byte[] file = new byte[fileSize];
		byte[] jpeg = TestImages.jpeg();
		System.arraycopy(jpeg, 0, file, 0, jpeg.length);
		ByteArrayOutputStream out = new ByteArrayOutputStream(fileSize + 512);
		out.writeBytes(("--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"image\"; filename=\"big.jpg\"\r\n"
				+ "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
		out.writeBytes(file);
		out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
		return out.toByteArray();
	}
}

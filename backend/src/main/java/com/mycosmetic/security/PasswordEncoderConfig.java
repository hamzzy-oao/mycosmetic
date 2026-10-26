package com.mycosmetic.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 비밀번호는 BCrypt로 저장한다. 웹 서버가 없는 프로필에서도 서비스가 뜨도록 SecurityConfig와 따로 둔다. */
@Configuration(proxyBeanMethods = false)
public class PasswordEncoderConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}

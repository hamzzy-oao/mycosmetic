package com.mycosmetic.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙이면 인증된 사용자 ID(JWT의 sub)를 넣어 준다.
 * 사용자 ID는 항상 이것으로만 얻는다. 경로나 본문의 사용자 ID는 쓰지 않는다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId {
}

package com.ssafy.eolssu.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration

// 로그인은 JWT 쿠키로만 하므로 Spring Boot가 만드는 기본 사용자·비밀번호는 쓰지 않는다.
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@ConfigurationPropertiesScan
class BackendApplication

fun main(args: Array<String>) {
	runApplication<BackendApplication>(*args)
}

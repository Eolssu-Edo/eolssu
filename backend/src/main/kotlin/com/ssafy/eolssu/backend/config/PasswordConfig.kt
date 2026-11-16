package com.ssafy.eolssu.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration(proxyBeanMethods = false)
class PasswordConfig {
    // Argon2id. 저장값 앞의 {argon2}로 방식을 구분해 나중에 바꿔도 기존 해시를 확인할 수 있다.
    // BCrypt는 72바이트까지만 써서 한글·이모지 비밀번호(최대 64자, 1글자 최대 4바이트)를 다 담지 못한다.
    @Bean
    fun passwordEncoder(): PasswordEncoder =
        DelegatingPasswordEncoder("argon2", mapOf("argon2" to Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()))
}

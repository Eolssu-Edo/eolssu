package com.ssafy.eolssu.backend.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling

// 주기 작업은 db 프로필에서만 돈다. 공유 DB를 쓰는 통합 테스트처럼 막아야 할 때는 eolssu.scheduling.enabled=false로 끈다.
@Configuration(proxyBeanMethods = false)
@Profile("db")
@ConditionalOnProperty(name = ["eolssu.scheduling.enabled"], havingValue = "true", matchIfMissing = true)
@EnableScheduling
class SchedulingConfig

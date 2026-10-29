package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.practice.application.RecordingUploadExpiryService
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingSessionExpiryRepository
import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.MyBatisRecordingSessionExpiryRepository
import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.PracticeSessionMapper
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

// 쓰기 작업이라 db 프로필에서만 만든다. ops 프로필(읽기 전용 점검)과 무DB 기본 프로필에서는 만들지 않는다. 주기 실행은 SchedulingConfig가 켠다.
@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class RecordingUploadExpiryConfig {
    @Bean
    fun recordingSessionExpiryRepository(mapper: PracticeSessionMapper): RecordingSessionExpiryRepository =
        MyBatisRecordingSessionExpiryRepository(mapper)

    @Bean
    fun recordingUploadExpiryService(repository: RecordingSessionExpiryRepository): RecordingUploadExpiryService =
        RecordingUploadExpiryService(repository, Clock.systemUTC())
}

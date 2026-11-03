package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.practice.application.UnsavedRecordingPurgeService
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.domain.UnsavedRecordingRepository
import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.MyBatisUnsavedRecordingRepository
import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.PracticeSessionMapper
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

// 매일 04:30(Asia/Seoul) 실행은 SchedulingConfig가 켠다. 녹음 저장소가 설정되지 않았으면 파일 삭제가 실패해 키를 되돌리고 다음 날로 미룬다.
@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class UnsavedRecordingPurgeConfig {
    @Bean
    fun unsavedRecordingRepository(mapper: PracticeSessionMapper): UnsavedRecordingRepository =
        MyBatisUnsavedRecordingRepository(mapper)

    @Bean
    fun unsavedRecordingPurgeService(repository: UnsavedRecordingRepository, storage: RecordingStorage) =
        UnsavedRecordingPurgeService(repository, storage::delete, Clock.systemUTC())
}

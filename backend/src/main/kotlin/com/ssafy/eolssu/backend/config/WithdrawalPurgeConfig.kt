package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.application.WithdrawalPurgeService
import com.ssafy.eolssu.backend.feature.auth.domain.WithdrawalPurgeRepository
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.MyBatisWithdrawalPurgeRepository
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.WithdrawalPurgeMapper
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.sheet.domain.SHEET_SOURCE_PREFIX
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.PlatformTransactionManager

// 매일 04:00(Asia/Seoul) 실행은 SchedulingConfig가 켠다. 녹음 저장소가 설정되지 않았으면 녹음이 있는 회원은 다음 날로 미뤄진다.
@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class WithdrawalPurgeConfig {
    @Bean
    fun withdrawalPurgeRepository(mapper: WithdrawalPurgeMapper, manager: PlatformTransactionManager): WithdrawalPurgeRepository =
        MyBatisWithdrawalPurgeRepository(mapper, manager)

    @Bean
    fun withdrawalPurgeService(repository: WithdrawalPurgeRepository, storage: RecordingStorage, sheets: SheetSourceStorage) =
        // 내 악보 원본은 따로 둔 악보 전용 버킷에 있다(S15P31E102-471). 키 앞부분으로 저장소를 고른다.
        WithdrawalPurgeService(
            repository,
            { key -> if (key.startsWith(SHEET_SOURCE_PREFIX)) sheets.delete(key) else storage.delete(key) },
            Clock.systemUTC(),
        )
}

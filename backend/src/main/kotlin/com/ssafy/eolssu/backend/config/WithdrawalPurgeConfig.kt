package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.application.WithdrawalPurgeService
import com.ssafy.eolssu.backend.feature.auth.domain.WithdrawalPurgeRepository
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.MyBatisWithdrawalPurgeRepository
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.WithdrawalPurgeMapper
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.sheet.domain.SHEET_SOURCE_PREFIX
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import java.time.Clock
import org.springframework.beans.factory.annotation.Qualifier
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.TemporarySheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.domain.TEMP_SHEET_SOURCE_PREFIX
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
    fun withdrawalPurgeService(repository: WithdrawalPurgeRepository, storage: RecordingStorage, sheets: SheetSourceStorage,
        audio: com.ssafy.eolssu.backend.feature.sheet.application.SheetAudios,
        @Qualifier("temporarySheetSourceStorage") temporary: TemporarySheetSourceStorage) =
        // 신규 임시 원본·과거 R2 원본·녹음의 소유 저장소를 키 앞부분으로 고른다.
        WithdrawalPurgeService(
            repository,
            { key -> when {
                key.startsWith(TEMP_SHEET_SOURCE_PREFIX) -> temporary.delete(key)
                key.startsWith(SHEET_SOURCE_PREFIX) -> sheets.delete(key)
                else -> storage.delete(key)
            } },
            Clock.systemUTC(),
            beforeDelete = audio::deleteUser,
        )
}

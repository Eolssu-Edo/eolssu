package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeAnalysisRepository
import com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence.MyBatisPracticeAnalysisRepository
import com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence.PracticeAnalysisMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

// 분석 결과 저장은 쓰기 작업이라 db 프로필에서만 만든다. 분석 실행(AnalyzePracticeSession)과 AI 클라이언트는
// 악보 음표(공통 악보 데이터)와 녹음 서명 URL(S15P31E102-181) 구현이 생기면 함께 연결한다.
@Configuration(proxyBeanMethods = false)
@Profile("db")
class PracticeAnalysisConfig {
    @Bean
    fun practiceAnalysisRepository(mapper: PracticeAnalysisMapper, transactionManager: PlatformTransactionManager): PracticeAnalysisRepository =
        MyBatisPracticeAnalysisRepository(mapper, TransactionTemplate(transactionManager))
}

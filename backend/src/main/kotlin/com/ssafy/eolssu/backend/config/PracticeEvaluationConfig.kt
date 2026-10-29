package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.evaluation.application.GetPracticeEvaluation
import com.ssafy.eolssu.backend.feature.evaluation.domain.PracticeEvaluations
import com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence.EvaluationReadMapper
import com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence.MyBatisPracticeEvaluations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class PracticeEvaluationConfig {
    @Bean
    fun practiceEvaluations(mapper: EvaluationReadMapper): PracticeEvaluations = MyBatisPracticeEvaluations(mapper)

    @Bean
    fun getPracticeEvaluation(evaluations: PracticeEvaluations) = GetPracticeEvaluation(evaluations)
}

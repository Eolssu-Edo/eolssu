package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.evaluation.application.GetEvaluationNotes
import com.ssafy.eolssu.backend.feature.evaluation.application.GetPracticeEvaluation
import com.ssafy.eolssu.backend.feature.evaluation.application.GetWeakSections
import com.ssafy.eolssu.backend.feature.evaluation.domain.EvaluationScoreCells
import com.ssafy.eolssu.backend.feature.evaluation.domain.PracticeEvaluations
import com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence.EvaluationReadMapper
import com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence.MyBatisPracticeEvaluations
import com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence.ScoreJsonNoteCells
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import tools.jackson.databind.json.JsonMapper

@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class PracticeEvaluationConfig {
    @Bean
    fun practiceEvaluations(mapper: EvaluationReadMapper): PracticeEvaluations = MyBatisPracticeEvaluations(mapper)

    @Bean
    fun evaluationScoreCells(mapper: EvaluationReadMapper, json: JsonMapper): EvaluationScoreCells = ScoreJsonNoteCells(mapper, json)

    @Bean
    fun getPracticeEvaluation(evaluations: PracticeEvaluations) = GetPracticeEvaluation(evaluations)

    @Bean
    fun getEvaluationNotes(evaluations: PracticeEvaluations) = GetEvaluationNotes(evaluations)

    @Bean
    fun getWeakSections(evaluations: PracticeEvaluations, scores: EvaluationScoreCells) = GetWeakSections(evaluations, scores)
}

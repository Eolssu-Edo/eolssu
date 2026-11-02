package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.lesson.application.LessonQueries
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonRepository
import com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence.LessonContentJson
import com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence.LessonMapper
import com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence.MyBatisLessonRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import tools.jackson.databind.json.JsonMapper

// 기초부터 배우기 단계·진도(LRN-01·02·03, S15P31E102-344).
@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class LessonConfig {
    @Bean
    fun lessonRepository(mapper: LessonMapper, jsonMapper: JsonMapper): LessonRepository =
        MyBatisLessonRepository(mapper, LessonContentJson(jsonMapper))

    @Bean
    fun lessonQueries(repository: LessonRepository) = LessonQueries(repository)
}

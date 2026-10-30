package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.song.application.GetModelAudio
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudioRepository
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.ModelAudioMapper
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.MyBatisModelAudioRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class ModelAudioConfig {
    @Bean fun modelAudioRepository(mapper: ModelAudioMapper): ModelAudioRepository = MyBatisModelAudioRepository(mapper)
    @Bean fun getModelAudio(repository: ModelAudioRepository) = GetModelAudio(repository)
}

package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.song.application.GetModelAudio
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudioRepository
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.ModelAudioMapper
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.MyBatisModelAudioRepository
import com.ssafy.eolssu.backend.feature.song.infrastructure.storage.ModelPerformanceReader
import com.ssafy.eolssu.backend.feature.song.infrastructure.storage.StoredModelAudioFiles
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudioFiles
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.RecordingStorageProperties
import java.net.URI
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class ModelAudioConfig {
    @Bean fun modelAudioRepository(mapper: ModelAudioMapper): ModelAudioRepository = MyBatisModelAudioRepository(mapper)
    @Bean fun getModelAudio(repository: ModelAudioRepository) = GetModelAudio(repository)
    @Bean fun modelPerformanceReader() = ModelPerformanceReader()
    @Bean fun modelAudioFiles(storage: RecordingStorage, properties: RecordingStorageProperties, reader: ModelPerformanceReader): ModelAudioFiles {
        val endpoint = runCatching { URI(properties.endpoint) }.getOrNull()?.takeIf {
            properties.type == "s3" && properties.isConfigured && it.scheme == "https" && it.host != null &&
                it.rawUserInfo == null && it.rawQuery == null && it.rawFragment == null && it.rawPath in listOf("", "/")
        }
        return StoredModelAudioFiles(endpoint, endpoint?.let { storage::readUrl }, reader::read)
    }
}

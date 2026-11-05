package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.catalog.application.CatalogReferenceQueries
import com.ssafy.eolssu.backend.feature.catalog.application.JangdanPreviewAudio
import com.ssafy.eolssu.backend.feature.catalog.domain.CatalogReferenceRepository
import com.ssafy.eolssu.backend.feature.catalog.infrastructure.persistence.MyBatisCatalogReferenceRepository
import com.ssafy.eolssu.backend.feature.fingering.infrastructure.persistence.FingeringChartMapper
import com.ssafy.eolssu.backend.feature.instrument.infrastructure.persistence.InstrumentMapper
import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanMapper
import com.ssafy.eolssu.backend.feature.notation.infrastructure.persistence.NotationSymbolMapper
import com.ssafy.eolssu.backend.feature.pitch.infrastructure.persistence.PitchMapper
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.RecordingStorageProperties
import java.time.Duration
import java.util.Properties
import org.springframework.core.io.ClassPathResource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import tools.jackson.databind.json.JsonMapper

@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class CatalogReferenceConfig {
    @Bean
    fun catalogReferenceRepository(
        instruments: InstrumentMapper, pitches: PitchMapper, fingerings: FingeringChartMapper,
        jangdans: JangdanMapper, symbols: NotationSymbolMapper, json: JsonMapper,
    ): CatalogReferenceRepository = MyBatisCatalogReferenceRepository(instruments, pitches, fingerings, jangdans, symbols, json)

    @Bean
    fun catalogReferenceQueries(repository: CatalogReferenceRepository) = CatalogReferenceQueries(repository)

    @Bean
    fun jangdanPreviewAudio(storage: RecordingStorage, properties: RecordingStorageProperties): JangdanPreviewAudio {
        val resource = ClassPathResource("catalog/jangdan-preview-sha256.properties")
        val manifest = Properties()
        if (resource.exists()) resource.inputStream.use(manifest::load)
        val hashes = manifest.stringPropertyNames().associate { id ->
            val hash = manifest.getProperty(id)
            require(id.matches(Regex("[1-9][0-9]*")) && hash.matches(Regex("[0-9a-f]{64}")))
            id.toLong() to hash
        }
        return JangdanPreviewAudio(hashes) { key ->
            if (properties.type == "s3" && properties.isConfigured) storage.readUrl(key, Duration.ofMinutes(15)) else null
        }
    }
}

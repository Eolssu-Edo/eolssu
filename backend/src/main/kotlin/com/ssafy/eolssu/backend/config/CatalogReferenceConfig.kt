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
    fun authoringCapabilities(repository: CatalogReferenceRepository, json: JsonMapper) =
        com.ssafy.eolssu.backend.feature.catalog.application.AuthoringCapabilities(repository, json)

    @Bean
    fun catalogReferenceQueries(repository: CatalogReferenceRepository) = CatalogReferenceQueries(repository)

    @Bean
    fun jangdanPreviewAudio(storage: RecordingStorage, properties: RecordingStorageProperties): JangdanPreviewAudio {
        val hashes = loadHashes("jangdan-preview-sha256", Regex("[1-9][0-9]*")).mapKeys { it.key.toLong() }
        val samples = loadHashes("jangdan-practice-sha256", Regex("[a-z0-9_]+"))
        return JangdanPreviewAudio(hashes, samples) { key ->
            if (properties.type == "s3" && properties.isConfigured) storage.readUrl(key, Duration.ofMinutes(15)) else null
        }
    }

    private fun loadHashes(name: String, keyPattern: Regex): Map<String, String> {
        val resource = ClassPathResource("catalog/$name.properties")
        val manifest = Properties()
        if (resource.exists()) resource.inputStream.use(manifest::load)
        return manifest.stringPropertyNames().associateWith { id ->
            val hash = manifest.getProperty(id)
            require(keyPattern.matches(id) && hash.matches(Regex("[0-9a-f]{64}")))
            hash
        }
    }
}

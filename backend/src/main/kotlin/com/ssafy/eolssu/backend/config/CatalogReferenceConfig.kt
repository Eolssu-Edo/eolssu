package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.catalog.application.CatalogReferenceQueries
import com.ssafy.eolssu.backend.feature.catalog.domain.CatalogReferenceRepository
import com.ssafy.eolssu.backend.feature.catalog.infrastructure.persistence.MyBatisCatalogReferenceRepository
import com.ssafy.eolssu.backend.feature.fingering.infrastructure.persistence.FingeringChartMapper
import com.ssafy.eolssu.backend.feature.instrument.infrastructure.persistence.InstrumentMapper
import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanMapper
import com.ssafy.eolssu.backend.feature.notation.infrastructure.persistence.NotationSymbolMapper
import com.ssafy.eolssu.backend.feature.pitch.infrastructure.persistence.PitchMapper
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
}

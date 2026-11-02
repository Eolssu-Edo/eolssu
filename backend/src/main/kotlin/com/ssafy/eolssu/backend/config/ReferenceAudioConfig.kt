package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.catalog.domain.*
import com.ssafy.eolssu.backend.feature.catalog.infrastructure.persistence.MyBatisReferenceAudioRepository
import com.ssafy.eolssu.backend.feature.catalog.infrastructure.persistence.ReferenceAudioMapper
import com.ssafy.eolssu.backend.feature.catalog.infrastructure.storage.ReferenceAudioStorageProperties
import com.ssafy.eolssu.backend.feature.catalog.infrastructure.storage.S3ReferenceAudioReadUrls
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class ReferenceAudioConfig {
    @Bean
    fun referenceAudioRepository(mapper: ReferenceAudioMapper): ReferenceAudioRepository = MyBatisReferenceAudioRepository(mapper)

    @Bean
    fun referenceAudioReadUrls(properties: ReferenceAudioStorageProperties): ReferenceAudioReadUrls =
        if (properties.isConfigured) S3ReferenceAudioReadUrls.create(properties)
        else ReferenceAudioReadUrls { throw ReferenceAudioUnavailable() }
}

package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.song.application.GetSongScore
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.*
import com.ssafy.eolssu.backend.feature.song.infrastructure.catalog.ReviewedJangdanPlayback
import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import tools.jackson.databind.json.JsonMapper

@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class SongScoreConfig {
    @Bean
    fun getSongScore(mapper: SongScoreMapper, json: JsonMapper, jangdans: JangdanMapper) =
        GetSongScore(MyBatisSongScoreRepository(mapper), CommonScoreTimelineDecoder(json), ReviewedJangdanPlayback(json, jangdans::findById))
}

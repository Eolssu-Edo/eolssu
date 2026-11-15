package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.song.application.GetSongScore
import com.ssafy.eolssu.backend.feature.song.domain.SongJangdanPlaybackResolver
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.*
import com.ssafy.eolssu.backend.feature.song.infrastructure.catalog.ScoreGridJangdanPlayback
import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import tools.jackson.databind.json.JsonMapper

@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class SongScoreConfig {
    /** 제공 곡 연습 반주 판정. 곡 조회와 내 악보 복사가 같은 판정을 쓴다. */
    @Bean
    fun songJangdanPlayback(json: JsonMapper, jangdans: JangdanMapper): SongJangdanPlaybackResolver =
        ScoreGridJangdanPlayback(json) { name -> jangdans.listAll().firstOrNull { it.name == name } }

    @Bean
    fun getSongScore(mapper: SongScoreMapper, json: JsonMapper, playback: SongJangdanPlaybackResolver) =
        GetSongScore(MyBatisSongScoreRepository(mapper), CommonScoreTimelineDecoder(json), playback)
}

package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.sheet.application.*
import com.ssafy.eolssu.backend.feature.sheet.domain.*
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.*
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.SheetAudioMapper
import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanMapper
import com.ssafy.eolssu.backend.feature.song.domain.JangdanPlaybackPattern
import java.time.Clock
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.*
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import tools.jackson.databind.json.JsonMapper

@Configuration(proxyBeanMethods=false)
@Profile("db | ops")
class SheetAudioConfig {
    @Bean fun playbackClient(@Value("\${eolssu.sheet-audio.base-url:}") url: String,
        @Value("\${eolssu.sheet-audio.timeout-seconds:100}") seconds: Long,json: JsonMapper): PlaybackClient =
        HttpPlaybackClient(url,Duration.ofSeconds(seconds),json)
    @Bean fun sheetAudioJangdans(jangdans: JangdanMapper,json: JsonMapper)=SheetAudioJangdanResolver { sheet ->
        val row=sheet.jangdanId?.let(jangdans::findById) ?: return@SheetAudioJangdanResolver null
        val pattern=json.readTree(row.pattern)
        SheetAudioJangdan(row.name,row.bpmUnit,JangdanPlaybackPattern(pattern.path("beats").toList().map { it.asString() },pattern.path("group").toList().map { it.intValue() }))
    }
    @Bean fun sheetAudios(mapper: SheetAudioMapper,sheets: UserSheetRepository,scores: SheetScores,jangdans: SheetAudioJangdanResolver,storage: SheetSourceStorage,
        client: PlaybackClient,json: JsonMapper,clock: Clock,manager: PlatformTransactionManager,
        @Value("\${eolssu.sheet-audio.timeout-seconds:100}") seconds: Long)=
        // The worker is sequential, so a RUNNING row older than a whole attempt has no live owner.
        SheetAudios(mapper,sheets,scores,jangdans,storage,client,json,clock,manager,Duration.ofSeconds(seconds).plusMinutes(5))
}

@Component
@Profile("db | ops")
@ConditionalOnProperty(name=["eolssu.scheduling.enabled"],havingValue="true",matchIfMissing=true)
class SheetAudioScheduler(private val audio: SheetAudios) {
    @Volatile private var ready=false
    @EventListener(ApplicationReadyEvent::class) fun recover() { audio.recover(); ready=true }
    @Scheduled(fixedDelay=1000) fun run() { if(ready) audio.runOne() }
}

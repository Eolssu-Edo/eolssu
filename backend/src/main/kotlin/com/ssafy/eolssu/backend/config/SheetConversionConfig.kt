package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.sheet.application.SheetScores
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.*
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionQueue
import java.time.Clock
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.*
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Configuration(proxyBeanMethods=false)
@Profile("db | ops")
class SheetConversionConfig {
    @Bean fun recognitionClient(@Value("\${eolssu.sheet-conversion.omr-base-url:}") url: String,json: JsonMapper): RecognitionClient = HttpRecognitionClient(url,json)
    @Bean fun recognitionResultBuilder(json: JsonMapper,scores: SheetScores)=RecognitionResultBuilder(json,scores)
    @Bean fun conversionWorker(queue: ConversionQueue,@Qualifier("temporarySheetSourceStorage") storage: TemporarySheetSourceStorage,client: RecognitionClient,builder: RecognitionResultBuilder,
        clock: Clock,json: JsonMapper)=ConversionWorker(queue,storage,client,builder,clock,json)
}

/** 한 backend 인스턴스에서 실행한다. 잔여 원본과 QUEUED·RUNNING을 정리한 다음 큐를 읽는다. */
@Component
@Profile("db | ops")
@ConditionalOnProperty(name=["eolssu.scheduling.enabled"],havingValue="true",matchIfMissing=true)
class SheetConversionScheduler(private val queue: ConversionQueue,private val worker: ConversionWorker,private val clock: Clock,
    @Qualifier("temporarySheetSourceStorage") private val storage: TemporarySheetSourceStorage) {
    @Volatile private var ready=false
    @EventListener(ApplicationReadyEvent::class)
    fun recover() { storage.reset(); queue.recover(clock.instant()); worker.cleanup(); ready=true }
    @Scheduled(fixedDelay=1000)
    fun run() { if(ready) worker.runOne() }
    @Scheduled(fixedDelay=1000)
    fun cleanup() { if(ready) worker.cleanup() }
    /** DB가 막혀도 원본 기한을 지키려고 cleanup과 따로 돈다. */
    @Scheduled(fixedDelay=1000)
    fun purge() { storage.purgeExpired() }
}

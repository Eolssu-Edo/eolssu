package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorageException
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionQueue
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionJob
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionFile
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** 네트워크 호출 중 DB 잠금을 잡지 않는다. 삭제되거나 다른 시도가 된 결과는 저장하지 않고 생성 객체를 정리한다. */
class ConversionWorker(
    private val queue: ConversionQueue,private val storage: SheetSourceStorage,private val client: RecognitionClient,
    private val builder: RecognitionResultBuilder,private val clock: Clock,private val json: JsonMapper,
) {
    fun runOne() {
        if(!client.available || !storage.available) return
        val job=queue.claim(now()) ?: return
        var files=emptyList<ConversionFile>()
        try {
            files=queue.files(job.sheetId)
            if(files.isEmpty()) throw RecognitionFailure("SERVER_ERROR")
            val request=json.createObjectNode().put("conversionId","sheet-${job.sheetId}-${job.attempt}")
                .put("notation",job.notation).put("instrument",job.instrument).put("jangdan",job.jangdan)
            if(job.hwangPitch==null) request.putNull("hwangPitch") else request.put("hwangPitch",midi(job.hwangPitch))
            val urls=request.putArray("files")
            files.forEach { urls.addObject().put("url",sourceUrl(it)).put("contentType",it.contentType) }
            val response=client.recognize(request)
            if(response.path("status").asString()=="FAILED") {
                val cause=when(response.path("failReason").asString()) {
                    "NO_GRID_FOUND","NO_STAFF_FOUND" -> "NO_SCORE_FOUND"
                    // 인식기는 만료로 받지 못한 원본도 이렇게 답한다.
                    "UNSUPPORTED_FILE" -> if(files.any { storage.expired(it.objectKey) }) "TIMEOUT" else "UNSUPPORTED_FILE"
                    "TIMEOUT" -> "TIMEOUT"
                    "ENGINE_ERROR" -> "ENGINE_ERROR"
                    else -> "SERVER_ERROR"
                }
                throw RecognitionFailure(cause)
            }
            if(!queue.building(job)) return
            val name=response.path("score").path("music").path("jangdan").asString()
            val result=builder.build(job,response,queue.jangdan(name)?.defaultBpm ?: 60)
            queue.complete(job,result,now())
        } catch(e: Exception) {
            val cause=(e as? RecognitionFailure)?.reason ?: "SERVER_ERROR"
            queue.fail(job.sheetId,job.attempt,cause,now())
            log.warn("Sheet conversion {} attempt {} failed ({})",job.sheetId,job.attempt,cause)
        } finally {
            files.forEach { file ->
                try { storage.delete(file.objectKey); queue.forgetSource(file.objectKey) }
                catch(_: Exception) { log.warn("Temporary sheet source cleanup will be retried") }
            }
        }
    }
    /** 파일 기한은 복사본을 만든 때부터여서 DB 기한보다 먼저 끝날 수 있다. 그 사이의 작업도 TIMEOUT이다. */
    private fun sourceUrl(file: ConversionFile): String =
        try { storage.readUrl(file.objectKey,Duration.ofMinutes(15)) }
        catch(e: SheetSourceStorageException) { throw if(storage.expired(file.objectKey)) RecognitionFailure("TIMEOUT") else e }
    fun cleanup() {
        (storage as? TemporarySheetSourceStorage)?.purgeExpired()
        queue.expire(now())
        queue.finishedSources().forEach { file ->
            try { storage.delete(file.objectKey); queue.forgetSource(file.objectKey) }
            catch(_: Exception) { log.warn("Temporary sheet source cleanup will be retried") }
        }
    }
    private fun now()=clock.instant().truncatedTo(ChronoUnit.MICROS)
    private fun midi(note: String): Int {
        val semitone=when(note[0]) { 'C'->0;'D'->2;'E'->4;'F'->5;'G'->7;'A'->9;else->11 }
        val accidental=if(note.length==3) when(note[1]){'#'->1;else->-1} else 0
        return (note.last().digitToInt()+1)*12+semitone+accidental
    }
    companion object { private val log=LoggerFactory.getLogger(ConversionWorker::class.java) }
}

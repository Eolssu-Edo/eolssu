package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionQueue
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionJob
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionFile
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

data class StoredConversionPage(val pageIdx: Int,val objectKey: String,val width: Int,val height: Int)
fun interface ConversionPageRenderer {
    fun render(job: ConversionJob,files: List<ConversionFile>,response: JsonNode): List<StoredConversionPage>
}

/** 네트워크 호출 중 DB 잠금을 잡지 않는다. 삭제되거나 다른 시도가 된 결과는 저장하지 않고 생성 객체를 정리한다. */
class ConversionWorker(
    private val queue: ConversionQueue,private val storage: SheetSourceStorage,private val client: RecognitionClient,
    private val builder: RecognitionResultBuilder,private val renderer: ConversionPageRenderer,private val clock: Clock,private val json: JsonMapper,
) {
    fun runOne() {
        if(!client.available || !storage.available) return
        val job=queue.claim(now()) ?: return
        var pages=emptyList<StoredConversionPage>()
        try {
            val files=queue.files(job.sheetId)
            val request=json.createObjectNode().put("conversionId","sheet-${job.sheetId}-${job.attempt}")
                .put("notation",job.notation).put("instrument",job.instrument).put("jangdan",job.jangdan)
            if(job.hwangPitch==null) request.putNull("hwangPitch") else request.put("hwangPitch",midi(job.hwangPitch))
            val urls=request.putArray("files")
            files.forEach { urls.addObject().put("url",storage.readUrl(it.objectKey,Duration.ofMinutes(15))).put("contentType",it.contentType) }
            val response=client.recognize(request)
            if(response.path("status").asString()=="FAILED") {
                val cause=when(response.path("failReason").asString()) {
                    "NO_GRID_FOUND","NO_STAFF_FOUND" -> "NO_SCORE_FOUND"
                    "UNSUPPORTED_FILE" -> "UNSUPPORTED_FILE"
                    "TIMEOUT" -> "TIMEOUT"
                    "ENGINE_ERROR" -> "ENGINE_ERROR"
                    else -> "SERVER_ERROR"
                }
                throw RecognitionFailure(cause)
            }
            if(!queue.building(job)) return
            val name=response.path("score").path("music").path("jangdan").asString()
            val result=builder.build(job,response,queue.jangdan(name)?.defaultBpm ?: 60)
            pages=renderer.render(job,files,response)
            if(!queue.complete(job,result,pages,now())) discard(pages)
        } catch(e: Exception) {
            discard(pages)
            val cause=(e as? RecognitionFailure)?.reason ?: "SERVER_ERROR"
            queue.fail(job.sheetId,job.attempt,cause,now())
            log.warn("Sheet conversion {} attempt {} failed ({})",job.sheetId,job.attempt,cause)
        }
    }
    private fun discard(pages: List<StoredConversionPage>) {
        pages.forEach { try { storage.delete(it.objectKey) } catch(_: Exception) { log.warn("Sheet conversion page cleanup failed") } }
    }
    private fun now()=clock.instant().truncatedTo(ChronoUnit.MICROS)
    private fun midi(note: String): Int {
        val semitone=when(note[0]) { 'C'->0;'D'->2;'E'->4;'F'->5;'G'->7;'A'->9;else->11 }
        val accidental=if(note.length==3) when(note[1]){'#'->1;else->-1} else 0
        return (note.last().digitToInt()+1)*12+semitone+accidental
    }
    companion object { private val log=LoggerFactory.getLogger(ConversionWorker::class.java) }
}

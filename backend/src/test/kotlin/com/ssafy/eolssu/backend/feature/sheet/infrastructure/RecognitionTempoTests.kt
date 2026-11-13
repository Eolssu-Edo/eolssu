package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.application.SheetExpressions
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionJob
import com.ssafy.eolssu.backend.feature.song.domain.ScorePitch
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.CommonScoreTimelineDecoder
import java.time.LocalDateTime
import kotlin.test.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

class RecognitionTempoTests {
    private val json = JsonMapper.builder().build()
    private val binding = SheetExpressions(json)
    private val job = ConversionJob(882,1,"빠르기 시험","STAFF","DANSO",null,null,null,"RUNNING",1,null,0,LocalDateTime.now())
    private fun response(): ObjectNode {
        val score = json.readTree(checkNotNull(javaClass.getResource("/tempo-authoring/four-notes.json")).readText())
        val digest = binding.hash(json.writeValueAsString(score))
        return json.createObjectNode().apply {
            set("score",score); putObject("jgb").put("v",1); put("bpm",80)
            set("expressions",json.readTree("""{"schema":"common-score-expressions/v1","sourceSha256":"$digest","placements":[],"performancePlan":{"schema":"danso-performance/v1","input_sha256":"$digest","tempos":[{"gak":1,"cell":1,"start_bpm":80,"bpm":160,"end_gak":2,"end_cell":1}],"repeats":[]}}"""))
        }
    }
    private fun builder(response: ObjectNode, registered: Boolean = true): RecognitionResultBuilder {
        val pitches = response.path("score").path("events").filter { it.path("kind").asString()=="NOTE" }.mapIndexed { i,e ->
            ScorePitch((i+1).toLong(),e.path("yul").asString(),if (registered) e.path("octave").asInt() else -2,i,"試","시험")
        }.distinctBy { it.yul to it.octave }
        return RecognitionResultBuilder(json,CommonSheetScores(json,CommonScoreTimelineDecoder(json)) { pitches })
    }
    @Test fun `recognized BPM replaces catalog default and companion follows server score identity`() {
        val response=response()
        val result=builder(response).build(job,response,160)
        assertEquals(80,result.bpm)
        val score=assertNotNull(result.scoreJson)
        val expressions=json.readTree(result.expressionsJson)
        assertEquals(binding.hash(score),expressions.path("sourceSha256").asString())
        assertEquals(binding.hash(score),expressions.path("performancePlan").path("input_sha256").asString())
        assertEquals(80.0,expressions.path("performancePlan").path("tempos")[0].path("start_bpm").asDouble())
        assertEquals(160.0,expressions.path("performancePlan").path("tempos")[0].path("bpm").asDouble())
    }
    @Test fun `requested BPM scales ramp start and target once and unreadable pitch draft keeps plan`() {
        val response=response()
        val result=builder(response,false).build(job.copy(bpm=120),response,160)
        assertEquals(120,result.bpm)
        val draft=json.readTree(assertNotNull(result.draftJson))
        val cue=draft.path("expressions").path("performancePlan").path("tempos")[0]
        assertEquals(120.0,cue.path("start_bpm").asDouble())
        assertEquals(240.0,cue.path("bpm").asDouble())
        assertEquals(binding.hash(draft.path("score").toString()),draft.path("expressions").path("sourceSha256").asString())
    }
    @Test fun `absent numeric recognition keeps prior default and broken source binding is rejected`() {
        val response=response()
        response.remove("bpm"); response.remove("expressions")
        assertEquals(160,builder(response).build(job,response,160).bpm)
        val broken=response()
        (broken.path("expressions") as ObjectNode).put("sourceSha256","bad")
        assertFailsWith<RecognitionFailure> { builder(broken).build(job,broken,160) }
    }
}

package com.ssafy.eolssu.backend.feature.sheet

import com.ssafy.eolssu.backend.feature.sheet.infrastructure.RecognitionResultBuilder
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionJob
import com.ssafy.eolssu.backend.feature.sheet.application.SheetScores
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.time.LocalDateTime

@Tag("database") @ActiveProfiles("db") @SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class ConversionResultDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var scores: SheetScores
    private val json=JsonMapper.builder().build()
    private val job=ConversionJob(712,1,"가져온 악보","STAFF","DANSO",null,null,null,"RUNNING",1,null,0,LocalDateTime.now())
    @Test fun `valid imported score preserves provenance and counts unique cells excluding notices`() {
        val score=json.readTree(jdbc.queryForObject("SELECT score_json FROM songs WHERE id=1",String::class.java))
        val response=json.createObjectNode().set("score",score)
        response.putObject("jgb").put("v",1)
        response.putArray("pages").addObject().put("index",0).put("width",200).put("height",100)
        val issues=response.putArray("issues")
        val cellId=score.path("columns").get(0).path("cells").get(0).path("id").asString()
        repeat(2) { issues.addObject().put("kind","LOW_CONFIDENCE").put("cellId",cellId).put("page",0).putArray("bbox").add(20).add(10).add(40).add(30) }
        response.putArray("notices").addObject().put("kind","TRANSPOSED").putObject("detail").put("semitones",8)
        val result=RecognitionResultBuilder(json,scores).build(job,response)
        assertEquals(1,result.pendingCellCount)
        val stored=json.readTree(assertNotNull(result.scoreJson))
        assertEquals("sheet-712",stored.path("score").path("sourceId").asString())
        assertEquals("inferred",stored.path("provenance").path("review").asString())
        val details=json.readTree(result.resultJson)
        assertEquals(2,details.path("issues").size())
        assertEquals(0.1,details.path("issues").get(0).path("bbox").path("x").doubleValue())
        assertEquals(1,details.path("notices").size())
    }
    @Test fun `unreadable cells are stored as a draft that can be opened`() {
        val score=json.readTree(jdbc.queryForObject("SELECT score_json FROM songs WHERE id=1",String::class.java)) as ObjectNode
        val cell=score.path("columns").get(0).path("cells").get(0) as ObjectNode
        cell.putArray("units")
        val response=json.createObjectNode().set("score",score)
        response.putObject("jgb").put("v",1); response.putArray("issues"); response.putArray("notices")
        val result=RecognitionResultBuilder(json,scores).build(job,response)
        assertEquals(null,result.scoreJson)
        assertNotNull(result.draftJson)
        assertEquals("sheet-712",json.readTree(result.draftJson).path("score").path("score").path("sourceId").asString())
    }
    companion object {
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("spring.datasource.url") { System.getenv("TEST_DB_URL") }
            registry.add("spring.datasource.username") { System.getenv("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { System.getenv("TEST_DB_PASSWORD") }
        }
    }
}

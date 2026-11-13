package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import com.ssafy.eolssu.data.song.CommonScoreData
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

/** Injected into Android host tests by meter-validation.gradle; no product source changes. */
class MeterImportRuntimeTest {
    @Test fun importedScoresPreserveEditorRoundTripAndUseActualCandidatePolicy() {
        val root = File(requireNotNull(System.getenv("METER_AUDIT_ROOT")))
        val catalog = Json.parseToJsonElement(File(root,"catalog-list.json").readText()).jsonObject["result"]!!.jsonObject["items"]!!.jsonArray.map {
            val x=it.jsonObject
            CatalogJangdanSummary(x["jangdanId"]!!.jsonPrimitive.content,x["name"]!!.jsonPrimitive.content,
                x["defaultBpm"]!!.jsonPrimitive.int,x["bpmUnit"]!!.jsonPrimitive.content,x["beatCount"]!!.jsonPrimitive.int,
                x["groupCount"]!!.jsonPrimitive.int,x["patternCellEighths"]!!.jsonPrimitive.int)
        }
        val files = File(root,"contracts").listFiles()!!.filter { it.name.endsWith(".score.json") } +
            File(root,"actual").walkTopDown().filter { it.name=="engine.json" }.toList()
        val audit=Json.parseToJsonElement(File(root,"contracts/audit.json").readText()).jsonObject
        val expectedRows=(audit["fixedMusicXml"]!!.jsonArray + audit["actualOmr"]!!.jsonArray).associate {
            val row=it.jsonObject
            row["id"]!!.jsonPrimitive.content to row["candidates"]!!.jsonArray.map { id -> id.jsonPrimitive.content }
        }
        assertTrue(files.isNotEmpty())
        val report=buildJsonArray {
            for(file in files) {
                val raw=if(file.name=="engine.json") Json.parseToJsonElement(file.readText()).jsonObject["score"]!!.toString() else file.readText()
                val score=CommonScoreData.decode(raw)
                score.toScore()
                val draft=score.editingDraft(score.score.title,100,null)
                val saved=draft.toCommonScore()
                assertEquals(score.columns,saved.columns,file.path)
                assertEquals(score.events,saved.events,file.path)
                assertEquals(score.music,saved.music,file.path)
                val reloaded=CommonScoreData.decode(Json.encodeToString(saved))
                reloaded.toScore()
                assertEquals(saved,reloaded)
                val before=catalog.filter { draft.isCompatibleWith(it) }.map { it.jangdanId }
                val caseId=if(file.name=="engine.json") file.parentFile!!.name else file.nameWithoutExtension
                val expected=expectedRows.getValue(caseId.removeSuffix(".score"))
                assertEquals(expected,before,caseId)
                val after=catalog.filter { reloaded.editingDraft(reloaded.score.title,100,null).isCompatibleWith(it) }.map { it.jangdanId }
                assertEquals(before,after)
                addJsonObject { put("case",caseId)
                    put("editorRoundTrip",true);putJsonArray("candidates") { before.forEach { add(it) } }
                    putJsonArray("selectable") { catalog.filter { draft.canSelectJangdan(it) }.forEach { add(it.jangdanId) } } }
            }
        }
        File(root,"frontend-runtime.json").writeText(report.toString())
        println("METER_FRONTEND_CASES=${files.size}")
    }
}

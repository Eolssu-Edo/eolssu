package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.*
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.CommonSheetScores
import com.ssafy.eolssu.backend.feature.song.domain.ScorePitch
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.CommonScoreTimelineDecoder
import java.io.File
import java.time.Clock
import java.time.Instant
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.*

/** Actual application/decoder; disposable repository adapter, not a MySQL integration test. */
class MeterImportBackendTest {
    @Test fun draftSaveReadAndConfirmationKeepImportedTimeline() {
        val root=File(requireNotNull(System.getenv("METER_AUDIT_ROOT")))
        val json=JsonMapper.builder().build()
        val rows=json.readTree(File(root,"catalog-list.json").readText()).path("result").path("items").toList()
        val patterns=json.readTree(File(root.parentFile.parentFile.parentFile,"data/reference/jangdans.json")).path("rows").toList()
        val catalog=rows.map { r ->
            val pattern=patterns.first { it.path("name").asString()==r.path("name").asString() }.path("pattern")
            SheetJangdan(r.path("jangdanId").asString().toLong(),r.path("bpmUnit").asString(),r.path("name").asString(),pattern.toString())
        }
        val files=File(root,"contracts").listFiles()!!.filter { it.name.endsWith(".score.json") } +
            File(root,"actual").walkTopDown().filter { it.name=="engine.json" }.toList()
        val report=json.createArrayNode()
        for(file in files) {
            val source=if(file.name=="engine.json") json.readTree(file.readText()).path("score") else json.readTree(file.readText())
            val raw=source.toString()
            val events=source.path("events").toList()
            val pitches=events.filter { it.path("kind").asString()=="NOTE" }.map { it.path("yul").asString() to it.path("octave").intValue() }
                .distinct().mapIndexed { i,(y,o) -> ScorePitch(i+1L,y,o,0,"黃",y) }
            val scores=CommonSheetScores(json,CommonScoreTimelineDecoder(json)) { pitches }
            val unit=source.path("music").path("bpmUnit").asString()
            val name=source.path("music").path("jangdan").asString()
            val selected=catalog.singleOrNull { it.name==name }
            assertTrue(name.isEmpty() || selected != null, "Unknown catalog name: $name")
            val bpm=rows.singleOrNull { it.path("name").asString()==name }?.path("defaultBpm")?.intValue() ?: 60
            val count=events.count { it.path("kind").asString()=="NOTE" }
            val base=UserSheet(1,1,"meter", "STAFF","MANUAL",SheetStatus.NEEDS_REVIEW,selected?.id,name,bpm,unit,
                source.path("columns").size(),1,raw,count,0,Instant.EPOCH)
            val repo=MemorySheets(base,catalog)
            val service=EditUserSheet(repo,scores,Clock.systemUTC())
            assertIs<SaveSheetDraftResult.Saved>(service.saveDraft(SaveSheetDraftCommand(1,1,1,0,"meter",selected?.id,bpm,raw)))
            val draft=json.readTree(assertNotNull(repo.find(1,1)?.draft).json).path("score")
            assertEquals(source.path("music"),draft.path("music"))
            assertEquals(source.path("events"),draft.path("events"))
            assertEquals(source.path("columns"),draft.path("columns"))
            val validation=SheetJangdanValidation(json)
            val candidates=catalog.filter { option ->
                val changed=json.readTree(raw) as tools.jackson.databind.node.ObjectNode
                (changed.path("music") as tools.jackson.databind.node.ObjectNode).put("jangdan",option.name)
                validation.valid(changed.toString(),option)
            }.map { it.id }
            val saved=service.save(SaveSheetScoreCommand(1,1,1,null,selected?.id,bpm,raw,emptyList()))
            if(selected == null || selected.id in candidates) {
                assertIs<SaveSheetScoreResult.Saved>(saved)
                val stored=assertNotNull(repo.find(1,1)?.scoreJson)
                assertEquals(source.path("events"),json.readTree(stored).path("events"))
                assertEquals(source.path("columns"),json.readTree(stored).path("columns"))
                val timeline=assertNotNull(scores.timeline(stored,2,count,bpm,unit))
                assertEquals(events.size,timeline.size)
                timeline.zip(events).forEach { (e,n) ->
                    assertEquals(n.path("id").asString(),e.eventId)
                    assertEquals(n.path("startTick").intValue(),e.startTick)
                    assertEquals(n.path("durationTick").intValue(),e.durationTick)
                    assertEquals(n.path("startTick").longValue().toDouble()/source.path("music").path("ticksPerBeat").intValue(),e.onsetBeat,1e-10)
                }
            } else assertEquals(SaveSheetScoreResult.InvalidRequest,saved)
            report.addObject().put("case",if(file.name=="engine.json") file.parentFile.name else file.nameWithoutExtension)
                .put("draftRoundTrip",true).put("confirmation",if(saved is SaveSheetScoreResult.Saved) "SAVED" else "REJECTED_INCOMPATIBLE_JANGDAN")
                .put("accompaniment",if(selected == null) "none" else "catalog")
                .put("candidates",candidates.joinToString(","))
        }
        File(root,"backend-runtime.json").writeText(report.toString())
        println("METER_BACKEND_CASES=${files.size}; repository=memory; pitchCatalog=test-stand-in")
    }

    private class MemorySheets(var state: UserSheet,val catalog:List<SheetJangdan>): UserSheetRepository {
        override fun findJangdan(jangdanId:Long)=catalog.find { it.id==jangdanId }
        override fun find(sheetId:Long,userId:Long)=state.takeIf { it.id==sheetId && it.userId==userId }
        override fun page(userId:Long,kind:String?,page:Int,size:Int)=SheetPage(listOf(state),1)
        override fun updateDraft(update:SheetDraftUpdate):Boolean {
            if(state.scoreVersion!=update.baseScoreVersion || state.draftRevision!=update.draftRevision)return false
            state=state.copy(draft=SheetDraft(update.draftJson,update.baseScoreVersion,update.savedAt),draftRevision=update.draftRevision+1);return true
        }
        override fun updateScore(update:SheetScoreUpdate):Boolean {
            if(state.scoreVersion!=update.baseScoreVersion)return false
            state=state.copy(scoreJson=update.scoreJson,scoreVersion=state.scoreVersion+1,noteCount=update.noteCount,
                bpm=update.bpm,bpmUnit=update.bpmUnit,draft=null,draftRevision=0);return true
        }
        override fun delete(sheetId:Long,userId:Long)=false
    }
}

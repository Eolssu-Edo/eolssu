package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.data.song.*
import com.ssafy.eolssu.testing.CatalogViewModelTest
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class SheetAdaptationPersistenceTest : CatalogViewModelTest() {
    @Test fun undoAfterADraftSavePersistsZeroThroughTheRealWireAndDraftFirstServerMerge() = runTest {
        verifySavedUndo(draft = true)
    }

    @Test fun undoAfterADraftSavePersistsZeroThroughAConfirmedSaveAndFreshGet() = runTest {
        verifySavedUndo(draft = false)
    }

    @Test fun anUnacknowledgedUndoSaveReconcilesWithoutRepeatingTheWrite() = runTest {
        verifySavedUndo(draft = true, loseAcknowledgement = true)
    }

    private suspend fun TestScope.verifySavedUndo(draft: Boolean, loseAcknowledgement: Boolean = false) {
        val server = MergingServer(StandardTestDispatcher(testScheduler))
        val client = createHttpClient(server.engine, ServerAddress("http://127.0.0.1:18929"))
        val repository = ApiSheetDocumentRepository(client)
        fun open() = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(),
            documents = repository, pitchCatalog = ReadinessCatalog(),
            loadAudioInstruments = { listOf(CatalogInstrument("1", "DANSO", "단소")) }))
        try {
            val model = open()
            runCurrent()
            val original = model.uiState.value.draft
            assertNull(original.authoring?.get("adaptation"))
            val originalScore = original.toCommonScore()
            val originalSource = original.authoring?.get("source")
            model.selectAdaptationTranspose(3)
            model.requestApplyAdaptation(); model.applyAdaptation()
            model.saveDraft(); runCurrent()
            assertEquals(SheetSaveStatus.DraftSaved, model.uiState.value.saveStatus)
            assertEquals(JsonPrimitive(3), server.writes.single().getValue("authoring").jsonObject
                .getValue("adaptation").jsonObject["transpose"])

            model.undo(); runCurrent()
            assertEquals(original, model.uiState.value.draft)
            server.loseNextDraftAcknowledgement = loseAcknowledgement
            if (draft) model.saveDraft() else model.requestSave()
            runCurrent()
            if (loseAcknowledgement) {
                assertEquals(SheetSaveStatus.DraftFailed, model.uiState.value.saveStatus)
                model.retryDraftSave(); runCurrent()
            }
            assertEquals(if (draft) SheetSaveStatus.DraftSaved else SheetSaveStatus.Confirmed, model.uiState.value.saveStatus)
            if (draft) { model.saveDraft(); runCurrent() }
            assertEquals(2, server.writes.size, "확인된 같은 초안이나 응답 유실 복구는 중복 PUT을 보내지 않는다")
            var closed = false
            model.closeEditor { closed = true }; runCurrent()
            assertTrue(closed, "전송 메타데이터 정규화가 저장한 편집본을 미저장 상태로 만들지 않는다")
            assertFalse(model.uiState.value.closeWarning)

            val reopened = open()
            runCurrent()
            assertEquals(0, reopened.uiState.value.adaptation?.selectedTranspose, "서버 병합 후에도 되돌린 원조를 유지한다")
            assertEquals(originalScore, reopened.uiState.value.draft.toCommonScore())
            assertEquals(originalSource, reopened.uiState.value.draft.authoring?.get("source"))
            val sent = server.writes.last().getValue("authoring").jsonObject
            assertEquals(buildJsonObject {
                put("instrument", "DANSO"); put("transpose", 0); put("changes", JsonArray(emptyList()))
            }, sent["adaptation"], "null이나 필드 생략은 서버의 이전 +3을 지우지 못한다")
            assertEquals(setOf("scoreSha256"), sent.getValue("source").jsonObject.keys)
            assertEquals(original.authoring, model.uiState.value.draft.authoring, "전송 정규화로 undo 스냅샷을 바꾸지 않는다")
            reopened.requestApplyAdaptation(); reopened.applyAdaptation()
            assertEquals(originalScore, reopened.uiState.value.draft.toCommonScore(), "다시 적용해도 되돌린 +3이 살아나지 않는다")
        } finally { client.close() }
    }

    /** Mirrors SheetAuthoring.merge: stored draft wins, missing/null adaptation retains its baseline value. */
    private class MergingServer(dispatcher: CoroutineDispatcher) {
        private val score = smallScoreData().let { it.copy(music = it.music.copy(jangdan = "", hwangHz = 311.1269837220809)) }
        private val source = buildJsonObject {
            put("score", Json.encodeToJsonElement(score)); put("scoreSha256", "0".repeat(64))
            put("notes", buildJsonArray {
                score.events.filter { it.kind == "NOTE" }.zip(listOf(63, 80)).forEach { (event, midi) ->
                    add(buildJsonObject {
                        put("eventId", event.id); put("cellId", event.cellId); put("midi", midi)
                        put("startTick", event.startTick); put("durationTick", event.durationTick)
                    })
                }
            })
        }
        private var confirmedScore = Json.encodeToJsonElement(score)
        private var confirmedAuthoring = buildJsonObject { put("schema", "sheet-authoring/v1"); put("source", source) }
        private var storedDraft: JsonObject? = null
        private var version = 1
        var loseNextDraftAcknowledgement = false
        val writes = mutableListOf<JsonObject>()
        val engine = MockEngine(MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request ->
                val result = if (request.method == HttpMethod.Get) {
                    buildJsonObject {
                        put("sheetId", "31"); put("title", "병합 검사"); put("bpm", 60); put("scoreVersion", version.toString())
                        put("reviewStatus", "EVALUABLE"); put("conversionStatus", "DONE")
                        put("scoreData", confirmedScore); put("authoring", confirmedAuthoring)
                        storedDraft?.let { put("draft", it) }
                    }
                } else {
                    assertEquals(HttpMethod.Put, request.method)
                    val incoming = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    writes += incoming
                    assertEquals(version.toString(), incoming.getValue("baseScoreVersion").jsonPrimitive.content)
                    val metadata = incoming["authoring"] as? JsonObject
                    val baseline = storedDraft?.get("authoring") as? JsonObject ?: confirmedAuthoring
                    metadata?.get("source")?.jsonObject?.get("scoreSha256")?.let {
                        assertEquals(source["scoreSha256"], it)
                    }
                    val merged = JsonObject(baseline + buildMap {
                        metadata?.get("adaptation")?.takeUnless { it == JsonNull }?.let { put("adaptation", it) }
                    })
                    if (request.url.encodedPath.endsWith("/draft")) {
                        val revision = incoming.getValue("draftRevision").jsonPrimitive.int + 1
                        storedDraft = JsonObject(incoming + mapOf("draftRevision" to JsonPrimitive(revision), "authoring" to merged))
                        if (loseNextDraftAcknowledgement) {
                            loseNextDraftAcknowledgement = false
                            return@addHandler respond("{}", HttpStatusCode.ServiceUnavailable,
                                headersOf(HttpHeaders.ContentType, "application/json"))
                        }
                        buildJsonObject { put("sheetId", "31"); put("draftRevision", revision) }
                    } else {
                        confirmedScore = incoming.getValue("score"); confirmedAuthoring = merged
                        storedDraft = null; version++
                        buildJsonObject {
                            put("sheetId", "31"); put("scoreVersion", version.toString()); put("reviewStatus", "EVALUABLE")
                        }
                    }
                }
                respond(buildJsonObject { put("isSuccess", true); put("code", 200); put("result", result) }.toString(),
                    HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        })
    }
}

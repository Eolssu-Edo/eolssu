package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.*
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class SheetAdaptationRestoreTest : CatalogViewModelTest() {
    @Test fun draftSaveCloseAndReentryRestoresTheAppliedOffsetWithoutChangingTheScore() = runTest {
        val repository = MemoryDocuments()
        val first = open(repository)
        runCurrent()
        apply(first, 3)
        val applied = first.uiState.value.draft.toCommonScore()
        var closed = false
        first.saveDraftAndClose { closed = true }
        runCurrent()
        assertTrue(closed)
        assertEquals(1, repository.writes)

        val reopened = open(repository)
        runCurrent()
        assertEquals(3, reopened.uiState.value.adaptation?.selectedTranspose)
        assertEquals(applied, reopened.uiState.value.draft.toCommonScore())
        assertEquals(1, repository.writes, "재진입은 쓰기나 추가 이조를 하지 않는다")
        apply(reopened, 3)
        assertEquals(applied, reopened.uiState.value.draft.toCommonScore(), "같은 offset은 원본에서 다시 계산한다")
        assertEquals(repository.source, reopened.uiState.value.draft.authoring?.get("source"))
    }

    @Test fun confirmedSaveReentryRestoresNegativeOffsetAndAnExplicitZeroReset() = runTest {
        val repository = MemoryDocuments()
        val first = open(repository)
        runCurrent()
        apply(first, -2)
        val transposed = first.uiState.value.draft.toCommonScore()
        assertEquals(listOf("무" to -1, "협" to 1), transposed.events.filter { it.kind == "NOTE" }.map { it.yul to it.octave })
        runCurrent()
        assertTrue(first.uiState.value.completionValidation.canComplete, first.uiState.value.completionValidation.toString())
        first.requestSave(); runCurrent()
        assertEquals(SheetSaveStatus.Confirmed, first.uiState.value.saveStatus)

        val reopened = open(repository)
        runCurrent()
        assertEquals(-2, reopened.uiState.value.adaptation?.selectedTranspose)
        assertEquals(transposed, reopened.uiState.value.draft.toCommonScore())
        apply(reopened, 0)
        val reset = reopened.uiState.value.draft.toCommonScore()
        assertEquals(listOf("황" to 0, "중" to 1), reset.events.filter { it.kind == "NOTE" }.map { it.yul to it.octave })
        runCurrent()
        reopened.requestSave(); runCurrent()
        assertEquals(SheetSaveStatus.Confirmed, reopened.uiState.value.saveStatus)

        val zero = open(repository)
        runCurrent()
        assertEquals(0, zero.uiState.value.adaptation?.selectedTranspose)
        assertEquals(reset, zero.uiState.value.draft.toCommonScore())
        assertEquals(2, repository.writes)
        assertEquals(repository.source, zero.uiState.value.draft.authoring?.get("source"))
    }

    @Test fun rebuildingThePanelAndUndoRedoUseTheDraftsAppliedOffset() = runTest {
        val model = open(MemoryDocuments())
        runCurrent()
        apply(model, 4)
        model.updateTitle("제목만 수정")
        model.openAdaptation()
        assertEquals(4, model.uiState.value.adaptation?.selectedTranspose)
        model.selectAdaptationTranspose(-5) // 미리보기만 바꾼 값은 적용 이력이 아니다.
        model.closeAdaptation(); model.openAdaptation()
        assertEquals(4, model.uiState.value.adaptation?.selectedTranspose)
        model.undo() // 제목 수정 전.
        model.undo() // 이조 적용 전.
        model.openAdaptation()
        assertEquals(0, model.uiState.value.adaptation?.selectedTranspose)
        model.redo()
        model.openAdaptation()
        assertEquals(4, model.uiState.value.adaptation?.selectedTranspose)
    }

    @Test fun aNewDraftWithItsOwnOffsetTakesPrecedenceOverTheConfirmedScore() = runTest {
        val repository = MemoryDocuments()
        val first = open(repository)
        runCurrent()
        apply(first, 2)
        runCurrent()
        first.requestSave(); runCurrent()
        assertEquals(SheetSaveStatus.Confirmed, first.uiState.value.saveStatus)
        apply(first, -3)
        first.saveDraft(); runCurrent()
        val draft = first.uiState.value.draft.toCommonScore()

        val reopened = open(repository)
        runCurrent()
        assertEquals(-3, reopened.uiState.value.adaptation?.selectedTranspose)
        assertEquals(draft, reopened.uiState.value.draft.toCommonScore())
        apply(reopened, -3)
        assertEquals(draft, reopened.uiState.value.draft.toCommonScore())
    }

    @Test fun offsetsStayWithTheirScoreAndAccountAndOldCallbacksCannotWriteAfterAccountChange() = runTest {
        val repository = MemoryDocuments()
        val member = SignedInMember(FakeKeyValueStore().apply { putString("auth.member_id", "1") })
        val first = open(repository, member = member)
        runCurrent()
        apply(first, 3)
        first.saveDraft(); runCurrent()
        val restored = open(repository, member = member)
        runCurrent()
        assertEquals(3, restored.uiState.value.adaptation?.selectedTranspose)
        val otherScore = open(repository, id = "32", member = member)
        runCurrent()
        assertEquals(0, otherScore.uiState.value.adaptation?.selectedTranspose)

        member.set("2")
        assertNull(restored.uiState.value.adaptation)
        assertNull(restored.uiState.value.draft.authoring)
        restored.openAdaptation(); restored.saveDraft()
        runCurrent()
        assertEquals(1, repository.writes)
        // A new account reads its own response for the same sheet ID; no shared preference carries over.
        val otherAccount = open(MemoryDocuments(), member = member)
        runCurrent()
        assertEquals(0, otherAccount.uiState.value.adaptation?.selectedTranspose)
    }

    @Test fun malformedOrOutOfRangeMetadataDoesNotBecomeAnAppliedOffset() = runTest {
        for (invalid in listOf(JsonPrimitive("3"), JsonPrimitive(7), JsonPrimitive(-7), JsonPrimitive(2.5),
            JsonNull, JsonArray(emptyList()))) {
            val model = open(MemoryDocuments(buildJsonObject { put("transpose", invalid) }))
            runCurrent()
            assertFalse(model.uiState.value.documentFailed)
            assertEquals(0, model.uiState.value.adaptation?.selectedTranspose, "잘못된 저장 offset: $invalid")
        }
    }

    private fun apply(model: SheetEditViewModel, offset: Int) {
        model.selectAdaptationTranspose(offset)
        model.requestApplyAdaptation()
        model.applyAdaptation()
    }

    private fun open(repository: MemoryDocuments, id: String = "31", member: SignedInMember? = null): SheetEditViewModel =
        keep(SheetEditViewModel(EditSource.Mine(id), EditView.Jeongganbo, SampleMySheetRepository(),
            documents = repository, member = member, pitchCatalog = ReadinessCatalog(),
            loadAudioInstruments = { listOf(CatalogInstrument("1", "DANSO", "단소")) }))

    private class MemoryDocuments(adaptation: JsonElement? = null) : SheetDocumentRepository {
        private val score = smallScoreData().let { it.copy(music = it.music.copy(jangdan = "")) }
        val source = buildJsonObject {
            put("score", Json.encodeToJsonElement(score))
            put("scoreSha256", "0".repeat(64))
            put("notes", buildJsonArray {
                score.events.filter { it.kind == "NOTE" }.zip(listOf(63, 80)).forEach { (event, midi) ->
                    add(buildJsonObject {
                        put("eventId", event.id); put("cellId", event.cellId); put("midi", midi)
                        put("startTick", event.startTick); put("durationTick", event.durationTick)
                    })
                }
            })
        }
        private val initial = SheetDocument("31", "고정 악보", null, 60, "1", score, null,
            "EVALUABLE", "DONE", emptyList(), authoring = buildJsonObject {
                put("schema", "sheet-authoring/v1"); put("source", source)
                adaptation?.let { put("adaptation", it) }
            })
        private val documents = mutableMapOf("31" to initial, "32" to initial.copy(id = "32"))
        var writes = 0
            private set
        override suspend fun get(sheetId: String) = documents[sheetId]
        override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
            writes++
            val before = documents.getValue(sheetId)
            // JSON round trip models a fresh read, with no session-only adaptationBaseline.
            val saved = copy.copy(score = CommonScoreData.decode(Json.encodeToJsonElement(copy.score).toString()),
                authoring = copy.authoring?.let { Json.parseToJsonElement(it.toString()).jsonObject })
            if (draft) {
                documents[sheetId] = before.copy(draft = saved.copy(revision = copy.revision + 1))
                return SheetWrite.Saved(copy.baseScoreVersion, copy.revision + 1)
            }
            val version = (copy.baseScoreVersion.toInt() + 1).toString()
            documents[sheetId] = before.copy(title = copy.title, bpm = copy.bpm, jangdanId = copy.jangdanId,
                scoreVersion = version, score = saved.score, draft = null, authoring = saved.authoring)
            return SheetWrite.Saved(version, reviewStatus = "EVALUABLE")
        }
    }
}

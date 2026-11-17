package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.SampleMySheetRepository
import com.ssafy.eolssu.data.song.SheetDocument
import com.ssafy.eolssu.data.song.SheetDocumentRepository
import com.ssafy.eolssu.data.song.SheetWorkingCopy
import com.ssafy.eolssu.data.song.SheetWrite
import com.ssafy.eolssu.data.song.commonSourceSha256
import com.ssafy.eolssu.data.song.smallScoreData
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
internal class SheetSaveDetailsTest : CatalogViewModelTest() {
    @Test fun titleThenSavePreservesBaseAndSectionTemposAndCancelsTheDebouncedDraft() = runTest {
        val repository = RecordingDocuments()
        val model = open(repository)
        runCurrent()

        model.updateTitle("  저장할 제목  ")
        model.requestSave()
        model.requestSave()
        runCurrent()
        advanceTimeBy(1_000); runCurrent()

        val (draft, copy) = repository.writes.single()
        assertFalse(draft, "상세 변경이 예약한 초안 저장 대신 최종 저장 한 번만 보낸다")
        assertEquals("저장할 제목", copy.title)
        assertEquals("저장할 제목", copy.score.score.title)
        assertEquals(120, copy.bpm)
        assertEquals(listOf(60.0), copy.expressions!!.getValue("performancePlan").jsonObject
            .getValue("tempos").jsonArray.map { it.jsonObject.getValue("bpm").jsonPrimitive.double })
        assertEquals(SheetEditMessage.SavedForPractice, model.uiState.value.message)
        assertFalse(model.uiState.value.saving)
        assertEquals(SheetSaveStatus.Confirmed, model.uiState.value.saveStatus)
        val reopened = open(repository)
        runCurrent()
        assertEquals("저장할 제목", reopened.uiState.value.draft.title)
        assertEquals(120, reopened.uiState.value.draft.bpm)
        assertEquals(listOf(60.0), reopened.uiState.value.draft.tempoCues.map { it.bpm })
        assertNull(reopened.uiState.value.saveStatus, "조회만으로 확정 저장 완료를 표시하지 않는다")
    }

    @Test fun failedSaveKeepsTheEnteredDetailsAndRetriesTheSameMetadata() = runTest {
        val repository = RecordingDocuments(failFirst = true)
        val model = open(repository)
        runCurrent()

        model.updateTitle("다시 저장할 제목")
        model.requestSave()
        runCurrent()
        assertEquals(SheetEditMessage.SaveFailed, model.uiState.value.message)
        assertEquals(SheetSaveStatus.ConfirmationFailed, model.uiState.value.saveStatus)
        assertEquals("다시 저장할 제목", model.uiState.value.draft.title)
        assertEquals(120, model.uiState.value.draft.bpm)
        assertEquals(listOf(60.0), model.uiState.value.draft.tempoCues.map { it.bpm })
        assertFalse(model.uiState.value.confirming)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, repository.writes.size, "실패 후 오래된 초안 요청을 뒤늦게 보내지 않는다")

        model.requestSave()
        runCurrent()
        assertEquals(listOf(false, false), repository.writes.map { it.first })
        assertEquals(repository.writes.first().second, repository.writes.last().second)
        assertEquals("다시 저장할 제목", repository.writes.last().second.title)
        assertEquals(120, repository.writes.last().second.bpm)
        assertEquals(SheetEditMessage.SavedForPractice, model.uiState.value.message)
    }

    private fun open(repository: SheetDocumentRepository) = keep(SheetEditViewModel(
        EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository,
    ))

    private class RecordingDocuments(private val failFirst: Boolean = false) : SheetDocumentRepository {
        val writes = mutableListOf<Pair<Boolean, SheetWorkingCopy>>()
        private val score = smallScoreData()
        private val hash = commonSourceSha256(Json.encodeToJsonElement(score))
        private var document = SheetDocument(
            "31", "원래 제목", null, 120, "1", score, null, "EVALUABLE", null, emptyList(),
            expressions = buildJsonObject {
                put("schema", "common-score-expressions/v1"); put("sourceSha256", hash)
                put("placements", JsonArray(emptyList()))
                put("performancePlan", buildJsonObject {
                    put("schema", "danso-performance/v1"); put("input_sha256", hash)
                    put("tempos", buildJsonArray { add(buildJsonObject { put("gak", 2); put("cell", 1); put("bpm", 60) }) })
                })
            },
        )
        override suspend fun get(sheetId: String) = document

        override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
            writes += draft to copy
            if (failFirst && writes.size == 1) return SheetWrite.Failed
            document = document.copy(title = copy.title, bpm = copy.bpm, scoreVersion = "2", score = copy.score, expressions = copy.expressions)
            return SheetWrite.Saved("2", reviewStatus = "EVALUABLE", expressions = copy.expressions)
        }
    }
}

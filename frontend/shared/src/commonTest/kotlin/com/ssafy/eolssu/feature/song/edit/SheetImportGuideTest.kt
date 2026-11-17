package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.data.song.*
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.test.*

/** 가져온 악보를 열었을 때 음역 맞추기 추천과 인식 확인 개수를 한곳에서 안내한다(S15P31E102-979). */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SheetImportGuideTest : CatalogViewModelTest() {
    // 원본 황/청중(MIDI63/80)은 +6에서 옥타브 이동 없이 모두 맞는다. 원조(0)에서는 둘 다 연주할 수 없다.
    private val sixUp = object : ReferenceCatalogRepository by ReadinessCatalog() {
        override suspend fun authoringCapabilities(instrumentId: String) =
            ReadinessCatalog().authoringCapabilities(instrumentId).let { capability ->
                capability.copy(items = capability.items.filter { it.midi == 69 || it.midi == 86 })
            }
    }

    @Test fun theGuideOffersTheRecommendedTransposeWhileTheSliderStaysAtTheOriginalKey() = runTest {
        val model = open(sixUp)
        runCurrent()
        val guide = assertNotNull(model.uiState.value.importGuide)
        assertEquals(0, model.uiState.value.adaptation?.selectedTranspose, "893: 슬라이더 최초 선택은 원조 유지")
        assertTrue(guide.rangeNotes > 0)
        assertEquals(6, guide.recommendedTranspose)
        assertEquals(0, guide.recommendedRemaining)
        assertEquals(6, guide.recommendedShift, "지금 높이(원조)에서 6반음 올린다")
        assertEquals("단소", guide.instrumentName)
    }

    @Test fun theSaveDialogOffersTheSameOneTapFixAndClearsOnceApplied() = runTest {
        val model = open(sixUp)
        runCurrent()
        // 저장을 눌러 막힌 상태에서도 위 안내와 같은 추천을 보여 준다(S15P31E102-1085).
        val blocked = model.uiState.value
        assertFalse(blocked.completionValidation.canComplete)
        val guide = assertNotNull(blocked.rangeFixGuide())
        assertEquals(6, guide.recommendedTranspose)
        assertEquals(0, guide.reviewCells, "다시 볼 곳은 저장 창의 다른 줄이 맡는다")

        model.applyRecommendedAdaptation()

        val fixed = model.uiState.value
        assertEquals(0, fixed.completionValidation.invalidPitchCount)
        assertNull(fixed.rangeFixGuide(), "고치면 저장 창의 줄도 사라진다")
    }

    @Test fun oneTapAppliesTheRecommendationWhenNothingWasEditedAndUndoRestoresIt() = runTest {
        val model = open(sixUp)
        runCurrent()
        val before = model.uiState.value.draft

        model.applyRecommendedAdaptation()

        val state = model.uiState.value
        assertNotEquals(before, state.draft)
        assertEquals(6, state.draft.appliedTranspose)
        assertEquals(6, state.adaptation?.selectedTranspose)
        assertFalse(state.adaptation?.confirmationRequested == true, "편집 전이라 덮어쓸 수정이 없어 바로 적용한다")
        assertNull(state.importGuide?.recommendedTranspose, "적용한 추천은 다시 권하지 않는다")
        model.undo()
        assertEquals(before, model.uiState.value.draft)
    }

    @Test fun afterAnEditTheRecommendationStillAsksBeforeOverwriting() = runTest {
        val model = open(sixUp)
        runCurrent()
        model.selectTab(EditTab.Yul)
        model.selectCell(2) // 음이 없는 쉼표 정간만 바꿔 원본 음 대응은 유지한다
        model.put(ScoreNote.Hold)
        model.openAdaptation()
        val edited = model.uiState.value.draft

        model.applyRecommendedAdaptation()

        assertEquals(edited, model.uiState.value.draft)
        assertEquals(6, model.uiState.value.adaptation?.selectedTranspose)
        assertTrue(model.uiState.value.adaptation?.confirmationRequested == true)
    }

    @Test fun theCardKeepsCountingPlayableNotesAfterAnEditAndUndo() = runTest {
        val model = open(sixUp)
        runCurrent()
        val first = assertNotNull(model.uiState.value.importGuide)

        model.selectTab(EditTab.Yul)
        model.selectCell(2) // 음이 없는 쉼표 정간만 바꾼다
        model.put(ScoreNote.Hold)
        val edited = assertNotNull(model.uiState.value.importGuide)
        assertEquals("단소", edited.instrumentName, "1042: 편집 뒤에도 악기 이름을 잃지 않는다")
        assertEquals(first.rangeNotes, edited.rangeNotes, "음은 그대로라 낼 수 없는 음 수도 같다")

        model.undo()
        assertEquals(first, model.uiState.value.importGuide?.copy(reviewPosition = first.reviewPosition),
            "1042: 실행 취소하면 처음 카드와 같다")
    }

    @Test fun splittingLeavesNoBlankAndAPlayableNoteIsNotCountedAsUnplayable() = runTest {
        // 1136: 정간을 나누면 새 자리를 쉼(△)·지속(ㅡ)으로 채워 빈칸이 생기지 않고, 고른 음은 낼 수 없는 음으로 세지 않는다.
        val model = open(sixUp)
        runCurrent()
        model.applyRecommendedAdaptation()
        runCurrent()
        val before = model.uiState.value.draft
        model.selectTab(EditTab.Yul)
        model.selectCell(2) // 쉼표 정간
        model.split()
        val split = model.uiState.value
        assertEquals(listOf(ScoreNote.Rest, ScoreNote.Rest), split.draft.cells[2])
        assertEquals(1, split.selectedSub, "새 자리를 골라 두어 바로 율명을 넣을 수 있다")
        assertTrue(split.draft.unfilledCells.isEmpty())
        assertNull(split.importGuide)

        val (yul, octave) = assertNotNull(split.supportedInputPitches).first()
        model.put(ScoreNote.SourcePitch(yul, octave))
        val filled = model.uiState.value
        assertEquals(ScoreNote.Rest, filled.draft.cells[2][0], "고른 자리만 바뀐다")
        assertTrue(filled.completionValidation.rangeUnitKeys.isEmpty(), "고른 음은 칸에 칠하지 않는다")
        assertNull(filled.importGuide, "칠하지 않은 음을 낼 수 없는 음으로 세지 않는다")

        // 높이 맞추기를 다시 적용하면 원본 악보에서 높이만 바꾼 상태로 돌아간다.
        model.openAdaptation()
        model.requestApplyAdaptation()
        assertTrue(model.uiState.value.adaptation?.confirmationRequested == true, "고친 것이 있어 확인을 묻는다")
        model.applyAdaptation()
        assertEquals(before.cells, model.uiState.value.draft.cells)
    }

    @Test fun eachBlankSlotOfASplitCellIsCountedAndVisitedSeparately() = runTest {
        // 1136: 빈 정간을 나눠 빈 자리가 둘이면 다시 볼 곳도 둘이고, 이전·다음이 한 자리씩 옮긴다.
        val model = open(ReadinessCatalog())
        runCurrent()
        model.selectCell(2)
        model.erase()
        model.split()
        assertEquals(listOf(ScoreNote.Blank, ScoreNote.Blank), model.uiState.value.draft.cells[2])
        assertEquals(2, model.uiState.value.importGuide?.reviewCells)

        model.selectCell(0)
        model.moveRecognitionReview(1)
        assertEquals(2 to 0, model.uiState.value.selected to model.uiState.value.selectedSub)
        assertEquals(1, model.uiState.value.importGuide?.reviewPosition)
        model.moveRecognitionReview(1)
        assertEquals(2 to 1, model.uiState.value.selected to model.uiState.value.selectedSub)
        assertEquals(2, model.uiState.value.importGuide?.reviewPosition)
    }

    @Test fun recognitionReviewsAreCountedApartFromRangeAndTheGuideGoesAwayOnceChecked() = runTest {
        val model = open(ReadinessCatalog(), issues = listOf(json("""{"issueId":"i1","cellId":"c0-j0","type":"ORNAMENT_DROPPED"}""")))
        runCurrent()
        val guide = assertNotNull(model.uiState.value.importGuide)
        assertEquals(1, guide.reviewCells)
        assertEquals(0, guide.rangeNotes)
        assertNull(guide.recommendedTranspose)

        model.openFirstRecognitionReview()
        assertEquals(0, model.uiState.value.selected)
        // 1042: 닫기 버튼 없이, 다시 볼 곳을 다 확인하면 카드가 저절로 사라진다.
        assertTrue(model.uiState.value.canCheckSelectedReviewIssue)
        model.checkSelectedReviewIssue()
        assertNull(model.uiState.value.importGuide)
    }

    @Test fun reviewPlacesAreVisitedOneByOneWithPreviousAndNext() = runTest {
        val model = open(ReadinessCatalog(), issues = listOf(
            json("""{"issueId":"i1","cellId":"c0-j0","type":"ORNAMENT_DROPPED"}"""),
            json("""{"issueId":"i2","cellId":"c1-j0","type":"ORNAMENT_DROPPED"}"""),
        ))
        runCurrent()
        model.selectCell(1)
        assertNull(model.uiState.value.importGuide?.reviewPosition, "다시 볼 칸이 아니면 순서를 보이지 않는다")

        model.moveRecognitionReview(1)
        assertEquals(0, model.uiState.value.selected, "처음엔 첫 칸으로 간다")
        assertEquals(1, model.uiState.value.importGuide?.reviewPosition)

        model.moveRecognitionReview(1)
        assertEquals(2, model.uiState.value.selected)
        assertEquals(2, model.uiState.value.importGuide?.reviewPosition)
        model.moveRecognitionReview(1)
        assertEquals(2, model.uiState.value.selected, "마지막에서 다음은 그대로")

        model.moveRecognitionReview(-1)
        assertEquals(0, model.uiState.value.selected)
    }

    @Test fun whenNoHeightFixesEveryNoteThePanelKnowsHowManyRemain() = runTest {
        // 황(63)은 +6, 청중(80)은 +1에서만 MIDI69 한 음에 맞는다. 어느 높이로도 둘 다 맞출 수 없다(S15P31E102-1042).
        val oneNote = object : ReferenceCatalogRepository by ReadinessCatalog() {
            override suspend fun authoringCapabilities(instrumentId: String) =
                ReadinessCatalog().authoringCapabilities(instrumentId).let { capability ->
                    capability.copy(items = capability.items.filter { it.midi == 69 })
                }
        }
        val model = open(oneNote)
        runCurrent()
        assertEquals(1, model.uiState.value.adaptation?.unavoidableUnresolved)
        assertEquals(1, model.uiState.value.importGuide?.recommendedRemaining, "추천으로도 1개가 남는다")

        val fixable = open(sixUp)
        runCurrent()
        assertEquals(0, fixable.uiState.value.adaptation?.unavoidableUnresolved)
    }

    @Test fun aConvertedScoreOpensWithTheInputPanelFoldedUntilTheUserAsksToEdit() = runTest {
        val model = open(sixUp)
        runCurrent()
        assertFalse(model.uiState.value.inputPanelExpanded, "1042: 변환 뒤 처음 열면 입력판을 접는다")

        model.selectTab(EditTab.Yul)
        assertTrue(model.uiState.value.inputPanelExpanded, "탭을 고르면 펼친다")

        model.setInputPanelExpanded(false)
        model.openAdaptation()
        assertTrue(model.uiState.value.inputPanelExpanded, "높이 맞추기를 열면 펼친다")
    }

    @Test fun aScoreThatWasNotConvertedOpensWithTheInputPanelUnfolded() = runTest {
        val model = open(ReadinessCatalog(), conversionStatus = null)
        runCurrent()
        assertTrue(model.uiState.value.inputPanelExpanded)
    }

    @Test fun aScoreWithNothingToCheckShowsNoGuide() = runTest {
        val model = open(ReadinessCatalog())
        runCurrent()
        assertNull(model.uiState.value.importGuide)
    }

    private fun open(catalog: ReferenceCatalogRepository, issues: List<JsonObject> = emptyList(), conversionStatus: String? = "DONE"): SheetEditViewModel {
        val score = smallScoreData().let { it.copy(music = it.music.copy(jangdan = "")) }
        // 인식 결과처럼 원본 음고(MIDI63/80)를 authoring.source에 보존한 문서다.
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
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = SheetDocument("31", "가져온 악보", null, 60, "1", score,
                null, "NEEDS_REVIEW", conversionStatus, issues,
                authoring = buildJsonObject { put("schema", "sheet-authoring/v1"); put("source", source) })
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite = SheetWrite.Failed
        }
        return keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(),
            documents = repository, pitchCatalog = catalog,
            loadAudioInstruments = { listOf(CatalogInstrument("1", "DANSO", "단소")) }))
    }

    private fun json(value: String) = Json.parseToJsonElement(value) as JsonObject
}

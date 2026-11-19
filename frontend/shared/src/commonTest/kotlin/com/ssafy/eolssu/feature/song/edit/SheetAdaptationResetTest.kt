package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul
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

/**
 * 높이 맞추기의 '처음 악보'는 서버가 남긴 불변 원본(authoring.source)이다. 임시저장본을 다시 열거나 음 바로 고치기·장단 보정으로
 * 편집 상태를 다시 만들어도, 원음으로 다시 맞추면 고친 나눔·시김새가 지워지고 추천·못 내는 음 수가 처음과 같다.
 * 오선보 선율은 파자마파티즈의 노래 앞 네 마디(한 정간 = 4분음표, 8분음 둘은 한 정간 두 자리, 붙임줄·2분쉼표는 다음 칸 '-')다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SheetAdaptationResetTest : CatalogViewModelTest() {
    @Test fun aReopenedDraftStillStartsFromTheImportedScore() = runTest {
        val capability = danso()
        val imported = importedDraft()
        val first = imported.plans(capability)
        assertEquals(1, first.first().transpose, "단소는 +1반음을 추천한다")
        val adapted = imported.applyAdaptation(first.first { it.transpose == 1 }, capability)
        // +1 적용 뒤 정간을 나누고 시김새를 붙여 임시저장하고 다시 열면 저장한 악보가 편집 상태의 original이 된다.
        val edited = adapted.split(5).split(9).toggleSigimsae(0, Sigimsae.Chuseong).toggleSigimsae(5, Sigimsae.Toeseong, 1)
        val reopened = edited.toCommonScore().editingDraft(edited.title, edited.bpm, null)
            .copy(authoring = edited.authoringForSave("DANSO"))
        assertEquals(first.summary(), reopened.plans(capability).summary(), "다시 열어도 추천·못 내는 음 수는 처음 악보 기준이다")

        val originalKey = reopened.applyAdaptation(reopened.plans(capability).first { it.transpose == 0 }, capability)
        val expected = imported.applyAdaptation(first.first { it.transpose == 0 }, capability)
        assertEquals(emptyMap(), originalKey.sigimsae, "안내대로 붙인 시김새가 지워진다")
        assertEquals(expected.cells, originalKey.cells, "나눈 정간도 처음 악보로 돌아가고 높이만 원음이다")
        assertEquals(expected.toCommonScore(), originalKey.toCommonScore())
        assertEquals(0, originalKey.appliedTranspose)
        assertEquals(first.summary(), originalKey.plans(capability).summary(), "원음을 적용해도 추천은 +1반음 그대로다")
    }

    @Test fun aPitchCorrectionDoesNotBecomeTheNewStartingPoint() = runTest {
        val capability = danso()
        val imported = importedDraft()
        val adapted = imported.applyAdaptation(imported.plans(capability).first { it.transpose == 1 }, capability)
        // 음 바로 고치기는 고친 악보(나눔·시김새 포함)로 편집 상태를 다시 만든다.
        val corrected = adapted.split(5).toggleSigimsae(0, Sigimsae.Chuseong)
            .correctPitch(SheetPitchCorrection(2, 0, 2, 0, Yul.Hwangjong to 1, "c0-j2-r0-u0"), Yul.Hwangjong to 1)
        assertNotEquals(adapted.original, corrected.original)

        val result = corrected.applyAdaptation(corrected.plans(capability).first { it.transpose == 0 }, capability)
        assertEquals(emptyMap(), result.sigimsae)
        assertEquals(imported.cells.map { it.size }, result.cells.map { it.size })
        assertEquals(imported.plans(capability).summary(), result.plans(capability).summary())
    }

    @Test fun aJangdanCorrectionKeepsItsTimingWhenStartingAgainFromTheImport() = runTest {
        val gutgeori = CatalogJangdanSummary("2", "굿거리", 80, "DOTTED_QUARTER", 12, 4, 1)
        val before = SheetJangdanCorrectionTest().state(SheetJangdanCorrectionTest.recognized("QUARTER", 12, 36, "중모리"))
        // 시김새를 붙인 뒤 장단을 바꾸면 박 길이를 보정한 악보가 편집 상태의 original이 된다.
        val retimed = before.copy(draft = before.draft.toggleSigimsae(0, Sigimsae.Chuseong)).selectJangdan(gutgeori.jangdanId).draft
        assertEquals(listOf(Sigimsae.Chuseong), retimed.sigimsaeAt(0))
        val capability = ReadinessCatalog().authoringCapabilities("1")

        val result = retimed.applyAdaptation(retimed.plans(capability).first { it.transpose == 0 }, capability)
        assertEquals(emptyMap(), result.sigimsae)
        assertEquals("2", result.jangdanId)
        val music = result.toCommonScore().music
        assertEquals("DOTTED_QUARTER", music.bpmUnit, "고른 장단의 박 길이는 그대로다")
        assertEquals(music.ticksPerJeonggan, music.ticksPerBeat)
        assertEquals("굿거리", music.jangdan)
        assertEquals(retimed.toCommonScore().music.ticksPerJeonggan, music.ticksPerJeonggan)
    }

    @Test fun splitsAndSigimsaeAddedAfterAnAdaptationGoAwayWhenApplyingTheOriginalKey() = runTest {
        val model = open()
        runCurrent()
        val first = assertNotNull(model.uiState.value.adaptation).plans.summary()
        model.openAdaptation()
        model.selectAdaptationTranspose(1)
        model.requestApplyAdaptation(); model.applyAdaptation()
        runCurrent()
        val adapted = model.uiState.value.draft

        model.selectTab(EditTab.Yul)
        listOf(0, 5, 9).forEach { model.selectCell(it); model.split() }
        listOf(0, 2, 4, 8, 13).forEach { model.selectCell(it); model.toggleSigimsae(Sigimsae.Chuseong) }
        model.selectUnit(5, 1); model.toggleSigimsae(Sigimsae.Toeseong)
        assertTrue(model.uiState.value.draft.sigimsae.isNotEmpty())

        model.openAdaptation()
        model.selectAdaptationTranspose(0)
        model.requestApplyAdaptation()
        assertTrue(model.uiState.value.adaptation?.confirmationRequested == true, "고친 것이 있어 확인을 묻는다")
        model.applyAdaptation()
        runCurrent()
        val after = model.uiState.value
        assertEquals(emptyMap(), after.draft.sigimsae)
        assertEquals(adapted.cells.map { it.size }, after.draft.cells.map { it.size })
        assertEquals(first, after.adaptation?.plans?.summary())
    }

    private sealed interface U
    private data class N(val midi: Int) : U
    private data object R : U
    private data object H : U
    private val layout: List<List<U>> = listOf(
        listOf(N(67)), listOf(R), listOf(N(67)), listOf(N(64)),
        listOf(N(64)), listOf(N(69), N(67)), listOf(R), listOf(H),
        listOf(N(65)), listOf(N(65), N(65)), listOf(H, N(64)), listOf(N(62), N(60)),
        listOf(R), listOf(N(64)), listOf(N(62)), listOf(R),
    )
    private val melody: List<Int> get() = layout.flatten().filterIsInstance<N>().map { it.midi }

    // 운영 단소 선택 가능 음(SheetPitchAdaptationTest와 같은 2026-11-16 authoring-capabilities).
    private val dansoMidi = listOf(66, 68, 70, 72, 73, 75, 77, 78, 79, 80, 82, 84, 85, 87, 89, 90, 92)

    private suspend fun danso() = ReadinessCatalog().authoringCapabilities("1").let { c -> c.copy(items = c.items.filter { it.midi in dansoMidi }) }

    private fun SheetDraft.plans(capability: CatalogAuthoringCapabilities) =
        planPitchAdaptation(adaptationSourceNotes(), capability.items.filter { it.selectable }.map { AdaptationPitch(it.midi, it.yul, it.octave) })

    private fun List<PitchAdaptationPlan>.summary() = map { it.transpose to it.unresolved }

    private fun score(): CommonScoreData {
        val events = mutableListOf<CommonScoreData.Event>()
        val columns = layout.chunked(4).mapIndexed { c, column -> CommonScoreData.Column(c, column.mapIndexed { j, units ->
            val cellId = "c$c-j$j"
            CommonScoreData.Cell(cellId, j, units.mapIndexed { col, unit ->
                val length = 4L / units.size
                val start = (c * 4 + j) * 4L + col * length
                val id = "$cellId-r0-u$col"
                when (unit) {
                    is N -> {
                        val relative = unit.midi - 63
                        val yul = "황대태협고중유임이남무응"[relative.mod(12)].toString()
                        val octave = kotlin.math.floor(relative / 12.0).toInt()
                        events += CommonScoreData.Event(id, "NOTE", yul, octave, start, length, cellId, false, emptyList())
                        CommonScoreData.Unit(0, col, ScoreNote.SourcePitch(yul, octave).hangul, id)
                    }
                    R -> {
                        events += CommonScoreData.Event(id, "REST", null, null, start, length, cellId, false, emptyList())
                        CommonScoreData.Unit(0, col, "쉼", id)
                    }
                    H -> {
                        val last = events.removeAt(events.lastIndex)
                        events += last.copy(durationTick = last.durationTick + length)
                        CommonScoreData.Unit(0, col, "-", last.id)
                    }
                }
            })
        }) }
        return CommonScoreData(1, CommonScoreData.Header("파자마", "DANSO", 0, "staff", 1),
            CommonScoreData.Music(311.13, "", "QUARTER", 4, 4), columns, events,
            CommonScoreData.Provenance("staff", "0".repeat(64), "inferred", "staff-omr/test"))
    }

    private fun authoring(score: CommonScoreData): JsonObject = buildJsonObject {
        put("schema", "sheet-authoring/v1")
        put("source", buildJsonObject {
            put("kind", "STAFF_IMPORT")
            put("score", Json.encodeToJsonElement(score))
            put("scoreSha256", "0".repeat(64))
            put("notes", buildJsonArray {
                score.events.filter { it.kind == "NOTE" }.zip(melody).forEach { (event, midi) -> add(buildJsonObject {
                    put("eventId", event.id); put("cellId", event.cellId); put("midi", midi)
                    put("startTick", event.startTick); put("durationTick", event.durationTick)
                }) }
            })
        })
    }

    private fun importedDraft(): SheetDraft = score().let { it.editingDraft("파자마", 60, null).copy(authoring = authoring(it)) }

    private fun open(): SheetEditViewModel {
        val score = score()
        val catalog = object : ReferenceCatalogRepository by ReadinessCatalog() {
            override suspend fun authoringCapabilities(instrumentId: String) = danso()
        }
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = SheetDocument("31", "파자마", null, 60, "1", score,
                null, "NEEDS_REVIEW", "DONE", emptyList(), authoring = authoring(score))
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite = SheetWrite.Failed
        }
        return keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(),
            documents = repository, pitchCatalog = catalog,
            loadAudioInstruments = { listOf(CatalogInstrument("1", "DANSO", "단소")) }))
    }
}

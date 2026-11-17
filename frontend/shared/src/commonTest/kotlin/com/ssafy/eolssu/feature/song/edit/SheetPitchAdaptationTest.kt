package com.ssafy.eolssu.feature.song.edit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.ssafy.eolssu.data.song.*
import kotlinx.serialization.json.*
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

class SheetPitchAdaptationTest {
    @Test fun movingTheWholeSongByAnOctaveIsNotCountedAsAContourSplit() {
        val plans = planPitchAdaptation(source(60, 64), listOf(64, 68, 71, 75).map { AdaptationPitch(it, "황", 0) })
        val wholeOctave = plans.first { it.transpose == -1 }
        assertEquals(listOf(1, 1), wholeOctave.notes.map { it.octaveShift })
        assertEquals(2, wholeOctave.octaveChanges)
        assertEquals(0, wholeOctave.octaveSplits)
        assertEquals(-1, plans.first().transpose)
    }

    private fun source(vararg midi: Int) = midi.mapIndexed { i, m -> SourcePitchNote("c0-j$i-r0-u0", "c0-j$i", m) }
    private val sogeum = listOf(70, 72, 75, 77, 79, 82, 84, 87).map { AdaptationPitch(it, "황", 0) }

    // 운영 단소 선택 가능 음(2026-11-16 authoring-capabilities)과 파자마파티즈의 노래 오선보 선율(C4~A4).
    private val danso = listOf(66, 68, 70, 72, 73, 75, 77, 78, 79, 80, 82, 84, 85, 87, 89, 90, 92).map { AdaptationPitch(it, "황", 0) }
    private val pajamaMelody = run {
        val a = listOf(67, 67, 64, 64, 69, 67, 65, 65, 65, 65, 64, 62, 60, 64, 62)
        val b = listOf(60, 60, 62, 60, 64, 64, 62, 64, 62, 60, 62, 64, 65, 65, 65, 65, 64, 62, 64, 65, 67)
        val c = listOf(60, 60, 62, 60, 64, 64, 62, 64, 62, 64, 65, 67, 69, 69, 69, 67, 64, 65, 64, 62, 60)
        (a + b + a + c).toIntArray()
    }

    @Test fun movesTheWholeMelodyByOneOctaveInsteadOfFoldingOnlySomeNotes() {
        // 이전에는 +1에서 음마다 가까운 옥타브로 접어 43개만 올라가 선율이 13번 오르내렸다.
        val plans = planPitchAdaptation(source(*pajamaMelody), danso)
        val best = plans.first()
        assertEquals(1, best.transpose)
        assertEquals(0, best.unresolved)
        assertEquals(0, best.octaveSplits)
        assertEquals(setOf(1), best.notes.map { it.octaveShift }.toSet())
        assertEquals(pajamaMelody.toList().zipWithNext { x, y -> y - x }, best.notes.map { it.target!!.midi }.zipWithNext { x, y -> y - x })
        assertEquals(0, plans.first { it.transpose == -4 }.octaveSplits)
    }

    @Test fun ranksContourSplitsBeforeTheSizeOfTheKeyChange() {
        // 원조는 일부 음만 옥타브를 옮겨야 하므로, 끊김 없는 이조가 조 변화는 커도 앞선다.
        val supported = (64..76).map { AdaptationPitch(it, "황", 0) }
        val plans = planPitchAdaptation(source(60, 62, 64, 67), supported)
        assertEquals(0, plans.first().octaveSplits)
        assertTrue(plans.first { it.transpose == 0 }.octaveSplits > 0)
        assertEquals(-3, plans.first().transpose)
    }

    @Test fun alsoRecommendsTheSmallestKeyChangeWithoutDuplicatingTheDefault() {
        val supported = (64..76).map { AdaptationPitch(it, "황", 0) }
        assertEquals(listOf(-3, 0), recommendedAdaptations(planPitchAdaptation(source(60, 62, 64, 67), supported)).map { it.transpose })
        assertEquals(listOf(0), recommendedAdaptations(planPitchAdaptation(source(70), sogeum)).map { it.transpose })
    }

    @Test fun jangdanRetimingKeepsThePitchBaselineOfANewlyInsertedNote() {
        val score = SheetJangdanCorrectionTest.recognized("QUARTER", 5, 2, "단모리", grid = 10)
        val draft = SheetJangdanCorrectionTest().state(score).draft
        val capability = CatalogAuthoringCapabilities("1", "DANSO", "v1", 63,
            AuthoringPhysicalRange(verificationStatus = "UNKNOWN"), emptyList())
        // 높이 맞추기는 원본으로 되돌리므로, 맞춘 뒤 새로 넣은 음의 기준음이 재시간 보정 뒤에도 그대로인지 본다.
        val adapted = draft.applyAdaptation(planPitchAdaptation(draft.adaptationSourceNotes(), emptyList()).first { it.transpose == 1 }, capability)
            .putAt(9, 0, com.ssafy.eolssu.core.model.ScoreNote.SourcePitch("황", 0))
        val sources = adapted.sourcePitchNotes()
        val inserted = sources.first { it.cellId == "c0-j9" }.midi
        val retimed = requireNotNull(adapted.withJangdan(CatalogJangdanSummary("10", "세령산", 63, "QUARTER", 10, 4, 3)))
        assertEquals(inserted, retimed.sourcePitchNotes().first { it.cellId == "c0-j9" }.midi)
        assertEquals(sources, retimed.sourcePitchNotes())
        assertEquals(6, retimed.toCommonScore().music.ticksPerJeonggan)
    }

    @Test fun startsFromTheOriginalNotesSplitsAndSignsButKeepsMetadata() {
        // 높이 맞추기는 '원본 악보에서 높이만 바꾼 상태'로 간다. 직접 고친 음·나눔·시김새는 원본으로 돌아간다(S15P31E102-1136).
        val draft = smallScoreData().editingDraft("수정 제목", 123, null).copy(editedJangdanName = "수정 장단", jangdanId = "9")
        val edited = draft.putAt(0, 0, com.ssafy.eolssu.core.model.ScoreNote.SourcePitch("응", -2))
            .toggleSigimsae(0, Sigimsae.Chuseong).split(2)
            .putAt(2, 1, com.ssafy.eolssu.core.model.ScoreNote.SourcePitch("태", 0))
        assertEquals(draft.sourcePitchNotes(), edited.adaptationSourceNotes(), "높이 계산은 원본 음에서 시작한다")
        val capability = CatalogAuthoringCapabilities("1", "DANSO", "v1", 63,
            AuthoringPhysicalRange(verificationStatus = "UNKNOWN"), emptyList())
        val result = edited.applyAdaptation(planPitchAdaptation(edited.adaptationSourceNotes(), emptyList()).first { it.transpose == 0 }, capability)
        assertEquals(draft.cells, result.cells, "나눈 정간과 직접 넣은 음이 원본으로 돌아간다")
        assertEquals(draft.sigimsae, result.sigimsae)
        assertEquals(edited.title, result.title)
        assertEquals(123, result.bpm)
        assertEquals("수정 장단", result.jangdanName)
        assertEquals("9", result.jangdanId)
        val before = draft.toCommonScore()
        assertEquals(before.events.map { listOf(it.id, it.kind, it.startTick, it.durationTick, it.sigimsae) },
            result.toCommonScore().events.map { listOf(it.id, it.kind, it.startTick, it.durationTick, it.sigimsae) })
    }

    @Test fun aNoteWrittenInANewColumnMovesOnlyByTheChangeInHeight() {
        // 새 각의 음은 지금 높이로 적은 음이다. +1에서 적은 음을 +3으로 맞추면 2반음만 오른다(이전에는 3반음 더 올렸다).
        val capability = CatalogAuthoringCapabilities("1", "DANSO", "v1", 63,
            AuthoringPhysicalRange(verificationStatus = "UNKNOWN"), emptyList())
        val draft = smallScoreData().editingDraft("adapt", 60, null).copy(authoring = JsonObject(emptyMap()))
        val raised = draft.applyAdaptation(planPitchAdaptation(draft.adaptationSourceNotes(), emptyList()).first { it.transpose == 1 }, capability)
        val written = raised.addColumnAfter(raised.columnCount - 1).putAt(raised.cells.size, 0, com.ssafy.eolssu.core.model.ScoreNote.SourcePitch("황", 0))
        val added = written.adaptationSourceNotes().last()
        assertEquals(62, added.midi, "적용된 +1을 빼 원본 기준 음으로 센다")
        val plan = planPitchAdaptation(written.adaptationSourceNotes(), emptyList()).first { it.transpose == 3 }
        val moved = written.applyAdaptation(plan, capability).toCommonScore().events.last { it.kind == "NOTE" }
        assertEquals("태" to 0, moved.yul to moved.octave)
    }

    @Test fun restoresOriginalHighOctaveAfterSemitoneTransposeAndOnlyRaisesLowNotes() {
        val plan = planPitchAdaptation(source(88, 59, 71), sogeum).first()
        assertEquals(-1, plan.transpose)
        assertEquals(listOf(87, 70, 70), plan.notes.map { it.target?.midi })
        assertEquals(listOf(0, 1, 0), plan.notes.map { it.octaveShift })
    }

    @Test fun prefersOriginalKeyWhenOctaveFoldingCanResolveEverything() {
        val plan = planPitchAdaptation(source(58, 87), sogeum).first()
        assertEquals(0, plan.transpose)
        assertEquals(listOf(70, 87), plan.notes.map { it.target?.midi })
    }

    @Test fun unresolvedPitchIsNeverReplacedWithNearbySemitone() {
        val unchanged = planPitchAdaptation(source(70, 71), listOf(AdaptationPitch(70, "임", 0))).first { it.transpose == 0 }
        assertEquals(70, unchanged.notes[0].target?.midi)
        assertNull(unchanged.notes[1].target)
    }

    @Test fun instrumentTuningChangePreservesUnresolvedSoundingPitchAndTiming() {
        val original = smallScoreData()
        val draft = original.editingDraft("adapt", 60, null)
        val sources = draft.sourcePitchNotes()
        val plan = planPitchAdaptation(sources, emptyList()).first { it.transpose == -1 }
        val capabilities = CatalogAuthoringCapabilities("4", "DAEGEUM_SANJO", "v1", 65,
            AuthoringPhysicalRange(verificationStatus = "UNKNOWN"), emptyList())
        val adapted = draft.applyAdaptation(plan, capabilities).toCommonScore()
        val hwang = (69 + 12 * log2(adapted.music.hwangHz / 440.0)).roundToInt()
        assertEquals(65, hwang)
        assertEquals("DAEGEUM_SANJO", adapted.score.instrument)
        assertEquals(sources.map { it.midi - 1 }, adapted.events.filter { it.kind == "NOTE" }.map {
            hwang + "황대태협고중유임이남무응".indexOf(it.yul.orEmpty()) + 12 * (it.octave ?: 0)
        })
        assertEquals(original.events.map { Triple(it.id, it.startTick, it.durationTick) },
            adapted.events.map { Triple(it.id, it.startTick, it.durationTick) })
        assertEquals(original.provenance, adapted.provenance)
    }

    @Test fun keepsAStoredReferenceFrequencyThatAlreadyMatchesTheInstrument() {
        val capability = CatalogAuthoringCapabilities("1", "DANSO", "v1", 63,
            AuthoringPhysicalRange(verificationStatus = "UNKNOWN"), emptyList())
        listOf(311.1269837220809, 311.12698372208087).forEach { stored ->
            val score = smallScoreData().let { it.copy(music = it.music.copy(hwangHz = stored)) }
            val draft = score.editingDraft("adapt", 60, null)
            val plan = planPitchAdaptation(draft.sourcePitchNotes(), emptyList()).first { it.transpose == 0 }
            assertEquals(stored, draft.applyAdaptation(plan, capability).toCommonScore().music.hwangHz,
                "같은 기준음은 다시 계산한 마지막 자리로 바꾸지 않는다")
        }
        val detuned = smallScoreData().let { it.copy(music = it.music.copy(hwangHz = 315.0)) }.editingDraft("adapt", 60, null)
        val plan = planPitchAdaptation(detuned.sourcePitchNotes(), emptyList()).first { it.transpose == 0 }
        assertEquals(440.0 * 2.0.pow(-6 / 12.0), detuned.applyAdaptation(plan, capability).toCommonScore().music.hwangHz)
    }

    @Test fun reopeningAdaptedDraftUsesImmutableOriginalOctaves() {
        val original = smallScoreData()
        val metadata = buildJsonObject { put("source", buildJsonObject { put("notes", buildJsonArray {
            original.events.filter { it.kind == "NOTE" }.forEach { add(buildJsonObject {
                put("eventId", it.id); put("cellId", it.cellId); put("midi", 88)
                put("startTick", it.startTick); put("durationTick", it.durationTick)
            }) }
        }) }) }
        val draft = original.editingDraft("original", 60, null).copy(authoring = metadata)
        assertTrue(draft.sourcePitchNotes().all { it.midi == 88 })
        val capabilities = CatalogAuthoringCapabilities("2", "SOGEUM", "v1", 63,
            AuthoringPhysicalRange(verificationStatus = "UNKNOWN"), emptyList())
        val plan = planPitchAdaptation(draft.sourcePitchNotes(), listOf(AdaptationPitch(87, "황", 2))).first()
        val result = draft.applyAdaptation(plan, capabilities)
        assertTrue(result.toCommonScore().events.filter { it.kind == "NOTE" }.all { it.octave == 2 })
        assertTrue(result.sourcePitchNotes().all { it.midi == 88 })
        assertEquals(metadata["source"], result.authoring?.get("source"))
        val addedInHold = draft.putAt(0, 2, com.ssafy.eolssu.core.model.ScoreNote.SourcePitch("태", 0))
        assertEquals(65, addedInHold.sourcePitchNotes().last().midi, "이음에 새로 입력한 음은 다른 원본 음으로 되돌리지 않는다")
        val reusedId = draft.deleteColumn(0).putAt(0, 0, com.ssafy.eolssu.core.model.ScoreNote.SourcePitch("태", 0))
        assertEquals(listOf(65), reusedId.sourcePitchNotes().map { it.midi }, "지운 각의 위치 ID를 다른 원본 음에 연결하지 않는다")
        val reshaped = draft.erase(0)
        assertTrue(reshaped.sourcePitchNotes().none { it.midi == 88 }, "Reused position IDs must not pick a different original note")
    }
}

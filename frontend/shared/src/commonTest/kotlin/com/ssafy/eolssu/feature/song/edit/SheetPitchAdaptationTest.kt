package com.ssafy.eolssu.feature.song.edit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.ssafy.eolssu.data.song.*
import kotlinx.serialization.json.*
import kotlin.math.log2
import kotlin.math.roundToInt

class SheetPitchAdaptationTest {
    @Test fun recommendsFewerOctaveMovesBeforeASmallerKeyChange() {
        val plans = planPitchAdaptation(source(60, 64), listOf(64, 68, 71, 75).map { AdaptationPitch(it, "황", 0) })
        assertEquals(4, plans.first().transpose)
        assertEquals(0, plans.first().octaveChanges)
        assertEquals(2, plans.first { it.transpose == -1 }.octaveChanges)
    }

    private fun source(vararg midi: Int) = midi.mapIndexed { i, m -> SourcePitchNote("c0-j$i-r0-u0", "c0-j$i", m) }
    private val sogeum = listOf(70, 72, 75, 77, 79, 82, 84, 87).map { AdaptationPitch(it, "황", 0) }

    @Test fun alsoRecommendsTheSmallestKeyChangeWithoutDuplicatingTheDefault() {
        val plans = planPitchAdaptation(source(60, 64), listOf(64, 68, 71, 75).map { AdaptationPitch(it, "황", 0) })
        assertEquals(listOf(4, -1), recommendedAdaptations(plans).map { it.transpose })
        assertEquals(listOf(0), recommendedAdaptations(planPitchAdaptation(source(70), sogeum)).map { it.transpose })
    }

    @Test fun resetsManualPitchesButPreservesSplitsSignsMetadataAndTiming() {
        val draft = smallScoreData().editingDraft("수정 제목", 123, null).copy(editedJangdanName = "수정 장단", jangdanId = "9")
        val originalNotes = draft.sourcePitchNotes()
        val edited = draft.putAt(0, 0, com.ssafy.eolssu.core.model.ScoreNote.SourcePitch("응", -2))
            .toggleSigimsae(0, Sigimsae.Chuseong).split(2)
            .putAt(2, 1, com.ssafy.eolssu.core.model.ScoreNote.SourcePitch("태", 0))
        val sources = edited.sourcePitchNotes()
        assertEquals(originalNotes.map { it.midi }, sources.take(2).map { it.midi })
        assertEquals(65, sources.last().midi, "새 음은 현재 판본의 음고를 쓴다")
        val before = edited.toCommonScore()
        val capability = CatalogAuthoringCapabilities("1", "DANSO", "v1", 63,
            AuthoringPhysicalRange(verificationStatus = "UNKNOWN"), emptyList())
        val result = edited.applyAdaptation(planPitchAdaptation(sources, emptyList()).first { it.transpose == 0 }, capability)
        assertEquals(sources.map { it.midi }, result.sourcePitchNotes().map { it.midi })
        val twice = result.putAt(0, 0, com.ssafy.eolssu.core.model.ScoreNote.SourcePitch("응", -2))
        assertEquals(sources.map { it.midi }, twice.sourcePitchNotes().map { it.midi })
        assertEquals(edited.title, result.title)
        assertEquals(123, result.bpm)
        assertEquals("수정 장단", result.jangdanName)
        assertEquals("9", result.jangdanId)
        assertEquals(before.events.map { listOf(it.id, it.kind, it.startTick, it.durationTick, it.sigimsae) },
            result.toCommonScore().events.map { listOf(it.id, it.kind, it.startTick, it.durationTick, it.sigimsae) })
        assertEquals(before.columns.map { it.cells.map { cell -> cell.units.size to cell.lyric } },
            result.toCommonScore().columns.map { it.cells.map { cell -> cell.units.size to cell.lyric } })
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

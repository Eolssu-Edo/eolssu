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
    private fun source(vararg midi: Int) = midi.mapIndexed { i, m -> SourcePitchNote("c0-j$i-r0-u0", "c0-j$i", m) }
    private val sogeum = listOf(70, 72, 75, 77, 79, 82, 84, 87).map { AdaptationPitch(it, "황", 0) }

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
        val reshaped = draft.erase(0)
        assertEquals(false, reshaped.originalPitchLayoutMatches())
        assertTrue(reshaped.sourcePitchNotes().none { it.midi == 88 }, "Reused position IDs must not pick a different original note")
    }
}

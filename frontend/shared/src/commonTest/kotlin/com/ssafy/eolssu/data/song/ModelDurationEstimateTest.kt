package com.ssafy.eolssu.data.song

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModelDurationEstimateTest {
    @Test fun twoExplicitAccelerationsIncludeTheSlowerStartAndPreserveTheTargetAfterEachRamp() {
        val duration = estimatedModelDurationSeconds(472, 1.0, 160.0, listOf(
            ModelTempoChange(100, 160.0, 116, 80.0),
            ModelTempoChange(228, 160.0, 244, 80.0),
        ))
        val expected = 440 * 60.0 / 160 + 2 * 16 * 60.0 * ln(2.0) / 80
        assertEquals(181.6355323334387, expected, 1e-9)
        assertEquals(expected, assertNotNull(duration), 1e-9)
    }

    @Test fun savedPlanUsesCellTempoUnitsAndAllowsTheScoreEndAsTheRampBoundary() {
        val source = smallScoreData().let { it.copy(music = it.music.copy(ticksPerBeat = 12)) }
        val expressions = expressions("""[{"gak":1,"cell":2,"start_bpm":90,"bpm":360,"end_gak":3,"end_cell":1}]""")
        // One cell at 60 dotted-quarter/min, then two cells at 30 -> 120 dotted-quarter/min.
        val expected = (1.0 / 3 + 4 * ln(4.0) / 9) * 1000
        assertEquals(expected, assertNotNull(source.estimatedModelDurationMs(expressions, 60)), 1e-9)
    }

    @Test fun emptyExplicitPlanRetainsTheBaseTempoButMissingPlanHasNoEstimate() {
        val source = smallScoreData()
        assertEquals(3000.0, source.estimatedModelDurationMs(expressions("[]"), 60))
        assertNull(source.estimatedModelDurationMs(null, 60))
        assertNull(source.estimatedModelDurationMs(Json.parseToJsonElement("{}").jsonObject, 60))
    }

    @Test fun unsupportedTimingAndInvalidPlansFallBackWithoutInventingADuration() {
        val source = smallScoreData()
        val invalidTempos = listOf(
            """[{"gak":1,"cell":1,"bpm":0}]""",
            """[{"gak":1,"cell":1,"bpm":120,"start_bpm":60}]""",
            """[{"gak":1,"cell":1,"bpm":120,"end_gak":3}]""",
            """[{"gak":3,"cell":1,"bpm":120}]""",
            """[{"gak":1,"cell":1,"bpm":120,"end_gak":1,"end_cell":1}]""",
            """[{"gak":1,"cell":1,"bpm":120},{"gak":1,"cell":1,"bpm":90}]""",
            """[{"gak":1,"cell":1,"bpm":120,"end_gak":2,"end_cell":1},{"gak":1,"cell":2,"bpm":90}]""",
            """[{"gak":1,"cell":1}]""",
            """[null]""",
            """{}""",
        )
        for (tempos in invalidTempos) assertNull(source.estimatedModelDurationMs(expressions(tempos), 60), tempos)
        val repeated = Json.parseToJsonElement("""{"performancePlan":{"repeats":[{"start_gak":1,"end_gak":1,"times":2}],"tempos":[]}}""").jsonObject
        assertNull(source.estimatedModelDurationMs(repeated, 60))
        for (symbol in listOf("반복표", "도돌이표@2", "덧길이표", "반길이표")) {
            val marked = source.copy(events = source.events.map { it.copy(sigimsae = it.sigimsae + symbol) })
            assertNull(marked.estimatedModelDurationMs(expressions("[]"), 60), symbol)
        }
        assertNull(source.estimatedModelDurationMs(expressions("[]"), 0))
    }

    private fun expressions(tempos: String) =
        Json.parseToJsonElement("""{"performancePlan":{"tempos":$tempos}}""").jsonObject
}

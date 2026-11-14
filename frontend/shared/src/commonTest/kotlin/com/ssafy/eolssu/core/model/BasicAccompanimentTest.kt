package com.ssafy.eolssu.core.model

import com.ssafy.eolssu.core.audio.JangdanCursor
import com.ssafy.eolssu.core.audio.JangdanSample
import com.ssafy.eolssu.core.audio.planBasicAccompaniment
import kotlin.test.*

class BasicAccompanimentTest {
    private fun timing(unit: ScoreBpmUnit = ScoreBpmUnit.QUARTER) = ScoreTiming(311.13, "", unit, 1, 1)

    @Test fun everyPresetUsesTheWrittenClockWithoutRescalingIt() {
        val expectedCycles = listOf(2.0, 3.0, 3.0, 2.5, 2.5, 3.5, 3.5, 3.5)
        BasicAccompanimentPreset.entries.forEachIndexed { index, preset ->
            val setting = BasicAccompaniment(preset.pulseUnit, preset.groups, 0)
            val plan = assertNotNull(planBasicAccompaniment(setting, timing(), 60))
            assertEquals(expectedCycles[index], plan.cycleTicks)
            assertEquals(1_000.0, plan.msPerTick)
            assertEquals(preset.groups.sum(), plan.strikes.size)
        }
        val compound = BasicAccompaniment(BasicPulseUnit.DOTTED_QUARTER, listOf(2), 0)
        assertEquals(2.0, assertNotNull(planBasicAccompaniment(compound, timing(ScoreBpmUnit.DOTTED_QUARTER), 60)).cycleTicks)
        assertNull(planBasicAccompaniment(compound, timing(ScoreBpmUnit.JEONGGAN), 60))
        assertNull(planBasicAccompaniment(compound, timing(ScoreBpmUnit.JANGDAN_BEAT), 60))
    }

    @Test fun groupingAccentsAndPickupStartUseFractionalTicksAndResumeDoesNotRepeatAttacks() {
        val plan = assertNotNull(planBasicAccompaniment(BasicAccompaniment(BasicPulseUnit.EIGHTH, listOf(2, 3), 2), timing(), 60))
        assertEquals(listOf(0.0, 0.5, 1.0, 1.5, 2.0), plan.strikes.map { it.tick })
        assertEquals(listOf(JangdanSample.Deong, JangdanSample.Deok, JangdanSample.Kung, JangdanSample.Deok, JangdanSample.Deok), plan.strikes.map { it.clip.sample })
        val cursor = JangdanCursor(plan)
        assertTrue(cursor.due(1.5).isEmpty())
        assertEquals(listOf(2.0), cursor.due(2.0).map { it.tick })
        assertEquals(listOf(2.5), cursor.due(2.5).map { it.tick })
        assertEquals(listOf(4.5), cursor.due(4.5).map { it.tick })
        assertTrue(JangdanCursor(plan, resumeAfterTick = 2.5).due(2.5).isEmpty())
        assertEquals(0, plan.cellAt(4.5))
        assertEquals(-1, plan.cellAt(1.0))
    }
}

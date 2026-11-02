package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.pitchIndexOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LearnCurriculumTest {

    @Test
    fun nineStagesInOrderWithOnlyTheLastOptional() {
        assertEquals((1..9).toList(), LearnStages.map { it.number })
        assertEquals(listOf(9), LearnStages.filter { it.optional }.map { it.number })
        assertEquals(8, RequiredLearnStages.size)
        assertEquals("기본음", learnStageOf(4)?.title)
    }

    @Test
    fun everyInstrumentNoteIsInItsFingeringTable() {
        GuideInstrument.entries.forEach { instrument ->
            val content = instrument.learnContent
            (content.basic + content.scale).forEach { hanja ->
                assertNotNull(instrument.noteOf(hanja), "${instrument.id} $hanja")
            }
            content.ranges.forEach { range ->
                assertTrue(range.blow in instrument.blows, "${instrument.id} ${range.blow}")
                assertTrue(instrument.notesByPitch(range.blow).any { it.hanja == range.first }, "${instrument.id} ${range.first}")
            }
        }
    }

    @Test
    fun scaleRunsFromLowToHigh() {
        GuideInstrument.entries.forEach { instrument ->
            val pitches = instrument.learnContent.scale.map(::pitchIndexOf)
            assertEquals(pitches.sorted(), pitches, instrument.id)
            assertEquals(5, pitches.size)
        }
    }

    @Test
    fun patternsFollowWireframeOrder() {
        val scale = GuideInstrument.Danso.learnContent.scale
        assertEquals(listOf("仲", "林", "無", "潢", "汰"), LearnPattern.Ascending.notes(scale))
        assertEquals(listOf("汰", "潢", "無", "林", "仲"), LearnPattern.Descending.notes(scale))
        assertEquals(listOf("仲", "無", "汰", "無", "仲"), LearnPattern.SkipOne.notes(scale))
        assertEquals(listOf("仲", "潢", "林", "汰", "林"), LearnPattern.SkipTwo.notes(scale))
        assertEquals(listOf("無", "林", "仲", "林", "無"), LearnPattern.ThreeNoteSong.notes(scale))
    }

    @Test
    fun onlyArirangAndDorajiHaveEvaluableSampleSongs() {
        val titles = LearnSongs.map { it.title }.toSet()
        assertEquals(setOf("아리랑", "도라지타령"), SampleSongs.filter { it.evaluable && it.title in titles }.map { it.title }.toSet())
    }
}

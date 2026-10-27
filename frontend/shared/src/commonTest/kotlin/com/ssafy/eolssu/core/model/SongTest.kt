package com.ssafy.eolssu.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SongTest {

    private fun song(columns: Int, bpm: Int) = Song(
        id = "sample",
        title = "예시",
        level = SongLevel.Intro,
        jangdan = Jangdan("세마치", daegang = listOf(3, 3, 3), beats = 3, subdivision = 3),
        baseBpm = bpm,
        description = "",
        evaluable = true,
        solfege = null,
        score = Score(List(columns) { List(9) { ScoreCell(ScoreNote.Pitch(Yul.Hwangjong)) } }),
    )

    @Test
    fun durationUsesCellCountAndTempo() {
        // 16각 × 9정간 = 144정간, 60BPM 세마치에서 정간 하나는 1/3초 → 48초
        assertEquals(48, song(columns = 16, bpm = 60).durationSeconds())
        // 같은 곡을 느리게(0.6배 = 36BPM) → 80초
        assertEquals(80, song(columns = 16, bpm = 60).durationSeconds(bpm = 36))
    }

    @Test
    fun timelineUsesSongIdForEventIds() {
        assertEquals("sample-c0-r0-s0", song(columns = 1, bpm = 60).timeline.first().id)
    }
}

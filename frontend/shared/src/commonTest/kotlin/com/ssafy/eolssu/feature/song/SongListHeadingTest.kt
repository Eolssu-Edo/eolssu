package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.feature.song.component.songHeadingCharAlpha
import kotlin.test.Test
import kotlin.test.assertEquals

/** SONG-01 머리 문구를 앞 글자부터 한 글자씩 적는 투명도 계산(S15P31E102-1280). */
class SongListHeadingTest {
    @Test
    fun 적기_전에는_모든_글자가_보이지_않는다() {
        assertEquals(listOf(0f, 0f, 0f), (0..2).map { songHeadingCharAlpha(it, 0f) })
    }

    @Test
    fun 적는_중인_글자만_반쯤_보이고_앞_글자는_다_보이며_뒤_글자는_보이지_않는다() {
        assertEquals(listOf(1f, .5f, 0f), (0..2).map { songHeadingCharAlpha(it, 1.5f) })
    }

    @Test
    fun 다_적으면_모든_글자가_보인다() {
        assertEquals(listOf(1f, 1f, 1f), (0..2).map { songHeadingCharAlpha(it, 3f) })
    }
}

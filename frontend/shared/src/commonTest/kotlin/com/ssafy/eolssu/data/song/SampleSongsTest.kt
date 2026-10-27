package com.ssafy.eolssu.data.song

import kotlin.test.Test
import kotlin.test.assertEquals

/** 예시 악보가 와이어프레임(data.js)과 같게 만들어지는지. 기대값은 와이어프레임 페이지에서 뽑았다. */
class SampleSongsTest {

    private fun song(id: String) = SampleSongs.first { it.id == id }

    private fun column(id: String, c: Int) = song(id).score.columns[c].map { cell -> cell.notes.joinToString("") { it.symbol } }

    @Test
    fun generatedScoreMatchesWireframe() {
        assertEquals(listOf("太", "ㅡ", "仲", "ㅡ", "太", "ㅡ", "黃", "ㅡ", "ㅡ"), column("doraji", 0))
        assertEquals(listOf("南", "潢", "ㅡ", "潢", "ㅡ", "南", "ㅡ", "南", "南"), column("doraji", 7))
        assertEquals("林仲仲ㅡ太仲太ㅡㅡ太△黃ㅡㅡ黃ㅡㅡㅡ太黃", column("yeominrak", 0).joinToString(""))
    }

    @Test
    fun scoreSizesMatchWireframe() {
        val sizes = SampleSongs.map { "${it.id}:${it.score.columns.size}x${it.score.columns[0].size}" }
        assertEquals(
            listOf(
                "arirang:16x9", "doraji:8x9", "milyang:10x9", "niliriya:8x12", "hanobaek:7x12",
                "taryeong:9x12", "dodeuri:8x6", "seryeongsan:6x10", "gagok16:5x16", "yeominrak:6x20",
            ),
            sizes,
        )
    }

    @Test
    fun durationsMatchWireframe() {
        assertEquals(listOf(48, 22, 24, 36, 105, 36, 36, 72, 120, 160), SampleSongs.map { it.durationSeconds() })
    }

    @Test
    fun splitCellsOfArirangArePreserved() {
        assertEquals("太仲", column("arirang", 1)[5])
    }
}

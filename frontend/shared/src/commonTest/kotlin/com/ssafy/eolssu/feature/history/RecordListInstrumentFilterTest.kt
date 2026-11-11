package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.song.CatalogInstrument
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 기록 목록(RECORD-01)의 악기 필터: 곡 정보로 악기를 찾아, 고른 악기의 곡 묶음만 보인다(S15P31E102-631). */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RecordListInstrumentFilterTest : CatalogViewModelTest() {
    private fun record(id: String, songId: String, hour: Int) = PracticeRecord(
        id, songId, "아리랑", null, null, KoreanDateTime(2026, 11, 9, hour, 0), 48, 60, 70, emptyList(), "", emptyList(),
    )

    // 같은 '아리랑'이 악기마다 다른 곡 ID로 있다. hidden은 공개 목록에 없어 곡 상세로 찾는다.
    private val records = listOf(record("r1", "danso-arirang", 12), record("r2", "sogeum-arirang", 11), record("r3", "hidden-arirang", 10), record("r4", "mine", 9))

    private class Records(list: List<PracticeRecord>) : PracticeRecordRepository {
        override val records: StateFlow<List<PracticeRecord>> = MutableStateFlow(list)
        override suspend fun saveEvaluation(result: EvaluationResult, songTitle: String, memo: String) = error("쓰지 않음")
        override suspend fun updateMemo(recordId: String, memo: String) = Unit
        override suspend fun refresh(): Boolean = true
    }

    private class Songs : SongRepository {
        val details = mutableListOf<String>()
        override suspend fun getInstruments() = listOf(
            CatalogInstrument("i-danso", "DANSO", "단소"),
            CatalogInstrument("i-sogeum", "SOGEUM", "소금"),
            CatalogInstrument("i-jeongak", "JEONGAK_DAEGEUM", "정악대금"),
        )
        override suspend fun getSongs(instrumentId: String?) = listOf(
            SongSummary("danso-arirang", "아리랑", 1, "세마치", 60, true, instrumentId = "i-danso"),
            SongSummary("sogeum-arirang", "아리랑", 1, "세마치", 60, true, instrumentId = "i-sogeum"),
        )
        override suspend fun getSong(songId: String): Song? {
            details += songId
            return if (songId == "hidden-arirang") SampleSongs.first().copy(id = songId, instrumentId = "i-jeongak") else null
        }
    }

    @Test
    fun filtersSongGroupsByTheSongInstrument() = runTest {
        val songs = Songs()
        val model = keep(RecordListViewModel(null, Records(records), now = { 0L }, songs = songs))
        runCurrent()
        val state = model.uiState.value
        assertEquals(
            mapOf("danso-arirang" to Instrument.Danso, "sogeum-arirang" to Instrument.Sogeum, "hidden-arirang" to Instrument.DaegeumJeongak),
            state.songInstruments,
        )
        // 공개 목록에 없는 곡만 상세로 한 번씩 찾는다.
        assertEquals(listOf("hidden-arirang", "mine"), songs.details.sorted())
        assertEquals(4, state.groups.size)

        model.changeInstrument(Instrument.Sogeum)
        assertEquals(listOf("sogeum-arirang"), model.uiState.value.groups.map { it.songId })
        assertTrue(model.uiState.value.isOpen("sogeum-arirang"))
        assertEquals("r2", model.uiState.value.selectedRecordId)

        model.changeInstrument(Instrument.DaegeumJeongak)
        assertEquals(listOf("hidden-arirang"), model.uiState.value.groups.map { it.songId })

        // 기록이 없는 악기는 빈 묶음, 전체로 돌아오면 악기를 모르는 곡까지 모두 보인다.
        model.changeInstrument(Instrument.DaegeumSanjo)
        assertTrue(model.uiState.value.groups.isEmpty())
        model.changeInstrument(null)
        assertEquals(listOf("danso-arirang", "sogeum-arirang", "hidden-arirang", "mine"), model.uiState.value.groups.map { it.songId })
    }
}

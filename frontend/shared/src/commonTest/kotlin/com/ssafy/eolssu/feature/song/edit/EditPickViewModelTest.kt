package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetOrigin
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.data.song.ApiMySheetRepository
import com.ssafy.eolssu.data.song.PublishedSheetCopyProblem
import com.ssafy.eolssu.data.song.PublishedSheetCopyRepository
import com.ssafy.eolssu.data.song.PublishedSheetCopyResult
import com.ssafy.eolssu.data.song.CatalogFingerings
import com.ssafy.eolssu.data.song.CatalogInstrument
import com.ssafy.eolssu.data.song.CatalogJangdanDetail
import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import com.ssafy.eolssu.data.song.CatalogNotationSymbol
import com.ssafy.eolssu.data.song.CatalogPitch
import com.ssafy.eolssu.data.song.EditableSong
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.ReferenceCatalogRepository
import com.ssafy.eolssu.data.song.SampleMySheetRepository
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.song.smallScoreData
import com.ssafy.eolssu.testing.CatalogViewModelTest
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class EditPickViewModelTest : CatalogViewModelTest() {
    private fun sheet(id: String) = MySheet(id, MySheetOrigin.Imported, "악보 $id", updatedAt = KoreanDateTime(2026, 11, 1, 10, 0),
        instrumentCode = when (id) { "31" -> "DANSO"; "32" -> "SOGEUM"; else -> null })

    private val mySheets = object : MySheetRepository {
        override val sheets = MutableStateFlow(listOf(sheet("31"), sheet("32"), sheet("33")))
        override suspend fun delete(sheetId: String) = Unit
    }

    private val songs = object : SongRepository {
        val asked = mutableListOf<String?>()
        override suspend fun getInstruments() = listOf(CatalogInstrument("1", "DANSO", "단소"), CatalogInstrument("2", "SOGEUM", "소금"))
        override suspend fun getSongs(instrumentId: String?): List<SongSummary> {
            asked += instrumentId
            return listOf(SongSummary("10$instrumentId", "곡 $instrumentId", 1, "세마치", 60, true, instrumentId))
        }
        override suspend fun getSong(songId: String): com.ssafy.eolssu.core.model.Song? = null
    }

    @Test
    fun providedSongsCanBeSelectedBeforePersonalSheetsFinishLoading() = runTest {
        val refresh = CompletableDeferred<Boolean>()
        val slowSheets = object : MySheetRepository {
            override val sheets = mySheets.sheets
            override suspend fun refresh() = refresh.await()
            override suspend fun delete(sheetId: String) = Unit
        }
        val model = keep(EditPickViewModel(slowSheets, songs))
        model.selectInstrument(Instrument.Danso)
        runCurrent()
        assertEquals(EditPickLoad.Ready, model.uiState.value.load, "제공 곡은 내 악보 응답을 기다리지 않는다")
        model.toggle(EditPickTarget.Song("101"))
        assertTrue(model.uiState.value.canSelect)
        model.selectTab(EditPickTab.Mine)
        assertEquals(EditPickLoad.Loading, model.uiState.value.load)
        assertFalse(model.uiState.value.canSelect)
        refresh.complete(true)
        runCurrent()
        assertEquals(listOf("31"), model.uiState.value.sheets.map { it.id })
    }

    @Test
    fun songFailureDoesNotBlockPersonalSheetsAndRetryOnlyReloadsTheFailedTab() = runTest {
        var attempts = 0
        var sheetRefreshes = 0
        val failingSongs = object : SongRepository by songs {
            override suspend fun getSongs(instrumentId: String?): List<SongSummary> {
                attempts++
                if (attempts == 1) error("곡 목록 오류")
                return songs.getSongs(instrumentId)
            }
        }
        val countedSheets = object : MySheetRepository by mySheets {
            override suspend fun refresh(): Boolean { sheetRefreshes++; return true }
        }
        val model = keep(EditPickViewModel(countedSheets, failingSongs))
        model.selectInstrument(Instrument.Danso)
        runCurrent()
        assertEquals(EditPickLoad.Failed, model.uiState.value.load)
        model.selectTab(EditPickTab.Mine)
        assertEquals(EditPickLoad.Ready, model.uiState.value.load, "제공 곡 실패와 관계없이 내 악보를 연다")
        assertEquals(listOf("31"), model.uiState.value.sheets.map { it.id })
        model.selectTab(EditPickTab.Songs)
        model.retry()
        runCurrent()
        assertEquals(EditPickLoad.Ready, model.uiState.value.load)
        assertEquals(2, attempts)
        assertEquals(1, sheetRefreshes, "성공한 탭은 재조회하지 않는다")
    }

    @Test
    fun lateResponseCannotReplaceTheLatestListAfterSwitchingBackToTheSameInstrument() = runTest {
        val first = CompletableDeferred<List<SongSummary>>()
        val latest = CompletableDeferred<List<SongSummary>>()
        var dansoRequests = 0
        val delayed = object : SongRepository by songs {
            override suspend fun getSongs(instrumentId: String?): List<SongSummary> =
                if (instrumentId != "1") songs.getSongs(instrumentId)
                else withContext(NonCancellable) {
                    if (++dansoRequests == 1) first.await() else latest.await()
                }
        }
        val model = keep(EditPickViewModel(mySheets, delayed))
        model.selectInstrument(Instrument.Danso); runCurrent()
        model.selectInstrument(Instrument.Sogeum); runCurrent()
        model.selectInstrument(Instrument.Danso); runCurrent()
        latest.complete(listOf(SongSummary("201", "최신 곡", 1, "세마치", 60, true, "1")))
        runCurrent()
        first.complete(listOf(SongSummary("101", "이전 곡", 1, "세마치", 60, true, "1")))
        runCurrent()
        assertEquals(listOf("201"), model.uiState.value.songs.map { it.id })
        assertEquals(EditPickLoad.Ready, model.uiState.value.load)
    }

    @Test
    fun changingAccountWhileListsAreLoadingClearsBothTabs() = runTest {
        val member = SignedInMember(FakeKeyValueStore()).apply { set("1") }
        val response = CompletableDeferred<Boolean>()
        val delayed = object : MySheetRepository by mySheets {
            override suspend fun refresh() = response.await()
        }
        val model = keep(EditPickViewModel(delayed, songs, member = member))
        model.selectInstrument(Instrument.Danso); runCurrent()
        member.set("2")
        response.complete(true)
        runCurrent()
        assertEquals(EditPickUiState(), model.uiState.value)
    }

    @Test
    fun showsOnlyTheChosenInstrumentsSheetsAndSongs() = runTest {
        val model = keep(EditPickViewModel(mySheets, songs))
        runCurrent()
        assertEquals(EditPickLoad.NeedsInstrument, model.uiState.value.load, "악기를 고르기 전에는 악보를 받지 않는다")
        assertTrue(songs.asked.isEmpty())

        model.selectInstrument(Instrument.Danso); runCurrent()
        val danso = model.uiState.value
        assertEquals(EditPickLoad.Ready, danso.load)
        assertEquals(listOf<String?>("1"), songs.asked.toList(), "제공 곡은 서버 악기 ID로 거른다")
        assertEquals(listOf("101"), danso.songs.map { it.id })
        assertEquals(listOf("31"), danso.sheets.map { it.id }, "내 악보는 목록의 악기로 거른다")
        assertEquals(1, danso.uncheckedSheets, "읽지 못한 악보는 어느 악기에도 넣지 않는다")

        model.toggle(EditPickTarget.Sheet("31"))
        model.selectInstrument(Instrument.Sogeum); runCurrent()
        val sogeum = model.uiState.value
        assertNull(sogeum.picked)
        assertEquals(listOf("102"), sogeum.songs.map { it.id })
        assertEquals(listOf("32"), sogeum.sheets.map { it.id })
    }

    @Test
    fun usesOnlyListMetadataEvenWhenCodesAreMissingOrDraftsChange() = runTest {
        val paths = mutableListOf<String>()
        var draftCode = "SOGEUM"
        val client = createHttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            paths += path
            if (path != "/api/users/me/sheets") return@MockEngine respond("{}", HttpStatusCode.ServiceUnavailable)
            val result = """{"items":[
                {"sheetId":"31","kind":"JEONGGANBO","title":"단소","status":"NEEDS_REVIEW","source":"MANUAL","updatedAt":"2026-11-05T16:00:00Z","instrumentCode":"DANSO"},
                {"sheetId":"32","kind":"JEONGGANBO","title":"초안","status":"NEEDS_REVIEW","source":"MANUAL","updatedAt":"2026-11-05T16:00:00Z","hasDraft":true,"instrumentCode":"$draftCode"},
                {"sheetId":"33","kind":"JEONGGANBO","title":"코드 null","status":"NEEDS_REVIEW","source":"MANUAL","updatedAt":"2026-11-05T16:00:00Z","instrumentCode":null},
                {"sheetId":"34","kind":"JEONGGANBO","title":"코드 누락","status":"NEEDS_REVIEW","source":"MANUAL","updatedAt":"2026-11-05T16:00:00Z"},
                {"sheetId":"35","kind":"JEONGGANBO","title":"코드 공백","status":"NEEDS_REVIEW","source":"MANUAL","updatedAt":"2026-11-05T16:00:00Z","instrumentCode":" "}
            ],"page":0,"totalPages":1}"""
            respond("""{"isSuccess":true,"code":200,"result":$result}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val model = keep(EditPickViewModel(ApiMySheetRepository(client), songs))
            model.selectInstrument(Instrument.Danso)
            val danso = model.uiState.first { it.sheetsLoad == EditPickLoad.Ready }
            assertEquals(listOf("31"), danso.sheets.map { it.id })
            assertEquals(3, danso.uncheckedSheets)
            assertEquals(listOf("/api/users/me/sheets"), paths, "목록 선택에서는 악보 본문을 요청하지 않는다")

            draftCode = "DANSO"
            model.selectInstrument(Instrument.Sogeum)
            assertTrue(model.uiState.first { it.sheetsLoad == EditPickLoad.Ready }.sheets.isEmpty())
            model.selectInstrument(Instrument.Danso)
            assertEquals(listOf("31", "32"), model.uiState.first { it.sheetsLoad == EditPickLoad.Ready }.sheets.map { it.id },
                "수정 시각이 같아도 현재 목록의 초안 악기를 따른다")
            assertEquals(List(3) { "/api/users/me/sheets" }, paths)
            model.selectTab(EditPickTab.Mine)
            model.toggle(EditPickTarget.Sheet("32"))
            var opened: String? = null
            model.confirmSelection { opened = it }
            assertEquals("32", opened, "편집할 ID만 전달하고 본문 조회는 편집기에 맡긴다")
            assertEquals(3, paths.size)
        } finally { client.close() }
    }

    @Test
    fun listRefreshFailureShowsRetryWithoutLoadingScoresOrUsingCachedRows() = runTest {
        var available = true
        val paths = mutableListOf<String>()
        val client = createHttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            paths += path
            if (!available || path != "/api/users/me/sheets") return@MockEngine respond("{}", HttpStatusCode.ServiceUnavailable)
            val result = """{"items":[{"sheetId":"31","kind":"JEONGGANBO","title":"단소","status":"NEEDS_REVIEW","source":"MANUAL","updatedAt":"2026-11-05T16:00:00Z","instrumentCode":"DANSO"}],"page":0,"totalPages":1}"""
            respond("""{"isSuccess":true,"code":200,"result":$result}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val model = keep(EditPickViewModel(ApiMySheetRepository(client), songs))
            model.selectInstrument(Instrument.Sogeum)
            model.uiState.first { it.sheetsLoad == EditPickLoad.Ready }
            available = false
            model.selectInstrument(Instrument.Danso)
            model.selectTab(EditPickTab.Mine)
            val failed = model.uiState.first { it.sheetsLoad != EditPickLoad.Loading }
            assertEquals(EditPickLoad.Failed, failed.sheetsLoad)
            assertTrue(failed.sheets.isEmpty(), "갱신 실패를 오래된 목록이나 본문 조회로 대신하지 않는다")
            assertEquals(List(2) { "/api/users/me/sheets" }, paths)
            available = true
            model.retry()
            assertEquals(listOf("31"), model.uiState.first { it.sheetsLoad == EditPickLoad.Ready }.sheets.map { it.id })
            assertEquals(List(3) { "/api/users/me/sheets" }, paths)
        } finally { client.close() }
    }

    @Test
    fun onlyProvidedSongUsesCopyAndRetriesTheSameRequestBeforeOpeningThePersonalId() = runTest {
        val calls = mutableListOf<Pair<String, String>>()
        val firstResponse = CompletableDeferred<PublishedSheetCopyResult>()
        val repository = PublishedSheetCopyRepository { id, key ->
            calls += id to key
            if (calls.size == 1) firstResponse.await() else PublishedSheetCopyResult.Created("71")
        }
        val model = keep(EditPickViewModel(mySheets, songs, copies = repository))
        val opened = mutableListOf<String>()
        model.selectInstrument(Instrument.Danso)
        runCurrent()
        assertEquals(EditPickTab.Songs, model.uiState.value.tab, "처음에는 첫 탭인 제공 곡을 보인다")

        model.selectTab(EditPickTab.Mine)
        model.toggle(EditPickTarget.Sheet("31"))
        model.confirmSelection { opened += it }
        assertEquals(listOf("31"), opened)
        assertTrue(calls.isEmpty())

        model.selectTab(EditPickTab.Songs)
        model.toggle(EditPickTarget.Song("101"))
        model.confirmSelection { opened += it }
        model.confirmSelection { opened += it }
        runCurrent()
        assertTrue(model.uiState.value.copying)
        assertEquals(1, calls.size)
        assertEquals("101", calls.single().first)
        assertEquals(listOf("31"), opened)

        firstResponse.complete(PublishedSheetCopyResult.Rejected(PublishedSheetCopyProblem.Failed))
        runCurrent()
        assertEquals(PublishedSheetCopyProblem.Failed, model.uiState.value.copyProblem)
        model.confirmSelection { opened += it }
        runCurrent()
        assertEquals(listOf(calls.first(), calls.first()), calls)
        assertEquals(listOf("31", "71"), opened)
    }

    @Test
    fun changingAccountWhileCopyIsInProgressDoesNotOpenTheOldAccountsSheet() = runTest {
        val member = SignedInMember(FakeKeyValueStore()).apply { set("1") }
        val response = CompletableDeferred<PublishedSheetCopyResult>()
        val model = keep(EditPickViewModel(mySheets, songs,
            copies = PublishedSheetCopyRepository { _, _ -> response.await() }, member = member))
        var opened: String? = null
        model.selectInstrument(Instrument.Danso)
        runCurrent()
        model.selectTab(EditPickTab.Songs)
        model.toggle(EditPickTarget.Song("101"))
        model.confirmSelection { opened = it }
        runCurrent()
        assertTrue(model.uiState.value.copying)
        member.set("2")
        response.complete(PublishedSheetCopyResult.Created("71"))
        runCurrent()
        assertNull(opened)
        assertEquals(EditPickLoad.NeedsInstrument, model.uiState.value.load)
        assertFalse(model.uiState.value.copying)
    }

    @Test
    fun songCopyOpensTheRealScoreAndAsksBeforeDroppingEdits() = runTest {
        val repository = object : SongRepository {
            override suspend fun getInstruments() = emptyList<CatalogInstrument>()
            override suspend fun getSongs(instrumentId: String?) = emptyList<SongSummary>()
            override suspend fun getSong(songId: String): com.ssafy.eolssu.core.model.Song? = null
            override suspend fun getEditableSong(songId: String) = EditableSong(SampleSongs.first { it.id == "arirang" }, smallScoreData())
        }
        val model = keep(SheetEditViewModel(EditSource.CopySong("7"), EditView.Jeongganbo, SampleMySheetRepository(), songs = repository))
        assertTrue(model.uiState.value.documentLoading, "원본을 받기 전에는 예시 악보를 보이지 않는다")
        runCurrent()
        val draft = model.uiState.value.draft
        assertEquals(smallScoreData(), draft.original, "고른 곡의 공통 악보를 그대로 연다")
        assertEquals("아리랑 (내 편곡)", draft.title)

        var closed = false
        model.selectCell(2); model.put(ScoreNote.Pitch(Yul.Taeju))
        model.closeEditor { closed = true }; runCurrent()
        assertFalse(closed, "저장할 수 없는 복사본을 고쳤으면 닫기 전에 묻는다")
        assertTrue(model.uiState.value.closeWarning)
        model.discardChangesAndClose { closed = true }
        assertTrue(closed)
    }

    @Test
    fun registeredPitchListLimitsYulKeys() = runTest {
        val catalog = object : ReferenceCatalogRepository {
            override suspend fun pitches(instrumentId: String) = error("악기별 목록은 쓰지 않는다")
            override suspend fun allPitches() = listOf(CatalogPitch("1", "태", 2, "㶃", "중청태", 26))
            override suspend fun fingerings(instrumentId: String, pitchId: String?): CatalogFingerings = error("unused")
            override suspend fun jangdans(): List<CatalogJangdanSummary> = error("unused")
            override suspend fun jangdan(jangdanId: String): CatalogJangdanDetail? = error("unused")
            override suspend fun symbols(): List<CatalogNotationSymbol> = error("unused")
        }
        val model = keep(SheetEditViewModel(EditSource.CopySheet("x"), EditView.Jeongganbo, SampleMySheetRepository(), pitchCatalog = catalog))
        runCurrent()
        assertEquals(setOf("태" to 2), model.uiState.value.availablePitches)
        val before = model.uiState.value.draft
        model.selectRegister(YulRegister.Cheong); model.putYul(Yul.Taeju)
        assertEquals(before, model.uiState.value.draft, "등록되지 않은 청태는 적지 않는다")
        model.selectRegister(YulRegister.JungCheong); model.putYul(Yul.Taeju)
        assertEquals(ScoreNote.SourcePitch("태", 2), model.uiState.value.draft.cells[0].single())
    }
}

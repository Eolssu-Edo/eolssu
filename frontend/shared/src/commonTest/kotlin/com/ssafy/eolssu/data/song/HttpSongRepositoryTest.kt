package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 곡 조회 API 응답(운영 시드 단소 4곡)을 앱 곡·악보로 바꾸는지. 서버는 MockEngine으로 대신한다. */
class HttpSongRepositoryTest {
    private val paths = mutableListOf<String>()

    /** [reply]가 null이면 연결 실패다. */
    private fun repository(reply: (path: String) -> Pair<HttpStatusCode, String>?) = HttpSongRepository(
        createHttpClient(
            MockEngine { request ->
                paths += request.url.encodedPath
                val (status, body) = reply(request.url.encodedPath) ?: error("연결 실패")
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
            ServerAddress.Production,
        ),
    )

    private fun detail(json: String) = """{"httpStatus":"OK","isSuccess":true,"code":200,"result":$json}"""

    private val fixtures = listOf(Song1Notes, Song2Notes, Song3Notes, Song4Notes)

    @Test
    fun listConvertsAllSeedSongsWithNotesInServerOrder() = runTest {
        val songs = repository { HttpStatusCode.OK to SongListJson }.getSongs()

        assertEquals(listOf("1", "2", "3", "4"), songs.map { it.id })
        assertEquals(listOf("아리랑", "도라지타령", "밀양아리랑", "늴리리야"), songs.map { it.title })
        assertEquals(listOf(SongLevel.Intro, SongLevel.Intro, SongLevel.Elementary, SongLevel.Elementary), songs.map { it.level })
        assertTrue(songs.all { it.evaluable })
        // 율명 음만 연주 순서로 센 번호가 평가 noteIdx이므로, 앱 시간축의 율명 순서가 서버 NOTE 이벤트 순서와 같아야 한다.
        songs.zip(fixtures).forEach { (song, notes) ->
            val pitches = song.timeline.mapNotNull { (it.note as? ScoreNote.Pitch)?.yul?.hangul }
            assertEquals(notes, pitches, song.title)
        }
        assertEquals(listOf(61, 91, 85, 68), fixtures.map { it.size })
    }

    @Test
    fun jangdanFollowsScoreTicksNotTheSourceLineLength() = runTest {
        val (arirang, doraji, _, nilliriya) = repository { HttpStatusCode.OK to SongListJson }.getSongs()

        // 아리랑: 정간 하나가 한 박(점4분음표)이고 한 각(원본 악보 한 줄)이 12정간 = 세마치 네 주기.
        assertEquals(1, assertNotNull(arirang.jangdan).subdivision)
        assertEquals(listOf(3, 3, 3, 3), assertNotNull(arirang.jangdan).daegang)
        assertNull(assertNotNull(arirang.jangdan).gueum)
        assertEquals(30, arirang.durationSeconds(96)) // 48정간 × 0.625초
        // 도라지타령: 한 각이 세마치 한 주기(3박).
        assertEquals(listOf(3), assertNotNull(doraji.jangdan).daegang)
        // 늴리리야: 한 박이 세 정간이고 한 각이 굿거리 한 주기라 장단 묶음과 구음을 그대로 쓴다.
        assertEquals(3, assertNotNull(nilliriya.jangdan).subdivision)
        assertEquals(listOf(3, 3, 3, 3), assertNotNull(nilliriya.jangdan).daegang)
        assertEquals(12, assertNotNull(nilliriya.jangdan).gueum?.size)
        assertEquals("굿거리", assertNotNull(nilliriya.jangdan).name)
    }

    @Test
    fun listFailureThrowsSoScreenOffersRetry() = runTest {
        assertFailsWith<SongsUnavailableException> { repository { HttpStatusCode.ServiceUnavailable to "{}" }.getSongs() }
        assertFailsWith<SongsUnavailableException> { repository { null }.getSongs() }
    }

    @Test
    fun detailIsFetchedOnceAndMissingOrUnreachableIsNull() = runTest {
        val repository = repository { path ->
            when (path) {
                "/api/songs/2" -> HttpStatusCode.OK to detail(Song2Json)
                "/api/songs/9" -> HttpStatusCode.NotFound to "{}"
                else -> null
            }
        }
        assertEquals("도라지타령", repository.getSong("2")?.title)
        repository.getSong("2")
        assertEquals(listOf("/api/songs/2"), paths)
        assertNull(repository.getSong("9"))
        assertNull(repository.getSong("5"))
    }

    @Test
    fun songsFromTheListAreNotFetchedAgain() = runTest {
        val repository = repository { path -> if (path == "/api/songs") HttpStatusCode.OK to SongListJson else null }
        repository.getSongs()
        assertEquals("늴리리야", repository.getSong("4")?.title)
        assertEquals(listOf("/api/songs"), paths)
    }

    @Test
    fun sampleSongIdsUseSampleDataWithoutServer() = runTest {
        val repository = repository { error("서버를 부르면 안 된다") }
        assertEquals("아리랑", repository.getSong("arirang")?.title)
        assertTrue(paths.isEmpty())
    }

    @Test
    fun songThatCannotBeDrawnIsLeftOut() = runTest {
        val unknownYul = Song1Json.replaceFirst("\"symbol\":\"중\"", "\"symbol\":\"夾\"").replace("\"songId\":\"1\"", "\"songId\":\"7\"")
        val splitRow = Song2Json.replaceFirst("\"col\":0", "\"col\":1").replace("\"songId\":\"2\"", "\"songId\":\"8\"")
        val list = """{"isSuccess":true,"code":200,"result":{"items":[$unknownYul,$splitRow,$Song3Json]}}"""
        assertEquals(listOf("3"), repository { HttpStatusCode.OK to list }.getSongs().map { it.id })
    }
}

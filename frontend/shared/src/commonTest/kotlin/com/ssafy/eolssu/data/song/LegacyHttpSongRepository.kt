package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/** 곡 목록을 받지 못했다(연결 실패·서버 오류). 화면은 다시 시도를 준다. */
class SongsUnavailableException : Exception("곡 목록을 받지 못했다")

/**
 * 공개 곡 목록·상세(`GET /api/songs`, `GET /api/songs/{songId}`). 서버 곡 ID는 숫자 문자열이다.
 * 숫자가 아닌 ID는 아직 예시 데이터인 연습 기록·내 악보가 가리키는 예시 곡이라 [fallback]이 맡는다. 받은 곡은 앱이 켜 있는 동안 둔다.
 * 앱 악보 모델로 바꿀 수 없는 곡([toSong]이 실패)은 목록에서 뺀다.
 */
class HttpSongRepository(
    private val client: HttpClient,
    private val fallback: SongRepository = SampleSongRepository(),
) {
    private val songs = mutableMapOf<String, Song>()

    /** 서버 곡 목록. 응답을 받지 못하면 [SongsUnavailableException]을 던진다. */
    suspend fun getSongs(): List<Song> {
        val response = client.sendOrNull { get("/api/songs") }
        if (response?.status != HttpStatusCode.OK) throw SongsUnavailableException()
        val items = response.apiResponse<SongList>()?.result?.items ?: throw SongsUnavailableException()
        return items.mapNotNull { runCatching { it.toSong() }.getOrNull() }.onEach { songs[it.id] = it }
    }

    /** 곡 하나. 없거나(404) 받지 못하면 null. */
    suspend fun getSong(songId: String): Song? {
        if (!songId.isServerId()) return fallback.getSong(songId)
        songs[songId]?.let { return it }
        val response = client.sendOrNull { get("/api/songs/$songId") }
        if (response?.status != HttpStatusCode.OK) return null
        val item = response.apiResponse<SongItem>()?.result ?: return null
        return runCatching { item.toSong() }.getOrNull()?.also { songs[songId] = it }
    }

    @Serializable
    private class SongList(val items: List<SongItem>)
}

@Serializable
internal class SongItem(
    val songId: String,
    val title: String,
    val description: String? = null,
    val difficulty: Int,
    val defaultBpm: Int,
    val isScorable: Boolean,
    val jangdan: JangdanItem,
    val score: ScoreData,
)

@Serializable
internal class JangdanItem(val name: String, val bpmUnit: String, val pattern: JangdanPattern)

@Serializable
internal class JangdanPattern(val beats: List<String> = emptyList(), val group: List<Int> = emptyList())

/** 공통 악보 데이터(score_data@1) 가운데 앱이 쓰는 부분. `events`는 정간 글자와 같은 순서라 화면에서는 글자만 읽는다. */
@Serializable
internal class ScoreData(val music: ScoreMusic, val columns: List<ScoreColumn>)

@Serializable
internal class ScoreMusic(val ticksPerBeat: Int, val ticksPerJeonggan: Int)

@Serializable
internal class ScoreColumn(val cells: List<ScoreCellData>)

@Serializable
internal class ScoreCellData(val units: List<ScoreUnit>)

/** 정간 안 글자. [row]는 정간을 가로로 나눈 줄, [col]은 줄을 세로로 나눈 칸이다. */
@Serializable
internal class ScoreUnit(val row: Int, val col: Int = 0, val symbol: String)

/**
 * 서버 곡을 앱 곡으로 바꾼다. 정간 글자를 연주 순서대로 옮기므로, 율명 글자의 순서가 서버 NOTE 이벤트 순서(평가 `noteIdx`)와 같다.
 * - 글자: 한글 율명(`중`·`청황`)은 [Yul], `-`(이음)와 빈 정간은 지속, `쉼`은 쉼이다. 모르는 글자나 한 줄을 다시 나눈 정간(col > 0)은 그릴 수 없어 실패한다.
 * - 각은 원본 악보의 줄이라 장단 한 주기와 다를 수 있다. 한 박의 정간 수는 `ticksPerBeat / ticksPerJeonggan`이고, 대강은 각이 장단 주기와 같으면
 *   장단의 묶음(`group`)을, 아니면 장단 한 주기의 정간 수로 각을 나눈다. 구음은 각이 장단 주기와 같을 때만 붙인다.
 * - 계이름은 곡·악기 기준 데이터가 없어 표시하지 않는다.
 */
internal fun SongItem.toSong(): Song {
    val music = score.music
    require(music.ticksPerJeonggan > 0 && music.ticksPerBeat % music.ticksPerJeonggan == 0) {
        "정간 길이가 박을 나누지 않는다: ${music.ticksPerBeat}/${music.ticksPerJeonggan}"
    }
    val perBeat = music.ticksPerBeat / music.ticksPerJeonggan
    val columns = score.columns.map { column -> column.cells.map { cell -> ScoreCell(cell.units.map { it.toNote() }) } }
    require(columns.isNotEmpty() && columns.all { it.isNotEmpty() }) { "빈 악보" }
    val gak = columns.maxOf { it.size }
    return Song(
        id = songId,
        title = title,
        level = when (difficulty) {
            1 -> SongLevel.Intro
            2 -> SongLevel.Elementary
            else -> SongLevel.Intermediate
        },
        jangdan = jangdan.toJangdan(gak, perBeat),
        baseBpm = defaultBpm,
        description = description.orEmpty(),
        evaluable = isScorable,
        solfege = null,
        score = Score(columns),
    )
}

private fun ScoreUnit.toNote(): ScoreNote {
    require(col == 0) { "한 줄을 다시 나눈 정간은 그릴 수 없다" }
    return when (symbol) {
        "-", "" -> ScoreNote.Hold
        "쉼" -> ScoreNote.Rest
        else -> ScoreNote.Pitch(requireNotNull(Yul.fromHangul(symbol)) { "모르는 율명: $symbol" })
    }
}

/** 장단 무늬(`beats`)는 점4분음표 장단이면 한 박을 세 칸으로, 그 밖에는 한 박을 한 칸으로 적는다. */
private fun JangdanItem.toJangdan(gak: Int, perBeat: Int): Jangdan {
    val cycleBeats = if (bpmUnit == "DOTTED_QUARTER") pattern.beats.size / 3 else pattern.beats.size
    val cycleCells = cycleBeats * perBeat
    val daegang = when {
        pattern.group.sum() == gak && pattern.group.all { it > 0 } -> pattern.group
        cycleCells in 1..gak && gak % cycleCells == 0 -> List(gak / cycleCells) { cycleCells }
        else -> listOf(gak)
    }
    return Jangdan(
        name = name,
        daegang = daegang,
        beats = maxOf(1, gak / perBeat),
        subdivision = perBeat,
        gueum = pattern.beats.takeIf { it.size == gak },
    )
}

private fun String.isServerId(): Boolean = isNotEmpty() && all { it in '0'..'9' }

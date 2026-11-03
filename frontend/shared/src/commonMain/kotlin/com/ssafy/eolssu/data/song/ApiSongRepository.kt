package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.model.timeline
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** CAT01/02 메타데이터와 CAT04 정본 악보. 식별자·버전·정수 tick을 보존한다. */
class ApiSongRepository(private val client: HttpClient) : SongRepository {
    override suspend fun getInstruments() = InstrumentCatalog(client).load()

    override suspend fun getSongs(instrumentId: String?): List<SongSummary> {
        if (instrumentId != null && !validCatalogId(instrumentId)) throw CatalogUnavailable()
        val response = client.sendOrNull {
            get("/api/songs") { instrumentId?.let { parameter("instrumentId", it) } }
        } ?: throw CatalogUnavailable()
        if (response.status.value != 200) throw CatalogUnavailable()
        val items = response.apiResponse<SongListResponse>()?.takeIf { it.isSuccess }?.result?.items
            ?: throw CatalogUnavailable()
        val songs = items.map { it.summary() }
        if (songs.map { it.id }.distinct().size != songs.size) throw CatalogUnavailable()
        if (instrumentId != null && songs.any { it.instrumentId != instrumentId }) throw CatalogUnavailable()
        return songs
    }

    override suspend fun getSong(songId: String): Song? {
        if (!validCatalogId(songId)) return null
        val response = client.sendOrNull { get("/api/songs/$songId") } ?: throw CatalogUnavailable()
        if (response.status.value == 404) return null
        if (response.status.value != 200) throw CatalogUnavailable()
        val item = response.apiResponse<CatalogSong>()?.takeIf { it.isSuccess }?.result ?: throw CatalogUnavailable()
        val summary = item.summary()
        if (summary.id != songId) throw CatalogUnavailable()
        val level = summary.level ?: throw CatalogUnavailable()
        val scoreResponse = client.sendOrNull { get("/api/songs/$songId/score") } ?: throw CatalogUnavailable()
        if (scoreResponse.status.value != 200) throw CatalogUnavailable()
        val scoreItem = scoreResponse.apiResponse<CatalogScore>()?.takeIf { it.isSuccess }?.result ?: throw CatalogUnavailable()
        if (scoreItem.songId != songId || scoreItem.scoreVersion != item.scoreVersion ||
            scoreItem.noteCount != item.noteCount || scoreItem.defaultBpm != item.defaultBpm ||
            (item.jangdan != null && scoreItem.bpmUnit != item.jangdan.bpmUnit) || (item.score != null && item.score != scoreItem.score)
        ) throw CatalogUnavailable()
        val common = CommonScoreData.decode(scoreItem.score.toString())
        if (common.schemaVersion != scoreItem.schemaVersion) throw CatalogUnavailable()
        val score = common.toScore()
        val timeline = score.timeline(songId)
        if (timeline.size != scoreItem.events.size || timeline.zip(scoreItem.events).any { (event, projected) ->
                event.id != projected.eventId || event.noteIndex != projected.noteIdx ||
                    event.startTick != projected.startTick || event.durationTick != projected.durationTick ||
                    event.column != projected.columnIdx || event.row != projected.cellIdx ||
                    (if (event.noteIndex == null) projected.kind != "REST" || projected.pitchId != null
                        else projected.kind != "NOTE" || projected.pitchId?.let(::validCatalogId) != true)
            }
        ) throw CatalogUnavailable()
        val jangdan = item.jangdan
        val timing = score.timing ?: throw CatalogUnavailable()
        val source = score.source ?: throw CatalogUnavailable()
        if (source.scoreVersion != item.scoreVersion || source.title != item.title ||
            timing.bpmUnit.name != scoreItem.bpmUnit ||
            (jangdan != null && (timing.jangdan != jangdan.name || timing.bpmUnit.name != jangdan.bpmUnit)) ||
            score.timeline(songId).count { it.noteIndex != null } != item.noteCount
        ) throw CatalogUnavailable()
        return Song(
            id = songId,
            title = item.title,
            level = level,
            jangdan = jangdan?.model(),
            baseBpm = item.defaultBpm,
            description = item.description.orEmpty(),
            evaluable = item.isScorable,
            solfege = null,
            score = score,
            instrumentId = item.instrumentId,
            scoreVersion = item.scoreVersion,
            noteCount = item.noteCount,
        )
    }
}

@Serializable
private class CatalogScore(
    val songId: String, val scoreVersion: Int, val schemaVersion: Int, val noteCount: Int,
    val defaultBpm: Int, val bpmUnit: String, val score: JsonObject, val events: List<CatalogScoreEvent>,
)

@Serializable
private class CatalogScoreEvent(
    val eventId: String, val kind: String, val noteIdx: Int?, val pitchId: String?,
    val startTick: Long, val durationTick: Long, val columnIdx: Int, val cellIdx: Int,
)

/** 내용을 꾸미거나 예시 곡으로 대체하지 않고 화면의 재시도 상태로 전달한다. */
class CatalogUnavailable : Exception("Catalog unavailable or invalid")

internal fun validCatalogId(id: String): Boolean = id.matches(Regex("[1-9][0-9]{0,18}")) && (id.toLongOrNull() ?: 0) > 0

@Serializable
private class SongListResponse(val items: List<CatalogSong>)

@Serializable
private class CatalogSong(
    val songId: String,
    val title: String,
    val instrumentId: String,
    val difficulty: Int,
    val defaultBpm: Int,
    val isScorable: Boolean,
    val noteCount: Int,
    val scoreVersion: Int,
    val description: String? = null,
    val jangdan: CatalogJangdan? = null,
    val score: JsonObject? = null,
) {
    fun summary(): SongSummary {
        if (!validCatalogId(songId) || !validCatalogId(instrumentId) || title.isBlank() ||
            difficulty !in 1..5 || defaultBpm <= 0 || jangdan?.name?.isBlank() == true || noteCount < 0 || scoreVersion <= 0
        ) throw CatalogUnavailable()
        return SongSummary(
            songId, title, difficulty, jangdan?.name ?: canonicalJangdanName(), defaultBpm, isScorable, instrumentId, scoreVersion,
            instrumentCode = instrumentCode(), noteCount = noteCount,
        )
    }

    /** 목록에 함께 온 공통 악보의 `score.instrument`(DANSO 등). 곡 수 옆 악기 표시에만 쓰고, 없으면 null이다. */
    private fun instrumentCode(): String? {
        val code = (score?.get("score") as? JsonObject)?.get("instrument") as? JsonPrimitive ?: return null
        return code.content.takeIf { code.isString && it.isNotBlank() }
    }

    private fun canonicalJangdanName(): String {
        val name = (score?.get("music") as? JsonObject)?.get("jangdan") as? JsonPrimitive ?: return ""
        return name.content.takeIf { name.isString }.orEmpty()
    }
}

@Serializable
private class CatalogJangdan(val jangdanId: String, val name: String, val bpmUnit: String, val pattern: Pattern) {
    fun model(): Jangdan {
        if (!validCatalogId(jangdanId) || name.isBlank() || pattern.group.isEmpty() || pattern.group.any { it <= 0 } ||
            pattern.group.sumOf { it.toLong() } != pattern.beats.size.toLong()
        ) throw CatalogUnavailable()
        // reference의 구음 칸: 점4분음표는 8분음표 3칸, 4분음표는 1칸이다. 악보 정간 시간과 별개다.
        val subdivision = when (bpmUnit) {
            "QUARTER" -> 1
            "DOTTED_QUARTER" -> 3
            else -> throw CatalogUnavailable()
        }
        if (pattern.beats.size % subdivision != 0) throw CatalogUnavailable()
        return Jangdan(name, pattern.group, pattern.beats.size / subdivision, subdivision, pattern.beats)
    }
}

@Serializable
private class Pattern(val beats: List<String>, val group: List<Int>)

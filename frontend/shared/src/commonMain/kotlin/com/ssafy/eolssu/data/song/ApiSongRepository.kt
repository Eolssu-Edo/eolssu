package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.model.timeline
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Task256의 공개 곡 API. 원본 score JSON의 식별자·버전·시간을 보존한다. */
class ApiSongRepository(private val client: HttpClient) : SongRepository {
    override suspend fun getSongs(): List<SongSummary> {
        val response = client.sendOrNull { get("/api/songs") } ?: throw CatalogUnavailable()
        if (response.status.value != 200) throw CatalogUnavailable()
        val items = response.apiResponse<SongListResponse>()?.takeIf { it.isSuccess }?.result?.items
            ?: throw CatalogUnavailable()
        val songs = items.map { it.summary() }
        if (songs.map { it.id }.distinct().size != songs.size) throw CatalogUnavailable()
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
        val score = CommonScoreData.decode(item.score?.toString() ?: throw CatalogUnavailable()).toScore()
        val jangdan = item.jangdan
        val timing = score.timing ?: throw CatalogUnavailable()
        val source = score.source ?: throw CatalogUnavailable()
        if (source.scoreVersion != item.scoreVersion || source.title != item.title ||
            timing.jangdan != jangdan.name || timing.bpmUnit.name != jangdan.bpmUnit ||
            score.timeline(songId).count { it.noteIndex != null } != item.noteCount
        ) throw CatalogUnavailable()
        return Song(
            id = songId,
            title = item.title,
            level = level,
            jangdan = jangdan.model(),
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
    val jangdan: CatalogJangdan,
    val score: JsonObject? = null,
) {
    fun summary(): SongSummary {
        if (!validCatalogId(songId) || !validCatalogId(instrumentId) || title.isBlank() ||
            difficulty !in 1..5 || defaultBpm <= 0 || jangdan.name.isBlank() || noteCount < 0 || scoreVersion <= 0
        ) throw CatalogUnavailable()
        return SongSummary(songId, title, difficulty, jangdan.name, defaultBpm, isScorable, instrumentId, scoreVersion, noteCount = noteCount)
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

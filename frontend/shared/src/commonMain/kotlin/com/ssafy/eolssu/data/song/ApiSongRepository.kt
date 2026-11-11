package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.diagnostics.PerfSource
import com.ssafy.eolssu.core.diagnostics.PerfTarget
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.diagnostics.measureLoad
import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.JangdanPlayback
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.model.timeline
import com.ssafy.eolssu.core.network.AccountSession
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.ensureAccountSession
import com.ssafy.eolssu.core.network.getPublicOrNull
import com.ssafy.eolssu.core.network.inSession
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** CAT01/02 메타데이터와 CAT04 정본 악보. 식별자·버전·정수 tick을 보존한다. */
class ApiSongRepository(
    private val client: HttpClient,
    private val member: SignedInMember? = null,
) : SongRepository {
    private data class RetainedSong(
        val account: AccountSession,
        val song: Song,
        val detail: CatalogSong,
        val published: Boolean,
    )

    private data class Cache(val request: Any? = null, val entry: RetainedSong? = null)

    private val cache = MutableStateFlow(Cache())

    init {
        member?.onChange { cache.value = Cache() }
    }

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

    override suspend fun getSong(songId: String): Song? = load(songId)?.song

    override suspend fun getPublishedScoreVersion(songId: String): Int? = member.inSession {
        val account = member?.session?.value?.takeIf { it.memberId != null }
        ensureAccountSession()
        val request = beginLoad(account, songId, published = true)
        val current = loadCatalogSong(songId)
        ensureAccountSession()
        if (request != null && current != null) {
            cache.update { state ->
                val entry = state.entry
                if (state.request !== request || entry == null || entry.account != account ||
                    entry.song.id != songId || entry.song.scoreVersion != current.scoreVersion
                ) return@update state
                // 곡 제목이나 채점 가능 여부는 악보 판본을 유지한 채 바뀔 수 있다.
                state.copy(entry = entry.takeIf { it.detail == current }?.copy(published = true))
            }
        }
        current?.scoreVersion
    }

    override suspend fun getSong(songId: String, scoreVersion: Int): Song? {
        if (scoreVersion <= 0) throw CatalogUnavailable()
        return load(songId, scoreVersion)?.song
    }

    override suspend fun getEditableSong(songId: String): EditableSong? = load(songId)

    override suspend fun retainedSong(songId: String, scoreVersion: Int?): Song? = member.inSession {
        val entry = cache.value.entry ?: return@inSession null
        val song = entry.song
        val matchesVersion = if (scoreVersion == null) entry.published else song.scoreVersion == scoreVersion
        if (entry.account != member?.session?.value || song.id != songId || !matchesVersion) return@inSession null
        currentCoroutineContext()[PerformanceVisit]?.reuse(PerfTarget.SONG_BUNDLE, song.id, PerfSource.RETAINED, song.scoreVersion)
        song
    }

    /** 곡 상세와 정본 악보를 함께 검증한다. 편집기는 화면 모델과 함께 원본 공통 악보도 받는다. */
    private suspend fun load(songId: String, scoreVersion: Int? = null): EditableSong? = member.inSession {
        val account = member?.session?.value?.takeIf { it.memberId != null }
        ensureAccountSession()
        val request = beginLoad(account, songId, published = scoreVersion == null)
        measureLoad(PerfTarget.SONG_BUNDLE, songId, { it.song.scoreVersion }) {
            val detail = loadCatalogSong(songId, scoreVersion) ?: return@measureLoad null
            val bundle = loadVerified(detail, scoreVersion) ?: return@measureLoad null
            ensureAccountSession()
            if (account != null && request != null) {
                val entry = RetainedSong(account, bundle.song, detail, published = scoreVersion == null)
                cache.update { state ->
                    if (state.request === request) state.copy(entry = entry) else state
                }
            }
            bundle
        }
    }

    /** 새 조회가 시작되면 늦게 끝난 이전 조회는 공유 캐시를 덮어쓸 수 없다. */
    private fun beginLoad(account: AccountSession?, songId: String, published: Boolean): Any? {
        if (account == null) return null
        val request = Any()
        cache.update { state ->
            if (member?.session?.value != account) return@update state
            val entry = state.entry
            // 확인이 실패하거나 취소되면 기존 악보는 정확한 기록 판본으로만 재사용한다.
            if (published && entry != null && entry.account == account && entry.song.id == songId) {
                Cache(request, entry.copy(published = false))
            } else Cache(request, entry)
        }
        return request
    }

    private suspend fun loadCatalogSong(songId: String, scoreVersion: Int? = null): CatalogSong? {
        if (!validCatalogId(songId)) return null
        val response = client.getPublicOrNull("/api/songs/$songId") { scoreVersion?.let { parameter("scoreVersion", it) } }
            ?: throw CatalogUnavailable()
        if (response.status.value == 404) return null
        if (response.status.value != 200) throw CatalogUnavailable()
        val item = response.apiResponse<CatalogSong>()?.takeIf { it.isSuccess }?.result ?: throw CatalogUnavailable()
        if (item.songId != songId || item.scoreVersion <= 0 || (scoreVersion != null && item.scoreVersion != scoreVersion)) throw CatalogUnavailable()
        return item
    }

    private suspend fun loadVerified(item: CatalogSong, scoreVersion: Int?): EditableSong? {
        val songId = item.songId
        val level = item.summary().level ?: throw CatalogUnavailable()
        val scoreResponse = client.getPublicOrNull("/api/songs/$songId/score") { scoreVersion?.let { parameter("scoreVersion", it) } }
            ?: throw CatalogUnavailable()
        if (scoreResponse.status.value == 404) return null
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
        return EditableSong(Song(
            id = songId,
            title = item.title,
            level = level,
            jangdan = jangdan?.model(),
            baseBpm = item.defaultBpm,
            description = item.description.orEmpty(),
            evaluable = item.isScorable,
            score = score,
            instrumentId = item.instrumentId,
            scoreVersion = item.scoreVersion,
            noteCount = item.noteCount,
            catalogJangdanName = item.jangdanName,
            jangdanPlayback = (scoreItem.jangdanPlayback ?: scoreItem.supplementalJangdanPlayback?.takeIf {
                timing.jangdan.isBlank() && it.name == item.jangdanName
            })?.let { playback ->
                val expectedName = timing.jangdan.ifBlank { item.jangdanName.orEmpty() }
                if (playback.name != expectedName || playback.startTick >= timeline.last().let { requireNotNull(it.startTick) + requireNotNull(it.durationTick) }) throw CatalogUnavailable()
                runCatching { playback.model() }.getOrElse { throw CatalogUnavailable() }
            },
        ), common)
    }
}

@Serializable
private class CatalogScore(
    val songId: String, val scoreVersion: Int, val schemaVersion: Int, val noteCount: Int,
    val defaultBpm: Int, val bpmUnit: String, val score: JsonObject, val events: List<CatalogScoreEvent>,
    val jangdanPlayback: CatalogJangdanPlayback? = null,
    val supplementalJangdanPlayback: CatalogJangdanPlayback? = null,
)

@Serializable
private class CatalogJangdanPlayback(
    val jangdanId: String, val name: String, val startTick: Long, val cycleTicks: Long, val pattern: Pattern,
) {
    fun model(): JangdanPlayback {
        if (!validCatalogId(jangdanId)) throw CatalogUnavailable()
        return JangdanPlayback(jangdanId, name, startTick, cycleTicks, pattern.beats, pattern.group)
    }
}

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
private data class CatalogSong(
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
    val jangdanName: String? = null,
) {
    fun summary(): SongSummary {
        if (!validCatalogId(songId) || !validCatalogId(instrumentId) || title.isBlank() ||
            difficulty !in 1..5 || defaultBpm <= 0 || jangdan?.name?.isBlank() == true || noteCount < 0 || scoreVersion <= 0
        ) throw CatalogUnavailable()
        return SongSummary(
            songId, title, difficulty, jangdan?.name ?: jangdanName?.takeIf { it.isNotBlank() } ?: canonicalJangdanName(),
            defaultBpm, isScorable, instrumentId, scoreVersion,
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
private data class CatalogJangdan(val jangdanId: String, val name: String, val bpmUnit: String, val pattern: Pattern) {
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
private data class Pattern(val beats: List<String>, val group: List<Int>)

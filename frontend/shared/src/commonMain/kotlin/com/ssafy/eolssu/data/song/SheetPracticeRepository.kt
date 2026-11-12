package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.audio.SheetAudioDeviceStore
import com.ssafy.eolssu.core.audio.SheetAudioDownload
import com.ssafy.eolssu.core.model.JangdanPlayback
import com.ssafy.eolssu.core.model.ScoreTiming
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.ensureAccountSession
import com.ssafy.eolssu.core.network.inSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** 같은 계정이 확정 악보 버전으로 기기에 내려받은 음원만 연습에 연결한다. */
class SheetPracticeRepository(
    private val documents: SheetDocumentRepository,
    private val deviceAudio: SheetAudioDeviceStore,
    private val references: ReferenceCatalogRepository,
    private val instruments: InstrumentCatalog,
    private val member: SignedInMember,
    private val sheetAudio: SheetAudioRepository,
    private val now: () -> Long,
) : ModelAudioRepository {
    val deviceAudioAvailable: Boolean get() = deviceAudio.available
    suspend fun load(sheetId: String): Song? {
        val document = documents.get(sheetId) ?: return null
        if (document.id != sheetId) throw CatalogUnavailable()
        if (document.reviewStatus != "EVALUABLE" ||
            (document.conversionStatus != null && document.conversionStatus != "DONE")
        ) throw SheetPracticeNeedsReview()
        val score = document.score?.toScore() ?: throw CatalogUnavailable()
        val version = document.scoreVersion.toIntOrNull()?.takeIf { it > 0 } ?: throw CatalogUnavailable()
        val source = score.source ?: throw CatalogUnavailable()
        if (source.scoreVersion != version) throw CatalogUnavailable()
        val timing = requireNotNull(score.timing)
        val instrument = try {
            instruments.load().firstOrNull { it.code == source.instrument }
        } catch (_: CatalogUnavailable) {
            null
        }
        val accompaniment = document.jangdanId?.let { id ->
            try { references.jangdan(id)?.playbackFor(timing) } catch (_: CatalogUnavailable) { null }
        }
        return Song(
            id = sheetId,
            title = document.title,
            level = SongLevel.Intro,
            jangdan = accompaniment?.displayPattern,
            baseBpm = document.bpm,
            description = "",
            evaluable = false,
            score = score,
            instrumentId = instrument?.instrumentId,
            scoreVersion = version,
            jangdanPlayback = accompaniment,
        )
    }

    /** 연습을 열 때 기기 파일만 확인한다. 음원 서버에는 요청하지 않는다. */
    suspend fun hasLocalAudio(song: Song): Boolean = member.inSession {
        val accountId = member.id ?: return@inSession false
        val version = song.scoreVersion?.takeIf { it > 0 } ?: return@inSession false
        deviceAudio.find(accountId, song.id, version.toString()) != null
    }

    /** 사용자가 요청한 확정 악보의 M4A만 내려받는다. 장단은 연습 플레이어에서 별도로 켠다. */
    suspend fun download(song: Song, onDownloading: () -> Unit = {}): SheetPracticeDownload = member.inSession {
        if (!deviceAudio.available) return@inSession SheetPracticeDownload.Failed
        val accountId = member.id ?: return@inSession SheetPracticeDownload.Failed
        val version = song.scoreVersion?.takeIf { it > 0 }?.toString() ?: return@inSession SheetPracticeDownload.Failed
        if (deviceAudio.find(accountId, song.id, version) != null) return@inSession SheetPracticeDownload.Saved
        val instrumentId = song.instrumentId ?: run {
            val code = song.score.source?.instrument ?: return@inSession SheetPracticeDownload.Failed
            instruments.load().firstOrNull { it.code == code }?.instrumentId ?: return@inSession SheetPracticeDownload.Failed
        }
        val settings = SheetAudioSettings(instrumentId = instrumentId, bpm = song.baseBpm)
        val audio = withTimeoutOrNull(AUDIO_GENERATION_LIMIT_MS) {
            var audio = when (val request = sheetAudio.request(song.id, settings)) {
                is SheetAudioRequest.Accepted -> request.audio
                SheetAudioRequest.InProgress -> (sheetAudio.status(song.id) as? SheetAudioLookup.Found)?.audio
                else -> null
            } ?: return@withTimeoutOrNull null
            var misses = 0
            while (audio.matches(song.id, version, settings) && audio.status.inProgress) {
                delay((audio.pollAfterMs ?: 2_000).coerceIn(500, 30_000))
                when (val status = sheetAudio.status(song.id)) {
                    is SheetAudioLookup.Found -> {
                        audio = status.audio
                        misses = 0
                    }
                    SheetAudioLookup.None -> return@withTimeoutOrNull null
                    SheetAudioLookup.Unavailable -> if (++misses == 3) return@withTimeoutOrNull null
                }
            }
            audio
        } ?: return@inSession SheetPracticeDownload.Failed
        if (audio.sheetId == song.id && audio.currentScoreVersion != version) return@inSession SheetPracticeDownload.VersionChanged
        if (audio.status != SheetAudioStatus.Done || !audio.matches(song.id, version, settings)) return@inSession SheetPracticeDownload.Failed
        val file = audio.audio ?: return@inSession SheetPracticeDownload.Failed
        if (file.contentType != "audio/mp4" || file.expiresWithin(now(), 0)) return@inSession SheetPracticeDownload.Failed
        val sha256 = file.sha256 ?: return@inSession SheetPracticeDownload.Failed
        val durationMs = file.durationMs ?: return@inSession SheetPracticeDownload.Failed
        ensureAccountSession()
        onDownloading()
        val saved = deviceAudio.save(accountId, song.id, version, SheetAudioDownload(
            file.url, file.byteSize, sha256, durationMs, instrumentId, song.baseBpm, settings.withJangdan, file.performance,
        ))
        if (saved == null) SheetPracticeDownload.Failed else SheetPracticeDownload.Saved
    }

    override suspend fun get(song: Song): ModelAudio? = member.inSession {
        val accountId = member.id ?: return@inSession null
        val version = song.scoreVersion?.takeIf { it > 0 } ?: return@inSession null
        val bpmUnit = song.score.timing?.bpmUnit?.name ?: return@inSession null
        val file = deviceAudio.find(accountId, song.id, version.toString()) ?: return@inSession null
        val audio = ModelAudio(
            songId = song.id,
            scoreVersion = version,
            audioUrl = "",
            bpm = file.bpm,
            bpmUnit = bpmUnit,
            durationMs = file.durationMs,
            startOffsetMs = 0,
            contentType = "audio/mp4",
            sha256 = file.sha256,
            sheetPlayback = SheetPlayback(file.withJangdan, file.localFile),
        )
        val performance = file.performance ?: return@inSession audio
        try {
            audio.withPerformance(performance.timeline.toString(), song, performance.sourceCommonSha256)
        } catch (_: IllegalArgumentException) {
            audio
        }
    }
}

class SheetPracticeNeedsReview : Exception()

enum class SheetPracticeDownload { Saved, Failed, VersionChanged }

private const val AUDIO_GENERATION_LIMIT_MS = 12 * 60 * 1000L

private fun SheetAudio.matches(sheetId: String, version: String, settings: SheetAudioSettings): Boolean =
    this.sheetId == sheetId && scoreVersion == version && currentScoreVersion == version && !outdated &&
        instrumentId == settings.instrumentId && bpm == settings.bpm && withJangdan == settings.withJangdan

/** 악보와 장단의 BPM 단위가 다르면 한 주기를 악보의 tick 단위로 바꾼다. */
private fun CatalogJangdanDetail.playbackFor(timing: ScoreTiming): JangdanPlayback? {
    val units = mapOf("QUARTER" to 2, "DOTTED_QUARTER" to 3)
    val referenceUnit = units[bpmUnit] ?: return null
    val scoreUnit = units[timing.bpmUnit.name] ?: return null
    if (name != timing.jangdan || pattern.beats.size !in 1..64) return null
    val subdivision = if (referenceUnit == 3) 3 else 1
    if (pattern.beats.size % subdivision != 0) return null
    val cycleNumerator = pattern.beats.size.toLong() / subdivision * referenceUnit * timing.ticksPerBeat
    val cycle = cycleNumerator / scoreUnit
    if (cycleNumerator % scoreUnit != 0L || cycle !in 1..1_000_000_000L) return null
    return JangdanPlayback(jangdanId, name, 0, cycle, pattern.beats, pattern.group)
}

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
        val score = document.score?.toScore(document.expressions, baseBpm = document.bpm.toDouble()) ?: throw CatalogUnavailable()
        val version = document.scoreVersion.toIntOrNull()?.takeIf { it > 0 } ?: throw CatalogUnavailable()
        val source = score.source ?: throw CatalogUnavailable()
        if (source.scoreVersion != version) throw CatalogUnavailable()
        val timing = requireNotNull(score.timing)
        val basic = try { document.authoring?.basicAccompaniment(requireNotNull(document.score)) }
            catch (_: IllegalArgumentException) { throw CatalogUnavailable() }
        if (basic != null && (document.jangdanId != null || timing.jangdan.isNotBlank())) throw CatalogUnavailable()
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
            basicAccompaniment = basic,
            estimatedModelDurationMs = requireNotNull(document.score).estimatedModelDurationMs(document.expressions, document.bpm),
        )
    }

    /** 연습을 열 때 기기 파일만 확인한다. 음원 서버에는 요청하지 않는다. */
    suspend fun hasLocalAudio(song: Song): Boolean = member.inSession {
        val accountId = member.id ?: return@inSession false
        val version = song.scoreVersion?.takeIf { it > 0 } ?: return@inSession false
        deviceAudio.find(accountId, song.id, version.toString()) != null
    }

    /**
     * 기기 파일이 없을 때 서버가 이 확정 악보의 음원을 만드는 중인지 SHT-13으로 본다(S15P31E102-1039).
     * 편집기 등에서 이미 요청한 생성을 연습 화면이 '음원 다운로드'로 잘못 안내하지 않게 한다. 새 생성은 요청하지 않는다.
     */
    suspend fun generating(song: Song): Boolean = member.inSession {
        if (!deviceAudio.available) return@inSession false
        val version = song.scoreVersion?.takeIf { it > 0 }?.toString() ?: return@inSession false
        (sheetAudio.status(song.id) as? SheetAudioLookup.Found)?.audio?.generating(song.id, version) == true
    }

    /**
     * 사용자가 요청한 확정 악보의 M4A만 내려받는다. 장단은 연습 플레이어에서 별도로 켠다.
     * [resume]이면 SHT-12를 보내지 않고 이미 진행 중인 생성(SHT-13)만 기다린다. 끝난 음원이 연습 설정과 다르면 [SheetPracticeDownload.NotStarted]다.
     */
    suspend fun download(song: Song, resume: Boolean = false, onDownloading: () -> Unit = {}): SheetPracticeDownload = member.inSession {
        if (!deviceAudio.available) return@inSession SheetPracticeDownload.Failed
        val accountId = member.id ?: return@inSession SheetPracticeDownload.Failed
        val version = song.scoreVersion?.takeIf { it > 0 }?.toString() ?: return@inSession SheetPracticeDownload.Failed
        if (deviceAudio.find(accountId, song.id, version) != null) return@inSession SheetPracticeDownload.Saved
        val instrumentId = song.instrumentId ?: run {
            val code = song.score.source?.instrument ?: return@inSession SheetPracticeDownload.Failed
            instruments.load().firstOrNull { it.code == code }?.instrumentId ?: return@inSession SheetPracticeDownload.Failed
        }
        val settings = SheetAudioSettings(instrumentId = instrumentId, bpm = song.baseBpm)
        var unsupportedPitches = false
        val audio = withTimeoutOrNull(AUDIO_GENERATION_LIMIT_MS) {
            var audio = (if (resume) {
                (sheetAudio.status(song.id) as? SheetAudioLookup.Found)?.audio?.takeIf { it.sheetId == song.id }
            } else when (val request = sheetAudio.request(song.id, settings)) {
                is SheetAudioRequest.Accepted -> request.audio
                is SheetAudioRequest.UnsupportedPitches -> { unsupportedPitches = true; null }
                SheetAudioRequest.InProgress -> (sheetAudio.status(song.id) as? SheetAudioLookup.Found)?.audio
                else -> null
            }) ?: return@withTimeoutOrNull null
            var misses = 0
            while (if (resume) audio.generating(song.id, version) else audio.requestMatches(song.id, version, settings) && audio.status.inProgress) {
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
        } ?: return@inSession when {
            unsupportedPitches -> SheetPracticeDownload.UnsupportedPitches
            resume -> SheetPracticeDownload.NotStarted
            else -> SheetPracticeDownload.Failed
        }
        if (audio.sheetId == song.id && audio.currentScoreVersion != version) return@inSession SheetPracticeDownload.VersionChanged
        if (resume && audio.status == SheetAudioStatus.Done && !audio.matches(song.id, version, settings)) {
            return@inSession SheetPracticeDownload.NotStarted
        }
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

/** [NotStarted]: 기다릴 생성이 없었거나 끝난 음원이 연습 설정과 달라 아직 내려받지 않았다(다운로드 버튼으로 돌아감). */
enum class SheetPracticeDownload { Saved, Failed, VersionChanged, UnsupportedPitches, NotStarted }

private const val AUDIO_GENERATION_LIMIT_MS = 12 * 60 * 1000L

/** 진행 중인 생성이 이 악보 판본·설정의 요청인지 본다. `outdated`는 보관 중인 이전 파일의 상태라 여기서 보지 않는다. */
private fun SheetAudio.requestMatches(sheetId: String, version: String, settings: SheetAudioSettings): Boolean =
    this.sheetId == sheetId && scoreVersion == version && currentScoreVersion == version &&
        instrumentId == settings.instrumentId && bpm == settings.bpm && withJangdan == settings.withJangdan

/** 이 확정 판본의 음원을 서버가 만드는 중인지 본다. 다른 화면에서 요청했을 수 있어 설정은 보지 않는다. */
private fun SheetAudio.generating(sheetId: String, version: String): Boolean =
    this.sheetId == sheetId && scoreVersion == version && currentScoreVersion == version && status.inProgress

/** 완료된 파일이 이 판본·설정의 최신 음원인지 본다. */
private fun SheetAudio.matches(sheetId: String, version: String, settings: SheetAudioSettings): Boolean =
    requestMatches(sheetId, version, settings) && !outdated

/** 확인된 패턴 칸 음가로 한 주기를 악보의 tick 단위로 바꾼다. */
internal fun CatalogJangdanDetail.playbackFor(timing: ScoreTiming): JangdanPlayback? {
    if (name != timing.jangdan || bpmUnit != timing.bpmUnit.name) return null
    val cycle = jangdanCycleTicks(pattern.beats.size, patternCellEighths, timing.bpmUnit.name, timing.ticksPerBeat.toLong()) ?: return null
    return JangdanPlayback(jangdanId, name, 0, cycle, pattern.beats, pattern.group)
}

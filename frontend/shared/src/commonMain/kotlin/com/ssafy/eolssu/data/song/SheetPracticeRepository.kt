package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.audio.SheetAudioDeviceStore
import com.ssafy.eolssu.core.model.JangdanPlayback
import com.ssafy.eolssu.core.model.ScoreTiming
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.inSession

/** 같은 계정이 확정 악보 버전으로 기기에 내려받은 음원만 연습에 연결한다. */
class SheetPracticeRepository(
    private val documents: SheetDocumentRepository,
    private val deviceAudio: SheetAudioDeviceStore,
    private val references: ReferenceCatalogRepository,
    private val instruments: InstrumentCatalog,
    private val member: SignedInMember,
) : ModelAudioRepository {
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
            solfege = null,
            score = score,
            instrumentId = instrument?.instrumentId,
            scoreVersion = version,
            jangdanPlayback = accompaniment,
        )
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

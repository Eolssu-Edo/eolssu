package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.JangdanPlayback
import com.ssafy.eolssu.core.model.ScoreTiming
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel

/** 개인 악보의 확정본과 같은 버전의 생성 음원만 연습 화면에 연결한다. */
class SheetPracticeRepository(
    private val documents: SheetDocumentRepository,
    private val audio: SheetAudioRepository,
    private val references: ReferenceCatalogRepository,
    private val instruments: InstrumentCatalog,
    private val now: () -> Long,
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

    override suspend fun get(song: Song): ModelAudio? = when (val result = audio.status(song.id)) {
        SheetAudioLookup.None -> null
        SheetAudioLookup.Unavailable -> throw CatalogUnavailable()
        is SheetAudioLookup.Found -> result.audio.forPractice(song)
    }

    private fun SheetAudio.forPractice(song: Song): ModelAudio? {
        val version = song.scoreVersion ?: return null
        if (sheetId != song.id || status != SheetAudioStatus.Done || outdated ||
            scoreVersion != version.toString() || currentScoreVersion != scoreVersion
        ) return null
        val file = audio ?: throw CatalogUnavailable()
        val duration = file.durationMs?.takeIf { it in 1..600_000L } ?: throw CatalogUnavailable()
        val sha256 = file.sha256?.takeIf { it.matches(SheetAudioSha256) } ?: throw CatalogUnavailable()
        if (bpm !in 30..160 || file.contentType != "audio/mp4" || file.expiresWithin(now(), 0)) throw CatalogUnavailable()
        return ModelAudio(
            songId = sheetId,
            scoreVersion = version,
            audioUrl = file.url,
            bpm = bpm,
            bpmUnit = requireNotNull(song.score.timing).bpmUnit.name,
            durationMs = duration,
            startOffsetMs = 0,
            contentType = file.contentType,
            sha256 = sha256,
            sheetPlayback = SheetPlayback(withJangdan, file.urlExpiresAt),
        )
    }
}

class SheetPracticeNeedsReview : Exception()

private val SheetAudioSha256 = Regex("[a-f0-9]{64}")

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

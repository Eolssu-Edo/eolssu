package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.diagnostics.PerformanceLog
import com.ssafy.eolssu.core.diagnostics.PerfPhase
import com.ssafy.eolssu.core.diagnostics.PerfSource
import com.ssafy.eolssu.core.diagnostics.PerfTarget
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.diagnostics.context
import com.ssafy.eolssu.core.diagnostics.measureLoad
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.AudioSource
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.song.ModelAudio
import com.ssafy.eolssu.data.song.ModelAudioRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class ModelAudioStatus { Idle, Loading, Playing, Paused, Completed, Interrupted, Unavailable, Unsupported, Failed }

/**
 * @property anchor 회색 표시를 부드럽게 보정하는 시계. 반주 속도에는 사용하지 않는다.
 * @property playbackAnchor 음원 위치를 측정한 시각과 실제 재생 속도로 흐르는 반주용 시계.
 */
data class ModelAudioState(
    val status: ModelAudioStatus = ModelAudioStatus.Idle,
    val audio: ModelAudio? = null,
    val mediaPositionMs: Long = 0,
    val anchor: MediaAnchor? = null,
    val playbackAnchor: MediaAnchor? = null,
    val playbackClockReady: Boolean = true,
)

/**
 * 재생 중인 음원 위치를 매 프레임 이어 그리기 위한 기준점. [mark] 시각에 [positionMs]였고 이후 [rate]배로 흐른다.
 * 플레이어는 위치를 50ms마다, 앞뒤로 조금씩 흔들리게 알려 주므로 그 값을 그대로 그리면 진행 선이 계단처럼 끊기거나 뒤로 튄다.
 */
data class MediaAnchor(val positionMs: Double, val mark: TimeMark, val rate: Double) {
    fun positionNow(): Double = positionMs + mark.elapsedNow().inWholeNanoseconds / 1_000_000.0 * rate
}

/**
 * 최초 0 위치에서는 디코더의 진행 보고를 기다린다. 이후 작은 오차는 약 1초에 걸쳐 빠르기를 보정하고,
 * 큰 후행 보고에는 표시 위치를 멈춰 기다린다. 탐색·재시작은 소유자가 기준점을 지운 뒤 새 위치에서 시작한다.
 */
internal fun MediaAnchor?.follow(reportedMs: Double, speed: Double, now: TimeMark): MediaAnchor {
    // MediaPlayer may report Playing at zero before its decoder clock starts moving.
    // Advancing from that notification paints future cells, then snaps them back on the first real report.
    if (this == null) return MediaAnchor(reportedMs, now, if (reportedMs == 0.0) 0.0 else speed)
    val predicted = positionNow()
    if (rate == 0.0) return MediaAnchor(maxOf(predicted, reportedMs), now, if (reportedMs > predicted) speed else 0.0)
    val drift = reportedMs - predicted
    // A large stale report is not a seek: hold the painted position until the media clock catches up.
    // Explicit seek/loading clears the anchor in ModelAudioSession before follow is called again.
    if (drift < -ANCHOR_SNAP_MS) return MediaAnchor(predicted, now, 0.0)
    if (abs(drift) > ANCHOR_SNAP_MS) return MediaAnchor(reportedMs, now, speed)
    return MediaAnchor(predicted, now, speed * (1 + drift / ANCHOR_CATCH_UP_MS).coerceIn(0.9, 1.1))
}

private const val ANCHOR_SNAP_MS = 250.0
private const val ANCHOR_CATCH_UP_MS = 1000.0

/** Coordinates one practice screen's asynchronous metadata and real player. No simulated playback fallback. */
internal class ModelAudioSession(
    private val repository: ModelAudioRepository?,
    private val player: AudioPlayer,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val perf: PerformanceVisit? = null,
    private val prepareAccompaniment: suspend (Song, Int) -> Boolean = { _, _ -> true },
) {
    private val _state = MutableStateFlow(ModelAudioState())
    val state = _state.asStateFlow()
    internal val playbackAnchor: MediaAnchor? get() = state.value.takeIf { it.status == ModelAudioStatus.Playing }?.playbackAnchor
    internal val playbackClockReady: Boolean get() = state.value.let {
        it.status != ModelAudioStatus.Playing || it.playbackClockReady
    }
    private var loadJob: Job? = null
    private var requestId = 0L
    private var sourceId: String? = null
    private var active = false
    private var currentSong: Song? = null
    private var bpm = 0
    private var startedAt: Long? = null

    init {
        scope.launch {
            player.state.collect { media ->
                if (!active || media.sourceId != sourceId || sourceId == null) return@collect
                val audio = _state.value.audio ?: return@collect
                val actionStarted = startedAt
                if (actionStarted != null && media.status == AudioPlaybackStatus.Playing) {
                    startedAt = null
                    perf?.milestone(PerfPhase.PLAYER_STARTED, once = false, startedAt = actionStarted)
                }
                val status = when (media.status) {
                    AudioPlaybackStatus.Idle -> return@collect
                    AudioPlaybackStatus.Loading -> ModelAudioStatus.Loading
                    AudioPlaybackStatus.Playing -> ModelAudioStatus.Playing
                    AudioPlaybackStatus.Paused -> ModelAudioStatus.Paused
                    AudioPlaybackStatus.Completed -> ModelAudioStatus.Completed
                    AudioPlaybackStatus.Interrupted -> ModelAudioStatus.Interrupted
                    AudioPlaybackStatus.Failed -> ModelAudioStatus.Failed
                    AudioPlaybackStatus.Unavailable -> ModelAudioStatus.Unsupported
                }
                // A truncated file must not be reported as a completed model performance.
                val minimumEnd = audio.startOffsetMs + (currentSong?.modelScoreDurationMs(audio) ?: 0.0)
                val checked = if (media.status == AudioPlaybackStatus.Completed && media.positionMs + 1.0 < minimumEnd)
                    ModelAudioStatus.Failed else status
                val previous = _state.value
                val nativeRate = media.positionClockRate?.toDouble() ?: speed(audio).toDouble()
                val anchor = if (checked != ModelAudioStatus.Playing) null else
                    previous.anchor.takeIf { previous.status == ModelAudioStatus.Playing }
                        .follow(media.positionMs.toDouble(), nativeRate, timeSource.markNow())
                val soundAnchor = if (checked != ModelAudioStatus.Playing) null else MediaAnchor(
                    media.positionMs.toDouble(), media.positionSampledAt ?: timeSource.markNow(),
                    media.positionClockRate?.toDouble() ?: if (media.positionMs == 0L) 0.0 else speed(audio).toDouble(),
                )
                _state.value = ModelAudioState(checked, audio, media.positionMs, anchor, soundAnchor, media.positionClockRate != 0f)
            }
        }
    }

    fun play(song: Song, bpm: Int, startTick: Double? = null) {
        if (loadJob?.isActive == true) return
        if (!player.available) {
            _state.value = ModelAudioState(ModelAudioStatus.Unsupported)
            return
        }
        val actionStarted = perf?.let { PerformanceLog.now() }
        startedAt = actionStarted
        if (actionStarted != null) perf?.milestone(PerfPhase.MODEL_AUDIO_PLAY, once = false, startedAt = actionStarted)
        currentSong = song
        this.bpm = bpm
        val cached = _state.value.audio?.takeIf { it.matches(song) }
        val previous = _state.value
        if (cached != null && previous.status in setOf(ModelAudioStatus.Paused, ModelAudioStatus.Interrupted)) {
            perf?.reuse(PerfTarget.MODEL_AUDIO, song.id, PerfSource.RETAINED, song.scoreVersion)
            val resume = player.state.value.sourceId == sourceId &&
                player.state.value.status == AudioPlaybackStatus.Paused
            val target = startTick?.let { song.modelMediaPosition(cached, it) } ?: previous.mediaPositionMs
            prepareExisting(song, cached, target, resume && target == previous.mediaPositionMs)
            return
        }
        active = true
        val request = ++requestId
        sourceId = null
        player.stop()
        _state.value = ModelAudioState(ModelAudioStatus.Loading)
        launchPreparation(request, CoroutineStart.DEFAULT) {
            val audio = repository?.let { measureLoad(PerfTarget.MODEL_AUDIO, song.id, { song.scoreVersion }) { it.get(song) } }
            if (audio?.performance != null && actionStarted != null) {
                perf?.milestone(PerfPhase.TIMELINE_READY, once = false, startedAt = actionStarted)
            }
            if (request != requestId || !active) return@launchPreparation
            when {
                repository == null -> _state.value = ModelAudioState(ModelAudioStatus.Unsupported)
                audio == null -> _state.value = ModelAudioState(ModelAudioStatus.Unavailable)
                !audio.matches(song) || audio.startOffsetMs + song.modelScoreDurationMs(audio) > audio.durationMs + 1.0 ->
                    _state.value = ModelAudioState(ModelAudioStatus.Failed)
                else -> startWhenReady(request, song, audio, startTick?.let { song.modelMediaPosition(audio, it) } ?: 0)
            }
        }
    }

    fun pause() {
        if (_state.value.status == ModelAudioStatus.Loading) {
            suspendAndRelease()
        } else player.pause()
    }

    fun restart(song: Song, bpm: Int, startTick: Double? = null) {
        stop()
        play(song, bpm, startTick)
    }

    fun setBpm(value: Int) {
        bpm = value
        _state.value.audio?.let { player.setSpeed(speed(it)) }
    }

    /**
     * 모범 연주 위치를 [deltaMs]만큼 옮긴다(보드 플레이어의 −5초 · +5초). 악보 구간(앞 여백 뒤 ~ 곡 끝) 안에서만 움직인다.
     * 재생 중이면 그 자리부터 이어 틀고, 멈춰 있거나 끝났으면 다음 재생이 그 자리에서 시작하도록 위치만 바꾼다.
     */
    fun seekBy(deltaMs: Long) {
        seekTo(_state.value.mediaPositionMs + deltaMs)
    }

    fun seekToFraction(fraction: Float) {
        if (!fraction.isFinite()) return
        val audio = _state.value.audio ?: return
        val song = currentSong ?: return
        val duration = song.modelScoreDurationMs(audio)
        seekTo(audio.startOffsetMs + (duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    /**
     * 모범 연주 위치를 악보 [tick]으로 옮긴다(구간 연습의 되풀이, S15P31E102-776). 재생 중이면 그 자리부터 이어 틀고,
     * 멈춰 있거나 끝났으면 다음 재생이 그 자리에서 시작한다.
     */
    fun seekToTick(tick: Double) {
        val audio = _state.value.audio ?: return
        val song = currentSong ?: return
        seekTo(song.modelMediaPosition(audio, tick))
    }

    private fun seekTo(positionMs: Long) {
        val current = _state.value
        val audio = current.audio ?: return
        val song = currentSong ?: return
        if (current.status !in setOf(ModelAudioStatus.Playing, ModelAudioStatus.Paused, ModelAudioStatus.Completed, ModelAudioStatus.Interrupted)) return
        val start = audio.startOffsetMs
        val end = start + song.modelScoreDurationMs(audio).toLong()
        val target = positionMs.coerceIn(start, end)
        if (current.status == ModelAudioStatus.Playing) {
            sourceId = null
            player.stop()
            prepareExisting(song, audio, target, resume = false)
        } else {
            ++requestId
            loadJob?.cancel()
            active = false
            player.stop()
            sourceId = null
            _state.value = current.copy(status = ModelAudioStatus.Paused, mediaPositionMs = target)
        }
    }

    /** A hidden screen retains score selection/position but releases audio focus and native media. */
    fun suspendAndRelease() {
        ++requestId
        loadJob?.cancel()
        active = false
        val media = player.state.value
        val previous = _state.value
        player.stop()
        _state.value = previous.copy(status = if (previous.audio == null) ModelAudioStatus.Idle else ModelAudioStatus.Paused,
            mediaPositionMs = if (sourceId != null && media.sourceId == sourceId) media.positionMs else previous.mediaPositionMs)
        sourceId = null
    }

    fun stop() {
        ++requestId
        loadJob?.cancel()
        active = false
        sourceId = null
        player.stop()
        _state.value = ModelAudioState()
    }

    fun release() { stop(); player.release() }

    private fun prepareExisting(song: Song, audio: ModelAudio, positionMs: Long, resume: Boolean) {
        active = true
        val request = ++requestId
        _state.value = ModelAudioState(ModelAudioStatus.Loading, audio, positionMs)
        launchPreparation(request, CoroutineStart.UNDISPATCHED) {
            startWhenReady(request, song, audio, positionMs, resume)
        }
    }

    private fun launchPreparation(request: Long, start: CoroutineStart, action: suspend () -> Unit) {
        loadJob = scope.launch(perf.context(), start = start) {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == requestId && active) _state.value = ModelAudioState(ModelAudioStatus.Failed)
            }
        }
    }

    private suspend fun startWhenReady(request: Long, song: Song, audio: ModelAudio, positionMs: Long, resume: Boolean = false) {
        var ready: Boolean
        do {
            val preparingBpm = bpm
            ready = audio.sheetPlayback != null || prepareAccompaniment(song, preparingBpm)
            if (request != requestId || !active) return
        } while (preparingBpm != bpm)
        if (!ready) {
            player.stop()
            sourceId = null
            _state.value = ModelAudioState(ModelAudioStatus.Failed, audio, positionMs)
        } else if (resume) {
            player.resume(speed(audio))
        } else {
            val id = "model-$request"
            sourceId = id
            _state.value = ModelAudioState(ModelAudioStatus.Loading, audio, positionMs)
            startedAt?.let { perf?.milestone(PerfPhase.PLAYER_LOAD, once = false, startedAt = it) }
            player.load(AudioSource(id, audio.audioUrl, localFile = audio.sheetPlayback?.localFile), positionMs, speed(audio))
        }
    }

    private fun speed(audio: ModelAudio): Float = bpm.toFloat() / audio.bpm
}

/** 재생 중인 모범 음원. 진행 선은 [anchor]에서 이어 간 음원 위치를 매 프레임 [modelPointAt]으로 악보 자리로 바꿔 그린다. */
data class ModelClock(val audio: ModelAudio, val anchor: MediaAnchor)

/** 음원 위치가 가리키는 악보 자리: 연주 순서 [position]인 음에서 [fraction]만큼 지난 곳과 앞 여백을 뺀 음원 위치 [scoreMs]. */
internal data class ModelPoint(val position: Int, val fraction: Double, val scoreMs: Double)

/**
 * Translate decoded media position to the original score ticks. Tail/leading silence never stretches the score.
 * 악보에 박자 정보와 음이 있고, 연주 기록이 있으면 그 악보의 것이어야 한다.
 */
internal fun Song.modelPointAt(audio: ModelAudio, mediaPositionMs: Double): ModelPoint {
    val mediaScoreMs = (mediaPositionMs - audio.startOffsetMs).coerceAtLeast(0.0)
    audio.performance?.let { performance ->
        val performed = performance.at(mediaScoreMs)
        val event = performed.segment.unit.event
        val fraction = ((performed.sourceTick - requireNotNull(event.startTick)) / requireNotNull(event.durationTick)).coerceIn(0.0, 1.0)
        return ModelPoint(event.index, fraction, mediaScoreMs.coerceAtMost(performance.durationMs))
    }
    val tick = mediaScoreMs * audio.bpm / 60_000.0 * requireNotNull(score.timing).ticksPerBeat
    val position = timeline.indexOfLast { requireNotNull(it.startTick) <= tick }.coerceAtLeast(0)
    val event = timeline[position]
    val fraction = ((tick - requireNotNull(event.startTick)) / requireNotNull(event.durationTick)).coerceIn(0.0, 1.0)
    return ModelPoint(position, fraction, mediaScoreMs.coerceAtMost(modelScoreDurationMs(audio)))
}

internal fun PracticeUiState.withModelAudio(playback: ModelAudioState): PracticeUiState {
    if (mode != PracticeMode.Model) return this
    val base = copy(modelAudioStatus = playback.status, playing = playback.status == ModelAudioStatus.Playing,
        eventStartedAt = null, modelPerformance = playback.audio?.performance, modelPerformancePositionMs = 0.0,
        sheetPerformanceBpm = playback.audio?.takeIf { it.sheetPlayback != null && it.performance != null }?.bpm,
        modelClock = null)
    val audio = playback.audio ?: return base
    val song = song ?: return base
    if (song.score.timing == null || song.timeline.isEmpty()) return base
    if (audio.performance != null && !audio.matches(song)) {
        return base.copy(modelAudioStatus = ModelAudioStatus.Failed, playing = false, modelPerformance = null, sheetPerformanceBpm = null)
    }
    val point = song.modelPointAt(audio, playback.mediaPositionMs.toDouble())
    val event = song.timeline[point.position]
    return base.copy(position = point.position, eventElapsedMs = song.eventDurationMs(event, bpm) * point.fraction,
        pausedFraction = point.fraction.toFloat(), elapsedMs = point.scoreMs * audio.bpm / bpm,
        modelPerformancePositionMs = if (audio.performance != null) point.scoreMs else 0.0,
        modelClock = playback.anchor?.let { ModelClock(audio, it) })
}

internal fun Song.modelScoreDurationMs(audio: ModelAudio): Double {
    val written = durationMs(audio.bpm)
    return audio.performance?.durationMs ?: if (audio.sheetPlayback == null) written
        else minOf(written, (audio.durationMs - audio.startOffsetMs).toDouble())
}

/** 장구는 선율과 같은 수행표의 원본 위치를 따른다. 반복 때는 원래 장단 위치로 돌아간다. */
internal fun Song.modelJangdanTickAt(audio: ModelAudio, mediaPositionMs: Double): Double {
    audio.performance?.let { return it.at((mediaPositionMs - audio.startOffsetMs).coerceAtLeast(0.0)).accompanimentTick }
    val point = modelPointAt(audio, mediaPositionMs)
    val event = timeline[point.position]
    return requireNotNull(event.startTick) + requireNotNull(event.durationTick) * point.fraction
}

/** 현재 수행부터 선택 구간을 처음 벗어나는 시각. 구간 안에서만 도는 반복은 보존한다. */
internal fun Song.modelSectionEndMediaPosition(audio: ModelAudio, ticks: ClosedFloatingPointRange<Double>, mediaPositionMs: Double): Double {
    val performance = audio.performance ?: return modelMediaPosition(audio, ticks.endInclusive).toDouble()
    val elapsed = (mediaPositionMs - audio.startOffsetMs).coerceAtLeast(0.0)
    for (segment in performance.segments) {
        if (segment.endMs <= elapsed) continue
        val entryMs = maxOf(elapsed, segment.startMs)
        val entryTick = segment.sourceTickAt(entryMs)
        if (entryTick < ticks.start || entryTick >= ticks.endInclusive) return audio.startOffsetMs + entryMs
        if (segment.endTick > ticks.endInclusive) return audio.startOffsetMs + segment.mediaMsAt(ticks.endInclusive)
    }
    return audio.startOffsetMs + performance.durationMs
}

/** 원본 악보 tick을 모범 음원의 위치로 바꾼다. 꾸밈음의 연주 시간과 앞 여백을 보존한다. */
internal fun Song.modelMediaPosition(audio: ModelAudio, tick: Double): Long {
    val performance = audio.performance
    val scoreMs = if (performance == null) tick * 60_000.0 / audio.bpm / requireNotNull(score.timing).ticksPerBeat else {
        val segment = performance.segments.firstOrNull { tick >= it.startTick && tick < it.endTick }
            ?: if (tick <= performance.segments.first().startTick) performance.segments.first() else performance.segments.last()
        segment.mediaMsAt(tick)
    }
    return (audio.startOffsetMs + scoreMs).toLong().coerceIn(0, audio.durationMs)
}

package com.ssafy.eolssu.feature.practice

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

/** @property anchor 재생 중일 때만 있다. 진행 선은 보고된 [mediaPositionMs] 대신 이 기준점에서 이어 그린다. */
data class ModelAudioState(
    val status: ModelAudioStatus = ModelAudioStatus.Idle,
    val audio: ModelAudio? = null,
    val mediaPositionMs: Long = 0,
    val anchor: MediaAnchor? = null,
)

/**
 * 재생 중인 음원 위치를 매 프레임 이어 그리기 위한 기준점. [mark] 시각에 [positionMs]였고 이후 [rate]배로 흐른다.
 * 플레이어는 위치를 50ms마다, 앞뒤로 조금씩 흔들리게 알려 주므로 그 값을 그대로 그리면 진행 선이 계단처럼 끊기거나 뒤로 튄다.
 */
data class MediaAnchor(val positionMs: Double, val mark: TimeMark, val rate: Double) {
    fun positionNow(): Double = positionMs + mark.elapsedNow().inWholeNanoseconds / 1_000_000.0 * rate
}

/**
 * 새로 보고된 위치로 기준점을 옮긴다. 크게 어긋나면(시작·이동·끊김 뒤) 그 자리로 바로 옮기고, 작은 어긋남은
 * 지금 그리는 위치에서 끊지 않고 흐르는 빠르기만 조금 바꿔 약 1초에 걸쳐 따라잡는다. 그래서 진행 선이 뒤로 가지 않는다.
 */
internal fun MediaAnchor?.follow(reportedMs: Double, speed: Double, now: TimeMark): MediaAnchor {
    val predicted = this?.positionNow() ?: return MediaAnchor(reportedMs, now, speed)
    val drift = reportedMs - predicted
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
    private val prepareAccompaniment: suspend (Song, Int) -> Boolean = { _, _ -> true },
) {
    private val _state = MutableStateFlow(ModelAudioState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var requestId = 0L
    private var sourceId: String? = null
    private var active = false
    private var currentSong: Song? = null
    private var bpm = 0

    init {
        scope.launch {
            player.state.collect { media ->
                if (!active || media.sourceId != sourceId || sourceId == null) return@collect
                val audio = _state.value.audio ?: return@collect
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
                val minimumEnd = audio.startOffsetMs + (audio.performance?.durationMs ?: currentSong?.durationMs(audio.bpm) ?: 0.0)
                val checked = if (media.status == AudioPlaybackStatus.Completed && media.positionMs + 1.0 < minimumEnd)
                    ModelAudioStatus.Failed else status
                val previous = _state.value
                val anchor = if (checked != ModelAudioStatus.Playing) null else
                    previous.anchor.takeIf { previous.status == ModelAudioStatus.Playing }
                        .follow(media.positionMs.toDouble(), speed(audio).toDouble(), timeSource.markNow())
                _state.value = ModelAudioState(checked, audio, media.positionMs, anchor)
            }
        }
    }

    fun play(song: Song, bpm: Int, startTick: Double? = null) {
        if (loadJob?.isActive == true) return
        if (!player.available) {
            _state.value = ModelAudioState(ModelAudioStatus.Unsupported)
            return
        }
        currentSong = song
        this.bpm = bpm
        val cached = _state.value.audio?.takeIf { it.matches(song) }
        val previous = _state.value
        if (cached != null && previous.status in setOf(ModelAudioStatus.Paused, ModelAudioStatus.Interrupted)) {
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
            val audio = repository?.get(song)
            if (request != requestId || !active) return@launchPreparation
            when {
                repository == null -> _state.value = ModelAudioState(ModelAudioStatus.Unsupported)
                audio == null -> _state.value = ModelAudioState(ModelAudioStatus.Unavailable)
                !audio.matches(song) || audio.startOffsetMs + (audio.performance?.durationMs ?: song.durationMs(audio.bpm)) > audio.durationMs + 1.0 ->
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

    fun restart(song: Song, bpm: Int) {
        stop()
        play(song, bpm)
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
        val current = _state.value
        val audio = current.audio ?: return
        val song = currentSong ?: return
        if (current.status !in setOf(ModelAudioStatus.Playing, ModelAudioStatus.Paused, ModelAudioStatus.Completed, ModelAudioStatus.Interrupted)) return
        val start = audio.startOffsetMs
        val end = start + (audio.performance?.durationMs ?: song.durationMs(audio.bpm)).toLong()
        val target = (current.mediaPositionMs + deltaMs).coerceIn(start, end)
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
        loadJob = scope.launch(start = start) {
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
            ready = prepareAccompaniment(song, preparingBpm)
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
            player.load(AudioSource(id, audio.audioUrl), positionMs, speed(audio))
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
    return ModelPoint(position, fraction, mediaScoreMs.coerceAtMost(durationMs(audio.bpm)))
}

internal fun PracticeUiState.withModelAudio(playback: ModelAudioState): PracticeUiState {
    if (mode != PracticeMode.Model) return this
    val base = copy(modelAudioStatus = playback.status, playing = playback.status == ModelAudioStatus.Playing,
        eventStartedAt = null, modelPerformance = playback.audio?.performance, modelPerformancePositionMs = 0.0, modelClock = null)
    val audio = playback.audio ?: return base
    val song = song ?: return base
    if (song.score.timing == null || song.timeline.isEmpty()) return base
    if (audio.performance != null && !audio.matches(song)) {
        return base.copy(modelAudioStatus = ModelAudioStatus.Failed, playing = false, modelPerformance = null)
    }
    val point = song.modelPointAt(audio, playback.mediaPositionMs.toDouble())
    val event = song.timeline[point.position]
    return base.copy(position = point.position, eventElapsedMs = song.eventDurationMs(event, bpm) * point.fraction,
        pausedFraction = point.fraction.toFloat(), elapsedMs = point.scoreMs * audio.bpm / bpm,
        modelPerformancePositionMs = if (audio.performance != null) point.scoreMs else 0.0,
        modelClock = playback.anchor?.let { ModelClock(audio, it) })
}

/** 원본 악보 tick을 모범 음원의 위치로 바꾼다. 꾸밈음의 연주 시간과 앞 여백을 보존한다. */
internal fun Song.modelMediaPosition(audio: ModelAudio, tick: Double): Long {
    val performance = audio.performance
    val scoreMs = if (performance == null) tick * 60_000.0 / audio.bpm / requireNotNull(score.timing).ticksPerBeat else {
        val segment = performance.segments.firstOrNull { tick >= it.startTick && tick < it.endTick }
            ?: if (tick <= performance.segments.first().startTick) performance.segments.first() else performance.segments.last()
        val fraction = ((tick - segment.startTick) / (segment.endTick - segment.startTick)).coerceIn(0.0, 1.0)
        segment.startMs + (segment.endMs - segment.startMs) * fraction
    }
    return (audio.startOffsetMs + scoreMs).toLong().coerceIn(0, audio.durationMs)
}

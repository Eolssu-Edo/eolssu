package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.AudioSource
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.song.ModelAudio
import com.ssafy.eolssu.data.song.ModelAudioRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ModelAudioStatus { Idle, Loading, Playing, Paused, Completed, Interrupted, Unavailable, Unsupported, Failed }

data class ModelAudioState(
    val status: ModelAudioStatus = ModelAudioStatus.Idle,
    val audio: ModelAudio? = null,
    val mediaPositionMs: Long = 0,
)

/** Coordinates one practice screen's asynchronous metadata and real player. No simulated playback fallback. */
internal class ModelAudioSession(
    private val repository: ModelAudioRepository?,
    private val player: AudioPlayer,
    private val scope: CoroutineScope,
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
                _state.value = ModelAudioState(checked, audio, media.positionMs)
            }
        }
    }

    fun play(song: Song, bpm: Int) {
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
            active = true
            if (player.state.value.sourceId == sourceId && player.state.value.status == AudioPlaybackStatus.Paused) {
                player.resume(speed(cached))
            } else loadMedia(cached, previous.mediaPositionMs)
            return
        }
        active = true
        val request = ++requestId
        sourceId = null
        player.stop()
        _state.value = ModelAudioState(ModelAudioStatus.Loading)
        loadJob = scope.launch {
            try {
                val audio = repository?.get(song)
                if (request != requestId || !active) return@launch
                when {
                    repository == null -> _state.value = ModelAudioState(ModelAudioStatus.Unsupported)
                    audio == null -> _state.value = ModelAudioState(ModelAudioStatus.Unavailable)
                    !audio.matches(song) || audio.startOffsetMs + (audio.performance?.durationMs ?: song.durationMs(audio.bpm)) > audio.durationMs + 1.0 ->
                        _state.value = ModelAudioState(ModelAudioStatus.Failed)
                    else -> loadMedia(audio, 0)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == requestId && active) _state.value = ModelAudioState(ModelAudioStatus.Failed)
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
            active = true
            loadMedia(audio, target)
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

    private fun loadMedia(audio: ModelAudio, positionMs: Long) {
        val id = "model-${++requestId}"
        sourceId = id
        _state.value = ModelAudioState(ModelAudioStatus.Loading, audio, positionMs)
        player.load(AudioSource(id, audio.audioUrl), positionMs, speed(audio))
    }

    private fun speed(audio: ModelAudio): Float = bpm.toFloat() / audio.bpm
}

/** Translate decoded media position to the original score ticks. Tail/leading silence never stretches the score. */
internal fun PracticeUiState.withModelAudio(playback: ModelAudioState): PracticeUiState {
    if (mode != PracticeMode.Model) return this
    val base = copy(modelAudioStatus = playback.status, playing = playback.status == ModelAudioStatus.Playing,
        eventStartedAt = null, modelPerformance = playback.audio?.performance, modelPerformancePositionMs = 0.0)
    val audio = playback.audio ?: return base
    val song = song ?: return base
    val timing = song.score.timing ?: return base
    val events = song.timeline
    if (events.isEmpty()) return base
    val mediaScoreMs = (playback.mediaPositionMs - audio.startOffsetMs).coerceAtLeast(0).toDouble()
    audio.performance?.let { performance ->
        if (!audio.matches(song)) return base.copy(modelAudioStatus = ModelAudioStatus.Failed, playing = false, modelPerformance = null)
        val performed = performance.at(mediaScoreMs)
        val event = performed.segment.unit.event
        val fraction = ((performed.sourceTick - requireNotNull(event.startTick)) / requireNotNull(event.durationTick)).coerceIn(0.0, 1.0)
        val elapsed = mediaScoreMs.coerceAtMost(performance.durationMs)
        return base.copy(position = event.index, eventElapsedMs = song.eventDurationMs(event, bpm) * fraction,
            pausedFraction = fraction.toFloat(), elapsedMs = elapsed * audio.bpm / bpm, modelPerformancePositionMs = elapsed)
    }
    val tick = mediaScoreMs * audio.bpm / 60_000.0 * timing.ticksPerBeat
    val position = events.indexOfLast { requireNotNull(it.startTick) <= tick }.coerceAtLeast(0)
    val event = events[position]
    val fraction = ((tick - requireNotNull(event.startTick)) / requireNotNull(event.durationTick)).coerceIn(0.0, 1.0)
    val clampedMs = mediaScoreMs.coerceAtMost(song.durationMs(audio.bpm))
    return base.copy(position = position, eventElapsedMs = song.eventDurationMs(event, bpm) * fraction,
        elapsedMs = clampedMs * audio.bpm / bpm, pausedFraction = fraction.toFloat())
}

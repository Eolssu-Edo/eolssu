package com.ssafy.eolssu.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.AudioSource
import com.ssafy.eolssu.core.audio.UnavailableAudioPlayer
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.RecordPlayback
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RECORD-02 상태. 녹음은 서버가 준 임시 주소(REC-03)로 재생하고, 재생 위치(ms)를 기록할 때의 속도로 정간에 놓는다.
 *
 * @property position 지금 듣는 음의 연주 순서.
 * @property notice 한 번 보여 줄 안내(스낵바). 보여 준 뒤 [RecordDetailViewModel.consumeNotice]로 지운다.
 * @property previous 이 기록 바로 전의 같은 곡 전체 연주(‘지난 연주와 비교’ 표시). 기록 목록에서 찾는다.
 */
data class RecordDetailUiState(
    val record: PracticeRecord? = null,
    val song: Song? = null,
    val position: Int = 0,
    val playing: Boolean = false,
    val showErrors: Boolean = true,
    val follow: Boolean = true,
    val songLookup: RecordSongLookup = RecordSongLookup.Ready,
    val notice: RecordNotice? = null,
    val previous: PracticeRecord? = null,
) {
    val eventCount: Int get() = song?.timeline?.size ?: 0

    /** 녹음을 다시 들을 수 있을 때만 재생 버튼을 쓴다(S15P31E102-369: `recordingAvailable`이 false면 재생을 숨긴다). */
    val canPlay: Boolean get() = record?.recordingAvailable == true && eventCount > 0

    /** 정간 하나의 길이(ms). 기록할 때의 속도를 쓴다. */
    private val cellMillis: Double get() = song?.let { it.cellDurationMs(record?.bpm ?: it.baseBpm) } ?: 0.0

    fun eventMillis(index: Int = position): Double = (song?.timeline?.getOrNull(index)?.unitDuration ?: 0.0) * cellMillis

    /** [index] 음이 시작하는 녹음 위치(ms). 녹음은 첫 음에서 시작한다고 본다. */
    fun startMillis(index: Int = position): Long = ((song?.timeline?.getOrNull(index)?.unitStart ?: 0.0) * cellMillis).toLong()

    /** 지금 위치까지 흐른 시간(초). */
    val elapsedSeconds: Double get() = startMillis() / 1000.0

    /** [seconds] 시점에 소리 나는 음의 연주 순서. */
    fun indexAt(seconds: Double): Int {
        val timeline = song?.timeline ?: return 0
        if (cellMillis <= 0 || timeline.isEmpty()) return 0
        val unit = seconds * 1000 / cellMillis
        return timeline.indexOfLast { it.unitStart <= unit }.coerceIn(0, timeline.lastIndex)
    }
}

enum class RecordSongLookup { Loading, Ready, Missing, Failed }

/** 기록 상세의 한 번짜리 안내. */
enum class RecordNotice {
    /** 녹음이 없거나 지운 기록이라 다시 들을 수 없음. */
    PlaybackUnavailable,

    /** 서버가 지금 녹음을 내줄 수 없음(503). */
    PlaybackBusy,

    /** 녹음 주소를 받지 못했거나 재생하지 못함. */
    PlaybackFailed,

    /** 기록 상세(메모·점수·오류 위치)를 받지 못함. 목록의 값은 그대로 보인다. */
    DetailFailed,
}

/**
 * RECORD-02. 태블릿 가로의 두 칸 배치에서는 한 ViewModel이 [show]로 오른쪽 상세의 기록을 바꾼다.
 * 기록을 열면 서버 상세(PRA-03·EVA-01)를 받아 메모·점수·오류 위치·다시 듣기 여부를 채운다(처음 열면 서버가 NEW를 끈다).
 * 녹음 주소는 15분 동안 쓸 수 있어, 만료 30초 전부터는 재생할 때 다시 받는다.
 */
class RecordDetailViewModel(
    recordId: String,
    private val recordRepository: PracticeRecordRepository,
    private val songRepository: SongRepository,
    private val player: AudioPlayer = UnavailableAudioPlayer(),
    private val now: () -> Long = { 0L },
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordDetailUiState())
    val uiState: StateFlow<RecordDetailUiState> = _uiState.asStateFlow()
    private var songJob: Job? = null
    private var detailJob: Job? = null
    private var playJob: Job? = null
    private var songRequest = 0L
    private var source: Pair<AudioSource, Long>? = null
    private var sourceRequest = 0
    private var currentId = ""

    init {
        viewModelScope.launch { player.state.collect(::onPlayer) }
        show(recordId)
    }

    /**
     * [recordId] 기록을 보여준다. 다른 기록으로 바꾸면 재생을 멈추고 처음 위치로 돌린다.
     * 오류 표시·악보 따라가기 설정은 그대로 둔다(와이어프레임 `Tab-Records-Land`).
     */
    fun show(recordId: String) {
        if (recordId.isEmpty() || _uiState.value.record?.id == recordId) return
        currentId = recordId
        stopPlayback()
        source = null
        songJob?.cancel()
        detailJob?.cancel()
        songRequest++
        val record = recordRepository.records.value.firstOrNull { it.id == recordId }
        _uiState.update { state ->
            state.copy(
                record = record, song = state.song?.takeIf { it.id == record?.songId }, position = 0, playing = false,
                previous = record?.previousIn(recordRepository.records.value),
                songLookup = if (record == null) RecordSongLookup.Loading else RecordSongLookup.Ready,
            )
        }
        loadDetail(recordId)
        if (record != null && _uiState.value.song == null) retrySong()
    }

    private fun loadDetail(recordId: String) {
        detailJob = viewModelScope.launch {
            val detail = try {
                // 기록 상세 경로로 바로 들어와 목록을 아직 받지 않았으면 먼저 받는다.
                if (recordRepository.records.value.none { it.id == recordId }) recordRepository.refresh()
                recordRepository.detail(recordId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update {
                    if (it.record == null) it.copy(songLookup = RecordSongLookup.Failed) else it.copy(notice = RecordNotice.DetailFailed)
                }
                return@launch
            }
            currentCoroutineContext().ensureActive()
            if (detail == null) {
                _uiState.update { it.copy(record = null, song = null, songLookup = RecordSongLookup.Missing, playing = false) }
                return@launch
            }
            val hadRecord = _uiState.value.record != null
            _uiState.update { it.copy(record = detail, previous = detail.previousIn(recordRepository.records.value)) }
            if (!hadRecord || _uiState.value.song == null) retrySong()
        }
    }

    fun retrySong() {
        val record = _uiState.value.record
        if (record == null) {
            // 상세를 받지 못해 기록을 모르는 상태에서 다시 시도하면 상세부터 다시 받는다.
            if (_uiState.value.songLookup == RecordSongLookup.Failed && detailJob?.isActive != true && currentId.isNotEmpty()) {
                _uiState.update { it.copy(songLookup = RecordSongLookup.Loading) }
                loadDetail(currentId)
            }
            return
        }
        if (songJob?.isActive == true || _uiState.value.song != null) return
        val request = ++songRequest
        _uiState.update { it.copy(songLookup = RecordSongLookup.Loading) }
        songJob = viewModelScope.launch {
            val song = try {
                songRepository.getSong(record.songId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update {
                    if (request == songRequest && it.record?.id == record.id) it.copy(songLookup = RecordSongLookup.Failed) else it
                }
                return@launch
            }
            currentCoroutineContext().ensureActive()
            _uiState.update {
                if (request == songRequest && it.record?.id == record.id) {
                    it.copy(song = song, songLookup = if (song == null) RecordSongLookup.Missing else RecordSongLookup.Ready)
                } else it
            }
        }
    }

    /** 재생·일시정지. 끝에서 재생하면 처음부터. 녹음을 다시 들을 수 없는 기록이면 아무것도 하지 않는다. */
    fun togglePlay() {
        val state = _uiState.value
        if (!state.canPlay) return
        if (state.playing) {
            pause()
            return
        }
        if (state.position >= state.eventCount - 1) _uiState.update { it.copy(position = 0) }
        playFrom(_uiState.value.startMillis())
    }

    /** 녹음 주소를 (필요하면 다시) 받아 [positionMs]부터 재생한다. */
    private fun playFrom(positionMs: Long) {
        val record = _uiState.value.record ?: return
        playJob?.cancel()
        _uiState.update { it.copy(playing = true) }
        playJob = viewModelScope.launch {
            val cached = source?.takeIf { (s, expires) -> s.id.startsWith(record.id) && now() < expires - URL_MARGIN_MS }?.first
            val audio = cached ?: when (val playback = recordRepository.playback(record.id)) {
                is RecordPlayback.Ready -> AudioSource("${record.id}#${++sourceRequest}", playback.url)
                    .also { source = it to playback.expiresAtMillis }
                RecordPlayback.Unavailable -> {
                    _uiState.update {
                        it.copy(playing = false, notice = RecordNotice.PlaybackUnavailable, record = it.record?.copy(recordingAvailable = false))
                    }
                    return@launch
                }
                RecordPlayback.Busy -> {
                    _uiState.update { it.copy(playing = false, notice = RecordNotice.PlaybackBusy) }
                    return@launch
                }
                RecordPlayback.Failed -> {
                    _uiState.update { it.copy(playing = false, notice = RecordNotice.PlaybackFailed) }
                    return@launch
                }
            }
            currentCoroutineContext().ensureActive()
            player.load(audio, positionMs, 1f)
        }
    }

    private fun onPlayer(playback: AudioPlaybackState) {
        val current = source?.first ?: return
        if (playback.sourceId != current.id) return
        when (playback.status) {
            AudioPlaybackStatus.Playing -> _uiState.update {
                it.copy(playing = true, position = it.indexAt(playback.positionMs / 1000.0))
            }
            AudioPlaybackStatus.Completed -> _uiState.update { it.copy(playing = false, position = (it.eventCount - 1).coerceAtLeast(0)) }
            AudioPlaybackStatus.Paused, AudioPlaybackStatus.Interrupted, AudioPlaybackStatus.Idle -> _uiState.update { it.copy(playing = false) }
            AudioPlaybackStatus.Failed, AudioPlaybackStatus.Unavailable -> _uiState.update {
                it.copy(playing = false, notice = if (it.playing) RecordNotice.PlaybackFailed else it.notice)
            }
            AudioPlaybackStatus.Loading -> Unit
        }
    }

    fun pause() {
        playJob?.cancel()
        player.pause()
        _uiState.update { it.copy(playing = false) }
    }

    private fun stopPlayback() {
        playJob?.cancel()
        player.stop()
        _uiState.update { it.copy(playing = false) }
    }

    /** 칸을 누르거나 재생 위치를 옮기면 그 음부터 듣는다. 재생 중이면 그 자리부터 이어 재생한다. */
    fun seek(index: Int) {
        _uiState.update { it.copy(position = index.coerceIn(0, (it.eventCount - 1).coerceAtLeast(0))) }
        if (_uiState.value.playing) playFrom(_uiState.value.startMillis())
    }

    fun skipSeconds(delta: Int) = seek(_uiState.value.let { it.indexAt(it.elapsedSeconds + delta) })

    fun toggleErrors() = _uiState.update { it.copy(showErrors = !it.showErrors) }

    fun toggleFollow() = _uiState.update { it.copy(follow = !it.follow) }

    fun consumeNotice() = _uiState.update { it.copy(notice = null) }

    override fun onCleared() {
        playJob?.cancel()
        songJob?.cancel()
        detailJob?.cancel()
        player.release()
    }

    private companion object {
        /** 녹음 주소 만료 전에 다시 받는 여유(ms). */
        const val URL_MARGIN_MS = 30_000L
    }
}

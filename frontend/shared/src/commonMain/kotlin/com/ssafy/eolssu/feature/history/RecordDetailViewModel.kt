package com.ssafy.eolssu.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RECORD-02 상태. 녹음 소리는 아직 없고 재생 위치만 정간 길이만큼 움직인다.
 *
 * @property position 지금 듣는 음의 연주 순서.
 */
data class RecordDetailUiState(
    val record: PracticeRecord? = null,
    val song: Song? = null,
    val position: Int = 0,
    val playing: Boolean = false,
    val showErrors: Boolean = true,
    val follow: Boolean = true,
    val songLookup: RecordSongLookup = RecordSongLookup.Ready,
) {
    val eventCount: Int get() = song?.timeline?.size ?: 0

    /** 정간 하나의 길이(ms). 기록할 때의 속도를 쓴다. */
    private val cellMillis: Double get() = song?.let { it.cellDurationMs(record?.bpm ?: it.baseBpm) } ?: 0.0

    fun eventMillis(index: Int = position): Double = (song?.timeline?.getOrNull(index)?.unitDuration ?: 0.0) * cellMillis

    /** 지금 위치까지 흐른 시간(초). */
    val elapsedSeconds: Double get() = (song?.timeline?.getOrNull(position)?.unitStart ?: 0.0) * cellMillis / 1000

    /** [seconds] 시점에 소리 나는 음의 연주 순서. */
    fun indexAt(seconds: Double): Int {
        val timeline = song?.timeline ?: return 0
        if (cellMillis <= 0 || timeline.isEmpty()) return 0
        val unit = seconds * 1000 / cellMillis
        return timeline.indexOfLast { it.unitStart <= unit }.coerceIn(0, timeline.lastIndex)
    }
}

enum class RecordSongLookup { Loading, Ready, Missing, Failed }

/** RECORD-02. 태블릿 가로의 두 칸 배치에서는 한 ViewModel이 [show]로 오른쪽 상세의 기록을 바꾼다. */
class RecordDetailViewModel(
    recordId: String,
    private val recordRepository: PracticeRecordRepository,
    private val songRepository: SongRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordDetailUiState())
    val uiState: StateFlow<RecordDetailUiState> = _uiState.asStateFlow()
    private var playJob: Job? = null
    private var songJob: Job? = null
    private var songRequest = 0L

    init {
        show(recordId)
    }

    /**
     * [recordId] 기록을 보여준다. 다른 기록으로 바꾸면 재생을 멈추고 처음 위치로 돌린다.
     * 오류 표시·악보 따라가기 설정은 그대로 둔다(와이어프레임 `Tab-Records-Land`).
     */
    fun show(recordId: String) {
        if (_uiState.value.record?.id == recordId) return
        playJob?.cancel()
        songJob?.cancel()
        songRequest++
        val record = recordRepository.records.value.firstOrNull { it.id == recordId }
        _uiState.update { state ->
            state.copy(
                record = record, song = state.song?.takeIf { it.id == record?.songId }, position = 0, playing = false,
                songLookup = if (record == null) RecordSongLookup.Missing else RecordSongLookup.Ready,
            )
        }
        record ?: return
        if (_uiState.value.song != null) return
        retrySong()
    }

    fun retrySong() {
        val record = _uiState.value.record ?: return
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

    /** 재생·일시정지. 끝에서 재생하면 처음부터. */
    fun togglePlay() {
        if (_uiState.value.song == null || _uiState.value.eventCount == 0) return
        if (_uiState.value.playing) {
            pause()
            return
        }
        _uiState.update { if (it.position >= it.eventCount - 1) it.copy(position = 0) else it }
        _uiState.update { it.copy(playing = true, follow = it.follow) }
        playJob = viewModelScope.launch {
            while (true) {
                delay(_uiState.value.eventMillis().toLong())
                val state = _uiState.value
                if (state.position >= state.eventCount - 1) {
                    _uiState.update { it.copy(playing = false) }
                    break
                }
                _uiState.update { it.copy(position = it.position + 1) }
            }
        }
    }

    fun pause() {
        playJob?.cancel()
        _uiState.update { it.copy(playing = false) }
    }

    /** 칸을 누르거나 재생 위치를 옮기면 그 음부터 듣는다. */
    fun seek(index: Int) = _uiState.update { it.copy(position = index.coerceIn(0, (it.eventCount - 1).coerceAtLeast(0))) }

    fun skipSeconds(delta: Int) = _uiState.update { it.copy(position = it.indexAt(it.elapsedSeconds + delta)) }

    fun toggleErrors() = _uiState.update { it.copy(showErrors = !it.showErrors) }

    fun toggleFollow() = _uiState.update { it.copy(follow = !it.follow) }

    override fun onCleared() {
        playJob?.cancel()
        songJob?.cancel()
    }
}

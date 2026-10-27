package com.ssafy.eolssu.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.Job
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
) {
    val eventCount: Int get() = song?.timeline?.size ?: 0

    /** 정간 하나의 길이(ms). 기록할 때의 속도를 쓴다. */
    private val cellMillis: Double get() = song?.jangdan?.cellMillis(record?.bpm ?: song?.baseBpm ?: 60) ?: 0.0

    fun eventMillis(index: Int = position): Double = (song?.timeline?.getOrNull(index)?.unitDuration ?: 0.0) * cellMillis

    /** 지금 위치까지 흐른 시간(초). */
    val elapsedSeconds: Double get() = (song?.timeline?.getOrNull(position)?.unitStart ?: 0.0) * cellMillis / 1000

    /** [seconds] 시점에 소리 나는 음의 연주 순서. */
    fun indexAt(seconds: Double): Int {
        val timeline = song?.timeline ?: return 0
        if (cellMillis <= 0) return 0
        val unit = seconds * 1000 / cellMillis
        return timeline.indexOfLast { it.unitStart <= unit }.coerceIn(0, timeline.lastIndex)
    }
}

class RecordDetailViewModel(
    recordId: String,
    recordRepository: PracticeRecordRepository,
    songRepository: SongRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordDetailUiState(record = recordRepository.records.value.firstOrNull { it.id == recordId }))
    val uiState: StateFlow<RecordDetailUiState> = _uiState.asStateFlow()
    private var playJob: Job? = null

    init {
        val songId = _uiState.value.record?.songId
        if (songId != null) viewModelScope.launch { _uiState.update { it.copy(song = songRepository.getSong(songId)) } }
    }

    /** 재생·일시정지. 끝에서 재생하면 처음부터. */
    fun togglePlay() {
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
    }
}

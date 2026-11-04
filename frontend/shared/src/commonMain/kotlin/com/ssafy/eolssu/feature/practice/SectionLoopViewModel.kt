package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * PRACTICE-03. 들어오면 3·2·1을 센 뒤 구간을 반복해 따라간다. 멈췄다가 ‘반복 계속’을 누르면 다시 3·2·1(1초마다)을 센다.
 * 녹음·소리·평가는 하지 않는다(UI 단계, S15P31E102-455). 화면을 떠나면 ViewModel과 함께 진행이 멈춘다.
 */
class SectionLoopViewModel(
    private val sourceId: String,
    private val section: ScoreSection,
    private val bpm: Int,
    private val repeats: Int?,
    private val repository: WeakSectionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SectionLoopUiState(section = section, bpm = bpm, repeats = repeats))
    val uiState: StateFlow<SectionLoopUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val source = repository.source(sourceId)
            _uiState.update {
                it.copy(
                    loading = false,
                    song = source?.song,
                    errors = source?.evaluation?.errors.orEmpty(),
                    cells = source?.let { s -> section.positions(s.song.score) }.orEmpty(),
                )
            }
            if (source != null) startCountdown()
        }
    }

    /** ‘반복 멈춤’ · ‘반복 계속’(마친 뒤에는 ‘처음부터 다시’). */
    fun togglePause() {
        val state = _uiState.value
        if (state.running || state.countdown != null) {
            job?.cancel()
            _uiState.update { it.pause() }
        } else {
            startCountdown()
        }
    }

    /** 재평가로 넘어가기 전에 진행을 멈춘다. */
    fun stop() {
        job?.cancel()
        _uiState.update { it.pause() }
    }

    private fun startCountdown() {
        job?.cancel()
        _uiState.update { it.beginCountdown() }
        job = viewModelScope.launch {
            while (isActive && _uiState.value.countdown != null) {
                delay(CountdownTickMillis)
                _uiState.update { it.countdownTick() }
            }
            while (isActive && _uiState.value.running) {
                delay(_uiState.value.cellMillis.roundToLong().coerceAtLeast(1))
                _uiState.update { it.advance() }
            }
        }
    }
}

package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.PracticeRecordUnavailable
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
 * RESULT-02 상태.
 *
 * @property savedRecord '연습 기록 저장'으로 저장한 기록. 있으면 저장 확인 시트를 띄운다.
 */
data class ResultUiState(
    val result: EvaluationResult? = null,
    val song: Song? = null,
    val savedRecord: PracticeRecord? = null,
    val sheetOpen: Boolean = false,
    val savingRecord: Boolean = false,
    val recordSaveError: RecordSaveError? = null,
    val memo: String = "",
    val songLookup: SongLookup = SongLookup.Loading,
    val resultLookup: SongLookup = SongLookup.Loading,
)

enum class RecordSaveError { Unavailable, Failed }

class ResultViewModel(
    private val evaluationId: String,
    private val evaluationRepository: EvaluationRepository,
    private val songRepository: SongRepository,
    private val recordRepository: PracticeRecordRepository,
    member: SignedInMember? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()
    private var songJob: Job? = null
    private var resultJob: Job? = null

    private val accountScope = member.screenScope(viewModelScope) {
        _uiState.value = ResultUiState()
    }

    init {
        retryResult()
    }

    fun retryResult() {
        if (!accountScope.isActive) return
        if (resultJob?.isActive == true || _uiState.value.result != null) return
        _uiState.update { it.copy(resultLookup = SongLookup.Loading) }
        resultJob = accountScope.launch {
            val result = try {
                evaluationRepository.result(evaluationId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(resultLookup = SongLookup.Failed) }
                return@launch
            }
            _uiState.update { it.copy(result = result, resultLookup = if (result == null) SongLookup.Missing else SongLookup.Ready) }
            retrySong()
        }
    }

    fun retrySong() {
        if (!accountScope.isActive) return
        val result = _uiState.value.result ?: return
        val songId = result.songId
        if (songJob?.isActive == true || _uiState.value.song != null) return
        _uiState.update { it.copy(songLookup = SongLookup.Loading) }
        songJob = accountScope.launch {
            val song = try {
                songRepository.getSong(songId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(songLookup = SongLookup.Failed) }
                return@launch
            }
            currentCoroutineContext().ensureActive()
            if (song != null && result.scoreVersion != null && song.scoreVersion != result.scoreVersion) {
                _uiState.update { it.copy(song = null, songLookup = SongLookup.Failed) }
                return@launch
            }
            _uiState.update { it.copy(song = song, songLookup = if (song == null) SongLookup.Missing else SongLookup.Ready) }
        }
    }

    /** 연습 기록에 저장하고 확인 시트를 연다. 같은 결과는 다시 저장하지 않는다. */
    fun save() {
        if (!accountScope.isActive) return
        val state = _uiState.value
        if (state.savingRecord) return
        val result = state.result ?: return
        val title = state.song?.title ?: return
        if (state.savedRecord != null) {
            _uiState.update { it.copy(sheetOpen = true, recordSaveError = null) }
            return
        }
        _uiState.update { it.copy(savingRecord = true, recordSaveError = null) }
        accountScope.launch {
            try {
                val record = recordRepository.saveEvaluation(result, title)
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(savedRecord = record, sheetOpen = true, memo = record.memo) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(sheetOpen = false, recordSaveError = error.toRecordSaveError()) }
            } finally {
                _uiState.update { it.copy(savingRecord = false) }
            }
        }
    }

    fun dismissRecordSaveError() = _uiState.update { it.copy(recordSaveError = null) }

    fun changeMemo(memo: String) = _uiState.update { it.copy(memo = memo) }

    /** 시트를 닫거나 기록으로 갈 때 적은 메모를 남긴다. */
    fun closeSheet() {
        if (!accountScope.isActive) return
        commitMemo()
        _uiState.update { it.copy(sheetOpen = false) }
    }

    fun commitMemo() {
        if (!accountScope.isActive) return
        val state = _uiState.value
        val record = state.savedRecord ?: return
        if (record.memo == state.memo) return
        accountScope.launch {
            try {
                recordRepository.updateMemo(record.id, state.memo)
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(savedRecord = record.copy(memo = state.memo)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(recordSaveError = error.toRecordSaveError()) }
            }
        }
    }
}

private fun Exception.toRecordSaveError(): RecordSaveError =
    if (this is PracticeRecordUnavailable) RecordSaveError.Unavailable else RecordSaveError.Failed

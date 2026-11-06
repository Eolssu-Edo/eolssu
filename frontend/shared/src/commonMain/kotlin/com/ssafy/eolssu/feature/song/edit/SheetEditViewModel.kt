package com.ssafy.eolssu.feature.song.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SheetDocument
import com.ssafy.eolssu.data.song.SheetDocumentRepository
import com.ssafy.eolssu.data.song.SheetWorkingCopy
import com.ssafy.eolssu.data.song.SheetWrite
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ssafy.eolssu.data.song.SheetAudio
import com.ssafy.eolssu.data.song.SheetAudioFile
import com.ssafy.eolssu.data.song.SheetAudioLookup
import com.ssafy.eolssu.data.song.SheetAudioRepository
import com.ssafy.eolssu.data.song.SheetAudioRequest
import com.ssafy.eolssu.data.song.SheetAudioSettings
import com.ssafy.eolssu.data.song.SheetAudioStatus
import com.ssafy.eolssu.data.user.InstrumentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * 악보 편집(EDIT-02 · 03)과 미리보기·저장(EDIT-04). 편집 화면 경로에 묶여 미리보기에서도 같은 악보를 본다.
 * 내 악보는 [documents]로 원본·초안을 읽고 버전 충돌을 지키며 저장한다. 아직 연결하지 않은 가져오기·복사 경로만 예시를 유지한다.
 * 음원은 서버에 저장된 내 악보([SheetEditUiState.serverSheetId])만 [sheetAudio]로 만들고(SHT-12) 끝날 때까지 상태를 묻는다(SHT-13).
 *
 * @param instruments 음원 악기로 보낼 회원 선택 악기. 없으면 서버 기본(단소).
 * @param now 서명 URL 만료 판단에 쓰는 현재 시각(epoch ms).
 */
class SheetEditViewModel(
    source: EditSource,
    startView: EditView,
    private val mySheetRepository: MySheetRepository,
    private val sheetAudio: SheetAudioRepository? = null,
    private val instruments: InstrumentRepository? = null,
    private val now: () -> Long = { 0L },
    private val documents: SheetDocumentRepository? = null,
    member: SignedInMember? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SheetEditUiState.initial(source,
            if (documents != null && source is EditSource.Mine) SheetDraft("", null, 60, listOf(emptyList()), columnSizes = listOf(1))
            else SheetEditSamples.draftFor(source, mySheetRepository.sheets.value), startView)
            .copy(documentLoading = documents != null && source is EditSource.Mine),
    )
    val uiState: StateFlow<SheetEditUiState> = _uiState.asStateFlow()

    /** 음원 요청·상태 조회 작업. 한 번에 하나만 돈다. */
    private var audioJob: Job? = null
    private var autoSaveJob: Job? = null
    private var loadJob: Job? = null
    private var closeJob: Job? = null
    private var persistedDraft: SheetDraft? = null
    private val writeLock = Mutex()
    private var loaded: SheetDocument? = null
    private var version = ""
    private var revision = 0
    private val accountScope = member.screenScope(viewModelScope) {
        loaded = null
        persistedDraft = null
        version = ""
        _uiState.update { it.copy(draft = SheetDraft("", null, 60, listOf(emptyList()), columnSizes = listOf(1)),
            documentLoading = false, documentFailed = true, saving = false, message = null, audio = SheetAudioUi.None,
            past = emptyList(), future = emptyList(), savedOnce = false) }
    }

    init {
        if (documents != null && source is EditSource.Mine) loadDocument()
        else if (_uiState.value.serverSheetId != null) refreshAudio(announce = false)
    }

    private fun change(transform: (SheetEditUiState) -> SheetEditUiState) {
        if (!accountScope.isActive || _uiState.value.documentLoading || _uiState.value.documentFailed) return
        val before = _uiState.value.draft
        _uiState.update(transform)
        if (before != _uiState.value.draft && loaded != null && !_uiState.value.saveConflict) {
            autoSaveJob?.cancel()
            autoSaveJob = accountScope.launch { delay(1_000); persist(draft = true, announce = false) }
        }
    }

    fun loadDocument() {
        val repository = documents ?: return
        val id = _uiState.value.serverSheetId ?: return
        if (!accountScope.isActive || loadJob?.isActive == true || loaded != null) return
        _uiState.update { it.copy(documentLoading = true, documentFailed = false) }
        loadJob = accountScope.launch {
            try {
                val document = repository.get(id) ?: error("악보 조회 실패")
                check(document.conversionStatus == null || document.conversionStatus == "DONE")
                val copy = document.draft
                val score = copy?.score ?: document.score ?: error("변환 완료 악보 없음")
                val draft = score.editingDraft(copy?.title ?: document.title, copy?.bpm ?: document.bpm, jangdan = null)
                loaded = document
                version = document.scoreVersion
                revision = copy?.revision ?: 0
                persistedDraft = draft
                _uiState.update { it.copy(draft = draft, selected = 0, past = emptyList(), future = emptyList(),
                    documentLoading = false, documentFailed = false, savedOnce = document.score != null,
                    withJangdan = document.jangdanId != null, saveConflict = false, view = EditView.Jeongganbo) }
                refreshAudio(announce = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(documentLoading = false, documentFailed = true) } }
        }
    }

    private fun persist(draft: Boolean, announce: Boolean = true): Job? {
        val repository = documents ?: return null
        val snapshot = _uiState.value
        val id = snapshot.serverSheetId ?: return null
        if (!accountScope.isActive || snapshot.documentLoading || snapshot.documentFailed || loaded == null) return null
        if (snapshot.saveConflict) { _uiState.update { it.copy(message = SheetEditMessage.SaveConflict) }; return null }
        return accountScope.launch {
            writeLock.withLock {
                if (_uiState.value.saveConflict) return@withLock
                _uiState.update { it.copy(saving = true, emptyWarning = false) }
                try {
                    val score = snapshot.draft.toCommonScore()
                    if (!draft) score.toScore() // 입력 중인 이음·분할은 초안에만 허용한다.
                    val copy = SheetWorkingCopy(snapshot.draft.title, loaded?.draft?.jangdanId ?: loaded?.jangdanId,
                        snapshot.draft.bpm, score, version, revision)
                    when (val result = repository.save(id, copy, draft)) {
                        is SheetWrite.Saved -> {
                            persistedDraft = snapshot.draft
                            version = result.scoreVersion
                            revision = result.revision
                            _uiState.update { it.copy(savedOnce = it.savedOnce || !draft,
                                audio = if (!draft && it.audio.latest != null) SheetAudioUi.None else it.audio,
                                message = if (announce) (if (draft) SheetEditMessage.Draft else SheetEditMessage.Saved) else it.message) }
                            if (!draft) {
                                mySheetRepository.refresh()
                                refreshAudio(announce = false)
                            }
                        }
                        SheetWrite.Conflict -> _uiState.update { it.copy(saveConflict = true, message = SheetEditMessage.SaveConflict) }
                        SheetWrite.Unfilled -> _uiState.update { it.copy(emptyWarning = true) }
                        SheetWrite.TooLarge -> _uiState.update { it.copy(message = SheetEditMessage.SaveTooLarge) }
                        SheetWrite.Failed -> _uiState.update { it.copy(message = SheetEditMessage.SaveFailed) }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { _uiState.update { it.copy(message = SheetEditMessage.SaveFailed) } }
                finally { if (accountScope.isActive) _uiState.update { it.copy(saving = false) } }
            }
        }
    }

    /** 자동 저장 대기 중 닫아도 현재 편집을 먼저 저장하고, 실패하면 화면을 유지한다. */
    fun closeEditor(onClose: () -> Unit) {
        if (!accountScope.isActive || closeJob?.isActive == true) return
        if (loaded == null || (persistedDraft == _uiState.value.draft && !_uiState.value.saving && !_uiState.value.saveConflict)) { onClose(); return }
        autoSaveJob?.cancel()
        val write = persist(draft = true, announce = false)
        closeJob = accountScope.launch {
            write?.join()
            if (persistedDraft == _uiState.value.draft && !_uiState.value.saveConflict) onClose()
            else _uiState.update { it.copy(closeWarning = true) }
        }
    }

    fun continueEditing() = _uiState.update { it.copy(closeWarning = false) }

    fun discardChangesAndClose(onClose: () -> Unit) {
        if (!accountScope.isActive) return
        autoSaveJob?.cancel()
        _uiState.update { it.copy(closeWarning = false) }
        onClose()
    }

    fun selectCell(index: Int) = change { it.select(index) }
    fun previousCell() = change { it.moveSelection(-1) }
    fun nextCell() = change { it.moveSelection(1) }
    fun put(note: ScoreNote) = change { it.put(note) }
    fun erase() = change { it.erase() }
    fun putSigimsae(value: Sigimsae) = change { it.putSigimsae(value) }
    fun clearSigimsae() = change { it.clearSigimsae() }
    fun toggleSigimsae() = change { it.copy(showSigimsae = !it.showSigimsae) }
    fun split() = change { it.split() }
    fun merge() = change { it.merge() }
    fun addColumn() = change { it.addColumn() }
    fun deleteColumn() = change { it.deleteColumn() }
    fun undo() = change { it.undo() }
    fun redo() = change { it.redo() }
    fun selectTab(tab: EditTab) = change { it.copy(tab = tab) }
    fun selectView(view: EditView) = change { it.copy(view = if (it.draft.original != null) EditView.Jeongganbo else view) }
    fun selectStaffNote(key: StaffNoteKey) = change { it.selectStaffNote(key) }
    fun shiftStaffNote(steps: Int) = change { it.shiftStaffNote(steps) }
    fun changeJangdan() = change { it.copy(message = SheetEditMessage.ComingSoon) }

    fun togglePreview() = change { it.copy(previewPlaying = !it.previewPlaying) }
    fun toggleWithJangdan() = change { it.copy(withJangdan = !it.withJangdan) }
    fun stopPreview() = change { it.copy(previewPlaying = false) }
    fun requestSave() {
        if (documents == null) { change { it.requestSave() }; return }
        if (_uiState.value.serverSheetId == null) { change { it.copy(message = SheetEditMessage.ComingSoon) }; return }
        if (_uiState.value.draft.unfilledCells.isNotEmpty()) { change { it.copy(emptyWarning = true) }; return }
        autoSaveJob?.cancel()
        persist(draft = false)
    }
    fun saveDraft() {
        if (documents == null) { change { it.saveDraft() }; return }
        if (_uiState.value.serverSheetId == null) { change { it.copy(message = SheetEditMessage.ComingSoon) }; return }
        autoSaveJob?.cancel()
        persist(draft = true)
    }
    fun closeWarning() = change { it.copy(emptyWarning = false) }
    fun fillEmpty() = change { it.fillEmpty() }
    fun consumeMessage() = change { it.copy(message = null) }

    /** '음원 만들기'·'다시 만들기'·'다시 시도'. 저장된 서버 악보가 있을 때만 SHT-12를 부르고, 접수되면 끝날 때까지 상태를 묻는다. */
    fun requestAudio() {
        if (!accountScope.isActive || _uiState.value.documentLoading || _uiState.value.documentFailed || _uiState.value.saving) return
        val repository = sheetAudio
        if (repository == null) {
            change { it.copy(message = SheetEditMessage.ComingSoon) }
            return
        }
        var started = false
        val next = _uiState.updateAndGet { state ->
            val changed = state.requestAudio()
            started = changed.audio is SheetAudioUi.Requesting && state.audio !is SheetAudioUi.Requesting
            changed
        }
        val sheetId = next.serverSheetId
        if (!started || sheetId == null) return
        startAudioJob {
            val instrumentId = instruments?.instrument?.value?.let { instruments.serverIdFor(it) }
            val settings = SheetAudioSettings(instrumentId = instrumentId, withJangdan = next.withJangdan, bpm = next.draft.bpm)
            when (val result = repository.request(sheetId, settings)) {
                is SheetAudioRequest.Accepted -> receive(sheetId, result.audio, announce = true)
                SheetAudioRequest.TooLong -> failRequest(SheetEditMessage.AudioTooLong)
                SheetAudioRequest.InvalidRequest -> failRequest(SheetEditMessage.AudioInvalid)
                SheetAudioRequest.NotFound -> failRequest(SheetEditMessage.AudioNotFound)
                SheetAudioRequest.NotReady -> failRequest(SheetEditMessage.AudioNotReady)
                SheetAudioRequest.InProgress -> failRequest(SheetEditMessage.AudioInProgress)
                SheetAudioRequest.TooManyRequests, SheetAudioRequest.Unavailable -> failRequest(SheetEditMessage.AudioBusy)
            }
        }
    }

    /** '다시 확인'과 화면 진입: SHT-13으로 현재 상태를 읽고, 만드는 중이면 조회를 잇는다. [announce]면 연결 실패도 알린다. */
    fun refreshAudio(announce: Boolean = true) {
        val repository = sheetAudio ?: return
        val sheetId = _uiState.value.serverSheetId ?: return
        startAudioJob {
            when (val lookup = repository.status(sheetId)) {
                is SheetAudioLookup.Found -> receive(sheetId, lookup.audio, announce = false)
                SheetAudioLookup.None -> change { it.copy(audio = SheetAudioUi.None) }
                SheetAudioLookup.Unavailable -> if (announce) change { it.copy(message = SheetEditMessage.AudioBusy) }
            }
        }
    }

    /**
     * 재생·저장할 파일. 서명 URL이 [URL_REFRESH_MARGIN_MS] 안에 만료되면 SHT-13으로 새 URL을 받아 돌려준다.
     * 파일이 없거나 새 URL을 받지 못하면 null이고, 받지 못한 경우 안내를 띄운다.
     */
    suspend fun audioFile(kind: SheetAudioKind): SheetAudioFile? {
        val state = _uiState.value
        val sheetId = state.serverSheetId ?: return null
        val file = state.audio.latest?.file(kind) ?: return null
        if (!file.expiresWithin(now(), URL_REFRESH_MARGIN_MS)) return file
        val repository = sheetAudio ?: return null
        return when (val lookup = repository.status(sheetId)) {
            is SheetAudioLookup.Found -> {
                change { it.copy(audio = lookup.audio.toUi()) }
                if (lookup.audio.status.inProgress) startAudioJob { watch(sheetId, lookup.audio) }
                lookup.audio.file(kind)
            }
            SheetAudioLookup.None -> {
                change { it.copy(audio = SheetAudioUi.None) }
                null
            }
            SheetAudioLookup.Unavailable -> {
                change { it.copy(message = SheetEditMessage.AudioBusy) }
                null
            }
        }
    }

    fun reportAudioLinkFailed() = change { it.copy(message = SheetEditMessage.AudioLinkFailed) }

    private fun startAudioJob(block: suspend () -> Unit) {
        audioJob?.cancel()
        audioJob = accountScope.launch { block() }
    }

    /** 서버 상태를 화면에 반영하고, 만드는 중이면 끝날 때까지 묻는다. [announce]면 접수·완료·실패를 알린다. */
    private suspend fun receive(sheetId: String, audio: SheetAudio, announce: Boolean) {
        change { state ->
            state.copy(
                audio = audio.toUi(),
                message = when {
                    !announce -> state.message
                    audio.status.inProgress -> SheetEditMessage.AudioRequested
                    audio.status == SheetAudioStatus.Done -> SheetEditMessage.AudioReady
                    else -> SheetEditMessage.AudioFailed
                },
            )
        }
        if (audio.status.inProgress) watch(sheetId, audio)
    }

    /** `pollAfterMs`마다 SHT-13을 묻는다. 끝나면 알리고, 응답을 [MAX_POLL_MISSES]번 연속 받지 못하면 멈춘다. */
    private suspend fun watch(sheetId: String, first: SheetAudio) {
        val repository = sheetAudio ?: return
        var current = first
        var misses = 0
        while (current.status.inProgress) {
            delay(current.pollAfterMs ?: DEFAULT_POLL_MS)
            when (val lookup = repository.status(sheetId)) {
                is SheetAudioLookup.Found -> {
                    misses = 0
                    current = lookup.audio
                    change { it.copy(audio = current.toUi()) }
                }
                SheetAudioLookup.None -> {
                    change { it.copy(audio = SheetAudioUi.None) }
                    return
                }
                SheetAudioLookup.Unavailable -> if (++misses >= MAX_POLL_MISSES) {
                    change { it.copy(audio = SheetAudioUi.Unknown(current), message = SheetEditMessage.AudioStatusUnknown) }
                    return
                }
            }
        }
        change { it.copy(message = if (current.status == SheetAudioStatus.Done) SheetEditMessage.AudioReady else SheetEditMessage.AudioFailed) }
    }

    /** SHT-12가 거절됐다. 요청 전 상태로 돌리고 이유를 알린다. */
    private fun failRequest(message: SheetEditMessage) = change { state ->
        val audio = state.audio
        state.copy(audio = if (audio is SheetAudioUi.Requesting) audio.previous?.toUi() ?: SheetAudioUi.None else audio, message = message)
    }

    private companion object {
        /** 서버가 `pollAfterMs`를 주지 않을 때 조회 간격. */
        const val DEFAULT_POLL_MS = 3_000L

        const val MAX_POLL_MISSES = 5

        /** 서명 URL 만료 이만큼 전부터 새로 받는다. */
        const val URL_REFRESH_MARGIN_MS = 30_000L
    }
}

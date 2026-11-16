package com.ssafy.eolssu.feature.song.edit

import kotlinx.serialization.json.encodeToJsonElement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.audio.AudioSource
import com.ssafy.eolssu.core.audio.DeviceSheetAudio
import com.ssafy.eolssu.core.audio.SheetAudioDeviceStore
import com.ssafy.eolssu.core.audio.SheetAudioDownload
import com.ssafy.eolssu.core.network.ensureAccountSession
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.instrumentDisplayRank
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.song.CatalogInstrument
import com.ssafy.eolssu.data.song.ReferenceCatalogRepository
import com.ssafy.eolssu.data.song.SheetAuthoringCatalog
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SheetDocument
import com.ssafy.eolssu.data.song.SheetDocumentRepository
import com.ssafy.eolssu.data.song.matchesDraftAcknowledgement
import com.ssafy.eolssu.data.song.SheetWorkingCopy
import com.ssafy.eolssu.data.song.SheetWrite
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ssafy.eolssu.data.song.SheetAudio
import com.ssafy.eolssu.data.song.SheetAudioFailure
import com.ssafy.eolssu.data.song.SheetAudioFile
import com.ssafy.eolssu.data.song.SheetAudioLookup
import com.ssafy.eolssu.data.song.SheetAudioRepository
import com.ssafy.eolssu.data.song.SheetAudioRequest
import com.ssafy.eolssu.data.song.SheetAudioSettings
import com.ssafy.eolssu.data.song.SheetAudioStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * 악보 편집(EDIT-02 · 03)과 미리보기·저장(EDIT-04). 편집 화면 경로에 묶여 미리보기에서도 같은 악보를 본다.
 * 내 악보는 [documents]로 원본·초안을 읽고 버전 충돌을 지키며 저장한다. 아직 연결하지 않은 가져오기·복사 경로만 예시를 유지한다.
 * 음원은 서버에 저장된 내 악보([SheetEditUiState.serverSheetId])만 [sheetAudio]로 만들고(SHT-12) 끝날 때까지 상태를 묻는다(SHT-13).
 *
 * @param loadAudioInstruments 확정 악보 악기를 확인하고 음원에 쓸 활성 악기를 고르는 목록.
 * @param now 서명 URL 만료 판단에 쓰는 현재 시각(epoch ms).
 * @param songs 제공 곡 복사본(Edit-Copy)의 원본 악보(CAT-04). 없으면 예시 곡으로 연다.
 * @param pitchCatalog 서버에 등록된 율명·옥타브(CAT-06). 율명 입력판은 이 음만 적는다.
 */
class SheetEditViewModel(
    source: EditSource,
    startView: EditView,
    private val mySheetRepository: MySheetRepository,
    private val sheetAudio: SheetAudioRepository? = null,
    private val now: () -> Long = { 0L },
    private val documents: SheetDocumentRepository? = null,
    private val member: SignedInMember? = null,
    private val songs: SongRepository? = null,
    pitchCatalog: ReferenceCatalogRepository? = null,
    private val deviceAudioStore: SheetAudioDeviceStore? = null,
    loadAudioInstruments: (suspend () -> List<CatalogInstrument>)? = null,
    authoringCatalog: SheetAuthoringCatalog? = null,
    /** 저장 본문 만들기·직렬화·전송을 돌릴 곳. 앱은 [kotlinx.coroutines.Dispatchers.Default]를 넘기고, null이면 화면 코루틴에서 그대로 한다. */
    private val saveDispatcher: kotlinx.coroutines.CoroutineDispatcher? = null,
) : ViewModel() {
    private val preparedCatalog = authoringCatalog ?: if (pitchCatalog != null && loadAudioInstruments != null)
        SheetAuthoringCatalog(loadAudioInstruments, pitchCatalog, viewModelScope, member) else null
    private val pitchCatalog = preparedCatalog?.references ?: pitchCatalog
    private val loadAudioInstruments: (suspend () -> List<CatalogInstrument>)? = preparedCatalog?.let { it::instruments } ?: loadAudioInstruments
    private val practiceVersion = (source as? EditSource.Mine)?.practiceVersion
    internal val showsAudioExport: Boolean get() = !canSaveAudioOnDevice || practiceVersion != null

    /** 서버 문서나 제공 곡 원본을 읽어 와야 여는 경로. 읽기 전에는 예시 악보를 보이지 않는다. */
    private val loadsRemote = (documents != null && source is EditSource.Mine) || (songs != null && source is EditSource.CopySong)
    private val requiresCompletionCatalog = pitchCatalog != null && loadAudioInstruments != null && loadsRemote
    private val startsWithAdaptation = loadsRemote && practiceVersion == null && startView == EditView.Jeongganbo &&
        pitchCatalog != null && loadAudioInstruments != null
    private val _uiState = MutableStateFlow(
        SheetEditUiState.initial(source,
            if (loadsRemote) SheetDraft("", null, 60, listOf(emptyList()), columnSizes = listOf(1))
            else SheetEditSamples.draftFor(source, mySheetRepository.sheets.value), startView)
            .copy(documentLoading = loadsRemote, tab = if (startsWithAdaptation) EditTab.Adaptation else EditTab.Yul,
                completionCatalogState = if (requiresCompletionCatalog) SheetCompletionCatalogState.Loading else SheetCompletionCatalogState.NotRequired,
                audioInstruments = if (showsAudioExport && source is EditSource.Mine && loadAudioInstruments != null)
                    SheetAudioInstruments() else null),
    )

    /** 서버에 저장할 수 없는 악보(예시·제공 곡 복사본)의 처음 상태. 고친 뒤 닫으면 버릴지 묻는다. */
    private var expressions: kotlinx.serialization.json.JsonObject? = null
    private var expressionSourceSha256: String? = null
    private var expressionsBpm: Int = 60
    private var baseline: SheetDraft? = if (loadsRemote) null else _uiState.value.draft
    val uiState: StateFlow<SheetEditUiState> = _uiState.asStateFlow()

    /** 음원 요청·상태 조회 작업. 한 번에 하나만 돈다. */
    private var audioJob: Job? = null
    private var audioInstrumentsJob: Job? = null
    private var audioSupportJob: Job? = null
    private var audioDownloadJob: Job? = null
    private var loadJob: Job? = null
    private var jangdanJob: Job? = null
    private var completionCatalogJob: Job? = null
    private var adaptationRequest = 0L
    private var closeJob: Job? = null
    private var persistedDraft: SheetDraft? = null
    private var persistedStatus: SheetSaveStatus? = null
    private var lastFinishedDraftSave: DraftSave? = null
    private var savedJangdanId: String? = null
    private var confirmedDraft: SheetDraft? = null
    private val writeLock = Mutex()
    private var confirmationPending = false
    private var loaded: SheetDocument? = null
    private var version = ""
    private var revision = 0
    private val accountScope = member.screenScope(viewModelScope) {
        loaded = null
        baseline = null
        persistedDraft = null
        persistedStatus = null
        lastFinishedDraftSave = null
        savedJangdanId = null
        confirmedDraft = null
        expressions = null
        expressionSourceSha256 = null
        expressionsBpm = 60
        version = ""
        _uiState.update { it.copy(draft = SheetDraft("", null, 60, listOf(emptyList()), columnSizes = listOf(1)),
            documentLoading = false, documentFailed = true, practiceVersionChanged = false,
            saving = false, confirming = false, saveStatus = null, message = null, audio = SheetAudioUi.None,
            deviceAudio = null, audioDownloading = false,
            reviewNotices = emptyList(), instrumentPitches = null, availableJangdans = null, completionWarning = false,
            completionCatalogState = SheetCompletionCatalogState.NotRequired,
            past = emptyList(), future = emptyList(), savedOnce = false, reviewIssues = emptyList(), checkedIssueIds = emptySet(),
            jangdanPicker = null, audioInstruments = null, adaptation = null) }
    }

    init {
        if (loadsRemote) loadDocument()
        else if (_uiState.value.serverSheetId != null) refreshAudio(announce = false)
        if (!requiresCompletionCatalog) loadPitches()
    }

    /** 등록 음 목록. 받지 못하면 null로 두고 저장 때 서버가 확인한다. */
    private fun loadPitches() {
        val catalog = pitchCatalog ?: return
        accountScope.launch {
            val pitches = try { catalog.allPitches() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { return@launch }
            _uiState.update { it.copy(availablePitches = pitches.map { pitch -> pitch.yul to pitch.octave }.toSet()) }
        }
    }

    /** 저장 검증의 악기 음역은 음원 내보내기 UI 표시 여부와 무관하게 읽는다. */
    private fun loadCompletionCatalog() {
        val catalog = pitchCatalog ?: return
        val state = _uiState.value
        if (!accountScope.isActive || state.documentLoading || state.documentFailed || completionCatalogJob?.isActive == true) return
        val instrumentCode = state.draft.original?.score?.instrument ?: return
        if (requiresCompletionCatalog) _uiState.update { it.copy(completionCatalogState = SheetCompletionCatalogState.Loading) }
        completionCatalogJob = accountScope.launch {
            if (requiresCompletionCatalog) {
                try {
                    val registered = catalog.allPitches().map { it.yul to it.octave }.toSet()
                    val instruments = requireNotNull(loadAudioInstruments).invoke()
                    val instrumentId = requireNotNull(instruments.singleOrNull { it.code == instrumentCode }?.instrumentId)
                    val capabilities = catalog.authoringCapabilities(instrumentId)
                    val pitches = capabilities.selectablePitches
                    val jangdans = catalog.jangdans()
                    require(registered.isNotEmpty() && registered.containsAll(pitches))
                    ensureAccountSession()
                    _uiState.update { if (it.draft.original?.score?.instrument != instrumentCode) it else it.copy(availablePitches = registered, instrumentPitches = pitches, authoringCapabilities = capabilities,
                        availableJangdans = jangdans,
                        completionCatalogState = SheetCompletionCatalogState.Ready) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    ensureAccountSession()
                    _uiState.update { it.copy(completionCatalogState = SheetCompletionCatalogState.Failed) }
                }
            }
            if (!requiresCompletionCatalog && state.draft.jangdanId != null) {
                try {
                    val jangdans = catalog.jangdans()
                    ensureAccountSession()
                    _uiState.update { it.copy(availableJangdans = jangdans) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* 장단 정보가 없으면 장단 해제 또는 다시 선택으로 해결한다. */ }
            }
        }
    }

    fun retryCompletionCatalog() = loadCompletionCatalog()

    fun openAdaptation() {
        val before = _uiState.value
        if (!accountScope.isActive || before.confirming || before.documentLoading || before.draft.original == null) return
        val existing = before.adaptation
        if (existing != null && existing.baseline == before.draft && !existing.loading && !existing.failed) {
            _uiState.update { it.copy(tab = EditTab.Adaptation, view = EditView.Jeongganbo) }
            return
        }
        if (existing != null && existing.baseline == before.draft && existing.failed && existing.selectedId != null) {
            _uiState.update { it.copy(tab = EditTab.Adaptation, view = EditView.Jeongganbo) }
            selectAdaptationInstrument(existing.selectedId)
            return
        }
        val options = preparedCatalog?.cachedInstruments()
        val selected = options?.firstOrNull { it.code == before.draft.original.score.instrument } ?: options?.firstOrNull()
        val capabilities = selected?.let { preparedCatalog?.cachedCapabilities(it.instrumentId) }
        if (options != null && selected != null && capabilities != null) {
            val panel = readyAdaptation(SheetAdaptationUi(instruments = options, selectedId = selected.instrumentId,
                baseline = before.draft), capabilities)
            ++adaptationRequest
            _uiState.update { it.copy(tab = EditTab.Adaptation, view = EditView.Jeongganbo, adaptation = panel) }
            return
        }
        val request = ++adaptationRequest
        _uiState.update { it.copy(tab = EditTab.Adaptation, view = EditView.Jeongganbo,
            adaptation = SheetAdaptationUi(baseline = before.draft)) }
        accountScope.launch {
            try {
                val options = requireNotNull(loadAudioInstruments).invoke()
                ensureAccountSession()
                if (request != adaptationRequest) return@launch
                _uiState.update { current -> if (current.adaptation == null) current else current.copy(
                    adaptation = current.adaptation.copy(instruments = options)) }
                val selected = options.firstOrNull { it.code == before.draft.original.score.instrument } ?: options.firstOrNull()
                selectAdaptationInstrument(requireNotNull(selected).instrumentId)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (request == adaptationRequest) _uiState.update { it.copy(adaptation = it.adaptation?.copy(loading = false, failed = true)) } }
        }
    }

    fun selectAdaptationInstrument(id: String) {
        val catalog = pitchCatalog ?: return
        val panel = _uiState.value.adaptation ?: return
        if (!accountScope.isActive || panel.baseline != _uiState.value.draft || panel.instruments.none { it.instrumentId == id }) return
        if (panel.selectedId == id && !panel.loading && !panel.failed) return
        preparedCatalog?.cachedCapabilities(id)?.let { capabilities ->
            ++adaptationRequest
            val ready = readyAdaptation(panel.copy(selectedId = id), capabilities)
            _uiState.update { it.copy(adaptation = ready) }
            return
        }
        val request = ++adaptationRequest
        _uiState.update { it.copy(adaptation = it.adaptation?.copy(selectedId = id, loading = true, failed = false, confirmationRequested = false)) }
        accountScope.launch {
            try {
                val capabilities = catalog.authoringCapabilities(id)
                ensureAccountSession()
                if (request != adaptationRequest) return@launch
                _uiState.update { state -> if (state.adaptation?.selectedId != id) state else state.copy(
                    adaptation = readyAdaptation(state.adaptation, capabilities)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (request == adaptationRequest) _uiState.update { state -> if (state.adaptation?.selectedId != id) state else
                state.copy(adaptation = state.adaptation.copy(loading = false, failed = true)) } }
        }
    }

    private fun readyAdaptation(panel: SheetAdaptationUi, capabilities: com.ssafy.eolssu.data.song.CatalogAuthoringCapabilities): SheetAdaptationUi {
        val pitches = capabilities.items.filter { it.selectable }.map { AdaptationPitch(it.midi, it.yul, it.octave) }
        val plans = planPitchAdaptation(panel.baseline.sourcePitchNotes(), pitches)
        return panel.copy(capabilities = capabilities, plans = plans, selectedTranspose = panel.baseline.appliedTranspose,
            loading = false, failed = false, confirmationRequested = false)
    }

    /** 문서가 준비됐다는 화면 뒤로 추천 로딩을 따로 노출하지 않는다. */
    private suspend fun initialAdaptation(draft: SheetDraft): SheetAdaptationUi {
        var panel = SheetAdaptationUi(baseline = draft)
        return try {
            val options = requireNotNull(loadAudioInstruments).invoke()
            val selected = options.firstOrNull { it.code == draft.original?.score?.instrument } ?: options.firstOrNull()
            panel = panel.copy(instruments = options, selectedId = requireNotNull(selected).instrumentId)
            val capabilities = requireNotNull(pitchCatalog).authoringCapabilities(selected.instrumentId)
            ensureAccountSession()
            readyAdaptation(panel, capabilities)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { panel.copy(loading = false, failed = true) }
    }

    fun selectAdaptationTranspose(value: Int) = _uiState.update { state ->
        if (state.adaptation?.plans?.any { it.transpose == value } != true) state
        else state.copy(adaptation = state.adaptation.copy(selectedTranspose = value, confirmationRequested = false))
    }

    fun closeAdaptation() {
        ++adaptationRequest
        _uiState.update { it.copy(adaptation = null, tab = if (it.tab == EditTab.Adaptation) EditTab.Yul else it.tab) }
    }

    fun requestApplyAdaptation() = _uiState.update { state ->
        val panel = state.adaptation
        if (panel == null || panel.loading || panel.failed || panel.selected == null || panel.baseline != state.draft) state
        else state.copy(adaptation = panel.copy(confirmationRequested = true))
    }

    fun cancelApplyAdaptation() = _uiState.update { it.copy(adaptation = it.adaptation?.copy(confirmationRequested = false)) }

    /**
     * 확인 안내의 '추천 적용'. 인식한 그대로라 덮어쓸 수정이 없으면 바로 적용하고(되돌리기로 취소),
     * 편집했거나 임시저장본을 열었으면 슬라이더의 적용과 같은 덮어쓰기 확인을 거친다.
     */
    fun applyRecommendedAdaptation() {
        val transpose = _uiState.value.importGuide?.recommendedTranspose ?: return
        selectAdaptationTranspose(transpose)
        requestApplyAdaptation()
        if (_uiState.value.past.isEmpty() && loaded?.draft == null) applyAdaptation()
    }

    /** 음역 문제가 아닌 첫 인식 확인 정간으로 간다. */
    fun openFirstRecognitionReview() {
        val validation = _uiState.value.completionValidation
        val index = (validation.correctionCellIndices - validation.rangeCellIndices).minOrNull() ?: return
        selectCell(index)
    }

    fun dismissImportGuide() = _uiState.update { it.copy(importGuideDismissed = true) }

    fun applyAdaptation() {
        val panel = _uiState.value.adaptation ?: return
        val plan = panel.selected ?: return
        val capability = panel.capabilities ?: return
        if (!panel.confirmationRequested || panel.loading || panel.failed || panel.baseline != _uiState.value.draft) return
        change(preserveAdaptation = true) { state ->
            val applied = state.applyAdaptation(plan, capability)
            val ready = readyAdaptation(panel.copy(baseline = applied.draft), capability)
                .copy(selectedTranspose = panel.selectedTranspose)
            applied.copy(tab = EditTab.Adaptation, adaptation = ready)
        }
        completionCatalogJob?.cancel()
        completionCatalogJob = null
        loadCompletionCatalog()
    }

    private fun change(preserveAdaptation: Boolean = false, transform: (SheetEditUiState) -> SheetEditUiState) {
        if (!accountScope.isActive || _uiState.value.documentLoading || _uiState.value.documentFailed) return
        val before = _uiState.value.draft
        _uiState.update { current ->
            val next = transform(current)
            // 최종 저장 응답은 보낸 악보의 확인 결과다. 초안 자동 저장 중에는 계속 편집한다.
            if (current.confirming && (next.draft != current.draft || next.checkedIssueIds != current.checkedIssueIds ||
                next.reviewIssues != current.reviewIssues)) current
            else if (next.draft != current.draft) next.copy(saveStatus = null,
                adaptation = if (preserveAdaptation) next.adaptation else null,
                tab = if (!preserveAdaptation && next.tab == EditTab.Adaptation) EditTab.Yul else next.tab)
            else next
        }
        if (before != _uiState.value.draft) {
            ++adaptationRequest
            _uiState.update { it.copy(serverPitchIssues = emptyList()) }
        }
        if (before.original?.score?.instrument != _uiState.value.draft.original?.score?.instrument) {
            completionCatalogJob?.cancel()
            completionCatalogJob = null
            _uiState.update { it.copy(authoringCapabilities = null, instrumentPitches = null,
                completionCatalogState = if (requiresCompletionCatalog) SheetCompletionCatalogState.Loading else it.completionCatalogState) }
            loadCompletionCatalog()
        }
        // 편집은 자동저장하지 않는다. 저장·임시저장을 누르거나 닫을 때 임시저장을 고른 경우에만 서버에 쓴다(S15P31E102-889).
    }

    fun loadDocument() {
        if ((_uiState.value.source as? EditSource.CopySong) != null) { loadSongCopy(); return }
        val repository = documents ?: return
        val id = _uiState.value.serverSheetId ?: return
        if (!accountScope.isActive || loadJob?.isActive == true || loaded != null) return
        _uiState.update { it.copy(documentLoading = true, documentFailed = false, practiceVersionChanged = false) }
        loadJob = accountScope.launch {
            try {
                val document = repository.get(id) ?: error("악보 조회 실패")
                ensureAccountSession()
                if (practiceVersion != null && document.scoreVersion.toIntOrNull() != practiceVersion) {
                    _uiState.update { it.copy(documentLoading = false, documentFailed = true,
                        practiceVersionChanged = true, savedOnce = false) }
                    return@launch
                }
                check(document.conversionStatus == null || document.conversionStatus == "DONE")
                val copy = document.draft.takeIf { practiceVersion == null }
                val score = copy?.score ?: document.score ?: error("변환 완료 악보 없음")
                val jangdanId = if (copy == null) document.jangdanId else copy.jangdanId
                val draft = score.editingDraft(copy?.title ?: document.title, copy?.bpm ?: document.bpm, jangdan = null)
                    .copy(jangdanId = jangdanId, authoring = copy?.authoring ?: document.authoring)
                    .withTempoExpressions(copy?.expressions ?: document.expressions, document.bpm)
                savedJangdanId = document.jangdanId
                val deviceAudio = member?.id?.let { accountId ->
                    try { deviceAudioStore?.find(accountId, id, document.scoreVersion) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                }
                ensureAccountSession()
                expressions = copy?.expressions ?: document.expressions
                expressionSourceSha256 = document.sourceSha256
                expressionsBpm = document.bpm
                loaded = document
                version = document.scoreVersion
                revision = copy?.revision ?: 0
                persistedDraft = draft
                confirmedDraft = document.score?.editingDraft(document.title, document.bpm, jangdan = null)
                    ?.copy(jangdanId = document.jangdanId, authoring = document.authoring)
                    ?.withTempoExpressions(document.expressions, document.bpm)
                val adaptation = if (startsWithAdaptation) initialAdaptation(draft) else null
                _uiState.update { it.copy(draft = draft, selected = 0, past = emptyList(), future = emptyList(),
                    documentLoading = false, documentFailed = false, savedOnce = document.score != null,
                    hasTempoPlan = expressions?.containsKey("performancePlan") == true,
                    withJangdan = draft.jangdanId != null, saveConflict = false, view = EditView.Jeongganbo,
                    reviewIssues = document.editorReviewIssues(document.score ?: score), checkedIssueIds = emptySet(),
                    reviewNotices = document.editorReviewNotices(),
                    deviceAudio = deviceAudio, adaptation = adaptation) }
                loadCompletionCatalog()
                loadAudioInstrumentOptions()
                refreshAudio(announce = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(documentLoading = false, documentFailed = true) } }
        }
    }

    /** 제공 곡 복사본: CAT-04 원본의 각·정간 배치와 시김새를 그대로 편집 상태로 옮긴다. 원본 곡은 바꾸지 않는다. */
    private fun loadSongCopy() {
        val repository = songs ?: return
        val songId = (_uiState.value.source as? EditSource.CopySong)?.songId ?: return
        if (!accountScope.isActive || loadJob?.isActive == true || baseline != null) return
        _uiState.update { it.copy(documentLoading = true, documentFailed = false) }
        loadJob = accountScope.launch {
            try {
                val editable = repository.getEditableSong(songId) ?: error("곡 조회 실패")
                val song = editable.song
                val draft = editable.score.editingDraft(SheetEditSamples.copyTitle(song.title), song.baseBpm, song.jangdan)
                baseline = draft
                val adaptation = if (startsWithAdaptation) initialAdaptation(draft) else null
                _uiState.update { it.copy(draft = draft, selected = 0, past = emptyList(), future = emptyList(),
                    documentLoading = false, documentFailed = false, view = EditView.Jeongganbo, adaptation = adaptation) }
                loadCompletionCatalog()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(documentLoading = false, documentFailed = true) } }
        }
    }

    private suspend fun <T> onSaveWorker(block: suspend () -> T): T =
        saveDispatcher?.let { kotlinx.coroutines.withContext(it) { block() } } ?: block()

    private class DraftSave(val draft: SheetDraft, var unconfirmedRequest: SheetWorkingCopy?)

    private fun persist(
        draft: Boolean,
        announce: Boolean = true,
    ): Job? {
        val repository = documents ?: return null
        val requested = _uiState.value
        val id = requested.serverSheetId ?: return null
        if (!accountScope.isActive || requested.documentLoading || requested.documentFailed || loaded == null) return null
        if (!draft && confirmationPending) return null
        if (requested.saveConflict) { _uiState.update { it.copy(message = SheetEditMessage.SaveConflict) }; return null }
        if (!draft) {
            if (blockInvalidCompletion(requested)) return null
            // 쓰기 잠금을 기다리는 동안에도 확정 저장의 중복 입력을 막는다.
            confirmationPending = true
        }
        return accountScope.launch {
            try {
                writeLock.withLock {
                    if (_uiState.value.saveConflict) return@withLock
                    val precedingDraft = lastFinishedDraftSave
                    var writing: SheetDraft? = null
                    try {
                        if (precedingDraft?.unconfirmedRequest != null) {
                            _uiState.update { it.copy(saving = true, saveStatus = null) }
                            val reconciled = reconcileDraftSave(repository, id, precedingDraft)
                            if (!reconciled) {
                                if (!_uiState.value.saveConflict) recordSaveFailure(_uiState.value.draft, draft, announce)
                                return@withLock
                            }
                        }
                        val snapshot = _uiState.value
                        if (draft && isDraftPersisted(snapshot.draft) && (!announce || persistedStatus == SheetSaveStatus.DraftSaved)) {
                            _uiState.update {
                                it.copy(saveStatus = if (announce || it.saveStatus == null) persistedStatus else it.saveStatus,
                                    message = if (announce) SheetEditMessage.Draft else it.message)
                            }
                            return@withLock
                        }
                        if (!draft && blockInvalidCompletion(snapshot)) return@withLock
                        writing = snapshot.draft
                        _uiState.update { it.copy(saving = true, confirming = !draft, saveStatus = null,
                            emptyWarning = false, completionWarning = false) }
                        // 큰 악보의 본문 만들기·해시·JSON 직렬화가 화면 스레드를 멈추지 않도록 저장 작업 스레드에서 한다.
                        // 작업 스레드에서는 화면 상태를 바꾸지 않도록 필요한 값을 먼저 읽어 둔다.
                        val baseExpressions = expressions
                        val baseSourceSha256 = expressionSourceSha256
                        val baseExpressionsBpm = expressionsBpm
                        val baseVersion = version
                        val baseRevision = revision
                        val resolvedIds = if (draft) emptyList() else snapshot.reviewIssues
                            .map(SheetReviewIssue::id).filter { it in snapshot.validatedCheckedIssueIds }
                        val copy = onSaveWorker {
                            val score = snapshot.draft.toCommonScore()
                            if (!draft) score.toScore() // 입력 중인 이음·분할은 초안에만 허용한다.
                            SheetWorkingCopy(snapshot.draft.title, snapshot.draft.jangdanId,
                                snapshot.draft.bpm, score, baseVersion, baseRevision, resolvedIds, authoring = snapshot.draft.authoringForSave(score.score.instrument), expressions = snapshot.draft.exportExpressions(baseExpressions,
                                    (baseExpressions?.get("sourceSha256") as? kotlinx.serialization.json.JsonPrimitive)?.content
                                        ?: baseSourceSha256
                                        ?: com.ssafy.eolssu.data.song.commonSourceSha256(kotlinx.serialization.json.Json.encodeToJsonElement(requireNotNull(snapshot.draft.original))),
                                    baseExpressionsBpm))
                        }
                        val attempt = if (draft) DraftSave(snapshot.draft, copy) else null
                        val result = try {
                            onSaveWorker { repository.save(id, copy, draft) }.also { ensureAccountSession() }
                        } finally {
                            if (attempt != null && accountScope.isActive) lastFinishedDraftSave = attempt
                        }
                        if (result != SheetWrite.Failed) attempt?.unconfirmedRequest = null
                        when (result) {
                            is SheetWrite.Saved -> {
                                if (!draft) {
                                    expressions = result.expressions
                                    expressionSourceSha256 = (expressions?.get("sourceSha256") as? kotlinx.serialization.json.JsonPrimitive)?.content
                                    expressionsBpm = snapshot.draft.bpm
                                }
                                persistedDraft = snapshot.draft
                                persistedStatus = if (draft) SheetSaveStatus.DraftSaved else SheetSaveStatus.Confirmed
                                if (!draft) confirmedDraft = snapshot.draft
                                val versionChanged = result.scoreVersion != version
                                version = result.scoreVersion
                                revision = result.revision
                                if (versionChanged) audioDownloadJob?.cancel()
                                val saveMessage = when {
                                    draft -> SheetEditMessage.Draft
                                    result.reviewStatus == "EVALUABLE" -> SheetEditMessage.SavedForPractice
                                    result.reviewStatus == "NEEDS_REVIEW" -> SheetEditMessage.SavedNeedsReview
                                    else -> SheetEditMessage.Saved
                                }
                                _uiState.update { it.copy(savedOnce = it.savedOnce || !draft,
                                    saveStatus = if (it.draft == snapshot.draft) persistedStatus else it.saveStatus,
                                    audio = if (!draft && it.audio.latest != null) SheetAudioUi.None else it.audio,
                                    deviceAudio = if (versionChanged) null else it.deviceAudio,
                                    audioDownloading = !versionChanged && it.audioDownloading,
                                    reviewIssues = it.reviewIssues.filterNot { issue -> issue.id in resolvedIds },
                                    checkedIssueIds = it.checkedIssueIds - resolvedIds.toSet(),
                                    message = if (announce) saveMessage else it.message) }
                                if (!draft) {
                                    savedJangdanId = snapshot.draft.jangdanId
                                    accountScope.launch { mySheetRepository.refresh() }
                                    if (_uiState.value.audioInstruments?.options?.isEmpty() == true) loadAudioInstrumentOptions()
                                    else validateAudioInstrument()
                                    refreshAudio(announce = false)
                                }
                            }
                            is SheetWrite.UnsupportedPitches -> {
                                _uiState.update { it.copy(serverPitchIssues = result.details.issues, completionWarning = true,
                                    message = SheetEditMessage.SaveUnsupportedPitches).focusCompletionIssue() }
                                loadCompletionCatalog()
                            }
                            is SheetWrite.TempoRejected -> _uiState.update { it.copy(tempoIssue = result.issue) }
                            SheetWrite.Conflict -> reportSaveConflict()
                            SheetWrite.Unfilled -> _uiState.update { it.copy(emptyWarning = true) }
                            SheetWrite.TooLarge -> recordSaveFailure(snapshot.draft, draft, announce, SheetEditMessage.SaveTooLarge)
                            SheetWrite.Failed -> recordSaveFailure(snapshot.draft, draft, announce)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        recordSaveFailure(writing ?: _uiState.value.draft, draft, announce)
                    } finally {
                        if (accountScope.isActive) _uiState.update { it.copy(saving = false, confirming = false) }
                    }
                }
            } finally {
                if (!draft) confirmationPending = false
            }
        }
    }

    private fun isDraftPersisted(draft: SheetDraft): Boolean =
        lastFinishedDraftSave?.unconfirmedRequest == null && persistedDraft == draft

    private fun reportSaveConflict() =
        _uiState.update { it.copy(saveConflict = true, message = SheetEditMessage.SaveConflict) }

    private suspend fun reconcileDraftSave(repository: SheetDocumentRepository, id: String, attempt: DraftSave): Boolean {
        val request = requireNotNull(attempt.unconfirmedRequest)
        val document = repository.get(id) ?: return false
        ensureAccountSession()
        val remote = document.draft
        val nextRevision = request.revision + 1
        when {
            document.scoreVersion != request.baseScoreVersion -> {
                reportSaveConflict()
                return false
            }
            remote?.matchesDraftAcknowledgement(request.copy(revision = nextRevision)) == true -> {
                revision = nextRevision
                persistedDraft = attempt.draft
                persistedStatus = SheetSaveStatus.DraftSaved
            }
            (remote?.revision ?: 0) == request.revision -> {
                // 아직 처리 중인 요청도 같은 리비전을 쓸 수 있으므로 현재 초안은 PUT의 성공을 확인한다.
                persistedDraft = null
                persistedStatus = null
            }
            else -> {
                reportSaveConflict()
                return false
            }
        }
        attempt.unconfirmedRequest = null
        return true
    }

    private fun recordSaveFailure(
        snapshot: SheetDraft,
        draft: Boolean,
        announce: Boolean,
        message: SheetEditMessage = SheetEditMessage.SaveFailed,
    ) {
        val status = when {
            message == SheetEditMessage.SaveTooLarge -> SheetSaveStatus.TooLarge
            draft -> SheetSaveStatus.DraftFailed
            else -> SheetSaveStatus.ConfirmationFailed
        }
        _uiState.update {
            if (it.draft != snapshot) it
            else it.copy(saveStatus = status, message = if (announce) message else it.message)
        }
    }

    /**
     * 저장한 내용과 같으면 바로 닫는다. 저장하지 않은 변경이 있으면 저절로 저장하지 않고 임시저장·계속 편집·버리기를 묻는다.
     * 누른 저장이 아직 진행 중이면 결과를 기다린 뒤 판단한다.
     */
    fun closeEditor(onClose: () -> Unit) {
        if (!accountScope.isActive || closeJob?.isActive == true) return
        if (loaded == null) {
            // 서버에 저장할 수 없는 악보는 고친 내용이 닫으면 사라지므로 먼저 묻는다.
            val start = baseline
            if (start != null && _uiState.value.draft != start && !_uiState.value.documentFailed) _uiState.update { it.copy(closeWarning = true) }
            else onClose()
            return
        }
        val state = _uiState.value
        if (isDraftPersisted(state.draft) && !state.saving && !confirmationPending && !state.saveConflict) {
            onClose()
            return
        }
        if (!state.saving && !confirmationPending) {
            _uiState.update { it.copy(closeWarning = true) }
            return
        }
        closeJob = accountScope.launch {
            writeLock.withLock { }
            if (isDraftPersisted(_uiState.value.draft) && !_uiState.value.saveConflict) onClose()
            else _uiState.update { it.copy(closeWarning = true) }
        }
    }

    /**
     * 닫기 확인에서 '임시저장하고 닫기': 지금 편집을 임시저장하고 성공하면 닫는다.
     * 저장하는 동안 확인 창에 저장 중을 보이며 다시 누르거나 창을 닫지 못하게 하고, 실패하면 화면과 확인 창을 유지한다.
     */
    fun saveDraftAndClose(onClose: () -> Unit) {
        if (!accountScope.isActive || closeJob?.isActive == true) return
        _uiState.update { it.copy(closeWarning = true, closeSaving = true) }
        val write = persist(draft = true, announce = false)
        closeJob = accountScope.launch {
            try {
                write?.join()
                if (isDraftPersisted(_uiState.value.draft) && !_uiState.value.saveConflict) {
                    _uiState.update { it.copy(closeWarning = false, closeSaving = false) }
                    onClose()
                }
            } finally {
                _uiState.update { it.copy(closeSaving = false) }
            }
        }
    }

    fun continueEditing() = _uiState.update { if (it.closeSaving) it else it.copy(closeWarning = false) }

    fun discardChangesAndClose(onClose: () -> Unit) {
        if (!accountScope.isActive || _uiState.value.closeSaving) return
        _uiState.update { it.copy(closeWarning = false) }
        onClose()
    }

    fun openNextReviewCell() = change { state ->
        state.nextReviewCellIndex?.let(state::select) ?: state
    }

    fun checkReviewIssue() = change { state ->
        val issue = state.nextReviewIssue ?: return@change state
        if (state.nextReviewCellIndex?.let { it != state.selected } == true) return@change state
        if (!state.canCheckNextReviewIssue) return@change state.copy(completionWarning = true)
        state.copy(checkedIssueIds = state.checkedIssueIds + issue.id)
    }

    fun checkSelectedReviewIssue() = change { state ->
        val issue = state.selectedReviewIssue ?: return@change state
        if (!state.canCheckSelectedReviewIssue) return@change state.copy(completionWarning = true)
        state.copy(checkedIssueIds = state.checkedIssueIds + issue.id)
    }

    fun openCompletionIssue() = change { state ->
        state.focusCompletionIssue().copy(completionWarning = false)
    }

    fun selectCell(index: Int) = change { it.select(index) }
    fun selectUnit(index: Int, sub: Int) = change { it.selectUnit(index, sub) }
    fun put(note: ScoreNote) = change { it.put(note) }
    fun erase() = change { it.erase() }
    fun putYul(base: Yul) = change { it.putYul(base) }
    fun applyPitchCorrection(target: SheetPitchCorrection, pitch: Pair<Yul, Int>) = change { it.applyPitchCorrection(target, pitch) }
    fun selectRegister(register: YulRegister) = change { it.selectRegister(register) }
    fun toggleSigimsae(value: Sigimsae) = change { it.toggleSigimsae(value) }
    fun removeSigimsae(position: Int) = change { it.removeSigimsae(position) }
    fun split() = change { it.split() }
    fun merge() = change { it.merge() }
    fun addColumn() = change { if (it.reviewIssues.isEmpty()) it.addColumn() else it.copy(message = SheetEditMessage.ReviewBeforeReshape) }
    fun deleteColumn() = change { if (it.reviewIssues.isEmpty()) it.deleteColumn() else it.copy(message = SheetEditMessage.ReviewBeforeReshape) }
    fun undo() = change { it.undo() }
    fun redo() = change { it.redo() }
    fun updateTitle(title: String) = change { it.updateTitle(title) }
    fun updateDetails(title: String, bpm: Int) = change { it.updateDetails(title, bpm) }
    fun updateTempoSettings(draft: SheetDraft) = change { it.updateTempoSettings(draft) }
    fun updateSettings(bpm: Int, jangdanId: String?) {
        jangdanJob?.cancel()
        change { it.updateSettings(bpm, jangdanId) }
    }
    fun selectTab(tab: EditTab) {
        if (tab == EditTab.Adaptation) openAdaptation()
        else change { it.copy(tab = tab) }
    }
    fun selectView(view: EditView) = change { state ->
        if (state.view == view || state.draft.original == null) return@change state.copy(view = view)
        when (view) {
            EditView.Staff -> state.copy(view = view, staffKey = StaffNoteKey(state.selected, state.selectedSub ?: 0))
            EditView.Jeongganbo -> (state.staffKey?.let { state.selectUnit(it.start, it.sub) } ?: state).copy(view = view)
        }
    }
    fun selectStaffNote(key: StaffNoteKey) = change { it.selectStaffNote(key) }
    fun shiftStaffNote(steps: Int) = change { it.shiftStaffNote(steps) }
    fun changeJangdan() {
        val state = _uiState.value
        if (!accountScope.isActive || state.documentLoading || state.documentFailed || jangdanJob?.isActive == true) return
        val catalog = pitchCatalog
        if (loaded == null || catalog == null) {
            change { it.copy(message = SheetEditMessage.JangdanUnavailable) }
            return
        }
        change { it.copy(jangdanPicker = SheetEditJangdanPicker.Loading) }
        jangdanJob = accountScope.launch {
            val picker = try {
                SheetEditJangdanPicker.Ready(catalog.jangdans())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                SheetEditJangdanPicker.Failed
            }
            change { it.copy(jangdanPicker = picker,
                availableJangdans = (picker as? SheetEditJangdanPicker.Ready)?.options ?: it.availableJangdans) }
        }
    }

    fun selectJangdan(id: String) = change { it.selectJangdan(id) }
    fun selectBasicAccompaniment(preset: com.ssafy.eolssu.core.model.BasicAccompanimentPreset, startCell: Int) {
        jangdanJob?.cancel()
        change { it.selectBasicAccompaniment(preset, startCell) }
    }
    fun clearJangdan() {
        jangdanJob?.cancel()
        change { it.clearJangdan() }
    }

    fun closeJangdanPicker() {
        jangdanJob?.cancel()
        change { it.copy(jangdanPicker = null) }
    }

    fun toggleWithJangdan() = change {
        if (it.canIncludeJangdan && !it.audioDownloading) it.copy(withJangdan = !it.withJangdan) else it
    }

    fun loadAudioInstrumentOptions() {
        if (!showsAudioExport) return
        val load = loadAudioInstruments ?: return
        val instrumentCode = confirmedDraft?.original?.score?.instrument ?: return
        if (!accountScope.isActive || audioInstrumentsJob?.isActive == true) return
        change { it.copy(audioInstruments = SheetAudioInstruments()) }
        audioInstrumentsJob = accountScope.launch {
            try {
                val options = load().filter { option -> Instrument.entries.any { it.serverCode == option.code } }
                    .sortedBy { instrumentDisplayRank(it.code) }
                ensureAccountSession()
                val selectedId = options.firstOrNull { it.code == instrumentCode }?.instrumentId
                change { it.copy(audioInstruments = SheetAudioInstruments(options, selectedId, loading = false)) }
                validateAudioInstrument()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureAccountSession()
                change { it.copy(audioInstruments = SheetAudioInstruments(loading = false)) }
            }
        }
    }

    fun selectAudioInstrument(id: String) {
        change { state ->
            val instruments = state.audioInstruments ?: return@change state
            if (state.audio.busy || state.audioDownloading || instruments.options.none { it.instrumentId == id }) return@change state
            state.copy(audioInstruments = instruments.copy(selectedId = id))
        }
        validateAudioInstrument()
    }

    private fun validateAudioInstrument() {
        val catalog = pitchCatalog ?: return
        val selected = _uiState.value.audioInstruments?.selectedId ?: return
        val score = confirmedDraft?.toCommonScore() ?: return
        audioSupportJob?.cancel()
        _uiState.update { it.copy(audioInstruments = it.audioInstruments?.copy(supportLoading = true, supportFailed = false, unsupported = emptyList())) }
        audioSupportJob = accountScope.launch {
            try {
                val issues = catalog.authoringCapabilities(selected).issues(score)
                ensureAccountSession()
                _uiState.update { if (it.audioInstruments?.selectedId != selected) it else it.copy(audioInstruments = it.audioInstruments.copy(supportLoading = false, unsupported = issues)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(audioInstruments = it.audioInstruments?.copy(supportLoading = false, supportFailed = true)) } }
        }
    }
    fun requestSave() {
        if (documents == null) { change { it.requestSave() }; return }
        if (_uiState.value.serverSheetId == null) { change { it.copy(message = SheetEditMessage.ComingSoon) }; return }
        if (blockInvalidCompletion(_uiState.value)) return
        persist(draft = false)
    }
    fun saveDraft() {
        if (documents == null) { change { it.saveDraft() }; return }
        if (_uiState.value.serverSheetId == null) { change { it.copy(message = SheetEditMessage.ComingSoon) }; return }
        persist(draft = true)
    }
    fun retryDraftSave() {
        val state = _uiState.value
        if (state.saveStatus != SheetSaveStatus.DraftFailed || state.saving || state.confirming || state.saveConflict) return
        _uiState.update { it.copy(saveStatus = null) }
        persist(draft = true, announce = false)
    }

    fun closeWarning() = change { it.copy(emptyWarning = false) }
    fun closeCompletionWarning() = change { it.copy(completionWarning = false) }
    fun fillEmpty() = change { it.fillEmpty() }
    fun consumeMessage() = change { it.copy(message = null) }

    private fun blockInvalidCompletion(state: SheetEditUiState): Boolean {
        val validation = state.completionValidation
        if (validation.canComplete) return false
        _uiState.update { it.focusCompletionIssue().copy(emptyWarning = validation.hasEmptyCells, completionWarning = !validation.hasEmptyCells) }
        return true
    }

    /** '음원 만들기'·'다시 만들기'·'다시 시도'. 저장된 서버 악보가 있을 때만 SHT-12를 부르고, 접수되면 끝날 때까지 상태를 묻는다. */
    fun requestAudio() {
        if (!showsAudioExport) return
        val current = _uiState.value
        if (!accountScope.isActive || current.documentLoading || current.documentFailed || current.saving || current.audioDownloading) return
        if (loaded != null && (current.draft.jangdanId != savedJangdanId || current.draft.bpm != confirmedDraft?.bpm ||
                current.draft.tempoExpressions != confirmedDraft?.tempoExpressions ||
                current.draft.tempoReferenceBpm != confirmedDraft?.tempoReferenceBpm)) {
            change { it.copy(message = SheetEditMessage.AudioNeedsSave) }
            return
        }
        val repository = sheetAudio
        if (repository == null) {
            change { it.copy(message = SheetEditMessage.ComingSoon) }
            return
        }
        if (current.savedOnce && current.audioInstruments != null && current.audioInstruments.selectedId == null) {
            change { it.copy(message = SheetEditMessage.AudioInstrumentRequired) }
            return
        }
        if (current.audioInstruments?.let { it.supportLoading || it.supportFailed || it.unsupported.isNotEmpty() } == true) {
            change { it.copy(message = SheetEditMessage.AudioUnsupportedPitches) }
            return
        }
        var started = false
        (_uiState.value.audio as? SheetAudioUi.Ready)?.audio?.audio
            ?.takeIf { it.expiresWithin(now(), 0) }?.let(::expireAudioDownload)
        val next = _uiState.updateAndGet { state ->
            val changed = state.requestAudio()
            started = changed.audio is SheetAudioUi.Requesting && state.audio !is SheetAudioUi.Requesting
            changed
        }
        val sheetId = next.serverSheetId
        if (!started || sheetId == null) return
        startAudioJob {
            if (pitchCatalog != null && next.audioInstruments?.selectedId != null) {
                val issues = try { pitchCatalog.authoringCapabilities(next.audioInstruments.selectedId).issues(requireNotNull(confirmedDraft).toCommonScore()) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failRequest(SheetEditMessage.AudioBusy); return@startAudioJob }
                if (issues.isNotEmpty()) {
                    change { it.copy(audioInstruments = it.audioInstruments?.copy(unsupported = issues)) }
                    failRequest(SheetEditMessage.AudioUnsupportedPitches)
                    return@startAudioJob
                }
            }
            val settings = SheetAudioSettings(instrumentId = next.audioInstruments?.selectedId, withJangdan = next.withJangdan, bpm = next.draft.bpm)
            when (val result = repository.request(sheetId, settings)) {
                is SheetAudioRequest.Accepted -> receive(sheetId, result.audio, announce = true)
                is SheetAudioRequest.UnsupportedPitches -> {
                    change { it.copy(audioInstruments = it.audioInstruments?.copy(unsupported = result.details.issues)) }
                    failRequest(SheetEditMessage.AudioUnsupportedPitches)
                }
                is SheetAudioRequest.TempoRejected -> {
                    failRequest(SheetEditMessage.AudioInvalid)
                    _uiState.update { it.copy(tempoIssue = result.issue) }
                }
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
        if (!showsAudioExport) return
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

    val canSaveAudioOnDevice: Boolean get() = deviceAudioStore?.available == true

    fun downloadableAudio(): SheetAudioFile? {
        val state = _uiState.value
        val audio = (state.audio as? SheetAudioUi.Ready)?.audio ?: return null
        if (!accountScope.isActive) return null
        if (audio.outdated || audio.scoreVersion != version || audio.currentScoreVersion != version) {
            change { it.copy(message = SheetEditMessage.SaveConflict) }
            return null
        }
        if (!state.matchesAudioSettings(audio.instrumentId, audio.bpm, audio.withJangdan)) {
            change { it.copy(message = SheetEditMessage.PreviewNeedsAudio) }
            return null
        }
        val file = audio.audio ?: return null
        if (!file.expiresWithin(now(), 0)) return file
        expireAudioDownload(file)
        change { it.copy(message = SheetEditMessage.AudioExpired) }
        return null
    }

    fun expireAudioDownload(file: SheetAudioFile) = change { state ->
        val ready = state.audio as? SheetAudioUi.Ready ?: return@change state
        if (ready.audio.audio != file) return@change state
        state.copy(audio = SheetAudioUi.Failed(ready.audio.copy(
            status = SheetAudioStatus.Failed, failureCode = SheetAudioFailure.Expired, audio = null,
        )))
    }

    fun saveAudioOnDevice() {
        val store = deviceAudioStore?.takeIf { it.available } ?: return
        val accountId = member?.id ?: return
        val state = _uiState.value
        val sheetId = state.serverSheetId ?: return
        val audio = (state.audio as? SheetAudioUi.Ready)?.audio ?: return
        val file = downloadableAudio() ?: return
        if (state.audioDownloading || state.deviceAudio?.matchesDownload(audio) == true) return
        if (file.contentType != "audio/mp4") return reportAudioLinkFailed()
        val hash = file.sha256 ?: return reportAudioLinkFailed()
        val duration = file.durationMs ?: return reportAudioLinkFailed()
        val scoreVersion = version
        val download = SheetAudioDownload(file.url, file.byteSize, hash, duration, audio.instrumentId, audio.bpm, audio.withJangdan,
            file.performance)
        change { it.copy(audioDownloading = true) }
        audioDownloadJob = accountScope.launch {
            try {
                val saved = store.save(accountId, sheetId, scoreVersion, download)
                ensureAccountSession()
                if (version == scoreVersion) {
                    if (saved == null) reportAudioLinkFailed()
                    else change { it.copy(deviceAudio = saved, message = SheetEditMessage.AudioDownloaded) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { reportAudioLinkFailed() }
            finally { if (version == scoreVersion) change { it.copy(audioDownloading = false) } }
        }
    }

    /** 확정 악보와 같은 버전·빠르기의 기기 음원만 미리 듣는다. */
    fun previewPlaybackId(): String? = previewDeviceAudio()?.playbackId

    private fun previewDeviceAudio(): DeviceSheetAudio? {
        val state = _uiState.value
        if (!accountScope.isActive || state.saving || state.draft != confirmedDraft) return null
        return if (showsAudioExport) state.playableDeviceAudio else state.deviceAudio?.takeIf { it.bpm == state.draft.bpm }
    }

    fun devicePlayback(preview: Boolean): AudioSource? {
        if (!accountScope.isActive) return null
        if (preview && _uiState.value.draft != confirmedDraft) {
            change { it.copy(message = SheetEditMessage.AudioNeedsSave) }
            return null
        }
        val file = if (preview) previewDeviceAudio() else _uiState.value.playableDeviceAudio
        if (file == null) {
            change { it.copy(message = SheetEditMessage.PreviewNeedsAudio) }
            return null
        }
        return AudioSource(file.playbackId, "", file.localFile)
    }

    fun reportAudioLinkFailed() = change { it.copy(message = SheetEditMessage.AudioLinkFailed) }

    private fun startAudioJob(block: suspend () -> Unit) {
        audioJob?.cancel()
        audioJob = accountScope.launch { block() }
    }

    /** 서버 상태를 반영하고 만드는 중이면 조회한다. [announce]면 접수·실패를 알리며 성공 안내는 앱에서 맡는다. */
    private suspend fun receive(sheetId: String, audio: SheetAudio, announce: Boolean) {
        change { state ->
            state.copy(
                audio = audio.forLoadedVersion().toUi(),
                message = when {
                    !announce -> state.message
                    audio.status.inProgress -> SheetEditMessage.AudioRequested
                    audio.status == SheetAudioStatus.Done -> state.message // App-wide completion owns success notices.
                    else -> SheetEditMessage.AudioFailed
                },
            )
        }
        if (audio.status.inProgress) watch(sheetId, audio)
    }

    /** `pollAfterMs`마다 SHT-13을 묻고 실패는 화면에 알린다. 연속 [MAX_POLL_MISSES]회 조회 실패 시 멈춘다. */
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
                    change { it.copy(audio = current.forLoadedVersion().toUi()) }
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
        if (current.status == SheetAudioStatus.Failed) change { it.copy(message = SheetEditMessage.AudioFailed) }
    }

    private fun SheetAudio.forLoadedVersion(): SheetAudio =
        if (version.isNotEmpty() && (scoreVersion != version || currentScoreVersion != version)) copy(outdated = true) else this

    /** SHT-12가 거절됐다. 요청 전 상태로 돌리고 이유를 알린다. */
    private fun failRequest(message: SheetEditMessage) = change { state ->
        val audio = state.audio
        state.copy(audio = if (audio is SheetAudioUi.Requesting) audio.previous?.toUi() ?: SheetAudioUi.None else audio, message = message)
    }

    private companion object {
        /** 서버가 `pollAfterMs`를 주지 않을 때 조회 간격. */
        const val DEFAULT_POLL_MS = 3_000L

        const val MAX_POLL_MISSES = 5

    }
}

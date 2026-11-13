package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.audio.DeviceSheetAudio

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.song.CatalogInstrument
import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import com.ssafy.eolssu.data.song.validCatalogId
import kotlin.math.log2
import kotlin.math.roundToInt

/** EDIT-02 아래 입력판의 탭. */
enum class EditTab { Adaptation, Yul, Sigimsae, Cell }

/** 편집기 보기: 정간보(EDIT-02) / 오선보로 보기(EDIT-03). 한쪽을 고치면 다른 쪽도 같은 악보를 보인다. */
enum class EditView { Jeongganbo, Staff }

sealed interface SheetEditJangdanPicker {
    data object Loading : SheetEditJangdanPicker
    data object Failed : SheetEditJangdanPicker
    data class Ready(val options: List<CatalogJangdanSummary>) : SheetEditJangdanPicker
}

data class SheetAudioInstruments(
    val options: List<CatalogInstrument> = emptyList(),
    val selectedId: String? = null,
    val loading: Boolean = true,
    val supportLoading: Boolean = false,
    val supportFailed: Boolean = false,
    val unsupported: List<com.ssafy.eolssu.data.song.AuthoringPitchIssue> = emptyList(),
)

enum class SheetCompletionCatalogState { NotRequired, Loading, Ready, Failed }

/** 화면 아래에 잠깐 띄우는 안내. */
enum class SheetSaveStatus { DraftSaved, Confirmed, DraftFailed, ConfirmationFailed, TooLarge }

enum class SheetEditMessage {
    SaveFailed, SaveConflict, SaveTooLarge, SaveUnsupportedPitches,
    /** 임시저장(SHT-11 초안). 빈 정간이 있어도 된다. */
    Draft,

    /** 저장(SHT-04). 빈 정간이 없을 때만 된다. */
    Saved,
    SavedForPractice,
    SavedNeedsReview,

    /** 저장한 적이 없어 음원을 만들 수 없다. */
    AudioNeedsSave,

    /** 음원 생성을 접수했다(SHT-12 202). 다 되면 알린다. */
    AudioRequested,

    /** 음원이 만들어졌다(DONE). 재생·저장할 수 있다. */
    AudioReady,

    /** 음원을 만들지 못했다(FAILED). 다시 시도할 수 있다. */
    AudioFailed,

    /** 연주 길이가 10분을 넘어 만들 수 없다(400 EXPORT_TOO_LONG). */
    AudioTooLong,

    /** 요청 설정이 맞지 않다(400 INVALID_REQUEST: 장단 없는 악보에 장단 소리, bpm 범위 밖). */
    AudioInvalid,
    AudioInstrumentRequired, AudioUnsupportedPitches,

    /** 이 회원의 다른 음원을 만드는 중이다(409 AUDIO_IN_PROGRESS). */
    AudioInProgress,

    /** 빈 칸·확인 필요 칸·변환 중이라 만들 수 없다(409 STATE_CONFLICT). */
    AudioNotReady,

    /** 악보가 없다(404). */
    AudioNotFound,

    /** 서버에 닿지 못했거나 잠시 막혔다(429·503·응답 없음). */
    AudioBusy,

    /** 만드는 중인 음원의 상태를 거듭 확인하지 못했다. */
    AudioStatusUnknown,

    /** 파일 주소를 기기 브라우저로 열지 못했다. */
    AudioLinkFailed,
    AudioDownloaded,
    AudioExpired,
    PreviewNeedsAudio,

    ReviewBeforeReshape,
    TempoPlanBeforeReshape,

    /** 서버 문서가 없는 경로 등에서 제공하지 않는 기능. */
    ComingSoon,
}

/**
 * 악보 편집(EDIT-02 · 03)과 미리보기·저장(EDIT-04)이 함께 쓰는 상태. 서버 문서의 로드·저장 결과는 ViewModel이 반영하며,
 * 음원은 서버에 저장된 내 악보([serverSheetId])가 있을 때만 SHT-12·13으로 만든다.
 *
 * @property selected 고른 정간 번호(연주 순서).
 * @property selectedSub 나눈 정간에서 고른 소박 순서(위→아래, 같은 줄은 왼→오른쪽). null이면 정간 전체를 고쳤다.
 * @property past 실행 취소로 돌아갈 악보(최근 [HISTORY_LIMIT]개). [future]는 다시 실행할 악보.
 * @property staffKey 오선보에서 고른 음표. null이면 첫 음표.
 * @property yulRegister 다음에 누를 율명에 붙일 음역 단계. 율명을 적으면 기본으로 돌아간다.
 * @property availablePitches 서버에 등록된 (율명, 옥타브). 내 악보 저장은 이 음만 받는다. null이면 목록을 받지 못했다.
 * @property emptyWarning 빈 정간이 있어 저장할 수 없다는 경고 창이 열려 있다.
 * @property savedOnce 저장한 악보가 있다. 음원은 마지막으로 저장한 악보로 만든다.
 * @property audio 내 악보 음원 칸의 상태(없음·요청 중·만드는 중·준비됨·실패·확인 못 함).
 */
data class SheetEditUiState(
    val source: EditSource,
    val draft: SheetDraft,
    val hwang: HwangPitch = HwangPitch.Default,
    val selected: Int = 0,
    val selectedSub: Int? = null,
    val tab: EditTab = EditTab.Yul,
    val view: EditView = EditView.Jeongganbo,
    val past: List<SheetDraft> = emptyList(),
    val future: List<SheetDraft> = emptyList(),
    val staffKey: StaffNoteKey? = null,
    val yulRegister: YulRegister = YulRegister.Base,
    val availablePitches: Set<Pair<String, Int>>? = null,
    val authoringCapabilities: com.ssafy.eolssu.data.song.CatalogAuthoringCapabilities? = null,
    val serverPitchIssues: List<com.ssafy.eolssu.data.song.AuthoringPitchIssue> = emptyList(),
    val instrumentPitches: Set<Pair<String, Int>>? = null,
    val completionCatalogState: SheetCompletionCatalogState = SheetCompletionCatalogState.NotRequired,
    val availableJangdans: List<CatalogJangdanSummary>? = null,
    val withJangdan: Boolean = true,
    val emptyWarning: Boolean = false,
    val completionWarning: Boolean = false,
    val savedOnce: Boolean = false,
    val audio: SheetAudioUi = SheetAudioUi.None,
    val audioInstruments: SheetAudioInstruments? = null,
    val deviceAudio: DeviceSheetAudio? = null,
    val audioDownloading: Boolean = false,
    val message: SheetEditMessage? = null,
    val tempoIssue: com.ssafy.eolssu.data.song.SheetTempoIssue? = null,
    val hasTempoPlan: Boolean = false,
    val documentLoading: Boolean = false,
    val documentFailed: Boolean = false,
    val practiceVersionChanged: Boolean = false,
    val saveStatus: SheetSaveStatus? = null,
    val saving: Boolean = false,
    val confirming: Boolean = false,
    val saveConflict: Boolean = false,
    val closeWarning: Boolean = false,
    val reviewIssues: List<SheetReviewIssue> = emptyList(),
    val reviewNotices: List<SheetReviewNotice> = emptyList(),
    val checkedIssueIds: Set<String> = emptySet(),
    val jangdanPicker: SheetEditJangdanPicker? = null,
    val adaptation: SheetAdaptationUi? = null,
) {
    val completionValidation: SheetCompletionValidation by lazy { validateCompletion() }
    internal val validatedCheckedIssueIds: Set<String>
        get() = (checkedIssueIds + completionValidation.resolvedPitchIssueIds)
            .intersect(completionValidation.checkableIssueIds)
    val canCheckNextReviewIssue: Boolean
        get() = !confirming && nextReviewIssue?.id in completionValidation.checkableIssueIds
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()
    val canIncludeJangdan: Boolean get() = draft.original == null || serverSheetId == null || draft.jangdanId != null
    val canEditDetails: Boolean get() = draft.original != null && serverSheetId != null

    internal fun updateDetails(title: String, bpm: Int): SheetEditUiState {
        if (!canEditDetails || bpm !in SheetBpmRange) return this
        val normalizedTitle = normalizedSheetTitle(title) ?: return this
        return commit(draft.copy(title = normalizedTitle, bpm = bpm)).copy(tempoIssue = null)
    }

    internal fun updateTempoSettings(next: SheetDraft): SheetEditUiState {
        if (!canEditDetails || next.bpm !in SheetBpmRange) return this
        return commit(draft.copy(bpm = next.bpm, tempoExpressions = next.tempoExpressions,
            tempoReferenceBpm = next.tempoReferenceBpm)).copy(tempoIssue = null)
    }

    /** 편집 설정의 빠르기·장단을 한 번에 적용한다. 제목은 저장 창에서만 바꾼다. */
    internal fun updateSettings(bpm: Int, jangdanId: String?): SheetEditUiState {
        if (!canEditDetails || bpm !in SheetBpmRange) return this
        val next = when {
            jangdanId == null -> draft.copy(bpm = bpm, jangdanId = null, editedJangdanName = "")
            jangdanId == draft.jangdanId -> draft.copy(bpm = bpm)
            else -> {
                val options = (jangdanPicker as? SheetEditJangdanPicker.Ready)?.options ?: availableJangdans.orEmpty()
                val option = options.firstOrNull { it.jangdanId == jangdanId } ?: return this
                draft.withJangdan(option, preserveTiming = hasTempoPlan)?.copy(bpm = bpm) ?: return this
            }
        }
        return commit(next).copy(jangdanPicker = null, withJangdan = if (jangdanId == null) false else withJangdanFor(next))
    }

    fun selectJangdan(id: String): SheetEditUiState {
        val options = (jangdanPicker as? SheetEditJangdanPicker.Ready)?.options ?: return this
        val option = options.firstOrNull { it.jangdanId == id && draft.canSelectJangdan(it, hasTempoPlan) } ?: return this
        if (option.jangdanId == draft.jangdanId && option.name == draft.jangdanName) return copy(jangdanPicker = null)
        val next = draft.withJangdan(option, preserveTiming = hasTempoPlan) ?: return this
        return commit(next).copy(jangdanPicker = null)
    }

    /** 사용자가 장단 연결을 해제한다. 정간·이벤트·tick·BPM은 그대로 두며 실행 취소할 수 있다. */
    fun clearJangdan(): SheetEditUiState =
        commit(draft.copy(jangdanId = null, editedJangdanName = "")).copy(withJangdan = false, jangdanPicker = null)

    private fun withJangdanFor(next: SheetDraft): Boolean =
        if (next.jangdanId == draft.jangdanId) withJangdan else next.jangdanId != null

    internal fun matchesAudioSettings(instrumentId: String, bpm: Int, withJangdan: Boolean): Boolean =
        bpm == draft.bpm && withJangdan == this.withJangdan &&
            (audioInstruments == null || audioInstruments.selectedId == instrumentId)

    val audioSettingsChanged: Boolean
        get() = (audio as? SheetAudioUi.Ready)?.audio?.let {
            !matchesAudioSettings(it.instrumentId, it.bpm, it.withJangdan)
        } == true

    internal val playableDeviceAudio: DeviceSheetAudio?
        get() = deviceAudio?.takeIf { matchesAudioSettings(it.instrumentId, it.bpm, it.withJangdan) }

    val pendingChecks: Int
        get() = (source as? EditSource.Import)?.pendingChecks
            ?: reviewIssues.filterNot { it.id in validatedCheckedIssueIds }.distinctBy(SheetReviewIssue::cellId).size

    val nextReviewIssue: SheetReviewIssue?
        get() = reviewIssues.firstOrNull { it.id !in validatedCheckedIssueIds }

    val nextReviewCellIndex: Int?
        get() = nextReviewIssue?.let { issue -> draft.sourceCellIds?.indexOf(issue.cellId)?.takeIf { it >= 0 } }

    val selectedReviewIssue: SheetReviewIssue?
        get() = draft.sourceCellIds?.getOrNull(selected)?.let { cellId ->
            reviewIssues.firstOrNull { it.cellId == cellId && it.id !in validatedCheckedIssueIds }
        }

    val canCheckSelectedReviewIssue: Boolean
        get() = !confirming && selectedReviewIssue?.id in completionValidation.checkableIssueIds

    internal fun applyAdaptation(plan: PitchAdaptationPlan, capability: com.ssafy.eolssu.data.song.CatalogAuthoringCapabilities): SheetEditUiState =
        commit(draft.applyAdaptation(plan, capability)).copy(
            adaptation = null, tab = EditTab.Adaptation, instrumentPitches = capability.selectablePitches,
        )

    /** 악보를 바꾼다. 바뀌지 않았으면 기록을 남기지 않는다. 고른 소박은 그 정간이 여전히 나뉘어 있을 때만 남긴다. */
    private fun commit(next: SheetDraft, selected: Int = this.selected, sub: Int? = if (selected == this.selected) selectedSub else null): SheetEditUiState {
        val index = selected.coerceIn(0, next.cells.lastIndex)
        val keptSub = sub?.takeIf { next.cells[index].size > 1 && it in next.cells[index].indices }
        if (next == draft) return copy(selected = index, selectedSub = keptSub)
        return copy(
            draft = next,
            hasTempoPlan = nextTempoPlan(next),
            past = (past + draft).takeLast(HISTORY_LIMIT),
            future = emptyList(),
            selected = index,
            selectedSub = keptSub,
            withJangdan = withJangdanFor(next),
            checkedIssueIds = checkedIssuesAfter(next),
        )
    }

    private fun nextTempoPlan(next: SheetDraft): Boolean =
        if (draft.tempoExpressions != null || next.tempoExpressions != null) next.tempoExpressions?.containsKey("performancePlan") == true
        else hasTempoPlan

    /** 한 번 확인한 칸도 내용이나 시김새를 바꾸거나 실행 취소하면 다시 확인한다. */
    private fun checkedIssuesAfter(next: SheetDraft): Set<String> {
        if (checkedIssueIds.isEmpty()) return emptySet()
        val beforePitches = draft.reviewSoundingPitches() ?: return emptySet()
        val afterPitches = next.reviewSoundingPitches() ?: return emptySet()
        return checkedIssueIds.filterTo(mutableSetOf()) { id ->
            val issue = reviewIssues.firstOrNull { it.id == id } ?: return@filterTo false
            val before = draft.sourceCellIds?.indexOf(issue.cellId)?.takeIf { it >= 0 } ?: return@filterTo false
            val after = next.sourceCellIds?.indexOf(issue.cellId)?.takeIf { it >= 0 } ?: return@filterTo false
            draft.cells[before] == next.cells[after] && beforePitches[before] == afterPitches[after] &&
                draft.sigimsae.filterKeys { it.cell == before }.mapKeys { it.key.sub } ==
                next.sigimsae.filterKeys { it.cell == after }.mapKeys { it.key.sub }
        }
    }

    fun select(index: Int): SheetEditUiState = copy(selected = index.coerceIn(0, draft.cells.lastIndex), selectedSub = null)

    /** 악보에서 소박 하나를 눌렀다. 나눈 정간이면 그 소박만 고르고, 아니면 정간 전체를 고른다. */
    fun selectUnit(index: Int, sub: Int): SheetEditUiState {
        val cell = index.coerceIn(0, draft.cells.lastIndex)
        val notes = draft.cells[cell]
        return copy(selected = cell, selectedSub = sub.takeIf { notes.size > 1 && it in notes.indices })
    }

    /**
     * 자판의 율명·기호를 적는다. 소박을 골랐으면 그 소박만 바꾸고 같은 정간의 다음 소박으로(마지막이면 다음 정간으로) 간다.
     * 아니면 정간에 적고, 정간이 다 차면 다음 정간으로 넘어간다.
     */
    fun put(note: ScoreNote): SheetEditUiState {
        val sub = selectedSub
        if (sub != null && draft.cells[selected].size > 1) {
            val next = draft.putAt(selected, sub, note)
            return if (sub + 1 < next.cells[selected].size) commit(next, selected, sub + 1)
            else commit(next, (selected + 1).coerceAtMost(next.cells.lastIndex), null)
        }
        val (next, advance) = draft.put(selected, note)
        return commit(next, if (advance) (selected + 1).coerceAtMost(next.cells.lastIndex) else selected, null)
    }

    /** 다섯 음역 중 하나를 직접 고른다. 다시 눌러도 같은 음역을 유지하며 악보는 바꾸지 않는다. */
    fun selectRegister(register: YulRegister): SheetEditUiState = copy(yulRegister = register)

    /** 지금 음역 단계를 붙여 [base]를 적는다. 서버에 등록되지 않은 음은 적지 않는다. 적고 나면 음역은 기본으로 돌아간다. */
    fun putYul(base: Yul): SheetEditUiState = putYul(base, yulRegister.octave)

    /** [base]를 [octave] 음역으로 적는다. 같은 음 데이터([SheetDraft.pitchNote])로 적는다. */
    fun putYul(base: Yul, octave: Int): SheetEditUiState {
        if (!isAvailablePitch(supportedInputPitches, base, octave)) return this
        return put(draft.pitchNote(base, octave)).copy(yulRegister = YulRegister.Base)
    }

    /** 후보를 보는 동안 입력이 바뀌었으면 적용하지 않는다. 시작 음만 교체해 지속·분할·원본 이벤트를 보존한다. */
    internal fun applyPitchCorrection(target: SheetPitchCorrection, pitch: Pair<Yul, Int>): SheetEditUiState {
        if (confirming || target !in pitchCorrectionsAt(target.cell, target.sub) || pitch !in pitchAlternatives(target.pitch)) return this
        val next = draft.correctPitch(target, pitch)
        return commit(next, target.originCell, target.originSub)
    }

    /** 고른 소박만, 소박을 고르지 않았으면 정간 전체를 지운다. */
    fun erase(): SheetEditUiState = selectedSub?.let { commit(draft.eraseAt(selected, it)) } ?: commit(draft.erase(selected), sub = null)

    /** 시김새를 붙일 소박. 고른 소박이 있으면 그 소박, 아니면 정간의 첫 율명 소박이다. */
    val sigimsaeSub: Int get() = draft.sigimsaeSub(selected, selectedSub)

    /** 고른 소박에 시김새를 붙이거나(끝에 추가) 이미 있으면 뗀다. */
    fun toggleSigimsae(value: Sigimsae): SheetEditUiState = commit(draft.toggleSigimsae(selected, value, sigimsaeSub))

    fun removeSigimsae(position: Int): SheetEditUiState = commit(draft.removeSigimsae(selected, position, sigimsaeSub))


    fun split(): SheetEditUiState = if (hasTempoPlan) copy(message = SheetEditMessage.TempoPlanBeforeReshape)
        else commit(draft.split(selected), sub = null)

    fun merge(): SheetEditUiState = if (hasTempoPlan) copy(message = SheetEditMessage.TempoPlanBeforeReshape)
        else commit(draft.merge(selected), sub = null)

    /** 고른 정간의 각 왼쪽에 빈 각을 넣고 새 각의 첫 정간을 고른다. */
    fun addColumn(): SheetEditUiState {
        if (hasTempoPlan) return copy(message = SheetEditMessage.TempoPlanBeforeReshape)
        val column = draft.columnOf(selected)
        val next = draft.addColumnAfter(column)
        return commit(next, next.indexOf(column + 1, 0))
    }

    /** 고른 정간의 각을 지운다. 선택은 같은 자리(마지막 각이었으면 새 마지막 각)로 둔다. */
    fun deleteColumn(): SheetEditUiState {
        if (hasTempoPlan) return copy(message = SheetEditMessage.TempoPlanBeforeReshape)
        val column = draft.columnOf(selected)
        val next = draft.deleteColumn(column)
        if (next == draft) return this
        return commit(next, next.indexOf(minOf(column, next.columnCount - 1), 0))
    }

    fun undo(): SheetEditUiState {
        val previous = past.lastOrNull() ?: return this
        return copy(draft = previous, hasTempoPlan = nextTempoPlan(previous), past = past.dropLast(1), future = listOf(draft) + future,
            selected = selected.coerceIn(0, previous.cells.lastIndex), selectedSub = null, withJangdan = withJangdanFor(previous),
            checkedIssueIds = checkedIssuesAfter(previous))
    }

    fun redo(): SheetEditUiState {
        val next = future.firstOrNull() ?: return this
        return copy(draft = next, hasTempoPlan = nextTempoPlan(next), past = past + draft, future = future.drop(1),
            selected = selected.coerceIn(0, next.cells.lastIndex), selectedSub = null, withJangdan = withJangdanFor(next),
            checkedIssueIds = checkedIssuesAfter(next))
    }

    /** 오선보에서 고른 음표. */
    fun staffNote(notes: List<StaffNote> = staffNotes(draft)): StaffNote? = notes.pick(staffKey)

    /** 오선보에서 음표를 고르면 정간보의 선택도 그 음표가 시작하는 정간으로 옮긴다. */
    fun selectStaffNote(key: StaffNoteKey): SheetEditUiState =
        if (draft.original != null) selectUnit(key.start, key.sub).copy(staffKey = key)
        else copy(staffKey = key, selected = key.start, selectedSub = null)

    internal fun canShiftCommonStaffPitch(direction: Int): Boolean = commonStaffPitchTarget(direction) != null

    private fun commonStaffPitchTarget(direction: Int): ScoreNote? {
        if (direction != -1 && direction != 1) return null
        val key = staffKey ?: return null
        val (base, octave) = draft.cells.getOrNull(key.start)?.getOrNull(key.sub)?.pitchKey() ?: return null
        val hwang = draft.original?.music?.hwangHz?.let(::staffHwangMidi) ?: return null
        val count = BaseYuls.size
        var target = (octave + 2) * count + base.ordinal + direction
        while (target in 0 until count * 5 && hwang + target - 2 * count in 0..127) {
            val pitch = BaseYuls[target % count]
            val register = target / count - 2
            if (isAvailablePitch(supportedInputPitches, pitch, register)) return draft.pitchNote(pitch, register)
            target += direction
        }
        return null
    }

    /** 오선보에서 고른 음표의 높이를 [steps]만큼 바꾼다. 쉼표는 바꾸지 않는다. */
    fun shiftStaffNote(steps: Int): SheetEditUiState {
        if (draft.original != null) {
            val key = staffKey ?: return this
            val target = commonStaffPitchTarget(steps) ?: return this
            return commit(draft.putAt(key.start, key.sub, target), key.start, key.sub)
        }
        val note = staffNote() ?: return this
        if (note.isRest) return this
        return commit(draft.shiftPitch(note.key.start, note.key.sub, steps), note.key.start).copy(staffKey = note.key)
    }

    /** '저장': 빈 정간이 있으면 경고 창을 열고, 없으면 저장했다고 알린다. */
    fun requestSave(): SheetEditUiState = when {
        completionValidation.hasEmptyCells -> focusCompletionIssue().copy(emptyWarning = true)
        !completionValidation.canComplete -> focusCompletionIssue().copy(completionWarning = true)
        else -> copy(emptyWarning = false, completionWarning = false, savedOnce = true, message = SheetEditMessage.Saved)
    }

    internal fun focusCompletionIssue(): SheetEditUiState = completionValidation.firstCellIndex?.let {
        select(it).copy(view = EditView.Jeongganbo)
    } ?: this

    /** '임시저장': 빈 정간이 있어도 된다. 경고 창에서 눌러도 같다. */
    fun saveDraft(): SheetEditUiState = copy(emptyWarning = false, completionWarning = false, message = SheetEditMessage.Draft)

    /** 서버에 저장된 내 악보 ID. 예시 악보나 아직 서버에 저장하지 않은 악보는 null이라 음원을 만들 수 없다. */
    val serverSheetId: String? get() = (source as? EditSource.Mine)?.sheetId?.takeIf { validCatalogId(it) }

    /**
     * '음원 만들기'·'다시 만들기'·'다시 시도': 마지막으로 저장한 악보로 만든다(SHT-12). 저장한 적이 없으면 먼저 저장하라고 알리고,
     * 서버 악보가 없으면(저장 API 연결 전) 준비 중 안내를 띄운다. 요청·생성 중이거나 현재 버전의 음원이 이미 있으면 그대로 둔다.
     */
    fun requestAudio(): SheetEditUiState = when {
        !savedOnce -> copy(message = SheetEditMessage.AudioNeedsSave)
        serverSheetId == null -> copy(message = SheetEditMessage.ComingSoon)
        !audio.canRequest && !audioSettingsChanged -> this
        else -> copy(audio = SheetAudioUi.Requesting(audio.latest))
    }

    /** 경고 창의 '빈 정간 채우기': 첫 빈 정간을 고르고 정간보 율명 입력으로 돌아간다. */
    fun fillEmpty(): SheetEditUiState {
        val first = draft.unfilledCells.firstOrNull() ?: selected
        return copy(emptyWarning = false, selected = first, selectedSub = null, view = EditView.Jeongganbo, tab = EditTab.Yul)
    }

    companion object {
        const val HISTORY_LIMIT = 30

        /** 들어온 경로에 맞는 처음 상태. 인식 결과에서 오면 와이어프레임처럼 5각 5정간을, 아니면 첫 정간을 고른다. */
        fun initial(source: EditSource, draft: SheetDraft, startView: EditView = EditView.Jeongganbo): SheetEditUiState {
            val fromImport = source is EditSource.Import
            val selected = if (fromImport) SheetEditSamples.IMPORT_FIRST_SELECTED.coerceAtMost(draft.cells.lastIndex) else 0
            return SheetEditUiState(
                source = source,
                draft = draft,
                hwang = (source as? EditSource.Import)?.hwang ?: HwangPitch.Default,
                selected = selected,
                view = startView,
                savedOnce = source is EditSource.Mine,
            )
        }
    }
}

/** 이음 자리와 악기 기준음 변경도 검토한 실제 음고에 반영한다. */
private fun SheetDraft.reviewSoundingPitches(): List<List<Int?>>? = runCatching {
    val score = toCommonScore()
    val hwangMidi = (69 + 12 * log2(score.music.hwangHz / 440.0)).roundToInt()
    val events = score.events.associateBy { it.id }
    score.columns.flatMap { it.cells }.map { cell ->
        cell.units.map { unit ->
            events[unit.eventId]?.takeIf { it.kind == "NOTE" }?.let { note ->
                hwangMidi + "황대태협고중유임이남무응".indexOf(requireNotNull(note.yul)) + 12 * requireNotNull(note.octave)
            }
        }
    }
}.getOrNull()

internal val SheetBpmRange = 1..Int.MAX_VALUE

/** SHT-04·11 count Unicode code points after trimming, including a surrogate pair as one. */
internal fun normalizedSheetTitle(value: String): String? {
    val title = value.trim()
    val length = title.indices.count { index ->
        index == 0 || !title[index].isLowSurrogate() || !title[index - 1].isHighSurrogate()
    }
    return title.takeIf { length in 1..50 }
}

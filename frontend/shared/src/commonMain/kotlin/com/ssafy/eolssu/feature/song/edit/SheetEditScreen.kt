package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.core.ui.score.ScoreNoteGlyph
import com.ssafy.eolssu.feature.song.edit.component.CompactEditScoreMetrics
import com.ssafy.eolssu.feature.song.edit.component.EditInputActions
import com.ssafy.eolssu.feature.song.edit.component.EditInputPanel
import com.ssafy.eolssu.feature.song.edit.component.EditScoreBoard
import com.ssafy.eolssu.feature.song.edit.component.EditScoreLook
import com.ssafy.eolssu.feature.song.edit.component.editorUnitMarks
import com.ssafy.eolssu.feature.song.edit.component.EditorTopBar
import com.ssafy.eolssu.feature.song.edit.component.HintText
import com.ssafy.eolssu.feature.song.edit.component.PillButton
import com.ssafy.eolssu.feature.song.edit.component.SegmentTabs
import com.ssafy.eolssu.feature.song.edit.component.SheetEditIcons
import com.ssafy.eolssu.feature.song.edit.component.SquareIconButton
import com.ssafy.eolssu.feature.song.edit.component.StaffSystemView
import com.ssafy.eolssu.feature.song.edit.component.editorMarks
import com.ssafy.eolssu.feature.song.edit.component.staffMarks
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.score_rest
import eolssu.shared.generated.resources.sheetedit_banner_import
import eolssu.shared.generated.resources.sheetedit_banner_open
import eolssu.shared.generated.resources.sheet_review_checked
import eolssu.shared.generated.resources.sheet_review_location
import eolssu.shared.generated.resources.sheet_review_missing_pitch
import eolssu.shared.generated.resources.sheet_review_pitch_label
import eolssu.shared.generated.resources.sheet_review_range
import eolssu.shared.generated.resources.sheet_review_removed_cell
import eolssu.shared.generated.resources.sheet_review_save_checks
import eolssu.shared.generated.resources.sheet_review_uncertain
import eolssu.shared.generated.resources.sheetedit_close
import eolssu.shared.generated.resources.sheet_details_edit
import eolssu.shared.generated.resources.sheetedit_down
import eolssu.shared.generated.resources.sheetedit_duration_dotted_eighth
import eolssu.shared.generated.resources.sheetedit_duration_dotted_half
import eolssu.shared.generated.resources.sheetedit_duration_dotted_quarter
import eolssu.shared.generated.resources.sheetedit_duration_eighth
import eolssu.shared.generated.resources.sheetedit_duration_half
import eolssu.shared.generated.resources.sheetedit_duration_other
import eolssu.shared.generated.resources.sheetedit_duration_quarter
import eolssu.shared.generated.resources.sheetedit_duration_sixteenth
import eolssu.shared.generated.resources.sheetedit_hint_jeongganbo
import eolssu.shared.generated.resources.sheetedit_preview
import eolssu.shared.generated.resources.sheetedit_redo
import eolssu.shared.generated.resources.sheetedit_staff_back
import eolssu.shared.generated.resources.sheetedit_staff_hint
import eolssu.shared.generated.resources.sheetedit_staff_note_label
import eolssu.shared.generated.resources.sheetedit_staff_same_place
import eolssu.shared.generated.resources.sheetedit_staff_same_place_hint
import eolssu.shared.generated.resources.sheetedit_staff_system
import eolssu.shared.generated.resources.sheetedit_staff_title
import eolssu.shared.generated.resources.sheetedit_staff_where_span
import eolssu.shared.generated.resources.sheetedit_staff_where_sub
import eolssu.shared.generated.resources.sheetedit_subtitle_editing
import eolssu.shared.generated.resources.sheetedit_subtitle_staff
import eolssu.shared.generated.resources.sheetedit_undo
import eolssu.shared.generated.resources.sheetedit_up
import eolssu.shared.generated.resources.sheetedit_view_jeongganbo
import eolssu.shared.generated.resources.sheetedit_view_staff
import eolssu.shared.generated.resources.sheetedit_where_short
import org.jetbrains.compose.resources.stringResource

/** 편집기(EDIT-02 · 03)의 사용자 행동. */
class SheetEditActions(
    val onClose: () -> Unit,
    val onPreview: () -> Unit,
    val onOpenDetails: () -> Unit,
    val onOpenChecks: () -> Unit,
    val onSelectView: (EditView) -> Unit,
    val onSelectCell: (Int) -> Unit,
    /** 악보에서 누른 정간과 그 안의 소박 순서. */
    val onSelectUnit: (Int, Int) -> Unit,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    val onSelectStaffNote: (StaffNoteKey) -> Unit,
    val onShiftStaffNote: (Int) -> Unit,
    val input: EditInputActions,
    val onOpenReviewCell: () -> Unit = {},
    val onCheckReviewIssue: () -> Unit = {},
)

/**
 * EDIT-02 정간보 편집 / EDIT-03 오선보로 보기. 위쪽 탭으로 두 보기를 오가며 같은 악보를 고친다.
 * 정간보는 연습 화면과 같은 렌더러로 그리고, 고른 정간은 연두 바탕·먹색 테두리, 빈 정간은 점선이다. 곁줄(가사 칸)은 칠하지 않는다.
 * 넓은 창에서는 [EolssuScaffold]가 휴대폰 너비로 가운데에 둔다.
 */
@Composable
fun SheetEditScreen(state: SheetEditUiState, snackbarHostState: SnackbarHostState, actions: SheetEditActions) {
    val draft = state.draft
    val staff = state.view == EditView.Staff
    EolssuScaffold(
        containerColor = EolssuTheme.colors.surface,
        snackbarHostState = snackbarHostState,
        topBar = {
            EditorTopBar(
                title = draft.title,
                subtitle = if (staff) {
                    stringResource(Res.string.sheetedit_subtitle_staff, state.hwang.label)
                } else {
                    stringResource(Res.string.sheetedit_subtitle_editing, jangdanLabel(draft.jangdanName), draft.bpm)
                },
                closeLabel = stringResource(Res.string.sheetedit_close),
                onClose = actions.onClose,
            ) {
                PillButton(stringResource(Res.string.sheetedit_preview), actions.onPreview)
            }
        },
        bottomBar = if (staff) {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EolssuButton(
                        stringResource(Res.string.sheetedit_staff_back),
                        { actions.onSelectView(EditView.Jeongganbo) },
                        Modifier.weight(1f),
                        style = EolssuButtonStyle.Secondary,
                        compact = true,
                    )
                    EolssuButton(stringResource(Res.string.sheetedit_preview), actions.onPreview, Modifier.weight(1f), compact = true)
                }
            }
        } else {
            null
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (staff) {
                    HintText(stringResource(Res.string.sheetedit_staff_hint), Modifier.weight(1f).padding(start = 4.dp), size = 12)
                } else {
                    SquareIconButton(SheetEditIcons.Undo, stringResource(Res.string.sheetedit_undo), actions.onUndo, enabled = state.canUndo)
                    SquareIconButton(SheetEditIcons.Redo, stringResource(Res.string.sheetedit_redo), actions.onRedo, enabled = state.canRedo)
                }
                if (state.canEditDetails) SquareIconButton(EolssuIcons.Pencil, stringResource(Res.string.sheet_details_edit), actions.onOpenDetails)
                if (!staff) Box(Modifier.weight(1f))
                if (state.draft.original == null) SegmentTabs(
                    labels = listOf(stringResource(Res.string.sheetedit_view_jeongganbo), stringResource(Res.string.sheetedit_view_staff)),
                    selected = state.view.ordinal,
                    onSelect = { actions.onSelectView(EditView.entries[it]) },
                )
            }
            if (staff) {
                StaffContent(state, actions, Modifier.weight(1f))
            } else {
                JeongganboContent(state, actions, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun JeongganboContent(state: SheetEditUiState, actions: SheetEditActions, modifier: Modifier = Modifier) {
    val draft = state.draft
    val song = remember(draft) { draft.toSong() }
    Column(modifier) {
        if (state.reviewIssues.isNotEmpty()) ReviewBanner(state, actions)
        else if (state.pendingChecks > 0) ImportBanner(state.pendingChecks, actions.onOpenChecks)
        Column(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HintText(stringResource(Res.string.sheetedit_hint_jeongganbo))
            EditScoreBoard(
                song = song,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                marks = editorMarks(draft, state.selected, state.selectedSub),
                unitMarks = editorUnitMarks(draft, state.selected, state.selectedSub),
                annotations = remember(draft) { draft.annotations() },
                focusColumn = draft.columnOf(state.selected),
                onUnitClick = { column, row, unitRow, unitCol ->
                    val index = draft.indexOf(column, row)
                    actions.onSelectUnit(index, draft.subIndexOf(index, unitRow, unitCol))
                },
            )
        }
        EditInputPanel(draft, state.selected, state.selectedSub, state.tab, state.yulRegister, state.availablePitches, actions.input)
    }
}

@Composable
private fun ReviewBanner(state: SheetEditUiState, actions: SheetEditActions) {
    val issue = state.nextReviewIssue
    val cellIndex = state.nextReviewCellIndex
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            .background(EolssuTheme.colors.accentTint, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (issue == null) {
            HintText(stringResource(Res.string.sheet_review_save_checks))
        } else {
            HintText(stringResource(Res.string.sheetedit_banner_import, state.pendingChecks))
            val reason = when (issue.kind) {
                "UNREGISTERED_PITCH" -> issue.pitch?.let { (name, octave) ->
                    stringResource(Res.string.sheet_review_pitch_label, name, octave)
                } ?: stringResource(Res.string.sheet_review_missing_pitch)
                "OUT_OF_RANGE" -> stringResource(Res.string.sheet_review_range)
                else -> stringResource(Res.string.sheet_review_uncertain)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (cellIndex == null) stringResource(Res.string.sheet_review_removed_cell, reason)
                    else stringResource(Res.string.sheet_review_location, state.draft.columnOf(cellIndex) + 1, state.draft.rowOf(cellIndex) + 1, reason),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (cellIndex != null && cellIndex != state.selected) {
                    PillButton(stringResource(Res.string.sheetedit_banner_open), actions.onOpenReviewCell)
                } else {
                    PillButton(stringResource(Res.string.sheet_review_checked), actions.onCheckReviewIssue)
                }
            }
        }
    }
}

/** 인식 결과에서 온 편집: '불러온 악보 · 인식을 확인할 정간 n개가 남았어요'와 '확인 정간 보기'. */
@Composable
private fun ImportBanner(pending: Int, onOpen: () -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            .background(colors.accentTint, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val text = stringResource(Res.string.sheetedit_banner_import, pending)
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
        Surface(
            onClick = onOpen,
            modifier = Modifier.heightIn(min = 40.dp),
            shape = CircleShape,
            color = colors.surface,
            contentColor = colors.text,
            border = BorderStroke(1.dp, colors.accentTintStrong),
        ) {
            Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.sheetedit_banner_open), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

/**
 * EDIT-03 오선보로 보기: 정간보 두 각씩 한 줄로 옮긴 오선보 두 줄(고른 음표가 든 네 각), 고른 음표 정보와 음 높이 올리기·내리기,
 * 같은 위치의 정간보. 같은 위치의 정간보는 곡 전체를 가로로 넘겨 볼 수 있어, 다른 각을 누르면 오선보도 그 각으로 넘어간다.
 * 황 = [SheetEditUiState.hwang] 기준이고 플랫으로 적는다.
 */
@Composable
private fun StaffContent(state: SheetEditUiState, actions: SheetEditActions, modifier: Modifier = Modifier) {
    val draft = state.draft
    val notes = remember(draft) { staffNotes(draft) }
    val selected = state.staffNote(notes)
    val song = remember(draft) { draft.toSong() }
    val cellsPerSystem = draft.gak * 2
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val systems = notes.groupBy { it.system }
        val systemCount = (draft.cells.size + cellsPerSystem - 1) / cellsPerSystem
        for (system in visibleStaffSystems(selected?.key?.start ?: state.selected, cellsPerSystem, systemCount)) {
            val first = system * cellsPerSystem
            val firstColumn = draft.columnOf(first) + 1
            val lastColumn = minOf(firstColumn + 1, draft.columnCount)
            val systemNotes = systems[system].orEmpty()
            StaffSystemView(
                label = stringResource(Res.string.sheetedit_staff_system, firstColumn, lastColumn),
                firstCell = first,
                notes = systemNotes,
                firstOrder = notes.indexOf(systemNotes.firstOrNull()) + 1,
                gak = draft.gak,
                hwang = state.hwang,
                selected = selected?.key,
                noteLabel = { note, order -> staffNoteLabel(note, order, state.hwang) },
                onPick = actions.onSelectStaffNote,
            )
        }
        if (selected != null) SelectedNoteCard(draft, selected, state.hwang, actions.onShiftStaffNote, Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.sheetedit_staff_same_place),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            )
            HintText(stringResource(Res.string.sheetedit_staff_same_place_hint))
        }
        EditScoreBoard(
            song = song,
            modifier = Modifier.fillMaxWidth(),
            marks = staffMarks(draft, selected),
            annotations = remember(draft) { draft.annotations() },
            metrics = CompactEditScoreMetrics,
            look = JeongganboLook(style = JeongganboLook.Style.Board, lyricLane = 19.dp, headCompact = true, daegangWhenFits = true, sigimsaeScale = EditScoreLook.sigimsaeScale),
            focusColumn = selected?.let { draft.columnOf(it.key.start) },
            scrollVertically = false,
            onCellClick = { column, row -> actions.onSelectStaffNote(StaffNoteKey(draft.indexOf(column, row), 0)) },
        )
    }
}

/** 고른 음표: 44dp 율명 칸 · 위치 · '중려 = 라♭(A♭4) · 점4분음표 (3정간)' · 음 높이 올리기·내리기. */
@Composable
private fun SelectedNoteCard(draft: SheetDraft, note: StaffNote, hwang: HwangPitch, onShift: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(14.dp))
            .border(1.dp, colors.lineSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(colors.accentTint, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            ScoreNoteGlyph(note.yul?.let { ScoreNote.Pitch(it) } ?: ScoreNote.Rest, size = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            val where = stringResource(Res.string.sheetedit_where_short, draft.columnOf(note.key.start) + 1, draft.rowOf(note.key.start) + 1)
            Text(
                if (note.subCount > 1) {
                    stringResource(Res.string.sheetedit_staff_where_sub, where, note.key.sub + 1)
                } else {
                    stringResource(Res.string.sheetedit_staff_where_span, where, note.span)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
            )
            val yul = note.yul
            Text(
                if (yul == null) {
                    stringResource(Res.string.score_rest)
                } else {
                    stringResource(Res.string.sheetedit_staff_title, yul.fullName, staffPitch(yul, hwang).solfegeName, durationLabel(note.length))
                },
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
        Row {
            SquareIconButton(SheetEditIcons.ChevronUp, stringResource(Res.string.sheetedit_up), { onShift(1) }, enabled = !note.isRest, size = 36.dp)
            SquareIconButton(EolssuIcons.ChevronDown, stringResource(Res.string.sheetedit_down), { onShift(-1) }, enabled = !note.isRest, size = 36.dp)
        }
    }
}

/** '16분음표 (반 정간)'처럼 음표 이름과 정간 길이. */
@Composable
private fun durationLabel(length: Double): String = when (staffDuration(length)) {
    StaffDuration.Sixteenth -> stringResource(Res.string.sheetedit_duration_sixteenth)
    StaffDuration.Eighth -> stringResource(Res.string.sheetedit_duration_eighth)
    StaffDuration.DottedEighth -> stringResource(Res.string.sheetedit_duration_dotted_eighth)
    StaffDuration.Quarter -> stringResource(Res.string.sheetedit_duration_quarter)
    StaffDuration.DottedQuarter -> stringResource(Res.string.sheetedit_duration_dotted_quarter)
    StaffDuration.Half -> stringResource(Res.string.sheetedit_duration_half)
    StaffDuration.DottedHalf -> stringResource(Res.string.sheetedit_duration_dotted_half)
    StaffDuration.Other -> stringResource(Res.string.sheetedit_duration_other, formatCells(length))
}

/** 정간 수를 소수 한 자리까지(2 · 2.5). */
private fun formatCells(length: Double): String {
    val tenths = kotlin.math.round(length * 10).toLong()
    return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

/** 오선보 음표의 읽기 이름: '3번째 태주 파(F4)'. 쉼표는 '3번째 쉼표'. */
@Composable
private fun staffNoteLabel(note: StaffNote, order: Int, hwang: HwangPitch): String {
    val yul = note.yul
    val name = if (yul == null) stringResource(Res.string.score_rest) else "${yul.fullName} ${staffPitch(yul, hwang).solfegeName}"
    return stringResource(Res.string.sheetedit_staff_note_label, order, name)
}

/** EDIT-02에서도 띄우는 안내: 편집기에서 생긴 것과, 뒤에서 만들던 음원의 완료·실패·확인 못 함 알림. */
internal fun SheetEditMessage.shownInEditor(): Boolean = this in EditorMessages

private val EditorMessages = setOf(
    SheetEditMessage.SaveFailed, SheetEditMessage.SaveConflict, SheetEditMessage.SaveTooLarge,
    SheetEditMessage.ReviewBeforeReshape,
    SheetEditMessage.ComingSoon,
    SheetEditMessage.AudioReady,
    SheetEditMessage.AudioFailed,
    SheetEditMessage.AudioStatusUnknown,
)

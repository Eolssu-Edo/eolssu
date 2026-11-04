package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.core.ui.score.ScoreNotation
import com.ssafy.eolssu.feature.song.edit.component.EditScoreBoard
import com.ssafy.eolssu.feature.song.edit.component.HintText
import com.ssafy.eolssu.feature.song.edit.component.SheetEditIcons
import com.ssafy.eolssu.feature.song.edit.component.previewMarks
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheetedit_preview_audio
import eolssu.shared.generated.resources.sheetedit_preview_beat
import eolssu.shared.generated.resources.sheetedit_preview_beat_sub
import eolssu.shared.generated.resources.sheetedit_preview_draft
import eolssu.shared.generated.resources.sheetedit_preview_hint
import eolssu.shared.generated.resources.sheetedit_preview_listen
import eolssu.shared.generated.resources.sheetedit_preview_pause
import eolssu.shared.generated.resources.sheetedit_preview_play
import eolssu.shared.generated.resources.sheetedit_preview_save
import eolssu.shared.generated.resources.sheetedit_preview_title
import eolssu.shared.generated.resources.sheetedit_preview_with_jangdan
import eolssu.shared.generated.resources.sheetedit_warn_fill
import eolssu.shared.generated.resources.sheetedit_warn_message
import eolssu.shared.generated.resources.sheetedit_warn_more
import eolssu.shared.generated.resources.sheetedit_warn_title
import eolssu.shared.generated.resources.sheetedit_where_cell
import org.jetbrains.compose.resources.stringResource

/** 경고 창에 위치를 모두 적는 빈 정간 수. 넘으면 '외 n곳'으로 줄인다. */
private const val WARN_LISTED = 3

/**
 * EDIT-04 미리보기 · 저장: 장단 한 줄 · 연습 화면과 같은 정간보(율명 옆 한글) · 미리듣기(장단 소리 함께) · 음원 받기(M4A, 마지막 저장본) ·
 * 임시저장(빈 정간 허용) / 저장(빈 정간이 있으면 경고 창). 빈 정간은 옅은 붉은 바탕과 점선으로 보인다.
 */
@Composable
fun EditPreviewScreen(
    state: SheetEditUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleJangdan: () -> Unit,
    onGetAudio: () -> Unit,
    onSaveDraft: () -> Unit,
    onSave: () -> Unit,
    onFillEmpty: () -> Unit,
    onCloseWarning: () -> Unit,
) {
    val colors = EolssuTheme.colors
    val draft = state.draft
    val song = remember(draft) { draft.toSong() }
    EolssuScaffold(
        containerColor = colors.surface,
        snackbarHostState = snackbarHostState,
        topBar = { EolssuTopBar(stringResource(Res.string.sheetedit_preview_title), onBack = onBack, subtitle = draft.title) },
        bottomBar = {
            PlayerRow(state, song.durationSeconds(draft.bpm), onTogglePlay, onToggleJangdan, onGetAudio)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EolssuButton(stringResource(Res.string.sheetedit_preview_draft), onSaveDraft, Modifier.weight(1f), style = EolssuButtonStyle.Secondary, compact = true)
                EolssuButton(stringResource(Res.string.sheetedit_preview_save), onSave, Modifier.weight(1.4f), compact = true)
            }
        },
    ) {
        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 장단은 한 줄만: '세마치장단 · 60 BPM  한 각 9정간 · 3박 · 6각'.
            Row(
                Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.sheetedit_preview_beat, jangdanLabel(draft.jangdan), draft.bpm),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    stringResource(Res.string.sheetedit_preview_beat_sub, draft.gak, draft.jangdan.beats, draft.columnCount),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = colors.textMuted,
                    maxLines = 1,
                )
            }
            HintText(stringResource(Res.string.sheetedit_preview_hint), Modifier.padding(top = 6.dp))
            EditScoreBoard(
                song = song,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                marks = previewMarks(draft),
                annotations = if (state.showSigimsae) remember(draft) { draft.annotations() } else emptyMap(),
                notation = ScoreNotation(showHangul = true),
            )
        }
    }
    if (state.emptyWarning) EmptyWarningDialog(draft, onFillEmpty, onSaveDraft, onCloseWarning)
}

/** 미리듣기 원형 버튼 · '미리듣기 · 0:18'과 '장단 소리 함께' · '음원 받기'. */
@Composable
private fun PlayerRow(state: SheetEditUiState, seconds: Int, onTogglePlay: () -> Unit, onToggleJangdan: () -> Unit, onGetAudio: () -> Unit) {
    val colors = EolssuTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onTogglePlay, modifier = Modifier.size(48.dp), shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (state.previewPlaying) EolssuIcons.Pause else EolssuIcons.Play,
                    contentDescription = stringResource(if (state.previewPlaying) Res.string.sheetedit_preview_pause else Res.string.sheetedit_preview_play),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.sheetedit_preview_listen, formatClock(seconds.toDouble())),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            Row(
                Modifier.toggleable(value = state.withJangdan, role = Role.Checkbox, onValueChange = { onToggleJangdan() }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.withJangdan,
                    onCheckedChange = null,
                    modifier = Modifier.size(32.dp),
                    colors = CheckboxDefaults.colors(checkedColor = colors.primary, checkmarkColor = colors.onPrimary, uncheckedColor = colors.textDisabled),
                )
                Text(stringResource(Res.string.sheetedit_preview_with_jangdan), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textSub)
            }
        }
        Surface(
            onClick = onGetAudio,
            modifier = Modifier.heightIn(min = 40.dp),
            shape = CircleShape,
            color = colors.surface,
            contentColor = colors.text,
            border = BorderStroke(1.dp, colors.lineSoft),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(SheetEditIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(Res.string.sheetedit_preview_audio), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

/** 빈 정간이 있어 저장할 수 없다는 경고 창: '빈 정간 채우기'(편집으로) · '임시저장'. 닫기 버튼 없이 가림막·뒤로 가기로 닫는다. */
@Composable
private fun EmptyWarningDialog(draft: SheetDraft, onFill: () -> Unit, onSaveDraft: () -> Unit, onClose: () -> Unit) {
    val empties = draft.emptyCells
    val listed = empties.take(WARN_LISTED).map { stringResource(Res.string.sheetedit_where_cell, draft.columnOf(it) + 1, draft.rowOf(it) + 1) }.joinToString(", ")
    val where = if (empties.size > WARN_LISTED) stringResource(Res.string.sheetedit_warn_more, listed, empties.size - WARN_LISTED) else listed
    EolssuDialog(
        title = stringResource(Res.string.sheetedit_warn_title, empties.size),
        message = stringResource(Res.string.sheetedit_warn_message, where),
        onDismissRequest = onClose,
    ) {
        EolssuButton(stringResource(Res.string.sheetedit_warn_fill), onFill)
        EolssuButton(stringResource(Res.string.sheetedit_preview_draft), onSaveDraft, style = EolssuButtonStyle.Secondary)
    }
}

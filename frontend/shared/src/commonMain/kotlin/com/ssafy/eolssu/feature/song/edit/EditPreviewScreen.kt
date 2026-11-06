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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
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
import com.ssafy.eolssu.data.song.SheetAudioFailure
import com.ssafy.eolssu.feature.song.edit.component.EditScoreBoard
import com.ssafy.eolssu.feature.song.edit.component.HintText
import com.ssafy.eolssu.feature.song.edit.component.SheetEditIcons
import com.ssafy.eolssu.feature.song.edit.component.previewMarks
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheetedit_audio_failed
import eolssu.shared.generated.resources.sheetedit_audio_failure_server
import eolssu.shared.generated.resources.sheetedit_audio_failure_timeout
import eolssu.shared.generated.resources.sheetedit_audio_failure_too_large
import eolssu.shared.generated.resources.sheetedit_audio_failure_unknown
import eolssu.shared.generated.resources.sheetedit_audio_generating
import eolssu.shared.generated.resources.sheetedit_audio_outdated
import eolssu.shared.generated.resources.sheetedit_audio_pause
import eolssu.shared.generated.resources.sheetedit_audio_play
import eolssu.shared.generated.resources.sheetedit_audio_play_failed
import eolssu.shared.generated.resources.sheetedit_audio_ready
import eolssu.shared.generated.resources.sheetedit_audio_refresh
import eolssu.shared.generated.resources.sheetedit_audio_regenerate
import eolssu.shared.generated.resources.sheetedit_audio_requesting
import eolssu.shared.generated.resources.sheetedit_audio_retry
import eolssu.shared.generated.resources.sheetedit_audio_save_m4a
import eolssu.shared.generated.resources.sheetedit_audio_save_midi
import eolssu.shared.generated.resources.sheetedit_audio_unknown
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

/** 내 악보 음원 칸의 동작. [onSave]는 M4A·MIDI 서명 URL을 기기 브라우저로 연다. */
class SheetAudioActions(
    val onRequest: () -> Unit,
    val onRefresh: () -> Unit,
    val onTogglePlayback: () -> Unit,
    val onSave: (SheetAudioKind) -> Unit,
)

/**
 * EDIT-04 미리보기 · 저장: 장단 한 줄 · 연습 화면과 같은 정간보(율명 옆 한글) · 미리듣기(장단 소리 함께) ·
 * 내 악보 음원(SHT-12·13: 만들기 → 만드는 중 → 재생·M4A·MIDI 저장, 악보가 바뀌면 다시 만들기, 실패하면 다시 시도) ·
 * 임시저장(빈 정간 허용) / 저장(빈 정간이 있으면 경고 창). 빈 정간은 옅은 붉은 바탕과 점선으로 보인다.
 */
@Composable
fun EditPreviewScreen(
    state: SheetEditUiState,
    snackbarHostState: SnackbarHostState,
    playback: AudioPlaybackState,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleJangdan: () -> Unit,
    audio: SheetAudioActions,
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
            PlayerRow(state, song.durationSeconds(draft.bpm), onTogglePlay, onToggleJangdan, audio)
            SheetAudioRow(state.audio, playback, audio)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EolssuButton(stringResource(Res.string.sheetedit_preview_draft), onSaveDraft, Modifier.weight(1f), style = EolssuButtonStyle.Secondary, compact = true, enabled = !state.saving && !state.saveConflict)
                EolssuButton(stringResource(Res.string.sheetedit_preview_save), onSave, Modifier.weight(1.4f), compact = true, enabled = !state.saving && !state.saveConflict)
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
                    stringResource(Res.string.sheetedit_preview_beat, jangdanLabel(draft.jangdanName), draft.bpm),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                if (draft.jangdan != null) Text(
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

/** 미리듣기 원형 버튼 · '미리듣기 · 0:18'과 '장단 소리 함께' · 음원 칩. */
@Composable
private fun PlayerRow(state: SheetEditUiState, seconds: Int, onTogglePlay: () -> Unit, onToggleJangdan: () -> Unit, audio: SheetAudioActions) {
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
        AudioChip(state.audio, audio)
    }
}

/**
 * 음원 칸의 주 버튼. 없음 '음원 만들기', 요청·생성 중은 돌아가는 표시와 함께 눌리지 않음, 준비됨은 표시만,
 * 악보가 바뀌면 '다시 만들기', 실패는 '다시 시도', 상태를 못 읽으면 '다시 확인'.
 */
@Composable
private fun AudioChip(ui: SheetAudioUi, actions: SheetAudioActions) {
    val colors = EolssuTheme.colors
    val chip: Triple<String, ImageVector?, (() -> Unit)?> = when (ui) {
        SheetAudioUi.None -> Triple(stringResource(Res.string.sheetedit_preview_audio), SheetEditIcons.Download, actions.onRequest)
        is SheetAudioUi.Requesting -> Triple(stringResource(Res.string.sheetedit_audio_requesting), null, null)
        is SheetAudioUi.Generating -> Triple(stringResource(Res.string.sheetedit_audio_generating), null, null)
        is SheetAudioUi.Ready ->
            if (ui.audio.outdated) Triple(stringResource(Res.string.sheetedit_audio_regenerate), EolssuIcons.Restart, actions.onRequest)
            else Triple(stringResource(Res.string.sheetedit_audio_ready), EolssuIcons.Check, null)
        is SheetAudioUi.Failed -> Triple(stringResource(Res.string.sheetedit_audio_retry), EolssuIcons.Restart, actions.onRequest)
        is SheetAudioUi.Unknown -> Triple(stringResource(Res.string.sheetedit_audio_refresh), EolssuIcons.Restart, actions.onRefresh)
    }
    val (label, icon, onClick) = chip
    Surface(
        onClick = onClick ?: {},
        modifier = Modifier.heightIn(min = 40.dp),
        enabled = onClick != null,
        shape = CircleShape,
        color = colors.surface,
        contentColor = if (onClick == null) colors.textMuted else colors.text,
        border = BorderStroke(1.dp, colors.lineSoft),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (ui.busy) CircularProgressIndicator(Modifier.size(14.dp), color = colors.primary, strokeWidth = 2.dp)
            else if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

/**
 * 음원 칩 아래 줄: 악보가 바뀜·실패·확인 못 함 안내와, 보관된 파일이 있으면 재생 · M4A 저장 · MIDI 저장.
 * 새로 만드는 중이나 실패에도 서버에 남은 이전 파일은 그대로 쓸 수 있다. 둘 다 없으면 아무것도 그리지 않는다.
 */
@Composable
private fun SheetAudioRow(ui: SheetAudioUi, playback: AudioPlaybackState, actions: SheetAudioActions) {
    val colors = EolssuTheme.colors
    val latest = ui.latest
    val notice = when {
        ui is SheetAudioUi.Failed -> stringResource(Res.string.sheetedit_audio_failed, failureLabel(ui.audio.failureCode))
        ui is SheetAudioUi.Unknown -> stringResource(Res.string.sheetedit_audio_unknown)
        latest?.outdated == true && ui !is SheetAudioUi.Generating -> stringResource(Res.string.sheetedit_audio_outdated)
        else -> null
    }
    val file = latest?.audio
    if (notice == null && file == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (notice != null) Text(notice, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textSub)
        if (file != null) {
            val loaded = playback.sourceId == file.playbackId
            val status = if (loaded) playback.status else AudioPlaybackStatus.Idle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                EolssuButton(
                    text = stringResource(if (status == AudioPlaybackStatus.Playing) Res.string.sheetedit_audio_pause else Res.string.sheetedit_audio_play),
                    onClick = actions.onTogglePlayback,
                    icon = if (status == AudioPlaybackStatus.Playing) EolssuIcons.Pause else EolssuIcons.Play,
                    small = true,
                    busy = status == AudioPlaybackStatus.Loading,
                )
                EolssuButton(stringResource(Res.string.sheetedit_audio_save_m4a), { actions.onSave(SheetAudioKind.M4a) }, style = EolssuButtonStyle.Secondary, small = true)
                if (latest.midi != null) {
                    EolssuButton(stringResource(Res.string.sheetedit_audio_save_midi), { actions.onSave(SheetAudioKind.Midi) }, style = EolssuButtonStyle.Secondary, small = true)
                }
            }
            if (loaded && status == AudioPlaybackStatus.Failed) {
                Text(stringResource(Res.string.sheetedit_audio_play_failed), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textSub)
            }
        }
    }
}

@Composable
private fun failureLabel(code: SheetAudioFailure?): String = stringResource(
    when (code) {
        SheetAudioFailure.Timeout -> Res.string.sheetedit_audio_failure_timeout
        SheetAudioFailure.TooLarge -> Res.string.sheetedit_audio_failure_too_large
        SheetAudioFailure.ServerError -> Res.string.sheetedit_audio_failure_server
        SheetAudioFailure.Unknown, null -> Res.string.sheetedit_audio_failure_unknown
    },
)

/** 빈 정간이 있어 저장할 수 없다는 경고 창: '빈 정간 채우기'(편집으로) · '임시저장'. 닫기 버튼 없이 가림막·뒤로 가기로 닫는다. */
@Composable
private fun EmptyWarningDialog(draft: SheetDraft, onFill: () -> Unit, onSaveDraft: () -> Unit, onClose: () -> Unit) {
    val empties = draft.unfilledCells
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

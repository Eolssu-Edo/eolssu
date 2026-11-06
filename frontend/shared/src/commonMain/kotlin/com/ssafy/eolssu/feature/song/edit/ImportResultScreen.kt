package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.feature.song.edit.component.EditScoreBoard
import com.ssafy.eolssu.feature.song.edit.component.importMarks
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheetedit_import_result_title
import eolssu.shared.generated.resources.sheetedit_result_all_done
import eolssu.shared.generated.resources.sheetedit_result_edit_jg
import eolssu.shared.generated.resources.sheetedit_result_file_staff
import eolssu.shared.generated.resources.sheetedit_result_listen
import eolssu.shared.generated.resources.sheetedit_result_listen_desc
import eolssu.shared.generated.resources.sheetedit_result_cell
import eolssu.shared.generated.resources.sheetedit_result_source_discarded
import eolssu.shared.generated.resources.sheetedit_result_pause
import eolssu.shared.generated.resources.sheetedit_result_play
import eolssu.shared.generated.resources.sheetedit_result_setting_jg
import eolssu.shared.generated.resources.sheetedit_result_setting_staff
import eolssu.shared.generated.resources.sheetedit_result_summary_jg
import eolssu.shared.generated.resources.sheetedit_result_summary_staff
import eolssu.shared.generated.resources.sheetedit_result_tap_hint
import eolssu.shared.generated.resources.sheetedit_where_cell
import org.jetbrains.compose.resources.stringResource

/**
 * EDIT-06 인식 결과 확인: 확인할 정간 요약 · 연습 화면과 같은 정간보(확인할 정간 '?') · 고른 정간의 인식 결과와 후보 · 들어보며 확인 · 편집 시작.
 * 장단은 인식한 값('세마치(인식)')으로 보인다. 곁줄(가사 칸)에는 표시를 칠하지 않는다.
 */
@Composable
fun ImportResultScreen(
    state: ImportResultUiState,
    onBack: () -> Unit,
    onSelectCell: (Int) -> Unit,
    onChoose: (String) -> Unit,
    onTogglePlay: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = EolssuTheme.colors
    val staff = state.kind == ImportKind.Staff
    val draft = remember(state.fixed, state.kind) { state.draft() }
    val song = remember(draft) { draft.toSong() }
    val fileName = SheetEditSamples.importFileName(state.kind)
    EolssuScaffold(
        containerColor = colors.surface,
        topBar = {
            EolssuTopBar(
                stringResource(Res.string.sheetedit_import_result_title),
                onBack = onBack,
                subtitle = if (staff) stringResource(Res.string.sheetedit_result_file_staff, fileName) else fileName,
            )
        },
        bottomBar = {
            ListenRow(state.playing, onTogglePlay)
            EolssuButton(stringResource(Res.string.sheetedit_result_edit_jg), onEdit)
        },
    ) {
        Column(Modifier.fillMaxSize().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val open = state.openChecks.size
            val total = draft.cells.size
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val summary = if (staff) {
                    stringResource(Res.string.sheetedit_result_summary_staff, open)
                } else {
                    stringResource(Res.string.sheetedit_result_summary_jg, total, total - open, open)
                }
                val hint = stringResource(Res.string.sheetedit_result_tap_hint)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.Bold)) { append(summary) }
                        append(" · ")
                        append(hint)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = colors.textMuted,
                )
                Text(
                    if (staff) {
                        stringResource(Res.string.sheetedit_result_setting_staff, state.hwang.label, draft.jangdan.name, draft.columnCount)
                    } else {
                        stringResource(Res.string.sheetedit_result_setting_jg, draft.jangdan.name, draft.columnCount, draft.bpm)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = colors.textMuted,
                )
            }
            EditScoreBoard(
                song = song,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                marks = importMarks(draft, state.checks, state.fixed, state.selected),
                focusColumn = state.selected?.let(draft::columnOf),
                onCellClick = { column, row -> onSelectCell(draft.indexOf(column, row)) },
            )
            CheckCard(state, draft, onChoose, Modifier.padding(horizontal = 12.dp))
        }
    }
}

/** 고른 확인 정간의 원본 조각 · 위치 · 설명 · 후보 버튼. 모두 확인했으면 완료 안내. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckCard(state: ImportResultUiState, draft: SheetDraft, onChoose: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.lineSoft, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val check = state.selectedCheck
        if (check == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.onPrimary)
                }
                Text(stringResource(Res.string.sheetedit_result_all_done), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(Res.string.sheetedit_result_cell, stringResource(Res.string.sheetedit_where_cell, draft.columnOf(check.index) + 1, draft.rowOf(check.index) + 1)),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textMuted,
                )
                Text(check.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold))
                Text(check.description, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp), color = colors.textMuted)
            }
        }
        Text(stringResource(Res.string.sheetedit_result_source_discarded), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val current = state.currentCandidate(check)
            check.candidates.forEach { candidate ->
                val on = candidate == current
                Surface(
                    onClick = { onChoose(candidate) },
                    modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 56.dp).semantics { selected = on },
                    shape = RoundedCornerShape(10.dp),
                    color = if (on) colors.primary else colors.surface,
                    contentColor = colors.text,
                    border = BorderStroke(1.dp, if (on) colors.primary else colors.lineSoft),
                ) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(candidate, style = EolssuTheme.serif.yul.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

/** '들어보며 확인': 40dp 옅은 연두 원형 재생 버튼과 설명. */
@Composable
private fun ListenRow(playing: Boolean, onToggle: () -> Unit) {
    val colors = EolssuTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onToggle, modifier = Modifier.size(48.dp), shape = CircleShape, color = colors.surface) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp).background(colors.accentTint, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(
                        if (playing) EolssuIcons.Pause else EolssuIcons.Play,
                        contentDescription = stringResource(if (playing) Res.string.sheetedit_result_pause else Res.string.sheetedit_result_play),
                        modifier = Modifier.size(16.dp),
                        tint = colors.text,
                    )
                }
            }
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)) { append(stringResource(Res.string.sheetedit_result_listen)) }
                append(" · ")
                append(stringResource(Res.string.sheetedit_result_listen_desc))
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = colors.textSub,
        )
    }
}

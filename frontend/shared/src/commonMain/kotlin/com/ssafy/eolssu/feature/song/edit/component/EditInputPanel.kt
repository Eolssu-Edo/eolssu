package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuSwitch
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.core.ui.score.ScoreSigimsaeGlyph
import com.ssafy.eolssu.feature.song.edit.EditTab
import com.ssafy.eolssu.feature.song.edit.SheetDraft
import com.ssafy.eolssu.feature.song.edit.Sigimsae
import com.ssafy.eolssu.feature.song.edit.StaffPitchOrder
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.score_blank
import eolssu.shared.generated.resources.score_hold
import eolssu.shared.generated.resources.score_rest
import eolssu.shared.generated.resources.sheetedit_autosaved
import eolssu.shared.generated.resources.sheetedit_cell_add_column
import eolssu.shared.generated.resources.sheetedit_cell_delete_column
import eolssu.shared.generated.resources.sheetedit_cell_jangdan
import eolssu.shared.generated.resources.sheetedit_cell_jangdan_change
import eolssu.shared.generated.resources.sheetedit_cell_jangdan_desc
import eolssu.shared.generated.resources.sheetedit_cell_merge
import eolssu.shared.generated.resources.sheetedit_cell_note
import eolssu.shared.generated.resources.sheetedit_cell_split
import eolssu.shared.generated.resources.sheetedit_erase
import eolssu.shared.generated.resources.sheetedit_key_hold
import eolssu.shared.generated.resources.sheetedit_key_input
import eolssu.shared.generated.resources.sheetedit_key_rest
import eolssu.shared.generated.resources.sheetedit_selected
import eolssu.shared.generated.resources.sheetedit_selected_sigimsae
import eolssu.shared.generated.resources.sheetedit_sig_chuseong
import eolssu.shared.generated.resources.sheetedit_sig_chuseong_desc
import eolssu.shared.generated.resources.sheetedit_sig_clear
import eolssu.shared.generated.resources.sheetedit_sig_jeonseong
import eolssu.shared.generated.resources.sheetedit_sig_jeonseong_desc
import eolssu.shared.generated.resources.sheetedit_sig_show
import eolssu.shared.generated.resources.sheetedit_sig_show_desc
import eolssu.shared.generated.resources.sheetedit_sig_toeseong
import eolssu.shared.generated.resources.sheetedit_sig_toeseong_desc
import eolssu.shared.generated.resources.sheetedit_sig_yoseong
import eolssu.shared.generated.resources.sheetedit_sig_yoseong_desc
import eolssu.shared.generated.resources.sheetedit_tab_cell
import eolssu.shared.generated.resources.sheetedit_tab_sigimsae
import eolssu.shared.generated.resources.sheetedit_tab_yul
import eolssu.shared.generated.resources.sheetedit_where_short
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** 편집 입력판이 부르는 동작. */
class EditInputActions(
    val onSelectTab: (EditTab) -> Unit,
    val onPut: (ScoreNote) -> Unit,
    val onErase: () -> Unit,
    val onSigimsae: (Sigimsae) -> Unit,
    val onToggleSigimsae: () -> Unit,
    val onClearSigimsae: () -> Unit,
    val onSplit: () -> Unit,
    val onMerge: () -> Unit,
    val onAddColumn: () -> Unit,
    val onDeleteColumn: () -> Unit,
    val onChangeJangdan: () -> Unit,
)

/**
 * EDIT-02 아래 입력판(위 모서리 20): 고른 정간 정보 · '자동 임시저장됨', 탭 율명·기호 / 시김새 / 정간·장단.
 */
@Composable
internal fun EditInputPanel(
    draft: SheetDraft,
    selected: Int,
    tab: EditTab,
    showSigimsae: Boolean,
    actions: EditInputActions,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.lineSoft, shape)
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(Res.string.sheetedit_selected)) }
                    append(" · ")
                    append(selectedCellInfo(draft, selected))
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                maxLines = 1,
            )
            Text(stringResource(Res.string.sheetedit_autosaved), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
        }
        SegmentTabs(
            labels = listOf(stringResource(Res.string.sheetedit_tab_yul), stringResource(Res.string.sheetedit_tab_sigimsae), stringResource(Res.string.sheetedit_tab_cell)),
            selected = tab.ordinal,
            onSelect = { actions.onSelectTab(EditTab.entries[it]) },
            fill = true,
            frameColor = colors.surfaceStrong,
        )
        when (tab) {
            EditTab.Yul -> YulKeys(actions)
            EditTab.Sigimsae -> SigimsaeKeys(draft.sigimsae[selected], showSigimsae, actions)
            EditTab.Cell -> CellTools(draft, actions)
        }
    }
}

/** '5각 5정간 · 南 남려 · 시김새 요성'. 빈 정간은 '빈 정간'. */
@Composable
private fun selectedCellInfo(draft: SheetDraft, index: Int): String {
    val where = stringResource(Res.string.sheetedit_where_short, draft.columnOf(index) + 1, draft.rowOf(index) + 1)
    val notes = draft.cells[index].filter { it != ScoreNote.Blank }
    val content = if (notes.isEmpty()) {
        stringResource(Res.string.score_blank)
    } else {
        notes.joinToString("") { it.symbol } + " " + notes.map { noteName(it) }.joinToString("·")
    }
    val sigimsae = draft.sigimsae[index]?.let { stringResource(Res.string.sheetedit_selected_sigimsae, stringResource(sigimsaeName(it))) }
    return listOfNotNull(where, content, sigimsae).joinToString(" · ")
}

@Composable
private fun noteName(note: ScoreNote): String = when (note) {
    is ScoreNote.Pitch -> note.yul.fullName
    is ScoreNote.SourcePitch -> note.fullName
    ScoreNote.Hold -> stringResource(Res.string.score_hold)
    ScoreNote.Rest -> stringResource(Res.string.score_rest)
    ScoreNote.Blank -> stringResource(Res.string.score_blank)
}

internal fun sigimsaeName(value: Sigimsae): StringResource = when (value) {
    Sigimsae.Yoseong -> Res.string.sheetedit_sig_yoseong
    Sigimsae.Toeseong -> Res.string.sheetedit_sig_toeseong
    Sigimsae.Chuseong -> Res.string.sheetedit_sig_chuseong
    Sigimsae.Jeonseong -> Res.string.sheetedit_sig_jeonseong
}

private fun sigimsaeDescription(value: Sigimsae): StringResource = when (value) {
    Sigimsae.Yoseong -> Res.string.sheetedit_sig_yoseong_desc
    Sigimsae.Toeseong -> Res.string.sheetedit_sig_toeseong_desc
    Sigimsae.Chuseong -> Res.string.sheetedit_sig_chuseong_desc
    Sigimsae.Jeonseong -> Res.string.sheetedit_sig_jeonseong_desc
}

/** 율명 8개와 지속(ㅡ)·쉼(△) 자판(5열)과 고른 정간 지우기. 다른 정간은 악보에서 직접 눌러 고른다. */
@Composable
private fun YulKeys(actions: EditInputActions) {
    val keys: List<Pair<ScoreNote, String>> = StaffPitchOrder.map { ScoreNote.Pitch(it) to it.hangul } +
        listOf(ScoreNote.Hold to stringResource(Res.string.sheetedit_key_hold), ScoreNote.Rest to stringResource(Res.string.sheetedit_key_rest))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (note, label) -> YulKey(note, label, { actions.onPut(note) }) }
            }
        }
        // 지우기는 자판 아래 한 줄을 다 쓴다. 넓어진 만큼 아이콘 옆에 글자를 함께 둔다.
        Surface(
            onClick = actions.onErase,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            shape = RoundedCornerShape(10.dp),
            color = EolssuTheme.colors.primary,
            contentColor = EolssuTheme.colors.onPrimary,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SheetEditIcons.Erase, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(Res.string.sheetedit_erase), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun RowScope.YulKey(note: ScoreNote, label: String, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val description = stringResource(Res.string.sheetedit_key_input, (note as? ScoreNote.Pitch)?.yul?.fullName ?: label)
    Surface(
        onClick = onClick,
        modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = description },
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        contentColor = colors.text,
        border = BorderStroke(1.dp, colors.lineSoft),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(vertical = 4.dp)) {
            val symbolStyle = if (note is ScoreNote.Pitch) {
                EolssuTheme.serif.yul.copy(fontSize = 19.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 21.sp)
            }
            Text(note.symbol, style = symbolStyle)
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp), color = colors.textMuted)
        }
    }
}

/** 흰 바탕 테두리 도구 버튼(44dp · 13sp 600). */
@Composable
private fun ToolButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        contentColor = colors.text,
        border = BorderStroke(1.dp, colors.lineSoft),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

/** 시김새 네 가지(2열 카드) · '시김새 작게 표시' 스위치 · 고른 정간의 시김새 지우기. */
@Composable
private fun SigimsaeKeys(current: Sigimsae?, show: Boolean, actions: EditInputActions) {
    val colors = EolssuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Sigimsae.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { value ->
                    val on = value == current
                    Surface(
                        onClick = { actions.onSigimsae(value) },
                        modifier = Modifier.weight(1f).semantics { selected = on },
                        shape = RoundedCornerShape(10.dp),
                        color = colors.surface,
                        contentColor = colors.text,
                        border = BorderStroke(2.dp, if (on) colors.text else colors.lineSoft),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SigimsaeTile(value)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(sigimsaeName(value)), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                                Text(
                                    stringResource(sigimsaeDescription(value)),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                                    color = colors.textMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.sheetedit_sig_show), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                Text(stringResource(Res.string.sheetedit_sig_show_desc), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = colors.textMuted)
            }
            EolssuSwitch(show, actions.onToggleSigimsae, stringResource(Res.string.sheetedit_sig_show), width = 40.dp, height = 24.dp, offColor = colors.surfaceStrong)
        }
        ToolButton(stringResource(Res.string.sheetedit_sig_clear), actions.onClearSigimsae, Modifier.fillMaxWidth())
    }
}

/** 시김새 기호 견본(30dp 점선 상자). 공통 렌더러의 기호 그림을 쓰고, 그림이 없는 시김새는 이름을 적는다. */
@Composable
private fun SigimsaeTile(value: Sigimsae) {
    val colors = EolssuTheme.colors
    Box(
        Modifier
            .size(30.dp)
            .drawBehind {
                drawRoundRect(
                    colors.textDisabled,
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        ScoreSigimsaeGlyph(value.scoreName, 14.sp)
    }
}

/** 정간 나누기·합치기, 각 추가·삭제, 장단 정보와 '장단 바꾸기'. */
@Composable
private fun CellTools(draft: SheetDraft, actions: EditInputActions) {
    val colors = EolssuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolButton(stringResource(Res.string.sheetedit_cell_split), actions.onSplit, Modifier.weight(1f))
            ToolButton(stringResource(Res.string.sheetedit_cell_merge), actions.onMerge, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolButton(stringResource(Res.string.sheetedit_cell_add_column), actions.onAddColumn, Modifier.weight(1f))
            ToolButton(stringResource(Res.string.sheetedit_cell_delete_column), actions.onDeleteColumn, Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.sheetedit_cell_jangdan, jangdanLabel(draft.jangdanName)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
                if (draft.jangdan != null) Text(
                    stringResource(Res.string.sheetedit_cell_jangdan_desc, draft.jangdan.beats, draft.jangdan.subdivision, draft.bpm),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = colors.textMuted,
                )
            }
            Surface(
                onClick = actions.onChangeJangdan,
                modifier = Modifier.heightIn(min = 34.dp),
                shape = CircleShape,
                color = colors.surface,
                contentColor = colors.text,
                border = BorderStroke(1.dp, colors.lineSoft),
            ) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.sheetedit_cell_jangdan_change), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
        HintText(stringResource(Res.string.sheetedit_cell_note))
    }
}

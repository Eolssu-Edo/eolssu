package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.HanbaeMark
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.*
import com.ssafy.eolssu.feature.song.edit.*
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** 전체와 구간 빠르기를 한 창에서 편집한다. 적용 전 변경은 이 창 안에만 둔다. */
@Composable
internal fun SheetEditSettingsDialog(
    draft: SheetDraft,
    onApply: (SheetDraft) -> Unit,
    onDismiss: () -> Unit,
    saving: Boolean = false,
    hasTempoPlan: Boolean = false,
    selectedCell: Int = 0,
) {
    var working by remember(draft) { mutableStateOf(draft) }
    var bpm by rememberSaveable { mutableStateOf(draft.bpm.toString()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var replacing by rememberSaveable { mutableStateOf<Int?>(null) }
    var markName by rememberSaveable { mutableStateOf<String?>(HanbaeMark.Slower.name) }
    var gradual by rememberSaveable { mutableStateOf(false) }
    var startGak by rememberSaveable { mutableStateOf((draft.columnOf(selectedCell.coerceIn(draft.cells.indices)) + 1).toString()) }
    var startCell by rememberSaveable { mutableStateOf((draft.rowOf(selectedCell.coerceIn(draft.cells.indices)) + 1).toString()) }
    var endGak by rememberSaveable { mutableStateOf("") }
    var endCell by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var initialBpm by remember { mutableStateOf<Double?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val editTarget = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    // 카드가 화면보다 길어도 어느 구간인지 보이도록 카드 윗부분(머리글과 입력판 시작)을 기준으로 스크롤한다.
    LaunchedEffect(editing, replacing) {
        if (editing) editTarget.bringIntoView(Rect(0f, 0f, 1f, with(density) { 280.dp.toPx() }))
    }
    val validBpm = bpm.toIntOrNull()?.takeIf { it in SheetBpmRange }
    val current = working.copy(bpm = validBpm ?: working.bpm)
    val changes = current.tempoChanges
    val mark = HanbaeMark.entries.firstOrNull { it.name == markName }
    val needsEnd = mark?.gradual ?: gradual
    fun position(g: String, c: String, terminal: Boolean = false): Int? {
        val gak = g.toIntOrNull() ?: return null
        val cell = c.toIntOrNull() ?: return null
        if (terminal && gak == current.columnCount + 1 && cell == 1) return current.cells.size
        if (gak !in 1..current.columnCount || cell !in 1..current.sizes[gak - 1]) return null
        return current.indexOf(gak - 1, cell - 1)
    }
    fun positionLabel(index: Int): String = if (index == current.cells.size) "악보 끝"
        else "${current.columnOf(index) + 1}각 ${current.rowOf(index) + 1}정간"
    fun edit(cue: SheetTempoChange?) {
        replacing = cue?.cell
        val at = cue?.cell ?: selectedCell.coerceIn(current.cells.indices)
        startGak = (current.columnOf(at) + 1).toString(); startCell = (current.rowOf(at) + 1).toString()
        markName = cue?.mark?.name ?: if (cue == null) HanbaeMark.Slower.name else null
        gradual = cue?.endCell != null
        target = cue?.targetBpm?.let { if (it == it.roundToInt().toDouble()) it.roundToInt().toString() else it.toString() }.orEmpty()
        initialBpm = cue?.startBpm
        val end = cue?.endCell
        endGak = end?.let { if (it == current.cells.size) current.columnCount + 1 else current.columnOf(it) + 1 }?.toString().orEmpty()
        endCell = end?.let { if (it == current.cells.size) 1 else current.rowOf(it) + 1 }?.toString().orEmpty()
        editing = true; error = null
    }
    EolssuDialog(title = stringResource(Res.string.sheet_settings_title), onDismissRequest = { if (!saving) onDismiss() }) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EolssuTextField(bpm, { if (!saving) bpm = it },
                label = stringResource(Res.string.sheet_tempo_whole) + " · " + draft.tempoUnitLabel,
                placeholder = "", type = TextFieldType.Number, imeAction = ImeAction.Done,
                error = if (validBpm == null) stringResource(Res.string.sheet_details_bpm_error) else null,
                bordered = false, containerColor = EolssuTheme.colors.groupedBackground)
            if (hasTempoPlan || changes.isNotEmpty()) Text(stringResource(Res.string.sheet_tempo_profile_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(Res.string.sheet_tempo_changes), style = MaterialTheme.typography.titleSmall)
            if (changes.isEmpty()) Text(stringResource(Res.string.sheet_tempo_none), style = MaterialTheme.typography.bodySmall)
            // 수정 중인 구간은 연두 바탕·테두리로 강조하고, 입력판을 그 항목 안에 붙여 대상이 분명하게 보이게 한다.
            val tempoForm: @Composable ColumnScope.() -> Unit = {
                Text(stringResource(Res.string.sheet_tempo_kind), style = MaterialTheme.typography.labelLarge)
                (listOf<HanbaeMark?>(null) + HanbaeMark.entries).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { choice -> EolssuButton(choice?.label ?: stringResource(Res.string.sheet_tempo_numeric),
                            { markName = choice?.name; if (choice == HanbaeMark.Original) target = bpm; error = null }, Modifier.weight(1f),
                            style = if (mark == choice) EolssuButtonStyle.Primary else EolssuButtonStyle.Secondary, small = true, compact = true, enabled = !saving) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EolssuTextField(startGak, { startGak = it; error = null }, stringResource(Res.string.sheet_tempo_start_gak), "", Modifier.weight(1f), type = TextFieldType.Number)
                    EolssuTextField(startCell, { startCell = it; error = null }, stringResource(Res.string.sheet_tempo_start_cell), "", Modifier.weight(1f), type = TextFieldType.Number)
                }
                if (mark == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EolssuButton(stringResource(Res.string.sheet_tempo_step), { gradual = false }, Modifier.weight(1f), small = true, style = if (!gradual) EolssuButtonStyle.Primary else EolssuButtonStyle.Secondary)
                    EolssuButton(stringResource(Res.string.sheet_tempo_ramp), { gradual = true }, Modifier.weight(1f), small = true, style = if (gradual) EolssuButtonStyle.Primary else EolssuButtonStyle.Secondary)
                }
                if (mark != HanbaeMark.Original) EolssuTextField(target, { target = it; error = null },
                    stringResource(Res.string.sheet_tempo_target) + " · " + draft.tempoUnitLabel, "", type = TextFieldType.Number,
                    help = stringResource(Res.string.sheet_tempo_target_help))
                if (needsEnd) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EolssuTextField(endGak, { endGak = it; error = null }, stringResource(Res.string.sheet_tempo_end_gak), "", Modifier.weight(1f), type = TextFieldType.Number)
                        EolssuTextField(endCell, { endCell = it; error = null }, stringResource(Res.string.sheet_tempo_end_cell), "", Modifier.weight(1f), type = TextFieldType.Number)
                    }
                    Text(stringResource(Res.string.sheet_tempo_end_help), style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = EolssuTheme.colors.rec, style = MaterialTheme.typography.bodySmall) }
                EolssuButton(stringResource(Res.string.sheet_tempo_keep_change), {
                    val start = position(startGak, startCell)
                    val finish = if (needsEnd) position(endGak, endCell, terminal = true) else null
                    val value = if (mark == HanbaeMark.Original) validBpm?.toDouble() else target.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                    if (start == null || (target.isNotBlank() && value == null) || (needsEnd && value != null && finish == null)) {
                        error = "실제 정간과 목표 빠르기, 끝 위치를 확인해 주세요."
                    } else {
                        val cue = SheetTempoChange(start, mark, value, finish.takeIf { needsEnd }, initialBpm)
                        error = current.tempoChangeError(cue, replacing)
                        if (error == null) { working = current.withTempoChange(cue, replacing); editing = false }
                    }
                }, enabled = !saving && validBpm != null)
                EolssuButton(stringResource(Res.string.common_cancel), { editing = false; error = null }, style = EolssuButtonStyle.Text)
            }
            changes.forEach { cue ->
                val active = editing && replacing == cue.cell
                TempoCard(active, if (active) editTarget else null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(positionLabel(cue.cell) + " · " + (cue.mark?.label ?: stringResource(Res.string.sheet_tempo_numeric)),
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        if (active) EditingBadge(stringResource(Res.string.sheet_tempo_editing))
                    }
                    Text(if (cue.resolved) (cue.endCell?.let { "${positionLabel(it)}까지 · " }.orEmpty() +
                        cue.startBpm?.takeIf { cue.endCell != null }?.let { "${if (it == it.roundToInt().toDouble()) it.roundToInt() else it} → " }.orEmpty() +
                        "${cue.targetBpm?.let { if (it == it.roundToInt().toDouble()) it.roundToInt() else it }} ${draft.tempoUnitLabel}/분")
                        else stringResource(Res.string.sheet_tempo_unresolved), style = MaterialTheme.typography.bodySmall)
                    if (active) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = EolssuTheme.colors.accentTintLine)
                        tempoForm()
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EolssuButton(stringResource(Res.string.sheet_tempo_edit), { edit(cue) }, Modifier.weight(1f), style = EolssuButtonStyle.Secondary, small = true, enabled = !saving)
                        EolssuButton(stringResource(Res.string.sheet_tempo_remove), {
                            error = current.tempoDeletionError(cue.cell)
                            if (error == null) { working = current.withoutTempoChange(cue.cell); editing = false }
                        }, Modifier.weight(1f), style = EolssuButtonStyle.Text, small = true, enabled = !saving)
                    }
                }
            }
            if (editing && (replacing == null || changes.none { it.cell == replacing })) {
                TempoCard(true, editTarget) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.sheet_tempo_new), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        EditingBadge(stringResource(Res.string.sheet_tempo_editing))
                    }
                    tempoForm()
                }
            }
            if (!editing) EolssuButton(stringResource(Res.string.sheet_tempo_add), { edit(null) }, style = EolssuButtonStyle.Secondary, enabled = !saving && validBpm != null)
            if (!editing) error?.let { Text(it, color = EolssuTheme.colors.rec, style = MaterialTheme.typography.bodySmall) }
        }
        EolssuButton(stringResource(Res.string.sheet_details_apply), { onApply(current) }, enabled = validBpm != null && !saving && !editing)
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary,
            enabled = !saving, containerColor = EolssuTheme.colors.groupedBackground, borderColor = Color.Transparent)
    }
}

/** 구간 빠르기 항목. 수정 중이면 연두 바탕·테두리로 강조하고 선택 상태를 알린다. */
@Composable
private fun TempoCard(active: Boolean, target: BringIntoViewRequester?, content: @Composable ColumnScope.() -> Unit) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth()
            .then(if (target != null) Modifier.bringIntoViewRequester(target) else Modifier)
            .clip(shape)
            .background(if (active) colors.accentSoft else colors.surface)
            .border(if (active) 1.5.dp else 1.dp, if (active) colors.accentTintLine else colors.lineSoft, shape)
            .then(if (active) Modifier.semantics { selected = true } else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun EditingBadge(text: String) {
    val colors = EolssuTheme.colors
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(colors.accentTint).padding(horizontal = 8.dp, vertical = 2.dp),
        color = colors.accentText, style = MaterialTheme.typography.labelSmall)
}

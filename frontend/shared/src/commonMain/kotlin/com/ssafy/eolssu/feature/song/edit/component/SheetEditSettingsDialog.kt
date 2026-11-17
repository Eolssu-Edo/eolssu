package com.ssafy.eolssu.feature.song.edit.component

import com.ssafy.eolssu.core.diagnostics.QaTarget

import com.ssafy.eolssu.core.model.InputLimit
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.*
import com.ssafy.eolssu.feature.song.edit.*
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private fun Double.bpmText(): String = if (this == roundToInt().toDouble()) roundToInt().toString()
    else ((this * 10).roundToInt() / 10.0).toString()

private fun SheetDraft.positionLabel(index: Int): String = "${columnOf(index) + 1}각 ${rowOf(index) + 1}정간"

/**
 * 기본 빠르기와 빠르기가 바뀌는 곳을 한 창에서 편집한다(S15P31E102-1089). 적용 전 변경은 이 창 안에만 둔다.
 * 바뀌는 곳마다 바로/점점과 목표만 정하고, 기호는 앞 빠르기와 비교해 자동으로 붙인다(기본 빠르기로 바로 돌아가면 ㅂㅅ).
 */
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
    // 빠르기 칸을 고친 뒤에만 오류를 보인다(S15P31E102-996).
    var bpmTouched by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var replacing by rememberSaveable { mutableStateOf<Int?>(null) }
    var start by rememberSaveable { mutableStateOf(0) }
    var end by rememberSaveable { mutableStateOf(0) }
    var kindName by rememberSaveable { mutableStateOf(SheetTempoKind.Step.name) }
    var target by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val editTarget = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    LaunchedEffect(editing, replacing) {
        if (editing) editTarget.bringIntoView(Rect(0f, 0f, 1f, with(density) { 320.dp.toPx() }))
    }
    val validBpm = bpm.toIntOrNull()?.takeIf { it in SheetBpmRange }
    val current = working.copy(bpm = validBpm ?: working.bpm)
    val cues = current.tempoCues
    val befores = current.tempoBefore(cues)
    val lastCell = (current.cells.size - 1).coerceAtLeast(0)
    val kind = SheetTempoKind.valueOf(kindName)

    /** [at] 직전의 빠르기. 고치는 변경 자신은 빼고 계산한다. */
    fun speedBefore(at: Int): Double {
        val previous = cues.filterNot { it.start == replacing }.lastOrNull { it.start < at && it.resolved } ?: return current.bpm.toDouble()
        return requireNotNull(previous.bpm)
    }

    fun open(cue: SheetTempoCue?) {
        replacing = cue?.start
        val free = selectedCell.coerceIn(0, lastCell).let { at ->
            if (cues.none { at in it.start..it.last }) at
            else (0..lastCell).firstOrNull { cell -> cues.none { cell in it.start..it.last } } ?: at
        }
        start = cue?.start ?: free
        val nextStart = cues.map { it.start }.filter { it > start }.minOrNull() ?: current.cells.size
        val gak = current.columnOf(start)
        end = cue?.end ?: minOf(current.indexOf(gak, current.sizes[gak] - 1), nextStart - 1).coerceAtLeast(start)
        kindName = (cue?.kind ?: SheetTempoKind.Step).name
        target = (cue?.bpm ?: (speedBefore(start) + 10).coerceAtMost(SheetBpmRange.last.toDouble())).bpmText()
        editing = true; error = null
    }
    val pending = if (editing) SheetTempoCue(start, kind, target.toDoubleOrNull(),
        if (kind == SheetTempoKind.Ramp) end else null) else null
    val pendingError = pending?.let { current.tempoCueError(it, replacing) }
    val preview = if (pending != null && pendingError == null) current.withTempoCue(pending, replacing) else current

    EolssuDialog(title = stringResource(Res.string.sheet_settings_title), onDismissRequest = { if (!saving) onDismiss() }) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BaseTempoRow(bpm, draft.tempoUnitLabel, enabled = !saving) { bpm = it; bpmTouched = true; error = null }
            if (bpmTouched && validBpm == null) Text(stringResource(Res.string.sheet_details_bpm_error, SheetBpmRange.first, SheetBpmRange.last),
                color = EolssuTheme.colors.rec, style = MaterialTheme.typography.bodySmall)
            if (hasTempoPlan || cues.isNotEmpty()) Text(stringResource(Res.string.sheet_tempo_profile_note), style = MaterialTheme.typography.bodySmall,
                color = EolssuTheme.colors.textSub)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.sheet_tempo_changes), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                preview.estimatedDurationSeconds(preview.bpm)?.let {
                    Text(stringResource(Res.string.sheet_tempo_duration, formatClock(it)), style = MaterialTheme.typography.bodySmall,
                        color = EolssuTheme.colors.textSub)
                }
            }
            if (cues.isEmpty() && !editing) Text(stringResource(Res.string.sheet_tempo_none), style = MaterialTheme.typography.bodySmall,
                color = EolssuTheme.colors.textSub)

            val form: @Composable ColumnScope.() -> Unit = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(SheetTempoKind.Step to Res.string.sheet_tempo_kind_step, SheetTempoKind.Ramp to Res.string.sheet_tempo_kind_ramp).forEach { (choice, text) ->
                        EolssuButton(stringResource(text), { kindName = choice.name; error = null }, Modifier.weight(1f), small = true, compact = true,
                            singleLine = true, style = if (kind == choice) EolssuButtonStyle.Primary else EolssuButtonStyle.Secondary, enabled = !saving,
                            qaTarget = QaTarget.SHEET_EDIT_SETTINGS_DIALOG_BUTTON_02)
                    }
                }
                PositionPicker(stringResource(if (kind == SheetTempoKind.Ramp) Res.string.sheet_tempo_start else Res.string.sheet_tempo_position),
                    current, start, enabled = !saving) { start = it; if (end < it) end = it; error = null }
                if (kind == SheetTempoKind.Ramp) PositionPicker(stringResource(Res.string.sheet_tempo_end), current, end, enabled = !saving) {
                    end = it; if (start > it) start = it; error = null
                }
                EolssuTextField(target, { target = it; error = null },
                    stringResource(Res.string.sheet_tempo_bpm), "", type = TextFieldType.Number, maxLength = InputLimit.BPM_DIGITS,
                    qaTarget = QaTarget.SHEET_EDIT_SETTINGS_DIALOG_FIELD_02)
                val shown = error ?: pendingError
                if (shown != null) Text(shown, color = EolssuTheme.colors.rec, style = MaterialTheme.typography.bodySmall)
                else if (pending != null) {
                    val before = speedBefore(pending.start)
                    val after = pending.bpm ?: before
                    val mark = pending.markFrom(before, current.bpm.toDouble())
                    Text(listOfNotNull(mark?.label, "${before.bpmText()} → ${after.bpmText()}").joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = EolssuTheme.colors.accentText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    replacing?.let { at ->
                        EolssuButton(stringResource(Res.string.sheet_tempo_remove), {
                            error = current.tempoCueDeletionError(at)
                            if (error == null) { working = current.withoutTempoCue(at); editing = false }
                        }, style = EolssuButtonStyle.Text, small = true, enabled = !saving, contentColor = EolssuTheme.colors.rec,
                            qaTarget = QaTarget.SHEET_EDIT_SETTINGS_DIALOG_BUTTON_07)
                    }
                    Spacer(Modifier.weight(1f))
                    EolssuButton(stringResource(Res.string.common_cancel), { editing = false; error = null }, style = EolssuButtonStyle.Text,
                        small = true, qaTarget = QaTarget.SHEET_EDIT_SETTINGS_DIALOG_BUTTON_05)
                    EolssuButton(stringResource(Res.string.sheet_tempo_keep_change), {
                        val cue = pending ?: return@EolssuButton
                        error = current.tempoCueError(cue, replacing)
                        if (error == null) { working = current.withTempoCue(cue, replacing); editing = false }
                    }, small = true, enabled = !saving && validBpm != null && pendingError == null, qaTarget = QaTarget.SHEET_EDIT_SETTINGS_DIALOG_BUTTON_04)
                }
            }

            cues.zip(befores).forEach { (cue, before) ->
                val active = editing && replacing == cue.start
                TempoCard(active, if (active) editTarget else null, onClick = if (!editing && !saving) ({ open(cue) }) else null) {
                    CueSummary(current, cue, before, active)
                    if (active) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = EolssuTheme.colors.accentTintLine)
                        form()
                    }
                }
            }
            if (editing && replacing == null) TempoCard(true, editTarget, onClick = null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.sheet_tempo_new), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold)
                    EditingBadge(stringResource(Res.string.sheet_tempo_editing))
                }
                form()
            }
            if (!editing) {
                error?.let { Text(it, color = EolssuTheme.colors.rec, style = MaterialTheme.typography.bodySmall) }
                EolssuButton(stringResource(Res.string.sheet_tempo_add), { open(null) }, style = EolssuButtonStyle.Secondary,
                    enabled = !saving && validBpm != null && current.cells.isNotEmpty(), qaTarget = QaTarget.SHEET_EDIT_SETTINGS_DIALOG_BUTTON_08)
            }
        }
        EolssuButton(stringResource(Res.string.sheet_details_apply), { onApply(current) }, enabled = validBpm != null && !saving && !editing, qaTarget = QaTarget.SHEET_EDIT_SETTINGS_DIALOG_BUTTON_09)
        EolssuButton(stringResource(Res.string.common_cancel), onDismiss, style = EolssuButtonStyle.Secondary,
            enabled = !saving, containerColor = EolssuTheme.colors.groupedBackground, borderColor = Color.Transparent, qaTarget = QaTarget.SHEET_EDIT_SETTINGS_DIALOG_BUTTON_10)
    }
}

/** 기본 빠르기 한 줄: 라벨 · 단위 = [−] 숫자 [+]. 숫자는 키보드로도 고친다. */
@Composable
private fun BaseTempoRow(value: String, unit: String, enabled: Boolean, onValue: (String) -> Unit) {
    val colors = EolssuTheme.colors
    val number = value.toIntOrNull()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.groupedBackground).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.sheet_tempo_whole), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text("$unit =", style = MaterialTheme.typography.bodyMedium, color = colors.textSub, maxLines = 1)
        StepButton("−", "기본 빠르기 내리기", enabled && (number ?: 0) > SheetBpmRange.first) {
            onValue(((number ?: SheetBpmRange.first) - 1).coerceIn(SheetBpmRange).toString())
        }
        BasicTextField(
            value = value,
            onValueChange = { input -> onValue(input.filter(Char::isDigit).take(InputLimit.BPM_DIGITS)) },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = colors.text, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.width(56.dp).semantics { contentDescription = "기본 빠르기" },
        )
        StepButton("+", "기본 빠르기 올리기", enabled && (number ?: Int.MAX_VALUE) < SheetBpmRange.last) {
            onValue(((number ?: SheetBpmRange.last) + 1).coerceIn(SheetBpmRange).toString())
        }
    }
}

/** 목록 한 줄: 기호, 위치, 앞 빠르기 → 바뀐 빠르기. 숫자가 없는 인식 기호는 미정으로 알린다. */
@Composable
private fun CueSummary(draft: SheetDraft, cue: SheetTempoCue, before: Double, active: Boolean) {
    val colors = EolssuTheme.colors
    val mark = if (cue.resolved) cue.markFrom(before, draft.bpm.toDouble()) else cue.mark
    // 기호 대신 뜻(조금 빠르게·본래 속도로 등)을 말로 보인다.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (cue.kind == SheetTempoKind.Ramp && cue.end != null) "${draft.positionLabel(cue.start)} – ${draft.positionLabel(cue.last)}"
                else draft.positionLabel(cue.start), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            val after = cue.bpm
            Text(listOfNotNull(mark?.label,
                if (cue.resolved && after != null) "${before.bpmText()} → ${after.bpmText()}" else stringResource(Res.string.sheet_tempo_unresolved))
                .joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = if (cue.resolved) colors.textSub else colors.rec)
        }
        if (active) EditingBadge(stringResource(Res.string.sheet_tempo_editing))
    }
}

/**
 * 위치 선택기: [3]각 [2]정간을 키보드로 바로 입력하고, ‹ ›로 한 정간씩 옮긴다.
 * 각을 바꾸면 정간 번호는 그 각 안으로 맞춘다. 없는 번호를 입력하면 칸만 빨갛게 두고 위치는 바꾸지 않는다.
 */
@Composable
private fun PositionPicker(label: String, draft: SheetDraft, index: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val colors = EolssuTheme.colors
    val gak = draft.columnOf(index)
    val row = draft.rowOf(index)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surface).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textSub, maxLines = 1)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberBox(gak + 1, "$label 각", enabled, valid = { it in 1..draft.columnCount }) { value ->
            val target = value - 1
            onChange(draft.indexOf(target, row.coerceAtMost(draft.sizes[target] - 1)))
        }
        Text("각", style = MaterialTheme.typography.bodyMedium, color = colors.textSub)
        NumberBox(row + 1, "$label 정간", enabled, valid = { it in 1..draft.sizes[gak] }) { value -> onChange(draft.indexOf(gak, value - 1)) }
        Text("정간", style = MaterialTheme.typography.bodyMedium, color = colors.textSub, maxLines = 1)
        Spacer(Modifier.weight(1f))
        StepButton("‹", "$label 한 정간 앞으로", enabled && index > 0) { onChange(index - 1) }
        StepButton("›", "$label 한 정간 뒤로", enabled && index < draft.cells.size - 1) { onChange(index + 1) }
    }
    }
}

/** 숫자 하나를 크게 보이는 입력 칸. 바깥 값이 바뀌면 입력 중이던 글자도 그 값으로 맞춘다. */
@Composable
private fun NumberBox(value: Int, description: String, enabled: Boolean, valid: (Int) -> Boolean, onValue: (Int) -> Unit) {
    val colors = EolssuTheme.colors
    var text by remember(value) { mutableStateOf(value.toString()) }
    val wrong = text.toIntOrNull()?.let { !valid(it) } ?: text.isNotEmpty()
    BasicTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter(Char::isDigit).take(3)
            text = digits
            digits.toIntOrNull()?.takeIf(valid)?.let(onValue)
        },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.text, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier.width(48.dp).height(42.dp).clip(RoundedCornerShape(8.dp)).background(colors.groupedBackground)
            .border(1.dp, if (wrong) colors.rec else colors.lineSoft, RoundedCornerShape(8.dp))
            .semantics { contentDescription = description },
        decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() } },
    )
}

@Composable
private fun StepButton(text: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(if (enabled) colors.accentTint else colors.lineSoft)
        .clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) colors.accentText else colors.textDisabled, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 구간 항목. 누르면 그 자리에서 고치고, 수정 중이면 연두 바탕·테두리로 강조한다. */
@Composable
private fun TempoCard(active: Boolean, target: BringIntoViewRequester?, onClick: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth()
            .then(if (target != null) Modifier.bringIntoViewRequester(target) else Modifier)
            .clip(shape)
            .background(if (active) colors.accentSoft else colors.surface)
            .border(if (active) 1.5.dp else 1.dp, if (active) colors.accentTintLine else colors.lineSoft, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (active) Modifier.semantics { selected = true } else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun EditingBadge(text: String) {
    val colors = EolssuTheme.colors
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(colors.accentTint).padding(horizontal = 8.dp, vertical = 2.dp),
        color = colors.accentText, style = MaterialTheme.typography.labelSmall)
}

package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuBottomSheet
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIconButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.SheetHeader
import com.ssafy.eolssu.core.ui.component.ToggleRow
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.ScoreNotation
import com.ssafy.eolssu.feature.practice.FingeringOrder
import com.ssafy.eolssu.feature.practice.PracticeDraft
import com.ssafy.eolssu.feature.practice.PracticePanel
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.TempoStep
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_apply
import eolssu.shared.generated.resources.practice_big_score
import eolssu.shared.generated.resources.practice_big_score_desc
import eolssu.shared.generated.resources.practice_fingering_closed
import eolssu.shared.generated.resources.practice_fingering_half
import eolssu.shared.generated.resources.practice_fingering_instrument
import eolssu.shared.generated.resources.practice_fingering_name
import eolssu.shared.generated.resources.practice_fingering_next
import eolssu.shared.generated.resources.practice_fingering_open
import eolssu.shared.generated.resources.practice_fingering_placeholder
import eolssu.shared.generated.resources.practice_fingering_prev
import eolssu.shared.generated.resources.practice_fingering_title
import eolssu.shared.generated.resources.practice_flute_end
import eolssu.shared.generated.resources.practice_flute_holes
import eolssu.shared.generated.resources.practice_flute_membrane
import eolssu.shared.generated.resources.practice_flute_mouth
import eolssu.shared.generated.resources.practice_jangdan_beat
import eolssu.shared.generated.resources.practice_jangdan_beat_desc
import eolssu.shared.generated.resources.practice_jangdan_gueum_note
import eolssu.shared.generated.resources.practice_jangdan_gueum_placeholder
import eolssu.shared.generated.resources.practice_jangdan_meta
import eolssu.shared.generated.resources.practice_jangdan_mic_notice
import eolssu.shared.generated.resources.practice_jangdan_no_gueum
import eolssu.shared.generated.resources.practice_jangdan_preview_start
import eolssu.shared.generated.resources.practice_jangdan_preview_stop
import eolssu.shared.generated.resources.practice_jangdan_sound
import eolssu.shared.generated.resources.practice_jangdan_sound_desc
import eolssu.shared.generated.resources.practice_jangdan_sound_on
import eolssu.shared.generated.resources.practice_jangdan_sub
import eolssu.shared.generated.resources.practice_jangdan_title
import eolssu.shared.generated.resources.practice_listen_note
import eolssu.shared.generated.resources.practice_listening
import eolssu.shared.generated.resources.practice_menu_jangdan
import eolssu.shared.generated.resources.practice_menu_notation
import eolssu.shared.generated.resources.practice_menu_tempo
import eolssu.shared.generated.resources.practice_notation_example
import eolssu.shared.generated.resources.practice_notation_example_aux
import eolssu.shared.generated.resources.practice_notation_example_keep
import eolssu.shared.generated.resources.practice_notation_example_plain
import eolssu.shared.generated.resources.practice_notation_hangul
import eolssu.shared.generated.resources.practice_notation_hangul_desc
import eolssu.shared.generated.resources.practice_notation_solfege
import eolssu.shared.generated.resources.practice_notation_solfege_desc
import eolssu.shared.generated.resources.practice_notation_solfege_none
import eolssu.shared.generated.resources.practice_notation_title
import eolssu.shared.generated.resources.practice_off
import eolssu.shared.generated.resources.practice_on
import eolssu.shared.generated.resources.practice_settings
import eolssu.shared.generated.resources.practice_tempo_note
import eolssu.shared.generated.resources.practice_tempo_sub
import eolssu.shared.generated.resources.practice_tempo_title
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** 보조 패널에서 부르는 동작. */
class PracticePanelActions(
    val onClose: () -> Unit,
    val onOpen: (PracticePanel) -> Unit,
    val onDraft: ((PracticeDraft) -> PracticeDraft) -> Unit,
    val onApplyNotation: () -> Unit,
    val onApplyJangdan: () -> Unit,
    val onApplyTempo: () -> Unit,
    val onToggleBigScore: () -> Unit,
    val onMoveFingering: (Int) -> Unit,
    val onListen: () -> Unit,
    val onTogglePreview: () -> Unit,
)

/**
 * 휴대폰·태블릿 세로에서는 아래에서 올라오는 시트로 보여 준다.
 * 시트 너비는 Material 기본값(최대 640dp, 가운데)이고, 태블릿 세로([tablet])는 창 높이의 45%(설정)·60%까지만 올라온다.
 */
@Composable
fun PracticePanelSheet(
    state: PracticeUiState,
    actions: PracticePanelActions,
    tablet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val panel = state.panel ?: return
    val song = state.song ?: return
    val apply = panelApply(panel, actions)
    val settings = panel == PracticePanel.Settings
    EolssuBottomSheet(
        onDismiss = actions.onClose,
        modifier = modifier,
        maxHeightFraction = when {
            tablet -> if (settings) 0.45f else 0.6f
            settings -> 0.6f
            else -> 0.78f
        },
        header = { PanelHeader(panel, actions.onClose) },
        footer = apply?.let { { EolssuButton(stringResource(Res.string.practice_apply), it) } },
    ) {
        PanelBody(panel, state, song, actions)
    }
}

/**
 * 태블릿 가로에서는 화면을 덮지 않고 오른쪽 상태 패널 자리에 연다. 머리·적용 버튼은 고정하고 본문만 스크롤한다.
 */
@Composable
fun PracticePanelInline(state: PracticeUiState, actions: PracticePanelActions, modifier: Modifier = Modifier) {
    val panel = state.panel ?: return
    val song = state.song ?: return
    val apply = panelApply(panel, actions)
    Column(modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)) {
        PanelHeader(panel, actions.onClose)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PanelBody(panel, state, song, actions)
        }
        if (apply != null) {
            EolssuButton(stringResource(Res.string.practice_apply), apply, Modifier.padding(top = 14.dp))
        }
    }
}

/** 고친 값을 반영하는 '적용' 동작. 바로 바뀌는 패널(설정 메뉴·운지법)은 없다. */
private fun panelApply(panel: PracticePanel, actions: PracticePanelActions): (() -> Unit)? = when (panel) {
    PracticePanel.Notation -> actions.onApplyNotation
    PracticePanel.Jangdan -> actions.onApplyJangdan
    PracticePanel.Tempo -> actions.onApplyTempo
    else -> null
}

@Composable
fun PanelHeader(panel: PracticePanel, onClose: () -> Unit) {
    when (panel) {
        PracticePanel.Settings -> SheetHeader(stringResource(Res.string.practice_settings), onClose)
        PracticePanel.Notation -> SheetHeader(stringResource(Res.string.practice_notation_title), onClose)
        PracticePanel.Fingering -> SheetHeader(
            stringResource(Res.string.practice_fingering_title), onClose,
            subtitle = stringResource(Res.string.practice_fingering_instrument),
        )
        PracticePanel.Jangdan -> SheetHeader(stringResource(Res.string.practice_jangdan_title), onClose)
        PracticePanel.Tempo -> SheetHeader(stringResource(Res.string.practice_tempo_title), onClose)
    }
}

@Composable
fun ColumnScope.PanelBody(panel: PracticePanel, state: PracticeUiState, song: Song, actions: PracticePanelActions) {
    when (panel) {
        PracticePanel.Settings -> SettingsMenu(state, song, actions)
        PracticePanel.Notation -> NotationPanel(state.draft, song, actions)
        PracticePanel.Fingering -> FingeringPanel(state, actions)
        PracticePanel.Jangdan -> JangdanPanel(state, song, actions)
        PracticePanel.Tempo -> TempoPanel(state.draft, song, actions)
    }
}

@Composable
private fun SettingsMenu(state: PracticeUiState, song: Song, actions: PracticePanelActions) {
    val settings = state.settings
    val notationOn = settings.showHangul || settings.showSolfege
    val step = TempoStep.nearest(song.baseBpm, state.bpm)
    Column(Modifier.padding(bottom = 4.dp)) {
        MenuRow(
            EolssuIcons.Text, stringResource(Res.string.practice_menu_notation),
            stringResource(if (notationOn) Res.string.practice_on else Res.string.practice_off), notationOn,
        ) { actions.onOpen(PracticePanel.Notation) }
        MenuRow(
            EolssuIcons.Metronome, stringResource(Res.string.practice_menu_jangdan),
            if (settings.jangdanSound) stringResource(Res.string.practice_jangdan_sound_on, song.jangdan.name) else song.jangdan.name,
            settings.jangdanSound,
        ) { actions.onOpen(PracticePanel.Jangdan) }
        MenuRow(EolssuIcons.Speed, stringResource(Res.string.practice_menu_tempo), tempoLabel(step), state.bpm != song.baseBpm) {
            actions.onOpen(PracticePanel.Tempo)
        }
    }
    ToggleRow(
        stringResource(Res.string.practice_big_score), settings.bigScore, actions.onToggleBigScore,
        description = stringResource(Res.string.practice_big_score_desc),
    )
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, status: String, on: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(horizontal = 2.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(colors.surface, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.text)
        }
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        Text(
            status,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
            color = if (on) colors.text else colors.textMuted,
        )
        Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.lineStrong)
    }
}

@Composable
private fun NotationPanel(draft: PracticeDraft, song: Song, actions: PracticePanelActions) {
    val hasSolfege = song.solfege != null
    ToggleRow(
        stringResource(Res.string.practice_notation_hangul), draft.showHangul,
        { actions.onDraft { it.copy(showHangul = !it.showHangul) } },
        description = stringResource(Res.string.practice_notation_hangul_desc),
    )
    ToggleRow(
        stringResource(Res.string.practice_notation_solfege), hasSolfege && draft.showSolfege,
        { actions.onDraft { it.copy(showSolfege = !it.showSolfege) } },
        description = stringResource(if (hasSolfege) Res.string.practice_notation_solfege_desc else Res.string.practice_notation_solfege_none),
        enabled = hasSolfege,
    )
    val colors = EolssuTheme.colors
    val plain = !draft.showHangul && !(hasSolfege && draft.showSolfege)
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    colors.line, cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        JeongganboView(
            song = song,
            modifier = Modifier.width(60.dp),
            metrics = JeongganboMetrics(columnWidth = 56.dp, cellHeight = 40.dp, yulSize = 19.sp, auxSize = 9.5.sp),
            notation = ScoreNotation(draft.showHangul, hasSolfege && draft.showSolfege),
            scrollState = null,
            maxColumns = 1,
            maxRows = 3,
        )
        Text(
            listOf(
                stringResource(Res.string.practice_notation_example),
                stringResource(if (plain) Res.string.practice_notation_example_plain else Res.string.practice_notation_example_aux),
                stringResource(Res.string.practice_notation_example_keep),
            ).joinToString("\n"),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = colors.textMuted,
        )
    }
}

@Composable
private fun FingeringPanel(state: PracticeUiState, actions: PracticePanelActions) {
    val colors = EolssuTheme.colors
    val yul = state.draft.note
    val index = FingeringOrder.indexOf(yul)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EolssuIconButton(EolssuIcons.Back, stringResource(Res.string.practice_fingering_prev), { actions.onMoveFingering(-1) }, enabled = index > 0)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(yul.symbol, style = EolssuTheme.serif.yul.copy(fontSize = 30.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold))
            Text(
                stringResource(Res.string.practice_fingering_name, yul.fullName, yul.hangul),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
            )
        }
        EolssuIconButton(
            EolssuIcons.Next, stringResource(Res.string.practice_fingering_next), { actions.onMoveFingering(1) },
            enabled = index < FingeringOrder.lastIndex,
        )
    }
    Column(
        Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(14.dp)).padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FluteOutline()
        Text(
            stringResource(Res.string.practice_fingering_placeholder),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem(stringResource(Res.string.practice_fingering_closed), fill = 1f)
            LegendItem(stringResource(Res.string.practice_fingering_open), fill = 0f)
            LegendItem(stringResource(Res.string.practice_fingering_half), fill = 0.5f)
        }
    }
    val listening = state.listening == yul
    EolssuButton(
        stringResource(if (listening) Res.string.practice_listening else Res.string.practice_listen_note),
        actions.onListen,
        style = EolssuButtonStyle.Secondary,
        icon = if (listening) EolssuIcons.Pause else EolssuIcons.Ear,
        contentColor = if (listening) colors.play else null,
        borderColor = if (listening) colors.play else null,
    )
}

@Composable
private fun LegendItem(label: String, fill: Float) {
    val colors = EolssuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.size(12.dp)) {
            val r = size.minDimension / 2
            if (fill > 0f) {
                drawArc(colors.text, startAngle = 90f, sweepAngle = 360f * fill, useCenter = true, size = size)
            }
            drawCircle(colors.text, radius = r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        }
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
    }
}

/**
 * 대금 윤곽(취구·청공·지공 6·칠성공). 어떤 구멍을 막는지는 실제 자료가 붙기 전까지 그리지 않는다(와이어프레임 `fluteFigure`).
 */
@Composable
private fun FluteOutline() {
    val colors = EolssuTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, color = colors.textMuted)
    val mouth = stringResource(Res.string.practice_flute_mouth)
    val membrane = stringResource(Res.string.practice_flute_membrane)
    val holes = stringResource(Res.string.practice_flute_holes)
    val end = stringResource(Res.string.practice_flute_end)
    Canvas(Modifier.fillMaxWidth().heightIn(max = 120.dp).height(70.dp)) {
        // 340×70 좌표계를 가로에 맞춰 늘린다.
        val s = size.width / 340f
        val dash = PathEffect.dashPathEffect(floatArrayOf(3 * s, 2 * s))
        fun label(text: String, x: Float, y: Float) {
            val layout = measurer.measure(text, labelStyle)
            drawText(layout, topLeft = Offset(x * s - layout.size.width / 2f, y * s - layout.size.height + 2 * s))
        }
        drawRoundRect(Color.White, Offset(10 * s, 16 * s), Size(320 * s, 28 * s), CornerRadius(14 * s))
        drawRoundRect(colors.lineStrong, Offset(10 * s, 16 * s), Size(320 * s, 28 * s), CornerRadius(14 * s), style = Stroke(1.5f * s))
        drawOval(colors.surfaceStrong, Offset(41 * s, 24 * s), Size(18 * s, 12 * s))
        drawOval(colors.textMuted, Offset(41 * s, 24 * s), Size(18 * s, 12 * s), style = Stroke(1.5f * s))
        label(mouth, 50f, 58f)
        drawOval(Color.White, Offset(92 * s, 25 * s), Size(16 * s, 10 * s))
        drawOval(colors.lineStrong, Offset(92 * s, 25 * s), Size(16 * s, 10 * s), style = Stroke(1.5f * s, pathEffect = dash))
        label(membrane, 100f, 58f)
        listOf(150f, 175f, 200f, 235f, 260f, 285f).forEachIndexed { i, x ->
            drawCircle(Color.White, 7 * s, Offset(x * s, 30 * s))
            drawCircle(colors.lineStrong, 7 * s, Offset(x * s, 30 * s), style = Stroke(1.5f * s, pathEffect = dash))
            label("${i + 1}", x, 58f)
        }
        drawCircle(Color.White, 4 * s, Offset(316 * s, 30 * s))
        drawCircle(colors.lineStrong, 4 * s, Offset(316 * s, 30 * s), style = Stroke(1.5f * s, pathEffect = dash))
        label(end, 316f, 58f)
        label(holes, 218f, 12f)
    }
}

@Composable
private fun JangdanPanel(state: PracticeUiState, song: Song, actions: PracticePanelActions) {
    val colors = EolssuTheme.colors
    val jangdan = song.jangdan
    val draft = state.draft
    val starts = jangdan.daegangStarts
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(jangdanLabel(jangdan), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), softWrap = false)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            val sub = if (jangdan.subdivision > 1) " " + stringResource(Res.string.practice_jangdan_sub, jangdan.subdivision) else ""
            Text(
                stringResource(Res.string.practice_jangdan_meta, jangdan.gak, jangdan.daegang.joinToString("·"), sub, state.bpm),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
                textAlign = TextAlign.End,
            )
            jangdan.aliases?.let {
                Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted, textAlign = TextAlign.End)
            }
        }
    }
    val perRow = minOf(jangdan.gak, 12)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until jangdan.gak).chunked(perRow).forEach { rowCells ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowCells.forEach { i ->
                    val strong = i in starts
                    val on = draft.previewing && draft.previewBeat == i
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                            .background(
                                when {
                                    on -> colors.accentSoft
                                    strong -> colors.surface
                                    else -> colors.background
                                },
                                RoundedCornerShape(8.dp),
                            )
                            .border(
                                1.dp,
                                when {
                                    on -> colors.accent
                                    strong -> colors.lineStrong
                                    else -> colors.line
                                },
                                RoundedCornerShape(8.dp),
                            )
                            .padding(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        if (strong) Text("${starts.indexOf(i) + 1}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        val gueum = jangdan.gueum
                        when {
                            gueum != null && gueum[i].isNotEmpty() ->
                                Text(gueum[i], style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = colors.textMuted, maxLines = 1)
                            gueum == null && strong ->
                                Text(stringResource(Res.string.practice_jangdan_gueum_placeholder), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.textMuted, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
    Text(
        stringResource(if (jangdan.gueum != null) Res.string.practice_jangdan_gueum_note else Res.string.practice_jangdan_no_gueum),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        color = colors.textMuted,
    )
    EolssuButton(
        stringResource(if (draft.previewing) Res.string.practice_jangdan_preview_stop else Res.string.practice_jangdan_preview_start),
        actions.onTogglePreview,
        style = EolssuButtonStyle.Secondary,
        icon = if (draft.previewing) EolssuIcons.Stop else EolssuIcons.Play,
        contentColor = if (draft.previewing) colors.play else null,
        borderColor = if (draft.previewing) colors.play else null,
    )
    ToggleRow(
        stringResource(Res.string.practice_jangdan_sound), draft.jangdanSound, { actions.onDraft { it.copy(jangdanSound = !it.jangdanSound) } },
        description = stringResource(Res.string.practice_jangdan_sound_desc),
    )
    ToggleRow(
        stringResource(Res.string.practice_jangdan_beat), draft.beatVisual, { actions.onDraft { it.copy(beatVisual = !it.beatVisual) } },
        description = stringResource(Res.string.practice_jangdan_beat_desc),
    )
    if (draft.jangdanSound) {
        Row(
            Modifier.fillMaxWidth().background(colors.accentSoft, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(EolssuIcons.Headphone, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(16.dp), tint = colors.text)
            Text(stringResource(Res.string.practice_jangdan_mic_notice), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp))
        }
    }
}

@Composable
private fun TempoPanel(draft: PracticeDraft, song: Song, actions: PracticePanelActions) {
    val colors = EolssuTheme.colors
    val step = draft.step
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tempoLabel(step), style = EolssuTheme.serif.headline.copy(fontSize = 26.sp, lineHeight = 31.sp))
        Text(
            stringResource(Res.string.practice_tempo_sub, step.bpm(song.baseBpm), song.baseBpm, "${(step.ratio * 100).roundToInt()}%"),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.textMuted,
        )
    }
    TempoSlider(step) { next -> actions.onDraft { it.copy(step = next) } }
    Text(stringResource(Res.string.practice_tempo_note), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
}

/** 5칸 중 한 곳에만 멈추는 일자 슬라이더. 손잡이를 끌거나 아래 이름을 눌러 고른다. */
@Composable
private fun TempoSlider(step: TempoStep, onChange: (TempoStep) -> Unit) {
    val colors = EolssuTheme.colors
    val steps = TempoStep.entries
    val stateLabel = tempoLabel(step)
    val tempoTitle = stringResource(Res.string.practice_tempo_title)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 4.dp)) {
        val thumb = 22.dp
        val trackWidth = maxWidth - thumb
        fun stepAt(x: Float, widthPx: Float, thumbPx: Float): TempoStep {
            val ratio = ((x - thumbPx / 2) / (widthPx - thumbPx)).coerceIn(0f, 1f)
            return steps[(ratio * (steps.size - 1)).roundToInt()]
        }
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(thumb)
                    .semantics {
                        contentDescription = tempoTitle
                        stateDescription = stateLabel
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { onChange(stepAt(it.x, size.width.toFloat(), thumb.toPx())) }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ -> onChange(stepAt(change.position.x, size.width.toFloat(), thumb.toPx())) }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                val pct = step.ordinal / (steps.size - 1).toFloat()
                Box(Modifier.padding(horizontal = thumb / 2).fillMaxWidth().height(4.dp).background(colors.surfaceStrong, RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(pct).height(4.dp).background(colors.text, RoundedCornerShape(2.dp)))
                }
                Box(
                    Modifier
                        .offset(x = trackWidth * pct)
                        .size(thumb)
                        .shadow(2.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .padding(4.dp)
                        .background(colors.text, CircleShape),
                )
            }
            Box(Modifier.fillMaxWidth().height(34.dp).padding(top = 6.dp)) {
                steps.forEach { s ->
                    val pct = s.ordinal / (steps.size - 1).toFloat()
                    val on = s == step
                    Text(
                        tempoLabel(s),
                        modifier = Modifier
                            .offset(x = thumb / 2 + trackWidth * pct - 32.dp)
                            .width(64.dp)
                            .clickable { onChange(s) }
                            .padding(vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                        color = if (on) colors.text else colors.textMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

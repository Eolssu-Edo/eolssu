package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.HoleState
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuBottomSheet
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIconButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuSwitch
import com.ssafy.eolssu.core.ui.component.SheetHeader
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.core.ui.score.FingeringFigure
import com.ssafy.eolssu.core.ui.score.HoleLegend
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.ScoreNotation
import com.ssafy.eolssu.core.ui.score.YulGlyph
import com.ssafy.eolssu.feature.practice.PracticeDraft
import com.ssafy.eolssu.feature.practice.PracticePanel
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.TempoStep
import eolssu.shared.generated.resources.jangdan_source
import com.ssafy.eolssu.feature.practice.JangdanStatus
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_apply
import eolssu.shared.generated.resources.practice_fingering_desc
import eolssu.shared.generated.resources.practice_fingering_instrument
import eolssu.shared.generated.resources.practice_fingering_missing
import eolssu.shared.generated.resources.practice_fingering_name
import eolssu.shared.generated.resources.practice_fingering_next
import eolssu.shared.generated.resources.practice_fingering_none
import eolssu.shared.generated.resources.practice_fingering_prev
import eolssu.shared.generated.resources.practice_fingering_rest
import eolssu.shared.generated.resources.practice_fingering_title
import eolssu.shared.generated.resources.practice_fingering_view
import eolssu.shared.generated.resources.practice_fingering_view_desc
import eolssu.shared.generated.resources.practice_instrument
import eolssu.shared.generated.resources.practice_jangdan_beat
import eolssu.shared.generated.resources.practice_jangdan_beat_desc
import eolssu.shared.generated.resources.practice_jangdan_gueum_note
import eolssu.shared.generated.resources.practice_jangdan_meta
import eolssu.shared.generated.resources.practice_jangdan_pattern_meta
import eolssu.shared.generated.resources.practice_jangdan_mic_notice
import eolssu.shared.generated.resources.practice_jangdan_no_gueum
import eolssu.shared.generated.resources.practice_jangdan_preview_start
import eolssu.shared.generated.resources.practice_jangdan_preview_stop
import eolssu.shared.generated.resources.practice_jangdan_sound
import eolssu.shared.generated.resources.practice_jangdan_sound_desc
import eolssu.shared.generated.resources.practice_jangdan_sound_off
import eolssu.shared.generated.resources.practice_jangdan_sound_on
import eolssu.shared.generated.resources.practice_jangdan_sub
import eolssu.shared.generated.resources.practice_jangdan_title
import eolssu.shared.generated.resources.practice_jangdan_unavailable
import eolssu.shared.generated.resources.practice_listen_note
import eolssu.shared.generated.resources.practice_listening
import eolssu.shared.generated.resources.practice_menu_jangdan
import eolssu.shared.generated.resources.practice_menu_notation
import eolssu.shared.generated.resources.practice_menu_tempo
import eolssu.shared.generated.resources.practice_notation_example
import eolssu.shared.generated.resources.practice_notation_hangul
import eolssu.shared.generated.resources.practice_notation_hangul_desc
import eolssu.shared.generated.resources.practice_notation_solfege
import eolssu.shared.generated.resources.practice_notation_solfege_desc
import eolssu.shared.generated.resources.practice_notation_solfege_none
import eolssu.shared.generated.resources.practice_notation_title
import eolssu.shared.generated.resources.practice_off
import eolssu.shared.generated.resources.practice_on
import eolssu.shared.generated.resources.practice_settings
import eolssu.shared.generated.resources.practice_settings_locked
import eolssu.shared.generated.resources.practice_tempo_note
import eolssu.shared.generated.resources.practice_tempo_sub
import eolssu.shared.generated.resources.practice_tempo_title
import org.jetbrains.compose.resources.stringResource

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
    /** 연습 설정의 ‘연습 악기’(`GuideInstrument.id`). */
    val onSetInstrument: (String) -> Unit = {},
    /** 연습 설정의 ‘운지법과 함께 보기’. 바로 반영된다. */
    val onToggleFingeringView: () -> Unit = {},
)

/**
 * 휴대폰·태블릿 세로에서는 아래에서 올라오는 시트(보드 `.sheet`)로 보여 준다.
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
    // 표기·장단·속도 패널을 닫으면 연습 설정 시트로 돌아온다(보드 `close`). 시트는 패널마다 새로 만들어 닫힘 애니메이션 뒤에도 다시 올라온다.
    key(panel) {
        EolssuBottomSheet(
            onDismiss = actions.onClose,
            modifier = modifier,
            maxHeightFraction = when {
                tablet -> if (settings) 0.45f else 0.6f
                settings -> 0.62f
                else -> 0.82f
            },
            header = { PanelHeader(panel, state, actions.onClose) },
            footer = apply?.let { { EolssuButton(stringResource(Res.string.practice_apply), it) } },
        ) {
            PanelBody(panel, state, song, actions)
        }
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
        PanelHeader(panel, state, actions.onClose)
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
fun PanelHeader(panel: PracticePanel, state: PracticeUiState, onClose: () -> Unit) {
    when (panel) {
        PracticePanel.Settings -> SheetHeader(stringResource(Res.string.practice_settings), onClose)
        PracticePanel.Notation -> SheetHeader(stringResource(Res.string.practice_notation_title), onClose)
        PracticePanel.Fingering -> SheetHeader(
            stringResource(Res.string.practice_fingering_title), onClose,
            subtitle = stringResource(Res.string.practice_fingering_instrument, stringResource(state.instrument.label)),
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

/**
 * PANEL-00 연습 설정: ‘연습 악기’ 고르기 → 쉬운 표기 · 장단 · 속도 메뉴 줄(`.menu-row`) → ‘운지법과 함께 보기’ 스위치(`.trow`).
 */
@Composable
private fun SettingsMenu(state: PracticeUiState, song: Song, actions: PracticePanelActions) {
    val settings = state.settings
    val notationOn = settings.showHangul || settings.showSolfege
    val step = TempoStep.nearest(song.baseBpm, state.bpm)
    val instrumentName = stringResource(state.instrument.label)
    // 평가 연주 중에는 채점 속도와 녹음에 닿는 장단·속도만 잠근다.
    val locked = state.playbackSettingsLocked
    val lockedStatus = stringResource(Res.string.practice_settings_locked)
    Column {
        InstrumentSelect(state.instrument, actions.onSetInstrument)
        MenuRow(
            EolssuIcons.NotationMark, stringResource(Res.string.practice_menu_notation),
            stringResource(if (notationOn) Res.string.practice_on else Res.string.practice_off),
        ) { actions.onOpen(PracticePanel.Notation) }
        MenuRow(
            EolssuIcons.JangdanMark, stringResource(Res.string.practice_menu_jangdan),
            when {
                locked -> lockedStatus
                state.jangdanProblem -> jangdanHint(state)
                else -> stringResource(if (settings.jangdanSound) Res.string.practice_jangdan_sound_on else Res.string.practice_jangdan_sound_off, song.jangdanName)
            },
            enabled = !locked,
        ) { actions.onOpen(PracticePanel.Jangdan) }
        MenuRow(EolssuIcons.TempoMark, stringResource(Res.string.practice_menu_tempo), if (locked) lockedStatus else tempoLabel(step), enabled = !locked) {
            actions.onOpen(PracticePanel.Tempo)
        }
        SettingRow(
            stringResource(Res.string.practice_fingering_view),
            stringResource(Res.string.practice_fingering_view_desc, instrumentName),
            checked = settings.fingeringView,
            onToggle = actions.onToggleFingeringView,
        )
    }
}

/** ‘연습 악기’(`.instrument-select`): 13sp 라벨과 오른쪽 고르기 상자(38dp, 옅은 테두리, 반지름 8, 13sp 500). */
@Composable
private fun InstrumentSelect(selected: GuideInstrument, onSelect: (String) -> Unit) {
    val colors = EolssuTheme.colors
    var open by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.practice_instrument)
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = colors.text)
        Box {
            Row(
                Modifier
                    .heightIn(min = 38.dp)
                    .border(1.dp, colors.line, RoundedCornerShape(8.dp))
                    .background(colors.surface, RoundedCornerShape(8.dp))
                    .clickable(role = Role.DropdownList) { open = true }
                    .padding(start = 10.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(selected.label), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = colors.text)
                Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textMuted)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
                GuideInstrument.entries.forEach { instrument ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(instrument.label),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = if (instrument == selected) FontWeight.SemiBold else FontWeight.Normal),
                            )
                        },
                        onClick = {
                            open = false
                            onSelect(instrument.id)
                        },
                    )
                }
            }
        }
    }
}

/** 메뉴 줄(`.menu-row`, 56dp): 위 선, 36dp 흰 네모 아이콘, 15sp 600 이름, 13sp 상태, 꺾쇠. [enabled]가 아니면 흐리고 누를 수 없다. */
@Composable
private fun MenuRow(icon: ImageVector, label: String, status: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .drawBehind { drawLine(colors.surfaceStrong, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).background(colors.surface, RoundedCornerShape(10.dp)).border(1.dp, colors.lineSoft, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.text)
        }
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
        Text(status, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.textMuted)
        Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.textDisabled)
    }
}

/**
 * 스위치 줄(`.trow`): 위 선(`#EEEBE3`), 위아래 12dp, 15sp 600 이름과 13sp 설명, 오른쪽 50×30 스위치.
 * [enabled]가 아니면 줄을 흐리게 하고 스위치를 끈다(`.trow.off`).
 */
@Composable
private fun SettingRow(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
    topLine: Boolean = true,
    vertical: Dp = 12.dp,
) {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f)
            .then(if (topLine) Modifier.drawBehind { drawLine(colors.surfaceStrong, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else Modifier)
            .padding(top = if (topLine) vertical else 0.dp, bottom = vertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
            Text(description, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = colors.textMuted)
        }
        EolssuSwitch(checked, onToggle, label, enabled = enabled)
    }
}

/** 작은 안내 글(`.tiny.muted`, 12sp). */
@Composable
private fun TinyNote(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp), color = EolssuTheme.colors.textMuted)
}

/** 흰 카드(`.card`): 옅은 테두리, 반지름 16. */
@Composable
private fun PanelCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth().background(colors.surface, shape).border(1.dp, colors.lineSoft, shape).padding(padding), content = content)
}

/**
 * PANEL-01 악보 표시 설정: 한글 읽는 법 · 계이름 스위치 줄과, 고른 설정을 보여 주는 미니 정간보 카드.
 */
@Composable
private fun NotationPanel(draft: PracticeDraft, song: Song, actions: PracticePanelActions) {
    val hasSolfege = song.solfege != null
    val colors = EolssuTheme.colors
    SettingRow(
        stringResource(Res.string.practice_notation_hangul), stringResource(Res.string.practice_notation_hangul_desc),
        draft.showHangul, { actions.onDraft { it.copy(showHangul = !it.showHangul) } }, topLine = false,
    )
    SettingRow(
        stringResource(Res.string.practice_notation_solfege),
        stringResource(if (hasSolfege) Res.string.practice_notation_solfege_desc else Res.string.practice_notation_solfege_none),
        hasSolfege && draft.showSolfege, { actions.onDraft { it.copy(showSolfege = !it.showSolfege) } }, enabled = hasSolfege,
    )
    Row(
        Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.lineSoft, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        JeongganboView(
            song = song,
            modifier = Modifier.width(90.dp),
            metrics = JeongganboMetrics(columnWidth = 86.dp, cellHeight = 44.dp, yulSize = 17.sp, auxSize = 9.sp, headHeight = 22.dp, columnGap = 0.dp, splitScaleTwo = 11f / 17f),
            notation = ScoreNotation(draft.showHangul, hasSolfege && draft.showSolfege),
            scrollState = null,
            maxColumns = 1,
            maxRows = 3,
            look = JeongganboLook(style = JeongganboLook.Style.Board, lyricLane = 19.dp, headCompact = true, daegangWhenFits = true),
        )
        Text(
            stringResource(Res.string.practice_notation_example),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = colors.textMuted,
        )
    }
}

/**
 * PANEL-03 운지법: 이전·다음 음(악보의 앞뒤 정간) 사이 36sp 율명과 읽는 법, 악기 사진 위 구멍 표시와 범례, ‘음 듣기’, 막는 구멍·출처.
 */
@Composable
private fun FingeringPanel(state: PracticeUiState, actions: PracticePanelActions) {
    val colors = EolssuTheme.colors
    val yul = state.draft.note
    val rest = state.draft.noteRest
    val instrument = state.instrument
    val note = if (rest) null else instrument.noteOf(yul.symbol)
    val holes = note?.holes ?: emptyList()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EolssuIconButton(EolssuIcons.Back, stringResource(Res.string.practice_fingering_prev), { actions.onMoveFingering(-1) }, bordered = false, enabled = state.selected > 0)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (rest) {
                Text("△", style = EolssuTheme.serif.yul.copy(fontSize = 36.sp, lineHeight = 40.sp), color = colors.textSub)
            } else {
                YulGlyph(yul, 36.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                if (rest) stringResource(Res.string.practice_fingering_rest) else stringResource(Res.string.practice_fingering_name, yul.fullName, yul.hangul),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = colors.textMuted,
            )
        }
        EolssuIconButton(
            EolssuIcons.Next, stringResource(Res.string.practice_fingering_next), { actions.onMoveFingering(1) },
            bordered = false, enabled = state.selected < state.eventCount - 1,
        )
    }
    PanelCard(padding = 10.dp) {
        FingeringFigure(instrument, holes, Modifier.fillMaxWidth(), description = stringResource(instrument.label))
        HoleLegend(Modifier.fillMaxWidth().padding(top = 8.dp))
    }
    val listening = state.listening == yul && !rest
    EolssuButton(
        stringResource(if (listening) Res.string.practice_listening else Res.string.practice_listen_note),
        actions.onListen,
        style = EolssuButtonStyle.Secondary,
        icon = if (listening) EolssuIcons.Pause else EolssuIcons.Speaker,
        enabled = !rest,
        contentColor = if (listening) colors.play else null,
        borderColor = if (listening) colors.play else null,
    )
    if (!rest) {
        TinyNote(
            if (note != null) {
                val closed = note.holes.mapIndexedNotNull { i, h -> if (h == HoleState.Closed) holeName(instrument, i) else null }
                stringResource(Res.string.practice_fingering_desc, closed.joinToString("·").ifEmpty { stringResource(Res.string.practice_fingering_none) }, instrument.source)
            } else {
                stringResource(Res.string.practice_fingering_missing, yul.fullName)
            },
        )
    }
}

/** 구멍 번호. 단소의 첫 항목은 뒷구멍이다(보드 `fcClosed`). */
private fun holeName(instrument: GuideInstrument, index: Int): String =
    if (instrument == GuideInstrument.Danso) (if (index == 0) "뒷" else "$index") else "${index + 1}"

/**
 * PANEL-04 장단: 장단 이름과 ‘한 각 N정간 · 대강 … · BPM’, 정간 칸(대강 첫 칸은 번호·구음, 미리듣기 중인 칸은 연두), 구음 안내,
 * ‘장단 듣기’, 장단 소리·현재 박 스위치, 헤드폰 안내 카드.
 */
@Composable
private fun JangdanPanel(state: PracticeUiState, song: Song, actions: PracticePanelActions) {
    val colors = EolssuTheme.colors
    val jangdan = song.displayJangdan
    if (jangdan == null) {
        Text(jangdanLabel(song.jangdanName), style = MaterialTheme.typography.titleMedium)
        TinyNote(jangdanHint(state))
        return
    }
    val draft = state.draft
    val starts = jangdan.daegangStarts
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(jangdanLabel(jangdan), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
        val sub = if (jangdan.subdivision > 1) " " + stringResource(Res.string.practice_jangdan_sub, jangdan.subdivision) else ""
        Text(
            stringResource(if (song.score.timing == null) Res.string.practice_jangdan_meta else Res.string.practice_jangdan_pattern_meta,
                jangdan.gak, jangdan.daegang.joinToString("·"), sub, state.bpm),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.textMuted,
        )
    }
    TinyNote(jangdanHint(state))
    TinyNote(stringResource(Res.string.jangdan_source))
    val perRow = minOf(jangdan.gak, 12)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        (0 until jangdan.gak).chunked(perRow).forEach { rowCells ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                rowCells.forEach { i ->
                    val strong = i in starts
                    val on = draft.previewing && draft.previewBeat == i
                    Column(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(if (on) colors.accent else colors.surface, RoundedCornerShape(6.dp))
                            .border(if (strong) 2.dp else 1.dp, if (strong) colors.text else colors.line, RoundedCornerShape(6.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (strong) Text("${starts.indexOf(i) + 1}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold), color = colors.text)
                        val gueum = jangdan.gueum?.getOrNull(i).orEmpty()
                        if (gueum.isNotEmpty()) Text(gueum, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp), color = colors.text, maxLines = 1)
                    }
                }
            }
        }
    }
    TinyNote(stringResource(if (jangdan.gueum != null) Res.string.practice_jangdan_gueum_note else Res.string.practice_jangdan_no_gueum))
    EolssuButton(
        stringResource(if (draft.previewing) Res.string.practice_jangdan_preview_stop else Res.string.practice_jangdan_preview_start),
        actions.onTogglePreview,
        enabled = state.jangdanSupported && state.jangdanStatus != JangdanStatus.Loading && !state.playbackSettingsLocked,
        style = EolssuButtonStyle.Secondary,
        icon = if (draft.previewing) EolssuIcons.Stop else EolssuIcons.Play,
        contentColor = if (draft.previewing) colors.play else null,
        borderColor = if (draft.previewing) colors.play else null,
    )
    Column {
        SettingRow(
            stringResource(Res.string.practice_jangdan_sound), stringResource(Res.string.practice_jangdan_sound_desc),
            draft.jangdanSound && state.jangdanSupported, { actions.onDraft { it.copy(jangdanSound = !it.jangdanSound) } }, enabled = state.jangdanSupported && !state.playbackSettingsLocked, vertical = 10.dp,
        )
        SettingRow(
            stringResource(Res.string.practice_jangdan_beat), stringResource(Res.string.practice_jangdan_beat_desc),
            draft.beatVisual, { actions.onDraft { it.copy(beatVisual = !it.beatVisual) } }, vertical = 10.dp,
        )
    }
    Row(
        Modifier.fillMaxWidth().background(colors.accentTint, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(EolssuIcons.Headphone, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(18.dp), tint = colors.text)
        Text(stringResource(Res.string.practice_jangdan_mic_notice), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp), color = colors.text)
    }
}

/** PANEL-05 연습 속도: Song Myung 30sp 단계 이름, ‘N BPM · 기준 N BPM’, 다섯 단계 슬라이더, 안내. */
@Composable
private fun TempoPanel(draft: PracticeDraft, song: Song, actions: PracticePanelActions) {
    val colors = EolssuTheme.colors
    val step = draft.step
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tempoLabel(step), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal), color = colors.text)
        Text(
            stringResource(Res.string.practice_tempo_sub, step.bpm(song.baseBpm), song.baseBpm),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
    TempoSlider(step, onStep = { s -> actions.onDraft { it.copy(step = s) } })
    TinyNote(stringResource(Res.string.practice_tempo_note))
}

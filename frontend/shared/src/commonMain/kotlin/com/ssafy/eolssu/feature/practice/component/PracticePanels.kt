package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.ui.component.InstrumentMenuItem
import com.ssafy.eolssu.core.ui.component.InstrumentPicture
import com.ssafy.eolssu.core.ui.component.EolssuMenuShape
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.HoleState
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIconButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuSwitch
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.core.ui.score.FingeringFigure
import com.ssafy.eolssu.core.ui.score.HoleLegend
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.ScoreNotation
import com.ssafy.eolssu.core.ui.score.ScoreNoteGlyph
import com.ssafy.eolssu.feature.practice.PracticeDraft
import com.ssafy.eolssu.feature.practice.PracticePanel
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.TempoStep
import com.ssafy.eolssu.feature.practice.JangdanStatus
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_back
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
import eolssu.shared.generated.resources.practice_jangdan_meta
import eolssu.shared.generated.resources.practice_jangdan_pattern_meta
import eolssu.shared.generated.resources.practice_jangdan_preview_start
import eolssu.shared.generated.resources.practice_jangdan_preview_stop
import eolssu.shared.generated.resources.practice_jangdan_sound
import eolssu.shared.generated.resources.practice_jangdan_sound_off
import eolssu.shared.generated.resources.practice_jangdan_sound_on
import eolssu.shared.generated.resources.practice_jangdan_sub
import eolssu.shared.generated.resources.practice_jangdan_title
import eolssu.shared.generated.resources.practice_listen_note
import eolssu.shared.generated.resources.practice_menu_jangdan
import eolssu.shared.generated.resources.practice_menu_notation
import eolssu.shared.generated.resources.practice_menu_tempo
import eolssu.shared.generated.resources.practice_notation_hangul
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
    val onDismiss: () -> Unit,
    val onOpen: (PracticePanel) -> Unit,
    val onDraft: ((PracticeDraft) -> PracticeDraft) -> Unit,
    val onToggleBigScore: () -> Unit,
    val onMoveFingering: (Int) -> Unit,
    val fingeringAudio: @Composable (GuideInstrument, FingeringNote) -> Unit,
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
    titleToggles: Boolean = false,
) {
    val panel = state.panel ?: return
    val song = state.song ?: return
    val settings = panel == PracticePanel.Settings
    // 배경 누르기와 상위로 뒤로가기를 분리한다. 모달 전체 배경이 닫기 입력을 받는다.
    Dialog(onDismissRequest = actions.onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val maxSheetHeight = maxHeight * when {
                tablet -> if (settings) 0.45f else 0.6f
                settings -> 0.62f
                else -> 0.82f
            }
            Box(Modifier.fillMaxSize().clickable(onClick = actions.onDismiss))
            Surface(
                modifier = modifier.align(Alignment.BottomCenter).widthIn(max = 640.dp).fillMaxWidth()
                    .heightIn(max = maxSheetHeight),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                // 연습 설정과 하위 화면(쉬운 표기·장단·속도·운지법) 모두 회색 시트 위 흰 카드다(S15P31E102-768).
                color = EolssuTheme.colors.groupedBackground,
            ) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)) {
                    PanelHeader(panel, state, actions.onClose)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PanelBody(panel, state, song, actions, titleToggles)
                    }
                }
            }
        }
    }
}

/** X 대신 하위 설정의 뒤로가기만 둔다. 배경 닫기는 시트의 onDismiss가 처리한다. */
@Composable
fun PanelHeader(panel: PracticePanel, state: PracticeUiState, onClose: () -> Unit) {
    val title = stringResource(when (panel) {
        PracticePanel.Settings -> Res.string.practice_settings
        PracticePanel.Notation -> Res.string.practice_notation_title
        PracticePanel.Fingering -> Res.string.practice_fingering_title
        PracticePanel.Jangdan -> Res.string.practice_jangdan_title
        PracticePanel.Tempo -> Res.string.practice_tempo_title
    })
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (panel != PracticePanel.Settings) {
            EolssuIconButton(EolssuIcons.Back, stringResource(Res.string.common_back), onClose, bordered = false)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
            if (panel == PracticePanel.Fingering) Text(
                stringResource(Res.string.practice_fingering_instrument, stringResource(state.instrument.label)),
                style = MaterialTheme.typography.bodySmall, color = EolssuTheme.colors.textMuted,
            )
        }
    }
}

@Composable
fun ColumnScope.PanelBody(panel: PracticePanel, state: PracticeUiState, song: Song, actions: PracticePanelActions, titleToggles: Boolean = false) {
    when (panel) {
        PracticePanel.Settings -> SettingsMenu(state, song, actions, titleToggles)
        PracticePanel.Notation -> NotationPanel(state.draft, song, actions)
        PracticePanel.Fingering -> FingeringPanel(state, actions)
        PracticePanel.Jangdan -> JangdanPanel(state, song, actions)
        PracticePanel.Tempo -> TempoPanel(state.draft, song, actions)
    }
}

/**
 * PANEL-00 연습 설정: ‘연습 악기’ 고르기 → 쉬운 표기 · 장단 · 속도 메뉴 줄(`.menu-row`) → ‘운지법과 함께 보기’ 스위치(`.trow`).
 * AI 연주 평가로 들어온 연습([PracticeUiState.solo]가 아님)은 고른 곡의 악기로 평가하므로 ‘연습 악기’를 두지 않는다(S15P31E102-561).
 * [titleToggles]이면 쉬운 표기·운지법이 곡 제목 옆 스위치에 있으므로 여기서 뺀다(S15P31E102-716).
 */
@Composable
private fun SettingsMenu(state: PracticeUiState, song: Song, actions: PracticePanelActions, titleToggles: Boolean) {
    val settings = state.settings
    val notationOn = settings.showHangul
    val step = TempoStep.nearest(song.baseBpm, state.bpm)
    val instrumentName = stringResource(state.instrument.label)
    // 평가 중에는 속도만 고정하고 장단 소리는 바꿀 수 있다.
    val locked = state.playbackSettingsLocked
    val lockedStatus = stringResource(Res.string.practice_settings_locked)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.solo && !state.learn) SettingsCard { InstrumentSelect(state.instrument, actions.onSetInstrument) }
        SettingsCard {
        if (!titleToggles) MenuRow(
            EolssuIcons.NotationMark, stringResource(Res.string.practice_menu_notation),
            stringResource(if (notationOn) Res.string.practice_on else Res.string.practice_off),
        ) { actions.onOpen(PracticePanel.Notation) }
        MenuRow(
            EolssuIcons.JangdanMark, stringResource(Res.string.practice_menu_jangdan),
            when {
                state.jangdanProblem -> jangdanHint(state)
                else -> stringResource(if (settings.jangdanSound) Res.string.practice_jangdan_sound_on else Res.string.practice_jangdan_sound_off, song.jangdanName)
            },
            // 장단 연결이 검수 중인 악보는 장단 메뉴를 아예 열지 않는다(S15P31E102-768).
            enabled = song.jangdanPlayback != null,
        ) { actions.onOpen(PracticePanel.Jangdan) }
        MenuRow(EolssuIcons.TempoMark, stringResource(Res.string.practice_menu_tempo), if (locked) lockedStatus else tempoLabel(step), enabled = !locked) {
            actions.onOpen(PracticePanel.Tempo)
        }
        }
        if (!titleToggles) SettingsCard {
            SettingRow(
                stringResource(Res.string.practice_fingering_view),
                stringResource(Res.string.practice_fingering_view_desc, instrumentName),
                checked = state.fingeringView,
                onToggle = actions.onToggleFingeringView,
                topLine = false,
                horizontal = SettingsCardInset,
                topPadding = 12.dp,
            )
        }
    }
}

/** 연습 설정 시트의 카드 안쪽 좌우 여백. */
private val SettingsCardInset: Dp = 16.dp

/** 연습 설정 묶음 카드: 회색 시트 위 테두리 없는 흰 카드 · 모서리 20dp(마이페이지 카드와 같은 모양, S15P31E102-768). */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(EolssuTheme.colors.surface, RoundedCornerShape(20.dp)).padding(vertical = 4.dp), content = content)
}

/** ‘연습 악기’: 15sp 600 라벨과 오른쪽 고르기 알약(38dp, 테두리 없는 회색 바탕, 악기 그림 28dp + 13sp 500). 목록에도 악기 그림을 둔다(S15P31E102-831). */
@Composable
private fun InstrumentSelect(selected: GuideInstrument, onSelect: (String) -> Unit) {
    val colors = EolssuTheme.colors
    var open by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.practice_instrument)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = SettingsCardInset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
        Box {
            Row(
                Modifier
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.groupedBackground)
                    .clickable(role = Role.DropdownList) { open = true }
                    .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InstrumentPicture(selected)
                Text(stringResource(selected.label), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = colors.text)
                Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textMuted)
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                shape = EolssuMenuShape,
                containerColor = colors.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.width(168.dp).padding(horizontal = 6.dp),
            ) {
                GuideInstrument.entries.forEach { instrument ->
                    InstrumentMenuItem(instrument, instrument == selected, onClick = {
                        open = false
                        onSelect(instrument.id)
                    })
                }
            }
        }
    }
}

/** 메뉴 줄(56dp): 흰 카드 안에서 선 없이, 36dp 회색 네모 아이콘, 15sp 600 이름, 13sp 상태, 꺾쇠. [enabled]가 아니면 흐리고 누를 수 없다. */
@Composable
private fun MenuRow(icon: ImageVector, label: String, status: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = SettingsCardInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).background(colors.groupedBackground, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.text)
        }
        // 이름은 줄바꿈하지 않고, 긴 상태 문구가 남은 폭 안에서 줄을 바꾼다.
        Text(label, softWrap = false, maxLines = 1, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
        Text(
            status,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.textMuted,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
    description: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
    topLine: Boolean = true,
    vertical: Dp = 12.dp,
    horizontal: Dp = 0.dp,
    topPadding: Dp? = null,
) {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f)
            .then(if (topLine) Modifier.drawBehind { drawLine(colors.surfaceStrong, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else Modifier)
            .padding(top = topPadding ?: if (topLine) vertical else 0.dp, bottom = vertical)
            .padding(horizontal = horizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = colors.textMuted)
            }
        }
        EolssuSwitch(checked, onToggle, label, enabled = enabled)
    }
}

/** 작은 안내 글(`.tiny.muted`, 12sp). */
@Composable
private fun TinyNote(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp), color = EolssuTheme.colors.textMuted)
}

/** 흰 카드: 회색 시트 위 테두리 없는 흰 바탕, 반지름 20(연습 설정 카드와 같은 모양). */
@Composable
private fun PanelCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(EolssuTheme.colors.surface, RoundedCornerShape(20.dp)).padding(padding), content = content)
}

/**
 * PANEL-01 악보 표시 설정: 한글 읽는 법 스위치 줄과, 고른 설정을 보여 주는 미니 정간보 카드.
 * 스위치 설명과 예시 설명 문장은 두지 않는다. 계이름 표시는 쓰지 않아 없앴다(S15P31E102-716).
 */
@Composable
private fun NotationPanel(draft: PracticeDraft, song: Song, actions: PracticePanelActions) {
    val colors = EolssuTheme.colors
    PanelCard(padding = 0.dp) {
        SettingRow(
            stringResource(Res.string.practice_notation_hangul), null,
            draft.showHangul, { actions.onDraft { it.copy(showHangul = !it.showHangul) } },
            topLine = false, vertical = 14.dp, horizontal = SettingsCardInset, topPadding = 14.dp,
        )
    }
    Row(
        Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(20.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        JeongganboView(
            song = song,
            modifier = Modifier.width(90.dp),
            metrics = JeongganboMetrics(columnWidth = 86.dp, cellHeight = 44.dp, yulSize = 17.sp, auxSize = 9.sp, headHeight = 22.dp, columnGap = 0.dp, splitScaleTwo = 11f / 17f),
            notation = ScoreNotation(draft.showHangul),
            scrollState = null,
            maxColumns = 1,
            maxRows = 3,
            look = JeongganboLook(style = JeongganboLook.Style.Board, lyricLane = 19.dp, headCompact = true, daegangWhenFits = true),
        )
    }
}

/**
 * PANEL-03 운지법: 이전·다음 음(악보의 앞뒤 정간) 사이 36sp 율명과 읽는 법, 악기 사진 위 구멍 표시와 범례, ‘음 듣기’, 막는 구멍.
 */
@Composable
private fun FingeringPanel(state: PracticeUiState, actions: PracticePanelActions) {
    val colors = EolssuTheme.colors
    val pitch = state.draft.note
    val instrument = state.instrument
    val note = instrument.fingeringFor(pitch)
    val name = when (pitch) {
        is ScoreNote.Pitch -> pitch.yul.fullName
        is ScoreNote.SourcePitch -> pitch.fullName
        else -> ""
    }
    val reading = note?.reading ?: when (pitch) {
        is ScoreNote.Pitch -> pitch.yul.hangul
        is ScoreNote.SourcePitch -> pitch.hangul
        else -> ""
    }
    val rest = pitch == ScoreNote.Rest
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EolssuIconButton(EolssuIcons.Back, stringResource(Res.string.practice_fingering_prev), { actions.onMoveFingering(-1) }, bordered = false, enabled = state.selected > 0)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreNoteGlyph(pitch, 36.sp, color = if (rest) colors.textSub else colors.text)
            Text(
                if (rest) stringResource(Res.string.practice_fingering_rest) else stringResource(Res.string.practice_fingering_name, name, reading),
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
        FingeringFigure(instrument, note?.holes.orEmpty(), Modifier.fillMaxWidth(), description = stringResource(instrument.label))
        HoleLegend(Modifier.fillMaxWidth().padding(top = 8.dp))
    }
    if (note != null) {
        actions.fingeringAudio(instrument, note)
    } else {
        EolssuButton(
            stringResource(Res.string.practice_listen_note), onClick = {}, enabled = false,
            style = EolssuButtonStyle.Secondary, icon = EolssuIcons.Speaker,
        )
    }
    if (!rest) {
        TinyNote(
            if (note != null) {
                val closed = note.holes.mapIndexedNotNull { i, h -> if (h == HoleState.Closed) holeName(instrument, i) else null }
                stringResource(Res.string.practice_fingering_desc, closed.joinToString("·").ifEmpty { stringResource(Res.string.practice_fingering_none) })
            } else {
                stringResource(Res.string.practice_fingering_missing, name)
            },
        )
    }
}

/** 구멍 번호. 단소의 첫 항목은 뒷구멍이다(보드 `fcClosed`). */
private fun holeName(instrument: GuideInstrument, index: Int): String =
    if (instrument == GuideInstrument.Danso) (if (index == 0) "뒷" else "$index") else "${index + 1}"

/**
 * PANEL-04 장단: 장단 이름과 ‘한 각 N정간 · 대강 … · BPM’, 정간 칸(대강 첫 칸은 번호·구음, 미리듣기 중인 칸은 연두),
 * ‘장단 듣기’, 장단 소리·현재 박 스위치.
 * 구음·속도 안내, 스위치 설명, 준비·재생 중 안내 같은 주석 문구는 두지 않고(S15P31E102-716),
 * 장단 소리를 쓸 수 없거나 준비 중인 이유만 적는다.
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
    PanelCard {
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
    if (state.jangdanProblem || song.jangdanPlayback == null || state.jangdanStatus == JangdanStatus.Loading) TinyNote(jangdanHint(state), Modifier.padding(top = 8.dp))
    val perRow = minOf(jangdan.gak, 12)
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
    EolssuButton(
        stringResource(if (draft.previewing) Res.string.practice_jangdan_preview_stop else Res.string.practice_jangdan_preview_start),
        actions.onTogglePreview,
        enabled = state.jangdanSupported && state.jangdanStatus != JangdanStatus.Loading && !state.playbackSettingsLocked,
        modifier = Modifier.padding(top = 14.dp),
        style = EolssuButtonStyle.Secondary,
        icon = if (draft.previewing) EolssuIcons.Stop else EolssuIcons.Play,
        contentColor = if (draft.previewing) colors.play else null,
        // 흰 카드 안이라 테두리 대신 회색 채움으로 누르는 자리를 보인다.
        containerColor = colors.groupedBackground,
        borderColor = if (draft.previewing) colors.play else Color.Transparent,
    )
    }
    PanelCard(padding = 0.dp) {
        SettingRow(
            stringResource(Res.string.practice_jangdan_sound), null,
            draft.jangdanSound && state.jangdanSupported, { actions.onDraft { it.copy(jangdanSound = !it.jangdanSound) } }, enabled = state.jangdanSupported,
            topLine = false, vertical = 14.dp, horizontal = SettingsCardInset, topPadding = 14.dp,
        )
        SettingRow(
            stringResource(Res.string.practice_jangdan_beat), null,
            draft.beatVisual, { actions.onDraft { it.copy(beatVisual = !it.beatVisual) } },
            topLine = false, vertical = 14.dp, horizontal = SettingsCardInset,
        )
    }

}

/** PANEL-05 연습 속도: Song Myung 30sp 단계 이름, ‘N BPM · 기준 N BPM’, 다섯 단계 슬라이더, 안내. */
@Composable
private fun TempoPanel(draft: PracticeDraft, song: Song, actions: PracticePanelActions) {
    val colors = EolssuTheme.colors
    val step = draft.step
    PanelCard {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // 다른 제목처럼 SemiBold로 둔다(S15P31E102-768).
        Text(tempoLabel(step), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 34.sp), color = colors.text)
        Text(
            stringResource(Res.string.practice_tempo_sub, step.bpm(song.baseBpm), song.baseBpm),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
    TempoSlider(step, onStep = { s -> actions.onDraft { it.copy(step = s) } })
    }
    TinyNote(stringResource(Res.string.practice_tempo_note), Modifier.padding(horizontal = 4.dp))
}

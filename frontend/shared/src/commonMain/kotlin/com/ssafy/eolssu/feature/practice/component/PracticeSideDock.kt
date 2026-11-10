package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuSwitch
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import com.ssafy.eolssu.feature.practice.PracticePanel
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.TempoStep
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_big_score
import eolssu.shared.generated.resources.practice_dock_hint
import eolssu.shared.generated.resources.practice_dock_info
import eolssu.shared.generated.resources.practice_dock_locked
import eolssu.shared.generated.resources.practice_dock_song_info
import eolssu.shared.generated.resources.practice_fingering_rest
import eolssu.shared.generated.resources.practice_fingering_title
import eolssu.shared.generated.resources.practice_jangdan_sound_on
import eolssu.shared.generated.resources.practice_menu_jangdan
import eolssu.shared.generated.resources.practice_menu_notation
import eolssu.shared.generated.resources.practice_menu_tempo
import eolssu.shared.generated.resources.practice_off
import eolssu.shared.generated.resources.practice_on
import eolssu.shared.generated.resources.practice_settings
import org.jetbrains.compose.resources.stringResource

/**
 * 태블릿 가로의 오른쪽 상태 패널(380dp). 위에 곡 정보와 연습 설정 바로가기, 아래에 상태별 버튼을 둔다.
 * 보조 패널을 열면 화면을 덮지 않고 이 자리에 연다. 연주 중에는 설정 바로가기가 잠긴다.
 */
@Composable
fun PracticeSideDock(
    state: PracticeUiState,
    panelActions: PracticePanelActions,
    bottomActions: PracticeBottomActions,
    modifier: Modifier = Modifier,
) {
    val song = state.song ?: return
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.clip(shape).background(colors.background).border(1.dp, colors.line, shape)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SongInfo(state, song)
                QuickSettings(state, song, panelActions)
            }
            Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PracticeBottomContent(state, bottomActions, stacked = true)
            }
        }
    }
}

@Composable
private fun SongInfo(state: PracticeUiState, song: Song) {
    val colors = EolssuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.practice_dock_song_info), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
        Text(state.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
        Text(
            stringResource(Res.string.practice_dock_info, jangdanLabel(song.jangdanName), state.bpm, songLevelLabel(song.level)),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.textMuted,
        )
    }
}

@Composable
private fun QuickSettings(state: PracticeUiState, song: Song, actions: PracticePanelActions) {
    val colors = EolssuTheme.colors
    val settings = state.settings
    // 평가 중 속도만 고정한다. 장단과 보기 설정은 바꿀 수 있다.
    val locked = state.playbackSettingsLocked
    val notationOn = settings.showHangul
    val noteLabel = when (val note = state.draft.note) {
        is ScoreNote.Pitch -> if (note.yul.drawnAs == null) "${note.symbol} ${note.yul.fullName}" else note.yul.fullName
        is ScoreNote.SourcePitch -> note.fullName
        else -> stringResource(Res.string.practice_fingering_rest)
    }
    Column {
        Text(
            stringResource(Res.string.practice_settings),
            modifier = Modifier.padding(bottom = 2.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textMuted,
        )
        QuickRow(
            stringResource(Res.string.practice_menu_notation),
            stringResource(if (notationOn) Res.string.practice_on else Res.string.practice_off), notationOn, locked = false,
        ) { actions.onOpen(PracticePanel.Notation) }
        QuickRow(
            stringResource(Res.string.practice_menu_jangdan),
            if (state.jangdanProblem) jangdanHint(state) else if (settings.jangdanSound) stringResource(Res.string.practice_jangdan_sound_on, song.jangdanName) else jangdanLabel(song.jangdanName),
            !state.jangdanProblem && settings.jangdanSound, locked = false,
        ) { actions.onOpen(PracticePanel.Jangdan) }
        QuickRow(
            stringResource(Res.string.practice_menu_tempo),
            tempoLabel(TempoStep.nearest(song.baseBpm, state.bpm)), state.bpm != song.baseBpm, locked,
        ) { actions.onOpen(PracticePanel.Tempo) }
        QuickRow(stringResource(Res.string.practice_fingering_title), noteLabel, on = false, locked = false) {
            actions.onOpen(PracticePanel.Fingering)
        }
        val bigLabel = stringResource(Res.string.practice_big_score)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(bigLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            EolssuSwitch(settings.bigScore, actions.onToggleBigScore, bigLabel)
        }
        Text(
            stringResource(if (locked) Res.string.practice_dock_locked else Res.string.practice_dock_hint),
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = colors.textMuted,
        )
    }
}

/** 설정 바로가기 한 줄: 이름 · 지금 값 · 화살표. 잠기면 흐리게 보이고 누를 수 없다. */
@Composable
private fun QuickRow(label: String, status: String, on: Boolean, locked: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .alpha(if (locked) 0.45f else 1f)
            .clickable(enabled = !locked, role = Role.Button, onClick = onClick)
            .drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        Text(
            status,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
            color = if (on) colors.text else colors.textMuted,
        )
        Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.lineStrong)
    }
}

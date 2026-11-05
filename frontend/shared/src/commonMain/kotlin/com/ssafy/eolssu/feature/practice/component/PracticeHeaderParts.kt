package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuPageIconButton
import com.ssafy.eolssu.core.ui.component.EolssuPageTopBar
import com.ssafy.eolssu.core.ui.component.PageHeading
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import com.ssafy.eolssu.feature.practice.PracticeMode
import com.ssafy.eolssu.feature.practice.PracticeUiState
import eolssu.shared.generated.resources.jangdan_practice_label
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_mode_countdown
import eolssu.shared.generated.resources.practice_mode_finished
import eolssu.shared.generated.resources.practice_mode_interrupted
import eolssu.shared.generated.resources.practice_mode_model
import eolssu.shared.generated.resources.practice_mode_no_input
import eolssu.shared.generated.resources.practice_mode_ready
import eolssu.shared.generated.resources.practice_mode_recording
import eolssu.shared.generated.resources.practice_settings
import eolssu.shared.generated.resources.practice_subtitle
import eolssu.shared.generated.resources.practice_subtitle_mine
import eolssu.shared.generated.resources.practice_subtitle_solo
import eolssu.shared.generated.resources.practice_title
import org.jetbrains.compose.resources.stringResource

/**
 * PRACTICE-01 상단(`.page-head`): 원형 뒤로 가기 · ‘연습’ · 오른쪽 연습 설정 톱니. 평가 연주 중에도 열린다(속도·장단만 설정 안에서 잠긴다).
 * [showSettings]가 아니면 오른쪽을 비운다(태블릿 가로는 상태 패널에 바로가기가 있다).
 * 혼자 연습이면 톱니 왼쪽에 작은 ‘한 음씩’ 토글([PracticeStepToggle])을 둔다(2차 Practice-Ext).
 */
@Composable
fun PracticeTopBar(
    state: PracticeUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = CompactContentMaxWidth,
    showSettings: Boolean = true,
    onToggleStep: () -> Unit = {},
) {
    EolssuPageTopBar(
        title = stringResource(Res.string.practice_title),
        onBack = onBack,
        modifier = modifier,
        maxWidth = maxWidth,
        right = {
            if (state.solo) {
                PracticeStepToggle(state, onToggleStep)
                if (showSettings) Spacer(Modifier.width(2.dp))
            }
            if (showSettings) {
                EolssuPageIconButton(EolssuIcons.Cog, stringResource(Res.string.practice_settings), onOpenSettings)
            }
        },
    )
}

/**
 * 본문 첫 줄(`.section-row`): 곡 제목(Song Myung 25sp, 운지법과 함께 보기는 22sp)과 오른쪽 `.pill` ‘{악기} · {난이도}’.
 * 연습 모드는 ‘{악기} · 연습 모드’, 내 악보 연습은 ‘{악기} · 내 악보’다.
 * [statusAtEnd]이면 `.pill` 자리에 상태 문구(‘● 연습 준비’·‘● 연주 중’ 등)를 두어, 휴대폰 화면에서 상태 줄 한 줄만큼 악보를 올린다.
 * 한 음씩 안내처럼 긴 문구는 제목을 밀어내지 않게 폭을 제한해 줄바꿈한다.
 */
@Composable
fun PracticeTitleRow(state: PracticeUiState, modifier: Modifier = Modifier, statusAtEnd: Boolean = false) {
    val song = state.song ?: return
    val instrument = stringResource(state.instrument.label)
    val pill = if (state.mine) stringResource(Res.string.practice_subtitle_mine, instrument)
        else if (state.solo) stringResource(Res.string.practice_subtitle_solo, instrument)
        else stringResource(Res.string.practice_subtitle, instrument, songLevelLabel(song.level))
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeading(state.title, Modifier.weight(1f), compact = state.fingeringView)
        if (statusAtEnd) PracticeStatusLabel(state, Modifier.widthIn(max = 168.dp)) else PracticePill(pill)
    }
}

/** `.pill`: 연두 20% 바탕에 10sp 600 짙은 연두 글자, 반지름 4, 여백 3·7. */
@Composable
fun PracticePill(text: String, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Text(
        text,
        modifier = modifier.background(colors.accentTint, RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold),
        color = colors.accentText,
        maxLines = 1,
    )
}

/**
 * 상태 문구(`.status-label`): 7dp 점과 12sp 글자. 연주 중 계열은 붉은 점·붉은 글자 600.
 * 연습 준비 / 모범 연주 중 / 준비해 주세요 / 연주 중 / 연주 중 · 소리 입력 없음 / 연주 완료 / 연주 중단.
 * 한 음씩 따라 불기 중이면 ‘한 음씩 · 黃(황)을 불어 보세요’ / 멈춤 / 곧 이어서 시작해요 / 끝까지 따라 불었어요([practiceStepStatus]).
 */
@Composable
fun PracticeStatusLabel(state: PracticeUiState, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val recording = state.mode == PracticeMode.Recording || state.mode == PracticeMode.Interrupted || state.resuming
    val text = practiceStepStatus(state) ?: stringResource(
        when {
            state.mode == PracticeMode.Interrupted -> Res.string.practice_mode_interrupted
            state.mode == PracticeMode.Recording && state.finished -> Res.string.practice_mode_finished
            state.mode == PracticeMode.Recording && state.noInputHint -> Res.string.practice_mode_no_input
            state.mode == PracticeMode.Recording || state.resuming -> Res.string.practice_mode_recording
            state.mode == PracticeMode.Countdown -> Res.string.practice_mode_countdown
            state.mode == PracticeMode.Rhythm -> Res.string.jangdan_practice_label
            state.mode == PracticeMode.Model && state.playing -> Res.string.practice_mode_model
            else -> Res.string.practice_mode_ready
        },
    )
    Row(
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).background(if (recording) colors.rec else colors.accent, CircleShape))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = if (recording) FontWeight.SemiBold else FontWeight.Normal),
            color = if (recording) colors.recText else colors.textSub,
        )
    }
}

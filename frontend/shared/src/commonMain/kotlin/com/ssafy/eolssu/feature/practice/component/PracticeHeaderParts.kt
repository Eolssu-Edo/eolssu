package com.ssafy.eolssu.feature.practice.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ssafy.eolssu.feature.practice.PracticeOptionKind
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.optionButtons
import com.ssafy.eolssu.feature.practice.sectionEvaluation
import eolssu.shared.generated.resources.jangdan_practice_label
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_eval_title
import eolssu.shared.generated.resources.practice_fingering_view
import eolssu.shared.generated.resources.practice_live_marks
import eolssu.shared.generated.resources.practice_notation_hangul
import eolssu.shared.generated.resources.practice_settings
import eolssu.shared.generated.resources.practice_step_toggle
import eolssu.shared.generated.resources.practice_subtitle
import eolssu.shared.generated.resources.practice_subtitle_mine
import eolssu.shared.generated.resources.practice_subtitle_solo
import eolssu.shared.generated.resources.practice_title
import eolssu.shared.generated.resources.weak_loop_evaluate
import eolssu.shared.generated.resources.weak_loop_title
import org.jetbrains.compose.resources.stringResource

/** 상단 제목: 혼자 연습·내 악보 연습은 ‘연습’, 평가 연습은 ‘연주 평가’, 구간 연습은 ‘구간 연습’, 구간 재평가는 ‘구간 재평가’. */
@Composable
fun practicePageTitle(state: PracticeUiState): String = stringResource(
    when {
        state.sectionEvaluation -> Res.string.weak_loop_evaluate
        state.sectionPractice != null -> Res.string.weak_loop_title
        state.solo || state.mine -> Res.string.practice_title
        else -> Res.string.practice_eval_title
    },
)

/**
 * PRACTICE-01 상단(`.page-head`): 원형 뒤로 가기 · 제목([practicePageTitle]) · 오른쪽 연습 설정 톱니. 평가 연주 중에도 열린다(속도·장단만 설정 안에서 잠긴다).
 * [showSettings]가 아니면 톱니를 두지 않는다(태블릿 가로는 상태 패널에 바로가기가 있다).
 * 태블릿은 톱니 왼쪽에 보기 버튼 묶음([options], [PracticeOptionButtonRow])을 둔다. 휴대폰은 제목이 잘리지 않게 본문 제목 줄 오른쪽에 둔다(S15P31E102-816·1037).
 */
@Composable
fun PracticeTopBar(
    state: PracticeUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = CompactContentMaxWidth,
    showSettings: Boolean = true,
    options: (@Composable () -> Unit)? = null,
) {
    EolssuPageTopBar(
        title = practicePageTitle(state),
        onBack = onBack,
        modifier = modifier,
        maxWidth = maxWidth,
        // 회색 바탕 화면(S15P31E102-768)이라 구분선 없이 흰 버튼만 띄운다.
        divider = false,
        right = {
            if (options != null) {
                options()
                if (showSettings) Spacer(Modifier.width(4.dp))
            }
            if (showSettings) {
                EolssuPageIconButton(EolssuIcons.Cog, stringResource(Res.string.practice_settings), onOpenSettings, bordered = false)
            }
        },
    )
}

/**
 * 보기 토글 묶음(S15P31E102-1037): 왼쪽부터 ‘퍼펙트 스코어’(평가) 또는 ‘한 음씩’(혼자 연습) · ‘운지법’ · ‘한글 표기’.
 * 어느 연습 모드·화면 크기에서도 같은 [PracticeOptionChip]을 같은 순서로 놓는다. 어떤 버튼을 둘지는 [optionButtons]가 정한다.
 * [include]로 일부만 놓는다. 휴대폰은 모드 버튼은 톱니 옆, 나머지는 제목 줄에 나눠 둔다.
 */
@Composable
fun PracticeOptionButtonRow(
    state: PracticeUiState,
    actions: PracticePanelActions,
    onToggleStep: () -> Unit,
    modifier: Modifier = Modifier,
    include: (PracticeOptionKind) -> Boolean = { true },
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        state.optionButtons().filter { include(it.kind) }.forEach { button ->
            val (label, onToggle) = when (button.kind) {
                PracticeOptionKind.Step -> Res.string.practice_step_toggle to onToggleStep
                PracticeOptionKind.LiveMarks -> Res.string.practice_live_marks to actions.onToggleLiveMarks
                PracticeOptionKind.Fingering -> Res.string.practice_fingering_view to actions.onToggleFingeringView
                PracticeOptionKind.Hangul -> Res.string.practice_notation_hangul to actions.onToggleHangul
            }
            PracticeOptionChip(stringResource(label), button.on, onToggle, enabled = button.enabled)
        }
    }
}

/**
 * 보기 토글 하나: 32dp 높이 흰 알약 · 12sp 600 글자 · 30×18dp 작은 스위치(켜면 연두)(S15P31E102-1037).
 * 예전 ‘한 음씩’ 토글과 같은 모양이다. 버튼처럼 보이지 않는다는 의견으로 단순 버튼에서 되돌렸다.
 * 누르는 곳은 스위치뿐이다(S15P31E102-924). 스위치는 보이는 크기를 그대로 두고 둘레로 [OptionChipTouch] 정사각형만큼 눌리게 넓히고,
 * 눌림 효과는 보이는 스위치 안에만 그린다(S15P31E102-968). 화면 읽기에는 [label] 이름의 스위치 하나로 읽힌다.
 */
@Composable
fun PracticeOptionChip(label: String, on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = EolssuTheme.colors
    Row(modifier.heightIn(min = OptionChipTouch), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .height(32.dp)
                .background(colors.surface, RoundedCornerShape(16.dp))
                .padding(start = 10.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                label,
                modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                color = if (enabled) colors.text else colors.textDisabled,
                maxLines = 1,
                softWrap = false,
            )
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .layout { measurable, _ ->
                        val touch = OptionChipTouch.roundToPx()
                        val width = OptionSwitchWidth.roundToPx()
                        val height = OptionSwitchHeight.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(touch, touch))
                        layout(width, height) { placeable.place((width - touch) / 2, (height - touch) / 2) }
                    }
                    .toggleable(value = on, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Switch, onValueChange = UserActionLog.change(QaTarget.PRACTICE_HEADER_PARTS_01, { onToggle() }))
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                // 다른 스위치([com.ssafy.eolssu.core.ui.component.EolssuSwitch])와 같은 색·그림자 없는 손잡이(S15P31E102-768).
                Box(
                    Modifier
                        .width(OptionSwitchWidth)
                        .height(OptionSwitchHeight)
                        .clip(RoundedCornerShape(9.dp))
                        .alpha(if (enabled) 1f else 0.45f)
                        .background(if (on) colors.primary else colors.line)
                        .indication(interaction, ripple()),
                ) {
                    Box(
                        Modifier
                            .offset(x = if (on) 14.dp else 2.dp, y = 2.dp)
                            .size(14.dp)
                            .background(colors.surface, CircleShape),
                    )
                }
            }
        }
    }
}

private val OptionSwitchWidth = 30.dp
private val OptionSwitchHeight = 18.dp

/** 작은 스위치 하나를 누르는 정사각형 한 변. 손가락 누름 기준 44dp 이상. */
private val OptionChipTouch = 44.dp

/**
 * 본문 첫 줄(`.section-row`): 곡 제목(Song Myung 25sp)과 오른쪽 `.pill` ‘{악기} · {난이도}’.
 * 곡 제목 크기는 ‘운지법과 함께 보기’를 켜고 꺼도 그대로다(S15P31E102-1017).
 * 연습 모드는 ‘{악기} · 연습 모드’, 내 악보 연습은 ‘{악기} · 내 악보’다.
 * [showPill]이 false이면(휴대폰 연습 화면) 오른쪽을 비워 둔다. 상태 문구(‘연습 준비’·‘연주 중’ 등)는 어느 화면에도 두지 않는다.
 * ‘운지법’·‘한글 표기’ 같은 보기 버튼은 휴대폰에서 이 줄 오른쪽([trailing])에 둔다(S15P31E102-1037).
 * 곡 제목은 어느 경우에도 한 줄이고, 다 들어가지 않을 때만 왼쪽으로 흘린다(S15P31E102-730).
 */
@Composable
fun PracticeTitleRow(
    state: PracticeUiState,
    modifier: Modifier = Modifier,
    showPill: Boolean = true,
    /** 알약 대신 제목 오른쪽에 둘 내용(휴대폰의 보기 버튼 묶음, S15P31E102-1037). */
    trailing: (@Composable () -> Unit)? = null,
) {
    val song = state.song ?: return
    val instrument = stringResource(state.instrument.label)
    val pill = if (state.mine) stringResource(Res.string.practice_subtitle_mine, instrument)
        else if (state.solo) stringResource(Res.string.practice_subtitle_solo, instrument)
        else stringResource(Res.string.practice_subtitle, instrument, songLevelLabel(song.level))
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeading(state.title, Modifier.weight(1f), singleLine = true)
        if (trailing != null) trailing() else if (showPill) PracticePill(pill)
    }
}

/** `.pill`: 연두 20% 바탕에 10sp 600 짙은 연두 글자, 반지름 4, 여백 3·7. */
@Composable
fun PracticePill(text: String, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Text(
        text,
        modifier = modifier.background(colors.accentTint, RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
        color = colors.accentText,
        maxLines = 1,
    )
}

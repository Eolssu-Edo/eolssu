package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.fingeringYul
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.score.YulGlyph
import com.ssafy.eolssu.feature.practice.PracticeMode
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.StepPhase
import com.ssafy.eolssu.feature.practice.reading
import com.ssafy.eolssu.feature.practice.stepShownIndex
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_step_listen_desc
import eolssu.shared.generated.resources.practice_step_manual_confirm
import eolssu.shared.generated.resources.practice_step_mine
import eolssu.shared.generated.resources.practice_step_now
import eolssu.shared.generated.resources.practice_step_off
import eolssu.shared.generated.resources.practice_step_on
import eolssu.shared.generated.resources.practice_step_rec_desc_on
import eolssu.shared.generated.resources.practice_step_rec_desc_resume
import eolssu.shared.generated.resources.practice_step_rec_desc_start
import eolssu.shared.generated.resources.practice_step_recording
import eolssu.shared.generated.resources.practice_step_toggle
import eolssu.shared.generated.resources.practice_step_toggle_desc
import org.jetbrains.compose.resources.stringResource

/**
 * 설정 버튼 옆 작은 ‘한 음씩’ 토글(와이어프레임 STEP_TOGGLE): 32dp 높이 흰 알약 · 12sp 600 글자 · 30×18dp 작은 스위치(켜면 연두).
 * 화면 읽기에는 ‘한 음씩 따라 불기 켜짐/꺼짐’ 스위치로 읽힌다. 혼자 연습의 준비·모범 연주 상태에서만 누를 수 있다.
 */
@Composable
fun PracticeStepToggle(state: PracticeUiState, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val on = state.step.on
    PracticeMiniToggle(
        label = stringResource(Res.string.practice_step_toggle),
        on = on,
        onToggle = onToggle,
        modifier = modifier,
        enabled = state.mode == PracticeMode.Ready || state.mode == PracticeMode.Model,
        description = stringResource(Res.string.practice_step_toggle_desc, stringResource(if (on) Res.string.practice_step_on else Res.string.practice_step_off)),
    )
}

/**
 * 작은 알약 스위치: 32dp 높이 테두리 없는 흰 알약 · 12sp 600 글자 · 30×18dp 작은 스위치(켜면 연두).
 * 누르는 곳은 스위치뿐이다(S15P31E102-924). 글자나 알약 바탕을 눌러도 바뀌지 않는다.
 * 스위치는 보이는 크기를 그대로 두고 둘레로 [MiniSwitchTouch] 정사각형만큼 눌리게 넓힌다.
 * 화면 읽기에는 스위치 하나가 [description]으로 읽히고 글자는 따로 읽히지 않는다.
 * ‘한 음씩’ 토글과 연주 평가 제목 옆 ‘운지법 보기’(S15P31E102-716)가 함께 쓴다.
 */
@Composable
fun PracticeMiniToggle(
    label: String,
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String = label,
) {
    val colors = EolssuTheme.colors
    Row(
        modifier.heightIn(min = MiniSwitchTouch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            )
            // 배치에는 30×18dp만 차지하고, 누르는 영역은 그 둘레로 넓혀 알약 밖까지 걸친다.
            Box(
                Modifier
                    .layout { measurable, _ ->
                        val touch = MiniSwitchTouch.roundToPx()
                        val width = MiniSwitchWidth.roundToPx()
                        val height = MiniSwitchHeight.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(touch, touch))
                        layout(width, height) { placeable.place((width - touch) / 2, (height - touch) / 2) }
                    }
                    .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                // 다른 스위치([com.ssafy.eolssu.core.ui.component.EolssuSwitch])와 같은 색·그림자 없는 손잡이(S15P31E102-768).
                Box(
                    Modifier
                        .width(MiniSwitchWidth)
                        .height(MiniSwitchHeight)
                        .background(if (on) colors.primary else colors.line, RoundedCornerShape(9.dp)),
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

private val MiniSwitchWidth = 30.dp
private val MiniSwitchHeight = 18.dp

/** 작은 스위치 하나를 누르는 정사각형 한 변. 손가락 누름 기준 44dp 이상. */
private val MiniSwitchTouch = 44.dp

/**
 * 한 음씩이 켜지면 플레이어 카드 대신 놓는 두 칸(와이어프레임 STEP_PANELS).
 * 진행 안내 문구는 두지 않는다(S15P31E102-717). 두 칸은 이름 줄과 내용 줄의 높이를 같게 해 위아래를 맞춘다.
 * 왼쪽 ‘현재 정간’은 누르면 그 정간 음의 운지 예시음을 들려주고, 오른쪽 ‘내 연주’는 누를 때마다 시작·멈춤(다시 누르면 멈춘 자리부터 바로 이어서)을 오간다.
 * ‘녹음 중’ 표시는 듣는 중에만 보인다. 판정기가 스스로 판정하지 못하면(대역) 임시 넘기기 버튼을 아래에 둔다.
 */
@Composable
fun PracticeStepPanels(state: PracticeUiState, actions: PracticeBottomActions, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val note = state.stepShownIndex?.let { state.song?.timeline?.getOrNull(it) }?.note
    val listening = state.step.phase == StepPhase.Listening
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val listenDesc = stringResource(Res.string.practice_step_listen_desc, note?.symbol.orEmpty())
            val yul = note?.fingeringYul
            StepPanel(
                label = stringResource(Res.string.practice_step_now),
                description = listenDesc,
                onClick = actions.onListenStepNote,
                modifier = Modifier.weight(1f),
                icon = {
                    val playing = yul != null && state.listening == yul
                    StepIcon(EolssuIcons.Speaker, if (playing) colors.accent else colors.accentTint, colors.text)
                },
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (yul != null) {
                        YulGlyph(yul, 28.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(note?.symbol.orEmpty(), style = EolssuTheme.serif.yul.copy(fontSize = 28.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold))
                    }
                    Text(note?.reading.orEmpty(), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.textSub)
                }
            }
            StepPanel(
                label = stringResource(Res.string.practice_step_mine),
                description = stringResource(
                    when (state.step.phase) {
                        StepPhase.Listening -> Res.string.practice_step_rec_desc_on
                        StepPhase.Paused -> Res.string.practice_step_rec_desc_resume
                        else -> Res.string.practice_step_rec_desc_start
                    },
                ),
                onClick = actions.onTapStepPlaying,
                modifier = Modifier.weight(1f),
                recording = listening,
                icon = { StepIcon(EolssuIcons.Mic, if (listening) colors.rec else colors.surfaceStrong, if (listening) colors.onRec else colors.text) },
            ) {
                if (listening) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(16.dp).background(colors.recSoft, CircleShape).padding(3.dp).background(colors.rec, CircleShape))
                        Text(
                            stringResource(Res.string.practice_step_recording),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
                            color = colors.recText,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        // 디버그 빌드에서만 값이 있다(S15P31E102-666). 릴리스에서는 아무것도 그리지 않는다.
        if (listening) PracticeStepGauge()
        if (state.step.manualConfirm && listening) {
            // 실제 음 판정(S15P31E102-341)을 붙이기 전의 대역 조작. 판정기가 스스로 판정하면 보이지 않는다.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.practice_step_manual_confirm),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .padding(vertical = 8.dp)
                        .drawBehind {
                            drawRoundRect(
                                colors.line,
                                cornerRadius = CornerRadius(size.height / 2),
                                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                            )
                        }
                        .clickable(role = Role.Button, onClick = actions.onConfirmStepNote)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textSub,
                )
            }
        }
    }
}

/**
 * 두 칸 중 하나: 왼쪽에 이름과 내용, 오른쪽 가운데에 32dp 동그란 아이콘. 높이 80dp 이상.
 * 이름·내용은 왼쪽 여백을 넓혀 조금 안쪽에 두고, 내용 줄 높이를 고정해 두 칸의 이름과 내용이 같은 높이에 오게 한다.
 */
@Composable
private fun StepPanel(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    recording: Boolean = false,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 80.dp).clearAndSetSemantics { contentDescription = description; role = Role.Button },
        shape = shape,
        // 플레이어 카드처럼 회색 바탕 위 테두리 없는 흰 카드(녹음 중엔 옅은 빨강)(S15P31E102-768).
        color = if (recording) colors.recSoft else colors.surface,
        contentColor = colors.text,
    ) {
        Row(
            // 두 칸 모두 안쪽 여백을 16dp로 같게 해 이름·내용·아이콘 자리를 맞춘다.
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.textMuted)
                Box(Modifier.height(StepContentHeight), contentAlignment = Alignment.CenterStart) { content() }
            }
            icon()
        }
    }
}

/** 두 칸의 내용 줄 높이. 28sp 율명 한 줄이 들어간다. */
private val StepContentHeight = 36.dp

@Composable
private fun StepIcon(icon: ImageVector, background: Color, tint: Color) {
    Box(Modifier.size(32.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
    }
}

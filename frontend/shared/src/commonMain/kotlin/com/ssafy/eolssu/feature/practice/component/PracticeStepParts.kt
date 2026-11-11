package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.ssafy.eolssu.feature.practice.stepProgress
import com.ssafy.eolssu.feature.practice.stepShownIndex
import com.ssafy.eolssu.feature.practice.stepUi
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_step_listen_desc
import eolssu.shared.generated.resources.practice_step_manual_confirm
import eolssu.shared.generated.resources.practice_step_mine
import eolssu.shared.generated.resources.practice_step_now
import eolssu.shared.generated.resources.practice_step_off
import eolssu.shared.generated.resources.practice_step_on
import eolssu.shared.generated.resources.practice_step_progress
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
    val colors = EolssuTheme.colors
    val on = state.step.on
    val enabled = state.mode == PracticeMode.Ready || state.mode == PracticeMode.Model
    val description = stringResource(Res.string.practice_step_toggle_desc, stringResource(if (on) Res.string.practice_step_on else Res.string.practice_step_off))
    Row(
        modifier
            .heightIn(min = 44.dp)
            .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(32.dp)
                .background(colors.surface, RoundedCornerShape(16.dp))
                .border(1.dp, colors.lineSoft, RoundedCornerShape(16.dp))
                .padding(start = 10.dp, end = 5.dp)
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(Res.string.practice_step_toggle),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                color = if (enabled) colors.text else colors.textDisabled,
                maxLines = 1,
            )
            Box(Modifier.width(30.dp).height(18.dp).background(if (on) colors.accent else colors.line, RoundedCornerShape(9.dp))) {
                Box(
                    Modifier
                        .offset(x = if (on) 14.dp else 2.dp, y = 2.dp)
                        .size(14.dp)
                        .shadow(1.dp, CircleShape)
                        .background(colors.surface, CircleShape),
                )
            }
        }
    }
}

/**
 * 한 음씩이 켜지면 플레이어 카드 대신 놓는 두 칸(와이어프레임 STEP_PANELS)과 진행 안내.
 * 왼쪽 ‘지금 음’은 누르면 지금 정간의 음을 들려주고, 오른쪽 ‘내 연주’는 누를 때마다 시작·멈춤(다시 누르면 멈춘 자리부터 바로 이어서)을 오간다.
 * ‘녹음 중’ 표시는 듣는 중에만 보인다. 판정기가 스스로 판정하지 못하면(대역) 임시 넘기기 버튼을 아래에 둔다.
 */
@Composable
fun PracticeStepPanels(state: PracticeUiState, actions: PracticeBottomActions, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val note = state.stepShownIndex?.let { state.song?.timeline?.getOrNull(it) }?.note
    val listening = state.step.phase == StepPhase.Listening
    val (current, total) = state.stepProgress
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
        if (total > 0) {
            Text(
                stringResource(Res.string.practice_step_progress, current, total),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 18.7.sp),
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
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

/** 두 칸 중 하나: 왼쪽에 이름과 내용, 오른쪽 가운데에 32dp 동그란 아이콘. 높이 80dp 이상. */
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
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 80.dp).clearAndSetSemantics { contentDescription = description; role = Role.Button },
        shape = shape,
        color = if (recording) colors.recSoft.copy(alpha = 0.5f) else colors.surface,
        border = BorderStroke(1.dp, if (recording) colors.rec.copy(alpha = 0.3f) else colors.lineSoft),
        contentColor = colors.text,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.textMuted)
                content()
            }
            icon()
        }
    }
}

@Composable
private fun StepIcon(icon: ImageVector, background: Color, tint: Color) {
    Box(Modifier.size(32.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
    }
}

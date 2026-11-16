package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingeringYul
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.score.ScoreNoteGlyph
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.StepPhase
import com.ssafy.eolssu.feature.practice.reading
import com.ssafy.eolssu.feature.practice.stepRepeatsPrevious
import com.ssafy.eolssu.feature.practice.stepShownIndex
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_step_finished
import eolssu.shared.generated.resources.practice_step_listen_desc
import eolssu.shared.generated.resources.practice_step_manual_confirm
import eolssu.shared.generated.resources.practice_step_mine
import eolssu.shared.generated.resources.practice_step_now
import eolssu.shared.generated.resources.practice_step_rec_desc_on
import eolssu.shared.generated.resources.practice_step_rec_desc_resume
import eolssu.shared.generated.resources.practice_step_rec_desc_start
import eolssu.shared.generated.resources.practice_step_recording
import eolssu.shared.generated.resources.practice_step_repeat_hint
import org.jetbrains.compose.resources.stringResource

/** 한 음씩 '현재 정간' 칸의 예시음 재생기: 누를 때 부를 [play]와 지금 재생 상태 [playback](S15P31E102-1006). */
class StepNoteAudio(val play: (FingeringNote) -> Unit, val playback: AudioPlaybackState = AudioPlaybackState())

/** '현재 정간' 예시음을 들려주는 길이. 소리가 나기 시작한 뒤 이만큼만 들려주고 멈춘다(S15P31E102-1006). */
const val StepNotePlayMs = 1_200L

/** '현재 정간' 예시음의 재생 상태. [com.ssafy.eolssu.feature.practice.PracticeRoute]가 넣고, 연결이 없으면 재생 전 상태다. */
val LocalStepNotePlayback = compositionLocalOf { AudioPlaybackState() }

/**
 * 한 음씩이 켜지면 플레이어 카드 대신 놓는 두 칸(와이어프레임 STEP_PANELS).
 * 진행 안내 문구는 두지 않는다(S15P31E102-717). 두 칸은 이름 줄과 내용 줄의 높이를 같게 해 위아래를 맞춘다.
 * 왼쪽 ‘현재 정간’은 누르면 그 정간 음의 운지 예시음을 [StepNotePlayMs]만큼 들려주고, 소리가 나는 동안 아이콘을 강조한다(S15P31E102-1006).
 * 오른쪽 ‘내 연주’는 누를 때마다 시작·멈춤(다시 누르면 멈춘 자리부터 바로 이어서)을 오간다.
 * ‘듣는 중’ 표시는 듣는 중에만 보이고, 마지막 음까지 맞게 불면 ‘끝까지 불었어요’를 둔다. 앞과 같은 음을 기다리면 한 번 끊고 불라는 안내를 둔다.
 * 두 칸은 내용이 달라도 높이를 같게 맞춘다(S15P31E102-1006). 판정기가 스스로 판정하지 못하면(대역) 임시 넘기기 버튼을 아래에 둔다.
 */
@Composable
fun PracticeStepPanels(state: PracticeUiState, actions: PracticeBottomActions, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val note = state.stepShownIndex?.let { state.song?.timeline?.getOrNull(it) }?.note
    val listening = state.step.phase == StepPhase.Listening
    val playback = LocalStepNotePlayback.current
    val noteActive = playback.status == AudioPlaybackStatus.Playing || playback.status == AudioPlaybackStatus.Loading
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 두 칸 중 높은 쪽에 맞춰 둘 다 늘린다. 율명 글자가 있는 왼쪽 칸이 더 높아져도 두 칸의 높이가 같다.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val listenDesc = stringResource(Res.string.practice_step_listen_desc, note?.symbol.orEmpty())
            val yul = note?.fingeringYul
            StepPanel(
                label = stringResource(Res.string.practice_step_now),
                description = listenDesc,
                onClick = actions.onListenStepNote,
                modifier = Modifier.weight(1f),
                icon = {
                    // 실제 재생기가 연결되면 그 상태를, 연결이 없으면(미리보기) 누른 표시를 따른다.
                    val playing = noteActive || (playback.sourceId == null && yul != null && state.listening == yul)
                    StepIcon(EolssuIcons.Speaker, if (playing) colors.accent else colors.accentTint, colors.text)
                },
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (note != null) ScoreNoteGlyph(note, 28.sp, fontWeight = FontWeight.Bold)
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
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                            color = colors.recText,
                            maxLines = 1,
                        )
                    }
                } else if (state.step.phase == StepPhase.Finished) {
                    // 마지막 음 뒤가 빈 정간·쉼이면 더 넘어갈 음이 없으므로 끝났음을 알린다.
                    Text(
                        stringResource(Res.string.practice_step_finished),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.accentText,
                        maxLines = 1,
                    )
                }
            }
        }
        // 디버그 빌드에서만 값이 있다(S15P31E102-666). 릴리스에서는 아무것도 그리지 않는다.
        if (state.stepRepeatsPrevious && !state.step.manualConfirm) {
            Text(
                stringResource(Res.string.practice_step_repeat_hint),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = colors.accentText,
                textAlign = TextAlign.Center,
            )
        }
        if (listening) PracticeStepGauge()
        if (state.step.manualConfirm && listening) {
            // 실제 음 판정(S15P31E102-341)을 붙이기 전의 대역 조작. 판정기가 스스로 판정하면 보이지 않는다.
            val confirmInteraction = remember { MutableInteractionSource() }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.practice_step_manual_confirm),
                    // 누르는 영역은 48dp 높이로 두고, 눌림 효과는 그린 점선 알약 안에만 둔다(S15P31E102-968).
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(interactionSource = confirmInteraction, indication = null, role = Role.Button, onClick = actions.onConfirmStepNote)
                        .wrapContentHeight()
                        .clip(CircleShape)
                        .drawBehind {
                            drawRoundRect(
                                colors.line,
                                cornerRadius = CornerRadius(size.height / 2),
                                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                            )
                        }
                        .indication(confirmInteraction, ripple())
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
        modifier = modifier.fillMaxHeight().heightIn(min = 80.dp).clearAndSetSemantics { contentDescription = description; role = Role.Button },
        shape = shape,
        // 플레이어 카드처럼 회색 바탕 위 테두리 없는 흰 카드(녹음 중엔 옅은 빨강)(S15P31E102-768).
        color = if (recording) colors.recSoft else colors.surface,
        contentColor = colors.text,
    ) {
        Row(
            // 두 칸 모두 안쪽 여백을 16dp로 같게 해 이름·내용·아이콘 자리를 맞춘다.
            Modifier.fillMaxHeight().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 이름은 위에 두고 내용 줄이 남은 높이를 채워, 두 칸의 이름과 내용이 같은 높이에 온다.
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.textMuted)
                Box(Modifier.weight(1f).heightIn(min = StepContentHeight), contentAlignment = Alignment.CenterStart) { content() }
            }
            icon()
        }
    }
}

/** 두 칸의 내용 줄 최소 높이. 28sp 율명 한 줄이 들어가고 큰 글꼴에서는 내용만큼 높아진다(S15P31E102-972). */
private val StepContentHeight = 36.dp

@Composable
private fun StepIcon(icon: ImageVector, background: Color, tint: Color) {
    Box(Modifier.size(32.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
    }
}

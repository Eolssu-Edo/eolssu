package com.ssafy.eolssu.feature.history.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import com.ssafy.eolssu.core.ui.component.DrumThumb
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.ssafy.eolssu.core.ui.component.EolssuButton
import eolssu.shared.generated.resources.record_result_practice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.feature.history.RecordDetailActions
import com.ssafy.eolssu.feature.history.RecordDetailUiState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_pause
import eolssu.shared.generated.resources.practice_play
import eolssu.shared.generated.resources.record_detail_back5_label
import eolssu.shared.generated.resources.record_detail_fwd5_label
import eolssu.shared.generated.resources.record_detail_position
import eolssu.shared.generated.resources.record_detail_seek
import org.jetbrains.compose.resources.stringResource


/** 지금 위치(시간 · 각·칸)와 전체 길이. */
@Composable
internal fun PlaybackTimeRow(state: RecordDetailUiState) {
    val colors = EolssuTheme.colors
    val event = state.song?.timeline?.getOrNull(state.position)
    Row(Modifier.fillMaxWidth()) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.SemiBold)) { append(formatClock(state.elapsedSeconds)) }
                if (event != null) append(" · " + stringResource(Res.string.record_detail_position, event.column + 1, event.row + 1))
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textMuted,
        )
        Text(formatClock(state.durationMillis / 1000.0), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
    }
}

/** 재생 위치 막대. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSlider(state: RecordDetailUiState, actions: RecordDetailActions, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val seekLabel = stringResource(Res.string.record_detail_seek)
    val interaction = remember { MutableInteractionSource() }
    val held = interaction.collectIsDraggedAsState().value || interaction.collectIsPressedAsState().value
    val sliderColors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary, inactiveTrackColor = colors.surfaceStrong)
    var draft by remember(state.record?.id) { mutableStateOf<Float?>(null) }
    var seekRevision by remember(state.record?.id) { mutableStateOf(0) }
    // 50ms 단위 오디오 보고 사이를 보간한다. 드래그·일시정지는 즉시 반영한다.
    val animated = key(state.record?.id, seekRevision) {
        animateFloatAsState(
            state.playbackPositionMs.toFloat(),
            animationSpec = if (state.playing && draft == null) tween(60, easing = LinearEasing) else snap(),
        ).value
    }
    Slider(
        value = draft ?: animated,
        onValueChange = { draft = it },
        onValueChangeFinished = {
            draft?.let { actions.onSeekMillis(it.toLong()) }
            draft = null
            seekRevision++
        },
        valueRange = 0f..state.durationMillis.coerceAtLeast(1L).toFloat(),
        enabled = state.canPlay,
        interactionSource = interaction,
        thumb = { DrumThumb(held, enabled = state.canPlay) },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                enabled = state.canPlay,
                colors = sliderColors,
                // 북 아래까지 선을 이어 손잡이 양옆의 흰 네모처럼 보이는 틈을 없앤다.
                thumbTrackGapSize = 0.dp,
            )
        },
        modifier = modifier.height(36.dp).semantics { contentDescription = seekLabel },
        colors = sliderColors,
    )
}

/**
 * 재생 묶음: 회색 바탕 위 흰 사각 묶음(52dp, 모서리 14) 안에 ↺5 · 재생 · ↻5(S15P31E102-768). 재생 버튼은 연두 사각(52×44, 모서리 10)이고, 5초 버튼은 바탕 없는 44dp다.
 * 녹음을 다시 들을 수 없는 기록(`recordingAvailable` false)은 모두 쓸 수 없다.
 */
@Composable
internal fun PlaybackButtons(state: RecordDetailUiState, actions: RecordDetailActions) {
    val colors = EolssuTheme.colors
    Row(
        // 옆 '취약 구간 연습' 버튼과 같은 높이 52dp · 모서리 14dp 사각 묶음, 안쪽 재생 버튼은 모서리 10dp(S15P31E102-768).
        Modifier.background(colors.surface, RoundedCornerShape(14.dp)).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SkipButton(EolssuIcons.ReplayBack, stringResource(Res.string.record_detail_back5_label), state.canPlay, digitOffset = 0.2.dp) { actions.onSkip(-5) }
        Surface(
            onClick = actions.onTogglePlay,
            enabled = state.canPlay,
            modifier = Modifier.size(width = 52.dp, height = 44.dp),
            shape = RoundedCornerShape(10.dp),
            color = if (state.canPlay) colors.primary else colors.surfaceStrong,
            contentColor = if (state.canPlay) colors.onPrimary else colors.textDisabled,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (state.playing) EolssuIcons.Pause else EolssuIcons.Play,
                    contentDescription = stringResource(if (state.playing) Res.string.practice_pause else Res.string.practice_play),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        SkipButton(EolssuIcons.ReplayForward, stringResource(Res.string.record_detail_fwd5_label), state.canPlay, digitOffset = (-0.2).dp) { actions.onSkip(5) }
    }
}

/**
 * 하단 재생 영역 내용(시간 · 막대 · 재생 묶음과 ‘취약 구간 연습’). 휴대폰·태블릿이 같은 구성이다.
 */
@Composable
internal fun PlaybackBarContent(state: RecordDetailUiState, actions: RecordDetailActions) {
    // 연습 화면 플레이어 카드처럼 시간·막대를 테두리 없는 흰 카드에 담는다(S15P31E102-768).
    Column(Modifier.fillMaxWidth().background(EolssuTheme.colors.surface, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 2.dp)) {
        PlaybackTimeRow(state)
        PlaybackSlider(state, actions)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlaybackButtons(state, actions)
        EolssuButton(stringResource(Res.string.record_result_practice), actions.onPractice, modifier = Modifier.weight(1f), enabled = state.canPractice)
    }
}

/** 5초 이동(44dp, 바탕 없음): 둥근 화살표 가운데에 ‘5’. */
@Composable
private fun SkipButton(icon: ImageVector, label: String, enabled: Boolean, digitOffset: Dp, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp).semantics { contentDescription = label },
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        contentColor = if (enabled) colors.text else colors.textDisabled,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Text(
                "5",
                modifier = Modifier.offset(x = digitOffset, y = 1.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

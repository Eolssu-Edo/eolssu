package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme

/*
 * 녹음·모범 연주 플레이어 부품. 연주 기록 상세(S15P31E102-768·794)의 배치를 연습 화면 플레이어도 함께 쓴다(S15P31E102-873).
 * 위에서부터 흰 카드([PlaybackTimeline]: 시간 줄 + 재생 위치 막대), 그 아래 한 줄([PlaybackControlRow]: ↺5 · 재생 · ↻5 묶음과 오른쪽 행동 버튼).
 */

/** 카드와 아래 조작 줄 사이 간격. 기록 상세 하단 바([EolssuBottomBar])의 간격과 같다. */
val PlaybackSectionSpacing: Dp = 10.dp

/** 조작 줄 오른쪽 행동 버튼(‘구간 연습’·‘연주 평가 시작’) 높이. 옆 5초 이동 버튼과 같다. */
val PlaybackActionHeight: Dp = 44.dp

/** 테두리 없는 흰 카드(모서리 16)에 시간 줄·재생 위치 막대를 담는다. */
@Composable
fun PlaybackTimeline(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(EolssuTheme.colors.surface, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 2.dp),
        content = content,
    )
}

/** 시간 줄(12sp): 왼쪽 지금 위치([playbackElapsedText]), 오른쪽 전체 길이. */
@Composable
fun PlaybackTimeText(elapsed: AnnotatedString, total: String, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
    Row(modifier.fillMaxWidth()) {
        Text(elapsed, modifier = Modifier.weight(1f), style = style, color = colors.textMuted)
        Text(total, style = style, color = colors.textMuted)
    }
}

/** 지금 위치: 시간은 진하게, 뒤에 붙는 [detail](‘· 3각 2칸’ 등)은 흐리게. */
@Composable
fun playbackElapsedText(clock: String, detail: String? = null): AnnotatedString {
    val colors = EolssuTheme.colors
    return buildAnnotatedString {
        withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.SemiBold)) { append(clock) }
        if (detail != null) append(" · $detail")
    }
}

/**
 * 재생 위치 막대(36dp): 연두 트랙과 삼태극 북 손잡이([DrumThumb]). 끄는 동안의 값과 놓을 때의 이동은 부르는 쪽이 정한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackPositionSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    enabled: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val colors = EolssuTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val held = interaction.collectIsDraggedAsState().value || interaction.collectIsPressedAsState().value
    val sliderColors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary, inactiveTrackColor = colors.surfaceStrong)
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        enabled = enabled,
        interactionSource = interaction,
        thumb = { DrumThumb(held, enabled = enabled) },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                enabled = enabled,
                colors = sliderColors,
                // 북 아래까지 선을 이어 손잡이 양옆의 흰 네모처럼 보이는 틈을 없앤다.
                thumbTrackGapSize = 0.dp,
            )
        },
        modifier = modifier.fillMaxWidth().height(36.dp).semantics { contentDescription = label },
        colors = sliderColors,
    )
}

/** 카드 아래 조작 줄: 왼쪽 재생 묶음, 오른쪽에 너비를 채우는 행동 버튼. */
@Composable
fun PlaybackControlRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = horizontalArrangement, content = content)
}

/**
 * 재생 묶음: 흰 사각 묶음(52dp, 모서리 14, 안쪽 4) 안에 ↺5 · 재생 · ↻5. 재생 버튼은 연두 사각(52×44, 모서리 10)이고,
 * 5초 버튼은 바탕 없는 44dp다. 쓸 수 없으면 회색으로 흐리게 그린다.
 */
@Composable
fun PlaybackSkipGroup(
    playing: Boolean,
    playEnabled: Boolean,
    playLabel: String,
    onTogglePlay: () -> Unit,
    backLabel: String,
    onBack: () -> Unit,
    forwardLabel: String,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
    skipEnabled: Boolean = playEnabled,
) {
    val colors = EolssuTheme.colors
    Row(
        modifier.background(colors.surface, RoundedCornerShape(14.dp)).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SkipButton(EolssuIcons.ReplayBack, backLabel, skipEnabled, digitOffset = 0.2.dp, onClick = onBack)
        Surface(
            onClick = onTogglePlay,
            enabled = playEnabled,
            modifier = Modifier.size(width = 52.dp, height = 44.dp),
            shape = RoundedCornerShape(10.dp),
            color = if (playEnabled) colors.primary else colors.surfaceStrong,
            contentColor = if (playEnabled) colors.onPrimary else colors.textDisabled,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (playing) EolssuIcons.Pause else EolssuIcons.Play, contentDescription = playLabel, modifier = Modifier.size(22.dp))
            }
        }
        SkipButton(EolssuIcons.ReplayForward, forwardLabel, skipEnabled, digitOffset = (-0.2).dp, onClick = onForward)
    }
}

/** 조작 줄 오른쪽 행동 버튼(높이 [PlaybackActionHeight]). 너비는 [modifier]로 정한다(보통 `weight(1f)`). */
@Composable
fun PlaybackActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    EolssuButton(
        text, onClick, modifier.height(PlaybackActionHeight),
        icon = icon, enabled = enabled, compact = true, small = true, minHeight = PlaybackActionHeight,
    )
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

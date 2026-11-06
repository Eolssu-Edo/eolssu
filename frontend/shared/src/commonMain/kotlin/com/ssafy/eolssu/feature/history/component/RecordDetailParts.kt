package com.ssafy.eolssu.feature.history.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuSwitch
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.feature.history.RecordDetailActions
import com.ssafy.eolssu.feature.history.RecordDetailUiState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_pause
import eolssu.shared.generated.resources.practice_play
import eolssu.shared.generated.resources.record_detail_back5
import eolssu.shared.generated.resources.record_detail_back5_label
import eolssu.shared.generated.resources.record_detail_fwd5
import eolssu.shared.generated.resources.record_detail_fwd5_label
import eolssu.shared.generated.resources.record_detail_metrics
import eolssu.shared.generated.resources.record_detail_position
import eolssu.shared.generated.resources.record_detail_seek
import eolssu.shared.generated.resources.record_detail_weak
import eolssu.shared.generated.resources.records_score_unit
import eolssu.shared.generated.resources.weak_record_compare
import eolssu.shared.generated.resources.weak_record_compare_with
import org.jetbrains.compose.resources.stringResource


/** 큰 점수(Noto Serif 굵게) + 작은 '점'. */
@Composable
internal fun RecordScoreText(record: PracticeRecord, size: TextUnit) {
    val colors = EolssuTheme.colors
    Text(
        buildAnnotatedString {
            append("${record.score}")
            withStyle(SpanStyle(fontSize = 12.sp, color = colors.textMuted, fontWeight = FontWeight.Medium)) { append(stringResource(Res.string.records_score_unit)) }
        },
        // 보드: 점수는 Noto Serif KR 굵게.
        style = EolssuTheme.serif.yul.copy(fontSize = size, lineHeight = size, fontWeight = FontWeight.Bold),
    )
}

/** '음정 88 · 박자 80 · 음 길이 84 · 60 BPM'. */
@Composable
internal fun recordMetricsLabel(record: PracticeRecord): String {
    val m = record.metrics.associate { it.metric to it.score }
    return stringResource(
        Res.string.record_detail_metrics,
        m[EvaluationMetric.Pitch] ?: 0, m[EvaluationMetric.Rhythm] ?: 0, m[EvaluationMetric.Length] ?: 0, record.bpm,
    )
}

/**
 * 테두리 안 이름 + 스위치(오류 표시·악보 따라가기).
 *
 * @param fillLabel 이름이 남는 너비를 채워 스위치를 오른쪽 끝에 둔다. 아니면 내용만큼의 너비가 된다.
 */
@Composable
internal fun SwitchBox(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    paddingH: Dp = 10.dp,
    fillLabel: Boolean = true,
) {
    Row(
        modifier.border(1.dp, EolssuTheme.colors.lineSoft, RoundedCornerShape(12.dp)).padding(horizontal = paddingH, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (fillLabel) 0.dp else 10.dp),
    ) {
        Text(
            label,
            modifier = if (fillLabel) Modifier.weight(1f) else Modifier,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
        // 보드: 40×24, 켜면 연두 · 끄면 `#EEEBE3`.
        EolssuSwitch(checked, onToggle, label, width = 40.dp, height = 24.dp, offColor = EolssuTheme.colors.surfaceStrong)
    }
}

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
        Text(formatClock((state.record?.recordedSeconds ?: 0).toDouble()), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
    }
}

/** 재생 위치 막대. */
@Composable
internal fun PlaybackSlider(state: RecordDetailUiState, actions: RecordDetailActions, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val seekLabel = stringResource(Res.string.record_detail_seek)
    Slider(
        value = state.position.toFloat(),
        onValueChange = { actions.onSeek(it.toInt()) },
        valueRange = 0f..(state.eventCount - 1).coerceAtLeast(1).toFloat(),
        modifier = modifier.height(24.dp).semantics { contentDescription = seekLabel },
        colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary, inactiveTrackColor = colors.surfaceStrong),
    )
}

/** −5초 · 재생 · +5초 버튼. */
@Composable
internal fun PlaybackButtons(state: RecordDetailUiState, actions: RecordDetailActions, skipSize: Dp, playSize: Dp, gap: Dp) {
    val colors = EolssuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
        SkipButton(stringResource(Res.string.record_detail_back5), stringResource(Res.string.record_detail_back5_label), skipSize) { actions.onSkip(-5) }
        // 녹음을 다시 들을 수 없는 기록(`recordingAvailable` false)은 재생 버튼을 쓸 수 없다.
        Surface(
            onClick = actions.onTogglePlay,
            enabled = state.canPlay,
            modifier = Modifier.size(playSize),
            shape = CircleShape,
            color = if (state.canPlay) colors.primary else colors.surfaceStrong,
            contentColor = if (state.canPlay) colors.onPrimary else colors.textDisabled,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (state.playing) EolssuIcons.Pause else EolssuIcons.Play,
                    contentDescription = stringResource(if (state.playing) Res.string.practice_pause else Res.string.practice_play),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        SkipButton(stringResource(Res.string.record_detail_fwd5), stringResource(Res.string.record_detail_fwd5_label), skipSize) { actions.onSkip(5) }
    }
}

/**
 * ‘지난 연주와 비교 · 10월 13일 68점 ›’(2차 RECORD-02 `RecordDetail-Ext`): 40dp · 반지름 10 · 크림 바탕. 누르면 COMPARE-01로 간다.
 * [previous]는 이 기록 바로 전의 같은 곡 전체 연주이고, 없으면 이름만 적는다.
 */
@Composable
internal fun CompareEntry(previous: PracticeRecord?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(10.dp), color = colors.accentTint, contentColor = colors.text) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append(stringResource(Res.string.weak_record_compare))
                    if (previous != null) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = colors.textSub)) {
                            append(" · " + stringResource(Res.string.weak_record_compare_with, monthDayLabel(previous.recordedAt), previous.score))
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

/** '취약 구간 연습하기' 버튼. 44dp 이상 큰 버튼이라 반지름 14 둥근 사각이다(시안 `border-radius: 14px`). */
@Composable
internal fun WeakSectionButton(onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 48.dp, paddingH: Dp = 12.dp) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.lineSoft),
    ) {
        Box(Modifier.padding(horizontal = paddingH), contentAlignment = Alignment.Center) {
            Text(
                stringResource(Res.string.record_detail_weak),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/**
 * 하단 재생 영역 내용(시간 · 막대 · 버튼 줄). 휴대폰과 태블릿 세로가 같은 구성이고 버튼 사이만 다르다([gap]).
 */
@Composable
internal fun PlaybackBarContent(state: RecordDetailUiState, actions: RecordDetailActions, gap: Dp) {
    PlaybackTimeRow(state)
    PlaybackSlider(state, actions)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
        PlaybackButtons(state, actions, skipSize = 48.dp, playSize = 64.dp, gap = gap)
        WeakSectionButton(actions.onWeakSection, Modifier.weight(1f))
    }
}

@Composable
private fun SkipButton(text: String, label: String, size: Dp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size).semantics { contentDescription = label },
        shape = CircleShape,
        color = EolssuTheme.colors.surface,
        border = BorderStroke(1.dp, EolssuTheme.colors.lineSoft),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

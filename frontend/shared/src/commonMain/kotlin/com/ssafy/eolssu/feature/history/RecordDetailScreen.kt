package com.ssafy.eolssu.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuSwitch
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.core.ui.component.timeLabel
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.ScoreNotation
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_pause
import eolssu.shared.generated.resources.practice_play
import eolssu.shared.generated.resources.record_detail_back5
import eolssu.shared.generated.resources.record_detail_back5_label
import eolssu.shared.generated.resources.record_detail_follow
import eolssu.shared.generated.resources.record_detail_fwd5
import eolssu.shared.generated.resources.record_detail_fwd5_label
import eolssu.shared.generated.resources.record_detail_hint
import eolssu.shared.generated.resources.record_detail_metrics
import eolssu.shared.generated.resources.record_detail_position
import eolssu.shared.generated.resources.record_detail_seek
import eolssu.shared.generated.resources.record_detail_show_errors
import eolssu.shared.generated.resources.record_detail_subtitle
import eolssu.shared.generated.resources.record_detail_title
import eolssu.shared.generated.resources.record_detail_weak
import eolssu.shared.generated.resources.record_detail_whole
import eolssu.shared.generated.resources.records_memo
import eolssu.shared.generated.resources.records_score_unit
import org.jetbrains.compose.resources.stringResource

/** 기록 상세에서 부르는 동작. */
class RecordDetailActions(
    val onBack: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onSeek: (Int) -> Unit,
    val onSkip: (Int) -> Unit,
    val onToggleErrors: () -> Unit,
    val onToggleFollow: () -> Unit,
    val onWeakSection: () -> Unit,
)

/** RECORD-02 기록 상세: 점수 요약, 오류 위치를 표시한 정간보, 녹음 재생 위치. */
@Composable
fun RecordDetailScreen(state: RecordDetailUiState, snackbarHostState: SnackbarHostState, actions: RecordDetailActions) {
    val record = state.record
    val song = state.song
    val colors = EolssuTheme.colors
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            EolssuTopBar(
                stringResource(Res.string.record_detail_title), onBack = actions.onBack,
                subtitle = record?.let {
                    stringResource(
                        Res.string.record_detail_subtitle,
                        "${monthDayLabel(it.recordedAt)} ${timeLabel(it.recordedAt)}",
                        it.songTitle,
                        it.section ?: stringResource(Res.string.record_detail_whole),
                    )
                },
            )
        },
        bottomBar = { if (song != null && record != null) PlaybackBar(state, actions) },
    ) {
        if (record == null || song == null) return@EolssuScaffold
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        append("${record.score}")
                        withStyle(SpanStyle(fontSize = 12.sp, color = colors.textMuted, fontWeight = FontWeight.Medium)) { append(stringResource(Res.string.records_score_unit)) }
                    },
                    style = EolssuTheme.serif.brand.copy(fontSize = 32.sp, lineHeight = 32.sp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val m = record.metrics.associate { it.metric to it.score }
                    Text(
                        stringResource(
                            Res.string.record_detail_metrics,
                            m[EvaluationMetric.Pitch] ?: 0, m[EvaluationMetric.Rhythm] ?: 0, m[EvaluationMetric.Length] ?: 0, record.bpm,
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.textMuted,
                    )
                    if (record.memo.isNotEmpty()) {
                        Text(stringResource(Res.string.records_memo, record.memo), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
                    }
                }
            }
            Row(Modifier.padding(top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchBox(stringResource(Res.string.record_detail_show_errors), state.showErrors, actions.onToggleErrors, Modifier.weight(1f))
                SwitchBox(stringResource(Res.string.record_detail_follow), state.follow, actions.onToggleFollow, Modifier.weight(1f))
            }
            Text(
                stringResource(Res.string.record_detail_hint),
                modifier = Modifier.padding(bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = colors.textMuted,
            )
            JeongganboView(
                song = song,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                notation = ScoreNotation(showHangul = true),
                currentIndex = state.position,
                follow = state.follow,
                errors = if (state.showErrors) record.errors.associate { (it.column to it.row) to it.metric } else emptyMap(),
                onEventClick = { actions.onSeek(it.index) },
            )
        }
    }
}

@Composable
private fun SwitchBox(label: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.border(1.dp, EolssuTheme.colors.line, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        EolssuSwitch(checked, onToggle, label)
    }
}

@Composable
private fun PlaybackBar(state: RecordDetailUiState, actions: RecordDetailActions) {
    val colors = EolssuTheme.colors
    val song = state.song ?: return
    val event = song.timeline.getOrNull(state.position)
    val seekLabel = stringResource(Res.string.record_detail_seek)
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
    Slider(
        value = state.position.toFloat(),
        onValueChange = { actions.onSeek(it.toInt()) },
        valueRange = 0f..(state.eventCount - 1).coerceAtLeast(1).toFloat(),
        modifier = Modifier.height(24.dp).semantics { contentDescription = seekLabel },
        colors = SliderDefaults.colors(thumbColor = colors.text, activeTrackColor = colors.text, inactiveTrackColor = colors.surfaceStrong),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SkipButton(stringResource(Res.string.record_detail_back5), stringResource(Res.string.record_detail_back5_label)) { actions.onSkip(-5) }
        Surface(onClick = actions.onTogglePlay, modifier = Modifier.size(64.dp), shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (state.playing) EolssuIcons.Pause else EolssuIcons.Play,
                    contentDescription = stringResource(if (state.playing) Res.string.practice_pause else Res.string.practice_play),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        SkipButton(stringResource(Res.string.record_detail_fwd5), stringResource(Res.string.record_detail_fwd5_label)) { actions.onSkip(5) }
        Surface(
            onClick = actions.onWeakSection,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = CircleShape,
            color = colors.background,
            border = BorderStroke(1.dp, colors.line),
        ) {
            Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.record_detail_weak),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SkipButton(text: String, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label },
        shape = CircleShape,
        color = EolssuTheme.colors.background,
        border = BorderStroke(1.dp, EolssuTheme.colors.line),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

package com.ssafy.eolssu.feature.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuBadge
import com.ssafy.eolssu.core.ui.component.EolssuBottomSheet
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIconButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import com.ssafy.eolssu.core.ui.component.timeLabel
import com.ssafy.eolssu.core.ui.score.ErrorLegend
import com.ssafy.eolssu.core.ui.score.ErrorScorePreview
import com.ssafy.eolssu.feature.result.layout.ResultExpandedLayout
import com.ssafy.eolssu.feature.result.layout.ResultMediumLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_close
import com.ssafy.eolssu.core.ui.component.songInstrumentLabel
import eolssu.shared.generated.resources.result_errors_cluster
import eolssu.shared.generated.resources.result_errors_count
import eolssu.shared.generated.resources.result_errors_none
import eolssu.shared.generated.resources.result_errors_preview
import eolssu.shared.generated.resources.result_errors_title
import eolssu.shared.generated.resources.result_go_records
import eolssu.shared.generated.resources.result_home
import eolssu.shared.generated.resources.result_memo_label
import eolssu.shared.generated.resources.result_memo_placeholder
import eolssu.shared.generated.resources.result_metric_length
import eolssu.shared.generated.resources.result_metric_pitch
import eolssu.shared.generated.resources.result_metric_rhythm
import eolssu.shared.generated.resources.result_other_song
import eolssu.shared.generated.resources.result_practice_again
import eolssu.shared.generated.resources.result_sample_badge
import eolssu.shared.generated.resources.result_save
import eolssu.shared.generated.resources.result_saved_meta
import eolssu.shared.generated.resources.result_saved_score
import eolssu.shared.generated.resources.result_saved_song
import eolssu.shared.generated.resources.result_saved_title
import eolssu.shared.generated.resources.result_score_unit
import eolssu.shared.generated.resources.result_subtitle
import eolssu.shared.generated.resources.result_title
import eolssu.shared.generated.resources.result_total_unit
import eolssu.shared.generated.resources.result_view_all_errors
import eolssu.shared.generated.resources.result_weak_section
import org.jetbrains.compose.resources.stringResource

/** 결과 화면에서 부르는 동작. */
class ResultActions(
    val onBack: () -> Unit,
    val onSave: () -> Unit,
    val onViewAllErrors: () -> Unit,
    val onWeakSection: () -> Unit,
    val onPracticeAgain: () -> Unit,
    val onOtherSong: () -> Unit,
    val onHome: () -> Unit,
    val onMemoChange: (String) -> Unit,
    val onCloseSheet: () -> Unit,
    val onGoRecords: () -> Unit,
    val onSelectFilter: (ErrorFilter) -> Unit,
    val onSelectCell: (ScoreCellPosition) -> Unit,
    val onRetrySong: () -> Unit = {},
)

/**
 * RESULT-02 평가 결과. 같은 상태를 창 너비에 따라 휴대폰([WindowWidthClass.Compact]), 태블릿 세로([WindowWidthClass.Medium]),
 * 태블릿 가로·넓은 웹 창([WindowWidthClass.Expanded]) 배치로 보여준다. 태블릿은 오류 위치 정간보를 같은 화면에 함께 둔다.
 */
@Composable
fun ResultScreen(
    state: ResultUiState,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    actions: ResultActions,
) {
    if (state.result == null || state.song == null) {
        EolssuScaffold(topBar = { EolssuTopBar(stringResource(Res.string.result_title), onBack = actions.onBack) }) {
            SongLookupContent(state.songLookup, actions.onRetrySong, actions.onBack, resultMissing = state.result == null)
        }
        return
    }
    when (widthClass) {
        WindowWidthClass.Compact -> ResultCompactLayout(state, snackbarHostState, actions)
        WindowWidthClass.Medium -> ResultMediumLayout(state, snackbarHostState, actions)
        WindowWidthClass.Expanded -> ResultExpandedLayout(state, snackbarHostState, actions)
    }
    val result = state.result
    val song = state.song
    if (state.sheetOpen && result != null && song != null) {
        SavedSheet(state, result, song, actions, tablet = widthClass != WindowWidthClass.Compact)
    }
}

/** 상단 바: 뒤로 · 제목(곡 · 악기 · 난이도) · 연습 기록 저장. */
@Composable
internal fun ResultTopBar(
    song: Song?,
    actions: ResultActions,
    modifier: Modifier = Modifier,
    maxWidth: Dp = CompactContentMaxWidth,
) {
    EolssuTopBar(
        title = stringResource(Res.string.result_title),
        onBack = actions.onBack,
        modifier = modifier,
        subtitle = song?.let {
            stringResource(Res.string.result_subtitle, it.title, songInstrumentLabel(it), songLevelLabel(it.level))
        },
        maxWidth = maxWidth,
        right = { EolssuIconButton(EolssuIcons.Bookmark, stringResource(Res.string.result_save), actions.onSave) },
    )
}

/** 휴대폰 배치. 오류 위치는 미리보기만 두고 '악보 전체에서 보기'로 넘어간다. */
@Composable
private fun ResultCompactLayout(state: ResultUiState, snackbarHostState: SnackbarHostState, actions: ResultActions) {
    val result = state.result
    val song = state.song
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        topBar = { ResultTopBar(song, actions) },
        bottomBar = {
            EolssuButton(stringResource(Res.string.result_practice_again), actions.onPracticeAgain, icon = EolssuIcons.Restart)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallOutlineButton(stringResource(Res.string.result_other_song), actions.onOtherSong, Modifier.weight(1f))
                SmallOutlineButton(stringResource(Res.string.result_home), actions.onHome, Modifier.weight(1f))
            }
        },
    ) {
        if (result == null || song == null) return@EolssuScaffold
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            TotalScore(result)
            MetricGrid(result, Modifier.padding(top = 16.dp))
            ErrorCard(result, song, actions.onViewAllErrors, Modifier.padding(top = 14.dp))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(EolssuIcons.Target, stringResource(Res.string.result_weak_section), actions.onWeakSection, Modifier.weight(1f))
                ActionTile(EolssuIcons.Bookmark, stringResource(Res.string.result_save), actions.onSave, Modifier.weight(1f))
            }
        }
    }
}

/** 총점·총평. 휴대폰은 가운데 정렬, 태블릿은 [alignStart]로 왼쪽에 붙이고 [scoreSize]로 글자 크기를 정한다. */
@Composable
internal fun TotalScore(
    result: EvaluationResult,
    modifier: Modifier = Modifier,
    alignStart: Boolean = false,
    scoreSize: TextUnit = 60.sp,
) {
    val colors = EolssuTheme.colors
    Column(
        if (alignStart) modifier else modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = if (alignStart) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        Text(
            buildAnnotatedString {
                append("${result.total}")
                withStyle(SpanStyle(fontSize = 18.sp, color = colors.textMuted, fontWeight = FontWeight.Medium)) {
                    append(stringResource(Res.string.result_total_unit))
                }
            },
            style = EolssuTheme.serif.brand.copy(fontSize = scoreSize, lineHeight = scoreSize),
        )
        Text(
            result.feedback,
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        )
        if (result.sample) EolssuBadge(stringResource(Res.string.result_sample_badge), Modifier.padding(top = 8.dp))
    }
}

/** 항목 점수 세 칸. [large]이면 태블릿 크기(여백·점수 글자)로 그린다. */
@Composable
internal fun MetricGrid(result: EvaluationResult, modifier: Modifier = Modifier, large: Boolean = false) {
    val colors = EolssuTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        result.metrics.forEach { metric ->
            Column(
                Modifier
                    .weight(1f)
                    .background(colors.surface, RoundedCornerShape(12.dp))
                    .padding(horizontal = if (large) 14.dp else 12.dp, vertical = if (large) 12.dp else 10.dp),
            ) {
                Text(metricLabel(metric.metric), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
                Text(
                    buildAnnotatedString {
                        append("${metric.score}")
                        withStyle(SpanStyle(fontSize = 11.sp, color = colors.textMuted, fontWeight = FontWeight.Medium)) {
                            append(stringResource(Res.string.result_score_unit))
                        }
                    },
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = if (large) 20.sp else 18.sp, fontWeight = FontWeight.Bold),
                )
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).background(colors.background, RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(metric.score / 100f).height(4.dp).background(colors.text, RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

@Composable
internal fun metricLabel(metric: EvaluationMetric): String = stringResource(
    when (metric) {
        EvaluationMetric.Pitch -> Res.string.result_metric_pitch
        EvaluationMetric.Rhythm -> Res.string.result_metric_rhythm
        EvaluationMetric.Length -> Res.string.result_metric_length
    },
)

@Composable
private fun ErrorCard(result: EvaluationResult, song: Song, onViewAll: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val cluster = result.densestColumnPair()
    Row(
        modifier.fillMaxWidth().border(1.dp, colors.line, MaterialTheme.shapes.medium).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(stringResource(Res.string.result_errors_title), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Text(
                    if (result.errors.isEmpty()) stringResource(Res.string.result_errors_none) else stringResource(Res.string.result_errors_count, result.errors.size),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textMuted,
                )
            }
            ErrorLegend()
            if (cluster != null) {
                Text(
                    stringResource(Res.string.result_errors_cluster, result.errors.size, cluster.second, cluster.first + 1, cluster.first + 2),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.5.sp),
                )
            }
            Row(
                Modifier.clickable(onClick = onViewAll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(Res.string.result_view_all_errors), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
        if (cluster != null) {
            val last = maxOf(3, cluster.first + 1).coerceAtMost(song.score.columns.lastIndex)
            val first = maxOf(0, last - 3)
            ErrorScorePreview(
                song = song,
                errors = result.errors,
                columns = first..last,
                highlight = cluster.first..cluster.first + 1,
                description = stringResource(Res.string.result_errors_preview, first + 1, last + 1),
            )
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), color = EolssuTheme.colors.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = EolssuTheme.colors.text)
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/** 테두리만 있는 알약 버튼(44dp). [filled]이면 회색 바탕, [icon]이 있으면 글자 앞에 둔다. */
@Composable
internal fun SmallOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    filled: Boolean = false,
    icon: ImageVector? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = CircleShape,
        color = if (filled) EolssuTheme.colors.surface else EolssuTheme.colors.background,
        border = BorderStroke(1.dp, EolssuTheme.colors.line),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            }
        }
    }
}

/** 저장 확인 시트. 태블릿([tablet])은 '닫기'와 '기록 보러 가기'를 한 줄에 둔다(시트 너비는 Material 기본값 최대 640dp). */
@Composable
private fun SavedSheet(state: ResultUiState, result: EvaluationResult, song: Song, actions: ResultActions, tablet: Boolean) {
    val colors = EolssuTheme.colors
    EolssuBottomSheet(
        onDismiss = actions.onCloseSheet,
        header = {
            Row(Modifier.padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.onPrimary)
                }
                Text(stringResource(Res.string.result_saved_title), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
            }
        },
        footer = {
            if (tablet) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EolssuButton(stringResource(Res.string.common_close), actions.onCloseSheet, Modifier.weight(1f), style = EolssuButtonStyle.Secondary)
                    EolssuButton(stringResource(Res.string.result_go_records), actions.onGoRecords, Modifier.weight(1.6f))
                }
            } else {
                EolssuButton(stringResource(Res.string.result_go_records), actions.onGoRecords)
                EolssuButton(stringResource(Res.string.common_close), actions.onCloseSheet, style = EolssuButtonStyle.Text)
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.result_saved_song, song.title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
                Text(stringResource(Res.string.result_saved_score, result.total), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
            }
            Text(
                stringResource(
                    Res.string.result_saved_meta,
                    "${monthDayLabel(result.recordedAt)} ${timeLabel(result.recordedAt)}",
                    formatClock(result.recordedSeconds.toDouble()),
                    result.bpm,
                ),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.result_memo_label), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            Box(
                Modifier.fillMaxWidth().height(46.dp).background(colors.surface, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                val textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = colors.text)
                if (state.memo.isEmpty()) Text(stringResource(Res.string.result_memo_placeholder), style = textStyle, color = colors.textMuted)
                BasicTextField(
                    value = state.memo,
                    onValueChange = actions.onMemoChange,
                    singleLine = true,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(colors.text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

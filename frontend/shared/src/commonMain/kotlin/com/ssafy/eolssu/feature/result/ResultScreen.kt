package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.diagnostics.PerfPhase
import com.ssafy.eolssu.core.diagnostics.observeFirstDraw
import com.ssafy.eolssu.core.ui.component.metricLabel
import com.ssafy.eolssu.core.ui.component.MetricCards
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
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
import com.ssafy.eolssu.feature.result.layout.ResultFillColumn
import com.ssafy.eolssu.feature.result.layout.ResultTabletLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_close
import com.ssafy.eolssu.core.ui.component.songInstrumentLabel
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
import eolssu.shared.generated.resources.result_saved_song
import eolssu.shared.generated.resources.result_saved_title
import eolssu.shared.generated.resources.result_score_unit
import eolssu.shared.generated.resources.result_subtitle
import eolssu.shared.generated.resources.result_title
import eolssu.shared.generated.resources.result_total_unit
import eolssu.shared.generated.resources.result_weak_section
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.ssafy.eolssu.feature.result.component.BoardTopBar
import com.ssafy.eolssu.feature.result.component.ErrorLocationCard
import com.ssafy.eolssu.feature.result.component.errorLocationCardHeight
import com.ssafy.eolssu.feature.result.component.LiteBadge
import com.ssafy.eolssu.feature.result.component.TotalScoreCard
import eolssu.shared.generated.resources.result_feedback_even_good
import eolssu.shared.generated.resources.result_feedback_even_low
import eolssu.shared.generated.resources.result_feedback_good_length
import eolssu.shared.generated.resources.result_feedback_good_pitch
import eolssu.shared.generated.resources.result_feedback_good_rhythm
import eolssu.shared.generated.resources.result_feedback_low_length
import eolssu.shared.generated.resources.result_feedback_low_pitch
import eolssu.shared.generated.resources.result_feedback_low_rhythm
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
    val onRetrySong: () -> Unit = {},
    val onRetryResult: () -> Unit = {},
)

/**
 * RESULT-02 평가 결과. 같은 상태를 창 너비에 따라 휴대폰([WindowWidthClass.Compact])과 태블릿·넓은 웹 창 배치로 보여준다.
 * 점수 중심 화면이라 오류는 틀린 정간 수만 적고, 정간보와 종류별 오류는 ‘오류 위치 상세 보기’(RESULT-03)에서 보여준다.
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
            SongLookupContent(
                if (state.result == null) state.resultLookup else state.songLookup,
                if (state.result == null) actions.onRetryResult else actions.onRetrySong,
                actions.onBack,
                resultLookup = state.result == null && state.scorePreview == null,
                header = state.scorePreview?.let { preview ->
                    {
                        Column(Modifier.widthIn(max = CompactContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            val summary = preview.summary()
                            TotalScoreCard(
                                total = preview.total,
                                feedback = resultFeedback(preview, summary),
                                practiceAgain = stringResource(Res.string.result_practice_again),
                                onPracticeAgain = actions.onPracticeAgain,
                            )
                            MetricCards(preview, summary.weak)
                        }
                    }
                },
            )
        }
        return
    }
    when (widthClass) {
        WindowWidthClass.Compact -> ResultCompactLayout(state, snackbarHostState, actions)
        // 태블릿 가로도 세로와 같은 가운데 배치다. 오류 정간보는 상세 화면에서 본다.
        WindowWidthClass.Medium, WindowWidthClass.Expanded -> ResultTabletLayout(state, snackbarHostState, actions)
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

/**
 * 휴대폰 배치(연두 보드 RESULT-02). 위에서부터 총점 카드 · 항목 세 칸 · 오류 위치 요약 카드 · ‘홈으로 가기’와 ‘다른 곡 고르기’,
 * 아래 고정 바에 ‘연습 기록 저장’ · ‘취약 구간 연습’을 같은 너비로 둔다. 상단 바에는 뒤로 가기·저장 아이콘이 없다.
 */
@Composable
private fun ResultCompactLayout(state: ResultUiState, snackbarHostState: SnackbarHostState, actions: ResultActions) {
    val result = state.result
    val song = state.song
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            BoardTopBar(
                stringResource(Res.string.result_title),
                subtitle = song?.let { stringResource(Res.string.result_subtitle, it.title, songInstrumentLabel(it), songLevelLabel(it.level)) },
            )
        },
        bottomBar = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EolssuButton(
                    stringResource(Res.string.result_save), actions.onSave, Modifier.weight(1f),
                    icon = EolssuIcons.Save, busy = state.savingRecord, compact = true,
                )
                EolssuButton(
                    stringResource(Res.string.result_weak_section), actions.onWeakSection, Modifier.weight(1f),
                    style = EolssuButtonStyle.Secondary, icon = EolssuIcons.Restart, compact = true,
                )
            }
        },
    ) {
        if (result == null || song == null) return@EolssuScaffold
        val summary = result.summary()
        // 틀린 부분 카드는 곡과 관계없이 같은 높이로 고정된다([errorLocationCardHeight]). 악보가 카드보다 길면 아랫부분만 가려진다.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fontScale = LocalDensity.current.fontScale
            val cardHeight = remember(fontScale) { errorLocationCardHeight(fontScale) }
            ResultFillColumn(
                fillHeight = cardHeight,
                gap = 14.dp,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                top = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        TotalScoreCard(
                            total = result.total,
                            feedback = resultFeedback(result, summary),
                            practiceAgain = stringResource(Res.string.result_practice_again),
                            onPracticeAgain = actions.onPracticeAgain,
                            extra = if (result.sample) {
                                { LiteBadge(stringResource(Res.string.result_sample_badge)) }
                            } else {
                                null
                            },
                        )
                        MetricCards(result, summary.weak)
                    }
                },
                fill = { ErrorLocationCard(result, song, actions.onViewAllErrors) },
                bottom = {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EolssuButton(stringResource(Res.string.result_home), actions.onHome, style = EolssuButtonStyle.Secondary, icon = EolssuIcons.House)
                        Text(
                            stringResource(Res.string.result_other_song),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable(role = Role.Button, onClick = actions.onOtherSong)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                            color = EolssuTheme.colors.textSub,
                        )
                    }
                },
            )
        }
    }
}

/** 총평: 서버 총평이 있으면 그것을, 없으면 [ResultSummary]로 고른 앱 문구(가장 낮은 항목 · 총점 80 기준). */
@Composable
internal fun resultFeedback(result: EvaluationResult, summary: ResultSummary = result.summary()): String {
    if (result.feedback.isNotBlank()) return result.feedback
    return stringResource(
        when (summary.focus) {
            null -> if (summary.good) Res.string.result_feedback_even_good else Res.string.result_feedback_even_low
            EvaluationMetric.Pitch -> if (summary.good) Res.string.result_feedback_good_pitch else Res.string.result_feedback_low_pitch
            EvaluationMetric.Rhythm -> if (summary.good) Res.string.result_feedback_good_rhythm else Res.string.result_feedback_low_rhythm
            EvaluationMetric.Length -> if (summary.good) Res.string.result_feedback_good_length else Res.string.result_feedback_low_length
        },
    )
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
            modifier = Modifier.observeFirstDraw(PerfPhase.SCORE_DRAWN),
            style = EolssuTheme.serif.brand.copy(fontSize = scoreSize, lineHeight = scoreSize),
        )
        // 서버 결과는 총평이 없다.
        if (result.feedback.isNotEmpty()) {
            Text(
                result.feedback,
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
        }
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

/**
 * 저장 확인 시트(보드 Result-Saved `.sheet`, 사이 14): 연두 원 안 체크 · ‘연습 기록에 저장했어요’(Song Myung 19sp) ·
 * 크림 카드(곡 · 녹음 정보 · 점수 Song Myung 26sp) · 메모 입력(52dp) · ‘기록 보러 가기’와 ‘닫기’(바탕 없음).
 * 태블릿([tablet])은 ‘닫기’와 ‘기록 보러 가기’를 한 줄에 둔다(시트 너비는 Material 기본값 최대 640dp).
 */
@Composable
private fun SavedSheet(state: ResultUiState, result: EvaluationResult, song: Song, actions: ResultActions, tablet: Boolean) {
    val colors = EolssuTheme.colors
    EolssuBottomSheet(
        onDismiss = actions.onCloseSheet,
        header = {
            Row(Modifier.padding(top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(24.dp), tint = colors.onPrimary)
                }
                Text(
                    stringResource(Res.string.result_saved_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 24.7.sp),
                    color = colors.text,
                )
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
                EolssuButton(stringResource(Res.string.common_close), actions.onCloseSheet, style = EolssuButtonStyle.Ghost)
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth().background(colors.accentTint, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.result_saved_song, song.title),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold),
                        color = colors.text,
                    )
                    Text(
                        stringResource(
                            Res.string.result_saved_meta,
                            "${monthDayLabel(result.recordedAt)} ${timeLabel(result.recordedAt)}",
                            formatClock(result.recordedSeconds.toDouble()),
                            result.bpm,
                        ),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = colors.textMuted,
                    )
                }
                Text(
                    buildAnnotatedString {
                        append("${result.total}")
                        withStyle(
                            SpanStyle(
                                fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                                fontSize = 12.sp,
                                color = colors.textMuted,
                            ),
                        ) {
                            append(stringResource(Res.string.result_score_unit))
                        }
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 26.sp, lineHeight = 30.sp),
                    color = colors.text,
                    maxLines = 1,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(Res.string.result_memo_label),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.textSub,
                )
                val fieldShape = RoundedCornerShape(12.dp)
                Box(
                    Modifier.fillMaxWidth().height(52.dp).background(colors.surface, fieldShape).border(1.dp, colors.line, fieldShape).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = colors.text)
                    if (state.memo.isEmpty()) Text(stringResource(Res.string.result_memo_placeholder), style = textStyle, color = colors.textDisabled)
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
}

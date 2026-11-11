package com.ssafy.eolssu.feature.result.component

import com.ssafy.eolssu.core.diagnostics.PerfPhase
import com.ssafy.eolssu.core.diagnostics.observeFirstDraw
import com.ssafy.eolssu.core.ui.score.errorCount
import com.ssafy.eolssu.core.ui.score.visibleErrors
import com.ssafy.eolssu.core.ui.score.visibleErrorKinds
import com.ssafy.eolssu.core.ui.score.visibleUnitErrors

import com.ssafy.eolssu.core.ui.score.ErrorFilter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.ScoreErrorKind
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.feature.result.ResultSummary
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.enso_ring
import eolssu.shared.generated.resources.enso_spin
import eolssu.shared.generated.resources.result_errors_count
import eolssu.shared.generated.resources.result_errors_none
import eolssu.shared.generated.resources.result_errors_title
import eolssu.shared.generated.resources.result_metric_weak
import eolssu.shared.generated.resources.result_score_of
import eolssu.shared.generated.resources.result_score_unit
import eolssu.shared.generated.resources.result_view_all_errors
import eolssu.shared.generated.resources.tiger_clap
import eolssu.shared.generated.resources.tiger_complete
import eolssu.shared.generated.resources.tiger_encourage
import eolssu.shared.generated.resources.tiger_play
import eolssu.shared.generated.resources.tiger_retry
import eolssu.shared.generated.resources.tiger_zero_score
import eolssu.shared.generated.resources.tiger_analysis_failed
import eolssu.shared.generated.resources.tiger_confused_atlas
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.feature.practice.component.PracticeSplitLineFill
import com.ssafy.eolssu.feature.practice.component.PracticeStackGap
import com.ssafy.eolssu.feature.practice.component.practiceScorePacking
import com.ssafy.eolssu.core.ui.score.ScoreHorizontalPadding
import com.ssafy.eolssu.core.ui.score.scoreStrips
import eolssu.shared.generated.resources.result_errors_preview

/*
 * 연두 보드 ANALYSIS-01 · RESULT-02 부품. 크기·색은 보드 CSS(`.topbar` · `.badge.lite` · `.card.tint` · `.ring` ·
 * `.lv.l2` · `.progress` · `.ms` · `.btn.sm` · `.btn.ghost`)를 dp로 옮겼다.
 */

private val CardShape = RoundedCornerShape(16.dp)

/** 보드 `.topbar`(56dp): 가운데 제목(Song Myung 19sp)과 보조 문구(12sp). 분석·결과 화면은 뒤로 가기·오른쪽 버튼이 없다. */
@Composable
fun BoardTopBar(title: String, modifier: Modifier = Modifier, subtitle: String? = null, maxWidth: Dp = CompactContentMaxWidth) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = maxWidth).fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 56.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 22.8.sp),
                color = EolssuTheme.colors.text,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = EolssuTheme.colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 보드 분석 대기 그림(220dp): 붓 일원상(210dp)이 3.2초에 한 바퀴 돌고 가운데에 연주하는 호랑이(128dp). */
@Composable
fun AnalyzingFigure(modifier: Modifier = Modifier) {
    val rotation by rememberInfiniteTransition(label = "enso").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3200, easing = LinearEasing)),
        label = "enso-rotation",
    )
    Box(modifier.size(220.dp).clearAndSetSemantics {}) {
        Image(
            painterResource(Res.drawable.enso_spin),
            contentDescription = null,
            modifier = Modifier.padding(5.dp).fillMaxSize().graphicsLayer { rotationZ = rotation },
        )
        // 보드 `translate(-50%, -52%)`: 호랑이 높이(약 104dp)의 2%만큼 위로.
        Image(
            painterResource(Res.drawable.tiger_play),
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).offset(y = (-2).dp).width(128.dp),
            contentScale = ContentScale.FillWidth,
        )
    }
}

// 사용자 제공 ‘머리 긁는 호랑이’ GIF의 9프레임(각 160ms)을 배경만 투명하게 뺀 240px 세로 atlas.
private const val ConfusedFrameSize = 240
private const val ConfusedFrameCount = 9
private const val ConfusedFrameMillis = 160L

/**
 * 분석 실패 그림. 녹음 문제로 다시 연주해야 하면([recordingProblem]) 물음표를 띄우고 머리를 긁는 호랑이 움짤(160dp),
 * 서버·전송 문제면 실패 전용 호랑이 그림(132dp)이다.
 */
@Composable
fun AnalysisFailedFigure(recordingProblem: Boolean) {
    if (!recordingProblem) {
        Image(
            painterResource(Res.drawable.tiger_analysis_failed),
            contentDescription = null,
            modifier = Modifier.size(132.dp),
            contentScale = ContentScale.Fit,
        )
        return
    }
    val bitmap = imageResource(Res.drawable.tiger_confused_atlas)
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(ConfusedFrameMillis)
            frame = (frame + 1) % ConfusedFrameCount
        }
    }
    Canvas(Modifier.size(160.dp).clearAndSetSemantics {}) {
        if (bitmap.height >= ConfusedFrameSize * ConfusedFrameCount) drawImage(
            image = bitmap,
            srcOffset = IntOffset(0, frame * ConfusedFrameSize),
            srcSize = IntSize(ConfusedFrameSize, ConfusedFrameSize),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
    }
}

/** 보드 `.badge.lite`: 흰 바탕 · 옅은 테두리 알약(26dp)에 12sp 회색 글자. 곡 이름 등에 쓴다. */
@Composable
fun LiteBadge(text: String, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier.heightIn(min = 26.dp).background(colors.surface, shape).border(1.dp, colors.lineSoft, shape).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.textMuted, maxLines = 1)
    }
}

/**
 * 보드 `.ring`(112dp): 연두 붓 일원상을 점수와 관계없이 그림 전체로 그리고,
 * 가운데에 점수(Song Myung 38sp)와 ‘/ 100점’(11sp)을 둔다.
 * 붓 획은 8시 방향에서 시작해 한 바퀴 돌아 7시 근처에서 꼬리가 끝나며, 꼬리까지 모두 연두로 칠한다.
 */
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val ring = painterResource(Res.drawable.enso_ring)
    val total = score.coerceIn(0, 100)
    val unit = stringResource(Res.string.result_score_of)
    Box(modifier.observeFirstDraw(PerfPhase.SCORE_DRAWN).size(112.dp).semantics(mergeDescendants = true) { contentDescription = "$total$unit" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { with(ring) { draw(size) } }
        Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$total", style = MaterialTheme.typography.titleMedium.copy(fontSize = 38.sp, lineHeight = 38.sp), color = colors.text)
            Text(
                unit,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                color = colors.textMuted,
            )
        }
    }
}

/** 보드 `.btn.primary.sm`: 40dp · 반지름 12 · 14sp · 좌우 12dp, 아이콘 16dp. 누르는 영역은 48dp를 유지한다. */
@Composable
fun SmallPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = EolssuTheme.colors
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 40.dp), shape = RoundedCornerShape(12.dp), color = colors.primary) {
        Row(
            Modifier.heightIn(min = 40.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onPrimary)
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = colors.onPrimary,
            )
        }
    }
}

/** 보드 `.btn.ghost.sm`: 바탕 없는 40dp 버튼(14sp `#5C564F`), 글자 뒤 화살표 16dp. */
@Composable
fun SmallGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: ImageVector? = EolssuIcons.Chevron,
    fontSize: TextUnit = 14.sp,
    minHeight: Dp = 40.dp,
) {
    val colors = EolssuTheme.colors
    val small = fontSize < 14.sp
    Surface(onClick = onClick, modifier = modifier.heightIn(min = minHeight), shape = RoundedCornerShape(12.dp), color = Color.Transparent) {
        Row(
            Modifier.heightIn(min = minHeight).padding(horizontal = if (small) 4.dp else 6.dp),
            horizontalArrangement = Arrangement.spacedBy(if (small) 2.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize, lineHeight = fontSize * 1.3f, fontWeight = FontWeight.Normal), color = colors.textSub, maxLines = 1)
            if (trailing != null) Icon(trailing, contentDescription = null, modifier = Modifier.size(if (small) 12.dp else 16.dp), tint = colors.textSub)
        }
    }
}

/**
 * 보드 총점 카드(`.card.tint`, 반지름 16 · 안쪽 14): 점수 고리 · 총평(16sp 굵게)과 ‘같은 곡 다시 연습’ 작은 버튼,
 * 오른쪽 아래에는 총점에 맞는 호랑이를 표시한다. [extra]는 총평 아래(예시 결과 표시 등)에 둔다.
 */
@Composable
fun TotalScoreCard(
    total: Int,
    feedback: String,
    practiceAgain: String,
    onPracticeAgain: () -> Unit,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
) {
    val colors = EolssuTheme.colors
    val mascot = when {
        total == 0 -> Res.drawable.tiger_zero_score
        total >= 90 -> Res.drawable.tiger_complete
        total >= ResultSummary.GoodScore -> Res.drawable.tiger_clap
        total >= 50 -> Res.drawable.tiger_encourage
        else -> Res.drawable.tiger_retry
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val reserveMascotSpace = maxWidth < 350.dp || LocalDensity.current.fontScale > 1f
        Row(
            Modifier.fillMaxWidth().background(colors.accentSoft, CardShape).border(1.dp, colors.accentTintStrong, CardShape).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScoreRing(total)
            Column(
                // 버튼이 두 줄이 될 수 있는 폭이나 글자 크기에서는 그림을 버튼 아래에 둔다.
                Modifier.weight(1f).padding(bottom = if (reserveMascotSpace) 48.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    feedback,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.6.sp, fontWeight = FontWeight.Bold),
                    color = colors.text,
                )
                SmallPrimaryButton(practiceAgain, onPracticeAgain, icon = EolssuIcons.Restart)
                extra?.invoke()
            }
        }
        Image(
            painterResource(mascot),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp).offset(y = 4.dp).width(64.dp),
            contentScale = ContentScale.FillWidth,
        )
    }
}

/**
 * 점수 화면의 틀린 부분 카드(`.card`, 안쪽 14 · 사이 10): 제목(Song Myung 17sp)과 틀린 정간 수 ·
 * 취약 구간 정간보 · 오른쪽 아래 작은 ‘틀린 부분 상세 보기 ›’. 범례·종류 필터·고른 정간 설명은 상세 화면(RESULT-03)에서만 보여준다.
 *
 * 정간보는 악보 연습·AI 연주 평가 휴대폰 악보와 같은 크기·모양(연습 보드 [JeongganboMetrics.practiceBoard] ·
 * [practiceScorePacking] · [JeongganboLook.practice])으로 그린다. 악보 전체를 그리되 틀린 칸이 가장 몰린 두 각
 * ([EvaluationResult.densestColumnPair])이 카드 가운데 오도록 고정하고([weakSectionBoard]) 좌우 각은 카드 폭만큼 보인다. 스크롤은 없다.
 * 긴 각은 연습 화면처럼 다음 줄로 넘기고, 정간은 연습 최소 크기보다 줄이지 않는다([weakSectionScoreHeight]). 페이지는 스크롤하지 않으며
 * 카드보다 긴 악보는 각 머리(시작 각 번호)부터 보이고 아랫부분만 가려진다.
 * 높이는 [modifier]로 정해 준다(`weight`·`height`).
 */
@Composable
fun ErrorLocationCard(result: EvaluationResult, song: Song, onViewDetail: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val count = result.errorCount(ErrorFilter.All)
    val cluster = result.densestColumnPair()
    val columnCount = song.score.columns.size
    val longestStrip = remember(song) { scoreStrips(song.score, song.jangdan).maxOfOrNull { it.rows.count() } ?: 0 }
    Column(
        modifier.fillMaxWidth().background(colors.surface, CardShape).border(1.dp, colors.lineSoft, CardShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.result_errors_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
                color = colors.text,
            )
            Text(
                if (count == 0) stringResource(Res.string.result_errors_none) else stringResource(Res.string.result_errors_count, count),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textMuted,
            )
        }
        if (columnCount > 0) {
            // 악보 전체를 그리고 취약 구간이 카드 가운데 오도록 고정한다. 좌우의 다른 각도 카드 폭만큼 보이고 스크롤은 없다.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                val fontScale = LocalDensity.current.fontScale
                val board = remember(song, longestStrip, cluster, maxWidth, maxHeight, fontScale) {
                    weakSectionBoard(song, longestStrip, cluster?.first, maxWidth, maxHeight, fontScale)
                }
                val description = stringResource(Res.string.result_errors_preview, board.weak.first + 1, board.weak.last + 1)
                JeongganboView(
                    song = song,
                    // 카드보다 넓으면 가운데 정렬된 뒤 [WeakSectionBoard.offset]만큼 옮겨 취약 구간을 가운데 둔다.
                    // 높이는 카드에 맞추지 않고 위(각 머리)부터 그린다. 카드보다 길면 아랫부분만 가려진다.
                    modifier = Modifier.align(Alignment.TopCenter).wrapContentHeight(Alignment.Top, unbounded = true)
                        .requiredWidth(board.width).offset(x = board.offset)
                        .semantics { contentDescription = description },
                    metrics = board.metrics,
                    scrollState = null,
                    errors = result.visibleErrors(ErrorFilter.All),
                    errorKinds = result.visibleErrorKinds(ErrorFilter.All),
                    unitErrors = result.visibleUnitErrors(song, ErrorFilter.All),
                    columns = board.columns,
                    look = JeongganboLook.practice(big = false),
                    columnsPerStack = board.columnsPerStack,
                    splitColumns = board.splitColumns,
                    stackGap = board.stackGap,
                    cellHeights = board.cellHeights,
                    stackRowHeights = board.stackRowHeights,
                )
            }
        }
        SmallGhostButton(
            stringResource(Res.string.result_view_all_errors), onViewDetail, Modifier.align(Alignment.End),
            fontSize = 11.sp, minHeight = 32.dp,
        )
    }
}

/** 카드에 그릴 정간보의 배치·크기. 악보 전체([columns])를 그리고 취약 구간([weak])이 카드 가운데 오게 [offset]만큼 옮긴다. */
internal data class WeakSectionBoard(
    val columns: IntRange,
    /** 틀린 칸이 가장 몰린 두 각(틀린 칸이 없으면 첫 각). */
    val weak: IntRange,
    val metrics: JeongganboMetrics,
    val columnsPerStack: Int,
    val splitColumns: Boolean,
    val stackGap: Dp,
    val cellHeights: Map<Pair<Int, Int>, Dp>,
    val stackRowHeights: List<Dp>,
    /** 악보 전체 폭. 카드보다 넓을 수 있다. */
    val width: Dp,
    /** 카드 가운데 정렬 기준에서 오른쪽(+)으로 옮길 거리. */
    val offset: Dp,
)

/**
 * 연습 화면 휴대폰 악보와 같은 배치([practiceScorePacking])를 [maxHeight]에 맞춰 구하고, 악보 전체 중 취약 구간이
 * 카드([maxWidth]) 가운데 오도록 위치를 정한다. 악보 끝 쪽 각이라 가운데에 둘 수 없으면 빈 곳이 생기지 않게 끝에 맞춘다.
 * 연습·평가 악보와 같은 크기를 지키려고 폭에 맞춰 줄이지 않는다.
 */
internal fun weakSectionBoard(
    song: Song,
    longestStrip: Int,
    clusterStart: Int?,
    maxWidth: Dp,
    maxHeight: Dp,
    fontScale: Float,
): WeakSectionBoard {
    val base = JeongganboMetrics.practiceBoard(big = false, longestStrip = longestStrip).copy(splitLineFill = PracticeSplitLineFill)
    // 카드가 낮아도 연습·평가 크기를 지키려고 최소 정간이 들어가는 높이 이상으로 배치한다. 넘치는 아랫부분은 카드에서 가려진다.
    val packHeight = maxOf(maxHeight, weakSectionScoreHeight(song, base, fontScale))
    val packing = practiceScorePacking(song.score, base, packHeight, fontScale, song.jangdan)
    val counts = scoreStrips(song.score, song.jangdan, splitColumns = packing.splitColumns).groupingBy { it.column }.eachCount()
    val stripsPerColumn = List(song.score.columns.size) { counts[it] ?: 1 }
    val perStack = packing.columnsPerStack.coerceAtLeast(1)
    val weak = weakColumns(clusterStart, stripsPerColumn.size)
    val lines = weakSectionLines(stripsPerColumn, perStack, weak)
    val rendered = packing.metrics.forFontScale(fontScale)
    // 연습·평가 악보와 같은 크기를 지키려고 폭에 맞춰 줄이지 않는다. 카드보다 넓은 부분은 좌우 끝에서 가려진다.
    val width = rendered.columnWidth * lines.total + rendered.columnGap * (lines.total - 1).coerceAtLeast(0) + ScoreHorizontalPadding
    // 첫 각이 오른쪽 끝에서 시작한다. 취약 구간 가운데의 오른쪽 끝에서의 거리.
    val fromRight = ScoreHorizontalPadding / 2 + (rendered.columnWidth + rendered.columnGap) * lines.weakCenter + rendered.columnWidth / 2
    val slack = ((width - maxWidth) / 2).coerceAtLeast(0.dp)
    val offset = (fromRight - width / 2).coerceIn(-slack, slack)
    return WeakSectionBoard(
        columns = if (stripsPerColumn.isEmpty()) IntRange.EMPTY else stripsPerColumn.indices,
        weak = weak,
        metrics = packing.metrics,
        columnsPerStack = perStack,
        splitColumns = packing.splitColumns,
        stackGap = PracticeStackGap,
        cellHeights = packing.cellHeights,
        stackRowHeights = packing.stackRowHeights,
        width = width,
        offset = offset,
    )
}

/** 카드 틀(안쪽 여백 14×2 · 사이 10×2 · 제목 줄 22sp · ‘틀린 부분 상세 보기’ 32)의 높이. */
private fun errorLocationCardChrome(fontScale: Float): Dp = 28.dp + 20.dp + 22.dp * fontScale.coerceAtLeast(1f) + 32.dp

/**
 * 틀린 부분 카드의 고정 높이. 곡과 관계없이 같다: 카드 틀 + [ErrorLocationScoreHeight].
 * 악보는 카드 높이와 관계없이 연습·평가 크기로 그리고([weakSectionScoreHeight]) 카드보다 길면 아랫부분이 가려진다.
 */
internal fun errorLocationCardHeight(fontScale: Float): Dp = errorLocationCardChrome(fontScale) + ErrorLocationScoreHeight

/**
 * 카드 안 악보 칸 높이. 단소 아리랑(한 각 12정간 중 3소박 8개)을 연습·평가 최소 정간 32dp로 두 줄에 나눠 그린 높이
 * (여백 14 + 각 머리 22 + 2 + 한 줄 무게 8 × 32 = 294, 4dp 단위로 맞춰 296)다.
 */
private val ErrorLocationScoreHeight = 296.dp

/**
 * 연습·평가 악보의 최소 정간(연습 [practiceScorePacking]의 최소 정간) 아래로 줄지 않는 가장 낮은 악보 높이.
 * 연습 배치를 그대로 돌려 최소 정간 이상이 되는 높이를 찾는다. 카드가 이보다 낮아도 이 높이로 배치해 크기를 지킨다.
 * [oneLine]이면 긴 각을 두 줄로 나누는 배치([PracticeScorePacking.splitColumns])는 건너뛰고 각이 한 줄에 들어가는 높이를 찾는다
 * (틀린 부분 상세, S15P31E102-740).
 */
internal fun weakSectionScoreHeight(song: Song, base: JeongganboMetrics, fontScale: Float, oneLine: Boolean = false): Dp {
    if (song.score.columns.isEmpty()) return 0.dp
    val minimum = minOf(base.cellHeight, if (base.cellHeight > 44.dp) 44.dp else 32.dp)
    var height = WeakSectionMinScan
    while (height < WeakSectionMaxScan) {
        val packing = practiceScorePacking(song.score, base, height, fontScale, song.jangdan)
        if (packing.metrics.cellHeight >= minimum && !(oneLine && packing.splitColumns)) return height
        height += WeakSectionScanStep
    }
    return WeakSectionMaxScan
}

private val WeakSectionMinScan = 40.dp
private val WeakSectionMaxScan = 1600.dp
private val WeakSectionScanStep = 4.dp

/** 틀린 칸이 가장 몰린 두 각([clusterStart], [clusterStart]+1). 틀린 칸이 없으면 첫 각. */
internal fun weakColumns(clusterStart: Int?, columnCount: Int): IntRange {
    if (columnCount <= 0) return IntRange.EMPTY
    val last = columnCount - 1
    if (clusterStart == null) return 0..0
    val first = clusterStart.coerceIn(0, last)
    return first..minOf(first + 1, last)
}

/** 악보 전체 세로줄 수와 취약 구간의 세로줄 수·가운데 위치(오른쪽 첫 줄부터 0, 소수 가능). */
internal data class WeakSectionLines(val total: Int, val weakCount: Int, val weakCenter: Float)

/**
 * 악보 전체를 그릴 때 세로줄 수와 취약 구간([weak])이 놓이는 줄. 한 세로줄에 한 각(또는 한 각의 여러 줄)을 두면 각마다
 * [stripsPerColumn]줄, 연습 화면처럼 [columnsPerStack]개 각을 위아래로 쌓으면 묶음마다 한 줄이다.
 */
internal fun weakSectionLines(stripsPerColumn: List<Int>, columnsPerStack: Int, weak: IntRange): WeakSectionLines {
    if (stripsPerColumn.isEmpty() || weak.isEmpty()) return WeakSectionLines(0, 0, 0f)
    val firstLine: Int
    val lastLine: Int
    val total: Int
    if (columnsPerStack > 1) {
        total = (stripsPerColumn.size + columnsPerStack - 1) / columnsPerStack
        firstLine = weak.first / columnsPerStack
        lastLine = weak.last / columnsPerStack
    } else {
        total = stripsPerColumn.sum()
        firstLine = (0 until weak.first).sumOf { stripsPerColumn[it] }
        lastLine = firstLine + weak.sumOf { stripsPerColumn[it] } - 1
    }
    return WeakSectionLines(total, lastLine - firstLine + 1, (firstLine + lastLine) / 2f)
}

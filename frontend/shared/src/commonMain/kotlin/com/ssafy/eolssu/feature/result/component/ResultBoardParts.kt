package com.ssafy.eolssu.feature.result.component

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import com.ssafy.eolssu.core.ui.score.ReadingScoreFormat
import com.ssafy.eolssu.core.ui.score.ScoreHorizontalPadding
import com.ssafy.eolssu.core.ui.score.rememberReadingScoreFormat
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

/** 실패 사유에 맞는 호랑이 오른쪽 아래에 경고 배지를 붙인다. */
@Composable
fun AnalysisFailedFigure(mascot: DrawableResource, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Box(modifier.size(width = 152.dp, height = 122.dp).clearAndSetSemantics {}) {
        Image(
            painterResource(mascot),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopCenter).width(144.dp),
            contentScale = ContentScale.FillWidth,
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(40.dp)
                .background(colors.background, CircleShape)
                .padding(3.dp)
                .background(colors.recSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(EolssuIcons.Alert, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.recText)
        }
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
    Box(modifier.size(112.dp).semantics(mergeDescendants = true) { contentDescription = "$total$unit" }, contentAlignment = Alignment.Center) {
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
 * 틀린 정간이 가장 몰린 곳의 정간보(공통 형식 [ReadingScoreFormat], 카드 폭에 들어가는 각만, 스크롤 없음) ·
 * 오른쪽 아래 작은 ‘틀린 부분 상세 보기 ›’. 범례·종류 필터·고른 정간 설명은 상세 화면(RESULT-03)에서만 보여준다.
 */
@Composable
fun ErrorLocationCard(result: EvaluationResult, song: Song, onViewDetail: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val count = result.errorCount(ErrorFilter.All)
    val cluster = result.densestColumnPair()
    val columnCount = song.score.columns.size
    val format = rememberReadingScoreFormat(song)
    val stripsPerColumn = remember(song) {
        val counts = scoreStrips(song.score, song.jangdan).groupingBy { it.column }.eachCount()
        List(columnCount) { counts[it] ?: 1 }
    }
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
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val rendered = format.metrics.forFontScale(LocalDensity.current.fontScale)
                val slots = ((maxWidth - ScoreHorizontalPadding + rendered.columnGap) / (rendered.columnWidth + rendered.columnGap))
                    .toInt().coerceAtLeast(1)
                val shown = previewColumns(cluster?.first, stripsPerColumn, slots)
                val description = stringResource(Res.string.result_errors_preview, shown.first + 1, shown.last + 1)
                // 보이는 줄만큼의 폭으로 카드 가운데에 둔다(각이 적은 곡도 한쪽에 붙지 않게).
                val used = shown.sumOf { stripsPerColumn[it] }
                val width = rendered.columnWidth * used + rendered.columnGap * (used - 1) + ScoreHorizontalPadding
                JeongganboView(
                    song = song,
                    modifier = Modifier.align(Alignment.Center).width(width).semantics { contentDescription = description },
                    metrics = format.metrics,
                    scrollState = null,
                    errors = result.visibleErrors(ErrorFilter.All),
                    errorKinds = result.visibleErrorKinds(ErrorFilter.All),
                    unitErrors = result.visibleUnitErrors(song, ErrorFilter.All),
                    columns = shown,
                    look = format.look,
                    cellHeights = format.cellHeights,
                )
            }
        }
        SmallGhostButton(
            stringResource(Res.string.result_view_all_errors), onViewDetail, Modifier.align(Alignment.End),
            fontSize = 11.sp, minHeight = 32.dp,
        )
    }
}

/**
 * 카드에 보여 줄 각 범위. 틀린 칸이 가장 몰린 두 각([clusterStart], [clusterStart]+1)부터 앞 각으로 넓히고,
 * 자리가 남으면 뒤 각으로 넓힌다. 각마다 세로 줄 수([stripsPerColumn])를 더해 [slots]를 넘지 않는다.
 * 틀린 칸이 없으면 첫 각부터 보여 준다. 첫 각은 자리가 모자라도 하나는 보여 준다.
 */
internal fun previewColumns(clusterStart: Int?, stripsPerColumn: List<Int>, slots: Int): IntRange {
    val last = stripsPerColumn.lastIndex
    if (last < 0) return IntRange.EMPTY
    val anchor = (clusterStart?.plus(1) ?: 0).coerceIn(0, last)
    var first = anchor
    var end = anchor
    var used = stripsPerColumn[anchor]
    while (first > 0 && used + stripsPerColumn[first - 1] <= slots) used += stripsPerColumn[--first]
    while (end < last && used + stripsPerColumn[end + 1] <= slots) used += stripsPerColumn[++end]
    return first..end
}

package com.ssafy.eolssu.feature.result.component

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
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
import com.ssafy.eolssu.core.ui.score.JeongganboLook
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.ErrorKindLegend
import com.ssafy.eolssu.feature.result.ErrorFilter
import com.ssafy.eolssu.feature.result.metricLabel
import com.ssafy.eolssu.feature.result.visibleErrors
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.enso_ring
import eolssu.shared.generated.resources.enso_spin
import eolssu.shared.generated.resources.enso_track
import eolssu.shared.generated.resources.result_errors_cluster
import eolssu.shared.generated.resources.result_errors_count
import eolssu.shared.generated.resources.result_errors_none
import eolssu.shared.generated.resources.result_errors_preview
import eolssu.shared.generated.resources.result_errors_title
import eolssu.shared.generated.resources.result_metric_weak
import eolssu.shared.generated.resources.result_score_of
import eolssu.shared.generated.resources.result_score_unit
import eolssu.shared.generated.resources.result_view_all_errors
import eolssu.shared.generated.resources.tiger_clap
import eolssu.shared.generated.resources.tiger_play
import eolssu.shared.generated.resources.tiger_retry
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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

/** 보드 분석 실패 그림(200×190): 다시 해 보는 호랑이(150dp) 오른쪽 아래에 붉은 경고 원(48dp, 바탕색 4dp 테두리). */
@Composable
fun AnalysisFailedFigure(modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Box(modifier.width(200.dp).height(190.dp).clearAndSetSemantics {}) {
        Image(
            painterResource(Res.drawable.tiger_retry),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopCenter).width(150.dp),
            contentScale = ContentScale.FillWidth,
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 4.dp)
                .size(56.dp)
                .background(colors.background, CircleShape)
                .padding(4.dp)
                .background(colors.recSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(EolssuIcons.Alert, contentDescription = null, modifier = Modifier.size(24.dp), tint = colors.recText)
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
 * 보드 `.ring`(112dp): 먹색 12% 일원상 위에 총점 비율만큼 연두 일원상을 12시 방향부터 시계 방향으로 잘라 그리고,
 * 가운데에 점수(Song Myung 38sp)와 ‘/ 100점’(11sp)을 둔다.
 */
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val track = painterResource(Res.drawable.enso_track)
    val ring = painterResource(Res.drawable.enso_ring)
    val total = score.coerceIn(0, 100)
    val unit = stringResource(Res.string.result_score_of)
    Box(modifier.size(112.dp).semantics(mergeDescendants = true) { contentDescription = "$total$unit" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            with(track) { draw(size) }
            when {
                total >= 100 -> with(ring) { draw(size) }
                total > 0 -> {
                    val wedge = Path().apply {
                        moveTo(center.x, center.y)
                        arcTo(Rect(center, size.minDimension), -90f, 360f * total / 100f, forceMoveTo = false)
                        close()
                    }
                    clipPath(wedge) { with(ring) { draw(size) } }
                }
            }
        }
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
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = colors.onPrimary, maxLines = 1)
        }
    }
}

/** 보드 `.btn.ghost.sm`: 바탕 없는 40dp 버튼(14sp `#5C564F`), 글자 뒤 화살표 16dp. */
@Composable
fun SmallGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: ImageVector? = EolssuIcons.Chevron) {
    val colors = EolssuTheme.colors
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 40.dp), shape = RoundedCornerShape(12.dp), color = Color.Transparent) {
        Row(
            Modifier.heightIn(min = 40.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal), color = colors.textSub, maxLines = 1)
            if (trailing != null) Icon(trailing, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textSub)
        }
    }
}

/**
 * 보드 총점 카드(`.card.tint`, 반지름 16 · 안쪽 14): 점수 고리 · 총평(16sp 굵게)과 ‘같은 곡 다시 연습’ 작은 버튼,
 * 오른쪽 아래에 손뼉 치는 호랑이(64dp)가 카드 아래로 4dp 걸친다. [extra]는 총평 아래(예시 결과 표시 등)에 둔다.
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
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(colors.accentSoft, CardShape).border(1.dp, colors.accentTintStrong, CardShape).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScoreRing(total)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            painterResource(Res.drawable.tiger_clap),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp).offset(y = 4.dp).width(64.dp),
            contentScale = ContentScale.FillWidth,
        )
    }
}

/** 보드 항목 세 칸(`.card` × 3, 사이 10): 이름(13sp) · ‘더 연습’ · 점수(22sp + ‘점’ 12sp) · 5dp 막대. */
@Composable
fun MetricCards(result: EvaluationResult, weak: Set<EvaluationMetric>, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        result.metrics.forEach { metric ->
            Column(
                Modifier.weight(1f).fillMaxHeight().background(colors.surface, CardShape).border(1.dp, colors.lineSoft, CardShape).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        metricLabel(metric.metric),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = colors.textMuted,
                        maxLines = 1,
                    )
                    if (metric.metric in weak) WeakBadge()
                }
                Text(
                    buildAnnotatedString {
                        append("${metric.score}")
                        withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = colors.textMuted)) {
                            append(stringResource(Res.string.result_score_unit))
                        }
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                        fontSize = 22.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.text,
                )
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(colors.surfaceStrong)) {
                    Box(Modifier.fillMaxWidth(metric.score.coerceIn(0, 100) / 100f).fillMaxHeight().background(colors.primary))
                }
            }
        }
    }
}

/** 보드 `.lv.l2`(20dp): ‘더 연습’. */
@Composable
private fun WeakBadge() {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.height(20.dp).background(colors.accentTintStrong, shape).border(1.dp, colors.accentTintLine, shape).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.result_metric_weak),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold),
            color = colors.text,
            maxLines = 1,
        )
    }
}

/**
 * 보드 오류 위치 카드(`.card`, 안쪽 14 · 사이 10): 제목(Song Myung 17sp)과 틀린 정간 수 · 가로 범례 · 몰린 각 문장 ·
 * 붙은 4각 정간보(틀린 정간은 종류별 바탕·테두리와 아이콘, 2차 결합본) · ‘악보 전체에서 보기 ›’.
 */
@Composable
fun ErrorLocationCard(result: EvaluationResult, song: Song, onViewAll: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val cluster = result.densestColumnPair()
    val columnCount = song.score.columns.size
    val last = (cluster?.let { maxOf(3, it.first + 1) } ?: 3).coerceAtMost(columnCount - 1)
    val first = maxOf(0, last - 3)
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
                if (result.errors.isEmpty()) stringResource(Res.string.result_errors_none) else stringResource(Res.string.result_errors_count, result.errors.size),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textMuted,
            )
        }
        if (result.errors.isNotEmpty()) {
            // 2차 결합본: 칸 색 견본 · 종류 아이콘 · 이름.
            ErrorKindLegend(showMissed = result.errors.any { it.kind == ScoreErrorKind.Missed })
        }
        if (cluster != null) {
            val range = "${cluster.first + 1}–${cluster.first + 2}각"
            val sentence = stringResource(Res.string.result_errors_cluster, result.errors.size, cluster.second, cluster.first + 1, cluster.first + 2)
            Text(
                buildAnnotatedString {
                    val at = sentence.indexOf(range)
                    if (at < 0) {
                        append(sentence)
                    } else {
                        append(sentence.substring(0, at))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(range) }
                        append(sentence.substring(at + range.length))
                    }
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = colors.text,
            )
        }
        if (columnCount > 0) {
            val description = stringResource(Res.string.result_errors_preview, first + 1, last + 1)
            BoxWithConstraints(Modifier.fillMaxWidth().semantics { contentDescription = description }) {
                val shown = last - first + 1
                JeongganboView(
                    song = song,
                    metrics = JeongganboMetrics.resultBoard((maxWidth - 2.dp) / shown),
                    scrollState = null,
                    errors = result.visibleErrors(ErrorFilter.All),
                    columns = first..last,
                    look = JeongganboLook.Result,
                )
            }
        }
        SmallGhostButton(stringResource(Res.string.result_view_all_errors), onViewAll, Modifier.align(Alignment.End))
    }
}

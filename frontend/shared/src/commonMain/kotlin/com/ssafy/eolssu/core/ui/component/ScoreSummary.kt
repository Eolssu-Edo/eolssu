package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.diagnostics.PerfPhase
import com.ssafy.eolssu.core.diagnostics.observeFirstDraw
import com.ssafy.eolssu.core.model.MetricScore
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.enso_ring
import eolssu.shared.generated.resources.top_bar_full_title
import eolssu.shared.generated.resources.result_score_of
import eolssu.shared.generated.resources.score_metric_description
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/*
 * 연주 결과 · 이전 연주 결과 · 이전 연주와 비교가 함께 쓰는 점수 부품(S15P31E102-753, 시안 D안).
 * 붓 일원상은 앱 그림(`enso_ring`)을 그대로 쓴다.
 */

/** 연두 붓 일원상([size]) 위 가운데에 [content]를 둔다. 점수와 관계없이 그림 전체를 그린다. */
@Composable
fun EnsoRing(size: Dp, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val ring = painterResource(Res.drawable.enso_ring)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { with(ring) { draw(this@Canvas.size) } }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/** 총점 일원상(88dp): 가운데 점수(명조 30sp)와 ‘/ 100점’(10sp). ‘총점’ 글자는 두지 않는다. */
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val total = score.coerceIn(0, 100)
    val unit = stringResource(Res.string.result_score_of)
    EnsoRing(
        ScoreRingSize,
        modifier.observeFirstDraw(PerfPhase.SCORE_DRAWN).semantics(mergeDescendants = true) { contentDescription = "$total$unit" },
    ) {
        Text(
            "$total",
            modifier = Modifier.clearAndSetSemantics {},
            style = EolssuTheme.serif.yul.copy(fontSize = 30.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
            color = colors.text,
        )
        Text(
            unit,
            modifier = Modifier.padding(top = 2.dp).clearAndSetSemantics {},
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
            color = colors.textMuted,
        )
    }
}

/**
 * 음정 · 박자 · 음 길이 일자 막대(두께 8 · 연두, 라벨 12sp · 점수 15sp 굵게).
 * 점수 칸은 ‘100’이 잘리지 않게 세 자리 너비를 잡고 줄바꿈하지 않는다.
 */
@Composable
fun MetricBars(metrics: List<MetricScore>, modifier: Modifier = Modifier, rowSpacing: Dp = 8.dp) {
    val colors = EolssuTheme.colors
    Column(modifier.padding(end = 2.dp), verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        metrics.forEach { metric ->
            val label = metricLabel(metric.metric)
            val description = stringResource(Res.string.score_metric_description, label, metric.score)
            Row(
                Modifier.semantics(mergeDescendants = true) { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    label,
                    modifier = Modifier.widthIn(min = 40.dp).clearAndSetSemantics {},
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = colors.textSub,
                    maxLines = 1,
                    softWrap = false,
                )
                Bar(metric.score / 100f, colors.primary, Modifier.weight(1f).height(8.dp))
                Text(
                    "${metric.score}",
                    modifier = Modifier.widthIn(min = 30.dp).clearAndSetSemantics {},
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
                    color = colors.text,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** 총점 일원상 지름. */
private val ScoreRingSize = 88.dp

/** 회색 바탕(#EEEBE3) 위에 [fraction]만큼 [color]로 칠한 둥근 막대. 높이는 [modifier]로 준다. */
@Composable
fun Bar(fraction: Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Box(modifier.background(colors.surfaceStrong, RoundedCornerShape(50))) {
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(color, RoundedCornerShape(50)))
    }
}

/** 연두 옅은 알약 버튼(높이 30 · 마이페이지 '연결'과 같은 연한 연두 바탕·짙은 연두 글자, 테두리 없음 · 12sp). 누르는 영역은 40dp를 둔다. */
@Composable
fun TintPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = EolssuTheme.colors
    Box(modifier.heightIn(min = 40.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.height(30.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceStrong,
            contentColor = if (enabled) colors.text else colors.textDisabled,
        ) {
            Row(
                Modifier.padding(start = 10.dp, end = if (trailing != null) 8.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
                if (trailing != null) Icon(trailing, contentDescription = null, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/**
 * 점수 상자(테두리 #E8E4DA · 반지름 16): 왼쪽 총점 일원상, 오른쪽 항목 막대와 그 아래 오른쪽 [action] 버튼,
 * 맨 아래 [memo](있을 때만, 큰따옴표로 감싼다). [extra]는 버튼 줄 왼쪽(예시 결과 표시 등)에 둔다.
 * [filled]이면 테두리 대신 흰 바탕 카드다(회색 바탕 화면, S15P31E102-768).
 * [title](곡 제목)이 있으면 상자 왼쪽 위에 두 줄까지 둔다(이전 연주 결과, S15P31E102-810). 없으면 지금과 같다.
 * [compact]이면 요소 크기는 그대로 두고 안쪽 여백·간격만 줄인다(이전 연주 결과, S15P31E102-819). 기본값은 지금과 같다.
 * [compact]에 [title]이 있으면 제목을 일원상 바로 위(왼쪽 칸)에 두고 항목 막대를 제목 줄 높이에서 시작해 상자 높이를 줄인다
 * (S15P31E102-825). 제목이 왼쪽 칸에 두 줄로 들어가지 않으면 잘라 내지 않고 상자 위 전체 너비에 둔다.
 */
@Composable
fun ScoreSummaryCard(
    total: Int,
    metrics: List<MetricScore>,
    modifier: Modifier = Modifier,
    memo: String? = null,
    action: (@Composable () -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
    filled: Boolean = false,
    title: String? = null,
    compact: Boolean = false,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val gap = if (compact) CompactSummaryGap else RegularSummaryGap
    Column(modifier.fillMaxWidth().then(if (filled) Modifier.background(colors.surface, shape) else Modifier.border(1.dp, colors.lineSoft, shape))) {
        if (compact && !title.isNullOrBlank()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val side = sideTitleWidth(title, maxWidth - gap.start - gap.end - gap.ringToBars - SideBarsMinWidth)
                Column { SummaryBody(total, metrics, title, side, gap, action, extra) }
            }
        } else {
            SummaryBody(total, metrics, title, null, gap, action, extra)
        }
        if (!memo.isNullOrBlank()) {
            Text(
                "“$memo”",
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp).fillMaxWidth()
                    .background(colors.background, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.text,
            )
        }
    }
}

/**
 * 제목 · 일원상 · 항목 막대 · 버튼 줄. [sideTitleWidth]가 있으면 제목을 그 너비까지의 왼쪽 칸에서 일원상 바로 위에 두고
 * 오른쪽 칸을 위로 맞춰 첫 막대가 제목 첫 줄과 같은 높이에 오게 한다. 없으면 제목을 위 전체 너비에 두고 일원상과 막대를 가운데로 맞춘다.
 */
@Composable
private fun SummaryBody(
    total: Int,
    metrics: List<MetricScore>,
    title: String?,
    sideTitleWidth: Dp?,
    gap: SummaryGap,
    action: (@Composable () -> Unit)?,
    extra: (@Composable () -> Unit)?,
) {
    val beside = sideTitleWidth != null && !title.isNullOrBlank()
    if (!title.isNullOrBlank() && !beside) {
        SummaryTitle(title, Modifier.fillMaxWidth().padding(start = gap.titleStart, top = gap.titleTop, end = gap.end + 2.dp))
    }
    Row(
        Modifier.fillMaxWidth().padding(
            start = gap.start,
            top = if (title.isNullOrBlank() || beside) gap.top else gap.topBelowTitle,
            end = gap.end,
            bottom = gap.bottom,
        ),
        horizontalArrangement = Arrangement.spacedBy(gap.ringToBars),
    ) {
        if (sideTitleWidth != null && !title.isNullOrBlank()) {
            Column(Modifier.widthIn(max = sideTitleWidth)) {
                SummaryTitle(title, Modifier.padding(start = SideTitleStart))
                ScoreRing(total)
            }
        } else {
            ScoreRing(total, Modifier.align(Alignment.CenterVertically))
        }
        Column(
            Modifier.weight(1f).align(if (beside) Alignment.Top else Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MetricBars(metrics, Modifier.padding(top = gap.barsTop), rowSpacing = gap.barRows)
            if (action != null || extra != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { extra?.invoke() }
                    action?.invoke()
                }
            }
        }
    }
}

/** 상자 안 곡 제목(16sp 굵게, 두 줄까지). */
@Composable
private fun SummaryTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier.semantics { heading() },
        style = summaryTitleStyle(),
        color = EolssuTheme.colors.text,
        maxLines = SummaryTitleLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun summaryTitleStyle() =
    MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)

/**
 * 제목을 일원상 위 왼쪽 칸에 둘 때의 칸 너비. 막대 칸에 [SideBarsMinWidth]를 남긴 [available](일원상보다 좁으면 일원상 너비)에
 * 제목이 [SummaryTitleLines]줄 안에 다 들어갈 때만 값을 주고, 잘리면 null(위 전체 너비에 둔다)이다.
 */
@Composable
private fun sideTitleWidth(title: String, available: Dp): Dp? {
    val width = maxOf(available, ScoreRingSize)
    val style = summaryTitleStyle()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fits = remember(title, style, width, density) {
        val maxWidth = with(density) { (width - SideTitleStart).roundToPx() }
        !measurer.measure(
            title, style, overflow = TextOverflow.Ellipsis, maxLines = SummaryTitleLines,
            constraints = Constraints(maxWidth = maxWidth),
        ).hasVisualOverflow
    }
    return width.takeIf { fits }
}

private const val SummaryTitleLines = 2

/** 왼쪽 칸 제목의 왼쪽 여백. 일원상 붓 자국의 왼쪽 끝과 글자 시작을 맞춘다. */
private val SideTitleStart = 2.dp

/** 제목을 왼쪽 칸에 둘 때 막대 칸에 남기는 최소 너비. ‘메모’ · ‘지난 연주와 비교’ 버튼 두 개가 한 줄에 들어간다. */
private val SideBarsMinWidth = 212.dp

/** 점수 상자 안쪽 여백·간격. 일원상·막대·글자 크기는 바꾸지 않는다. */
private class SummaryGap(
    val titleStart: Dp,
    val titleTop: Dp,
    val start: Dp,
    val top: Dp,
    val topBelowTitle: Dp,
    val end: Dp,
    val bottom: Dp,
    val ringToBars: Dp,
    val barsTop: Dp,
    val barRows: Dp,
)

private val RegularSummaryGap = SummaryGap(
    titleStart = 14.dp, titleTop = 12.dp, start = 12.dp, top = 10.dp, topBelowTitle = 4.dp,
    end = 10.dp, bottom = 8.dp, ringToBars = 12.dp, barsTop = 4.dp, barRows = 8.dp,
)

private val CompactSummaryGap = SummaryGap(
    titleStart = 12.dp, titleTop = 10.dp, start = 10.dp, top = 8.dp, topBelowTitle = 2.dp,
    end = 8.dp, bottom = 4.dp, ringToBars = 10.dp, barsTop = 2.dp, barRows = 6.dp,
)

/**
 * 왼쪽 정렬 상단 바(높이 56 · 위 8): 뒤로 가기(있을 때) · 제목(16sp 굵게, 한 줄) · 보조 문구(12sp) · 오른쪽 [right].
 * 제목이 잘렸을 때만 제목 칸을 누를 수 있고, 누르면 제목이 왼쪽으로 한 번 흘러 전체를 보여 준 뒤 말줄임으로 돌아온다.
 * 잘리지 않은 제목은 눌러도 반응하지 않는다(S15P31E102-968). 이전 연주 결과 · 이전 연주와 비교 · 연주 결과가 쓴다.
 */
@Composable
fun EolssuStartTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    maxWidth: Dp = CompactContentMaxWidth,
    right: @Composable () -> Unit = {},
) {
    val colors = EolssuTheme.colors
    // 말줄임으로 잘렸는지는 말줄임 상태로 그린 배치에서만 알 수 있어, 흐르는 동안에는 값을 바꾸지 않는다.
    var truncated by remember(title) { mutableStateOf(false) }
    // 0이 아니면 그 회차의 한 번 흐르기를 보여 준다. 다시 누르면 처음부터 다시 흐른다.
    var marqueeRun by remember(title) { mutableIntStateOf(0) }
    var titleWidthPx by remember(title) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val showFullTitle = stringResource(Res.string.top_bar_full_title)
    if (marqueeRun != 0) {
        LaunchedEffect(marqueeRun, titleWidthPx) {
            if (titleWidthPx == 0) return@LaunchedEffect
            // 한 번 흐르는 시간(시작 지연 + (글자 폭 + 간격) ÷ 속도)이 지나면 말줄임 제목으로 돌아온다.
            val travelDp = with(density) { titleWidthPx.toDp() } + TitleMarqueeSpacing
            delay(TitleMarqueeDelayMillis + (travelDp.value / TitleMarqueeVelocity.value * 1000).toLong())
            marqueeRun = 0
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            modifier.widthIn(max = maxWidth).fillMaxWidth().padding(top = 8.dp).heightIn(min = 56.dp)
                // 꺽쇠(44dp 칸 안 12dp, 4dp 당김)와 오른쪽 48dp 아이콘이 다른 상단 바처럼 본문 끝(20dp)에 오게 한다(S15P31E102-849).
                .padding(start = if (onBack != null) 12.dp else 20.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onBack != null) EolssuBackButton(onBack, size = 44.dp)
            // 눌림 효과는 헤더 폭 전체가 아니라 제목 칸의 둥근 모양 안에만 그린다.
            Column(
                Modifier.weight(1f).heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (truncated) Modifier.clickable(onClickLabel = showFullTitle, role = Role.Button) { marqueeRun++ }
                        else Modifier,
                    ),
                verticalArrangement = Arrangement.Center,
            ) {
                val titleStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                if (marqueeRun != 0) {
                    key(marqueeRun) {
                        Text(
                            title,
                            modifier = Modifier
                                .semantics { heading() }
                                .basicMarquee(
                                    iterations = 1,
                                    initialDelayMillis = TitleMarqueeDelayMillis.toInt(),
                                    spacing = MarqueeSpacing(TitleMarqueeSpacing),
                                    velocity = TitleMarqueeVelocity,
                                ),
                            style = titleStyle,
                            color = colors.text,
                            maxLines = 1,
                            onTextLayout = { titleWidthPx = it.size.width },
                        )
                    }
                } else {
                    Text(
                        title,
                        modifier = Modifier.semantics { heading() },
                        style = titleStyle,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { truncated = it.hasVisualOverflow },
                    )
                }
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            right()
        }
    }
}

/** 잘린 제목을 한 번 흘려 보일 때의 시작 지연·속도·이어지는 간격. */
private const val TitleMarqueeDelayMillis = 300L
private val TitleMarqueeVelocity = 40.dp
private val TitleMarqueeSpacing = 32.dp

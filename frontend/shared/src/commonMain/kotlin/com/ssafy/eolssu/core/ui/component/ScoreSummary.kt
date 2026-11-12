package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.diagnostics.PerfPhase
import com.ssafy.eolssu.core.diagnostics.observeFirstDraw
import com.ssafy.eolssu.core.model.MetricScore
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_close
import eolssu.shared.generated.resources.enso_ring
import eolssu.shared.generated.resources.top_bar_full_title
import eolssu.shared.generated.resources.result_score_of
import eolssu.shared.generated.resources.score_metric_description
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
        88.dp,
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
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
            color = colors.textMuted,
        )
    }
}

/**
 * 음정 · 박자 · 음 길이 일자 막대(두께 8 · 연두, 라벨 12sp · 점수 15sp 굵게).
 * 점수 칸은 ‘100’이 잘리지 않게 세 자리 너비를 잡고 줄바꿈하지 않는다.
 */
@Composable
fun MetricBars(metrics: List<MetricScore>, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Column(modifier.padding(end = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            color = colors.accentTint,
            contentColor = if (enabled) colors.accentText else colors.textDisabled,
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
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth().then(if (filled) Modifier.background(colors.surface, shape) else Modifier.border(1.dp, colors.lineSoft, shape))) {
        if (!title.isNullOrBlank()) {
            Text(
                title,
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, end = 12.dp).semantics { heading() },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = if (title.isNullOrBlank()) 10.dp else 4.dp, end = 10.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScoreRing(total, Modifier.align(Alignment.CenterVertically))
            Column(Modifier.weight(1f).align(Alignment.CenterVertically), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MetricBars(metrics, Modifier.padding(top = 4.dp))
                if (action != null || extra != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { extra?.invoke() }
                        action?.invoke()
                    }
                }
            }
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
 * 왼쪽 정렬 상단 바(높이 56 · 위 8): 뒤로 가기(있을 때) · 제목(16sp 굵게, 한 줄) · 보조 문구(12sp) · 오른쪽 [right].
 * 제목이 잘릴 수 있어 제목 칸을 누르면 전체 제목을 창으로 보여 준다. 이전 연주 결과 · 이전 연주와 비교 · 연주 결과가 쓴다.
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
    var fullTitleOpen by remember(title) { mutableStateOf(false) }
    val showFullTitle = stringResource(Res.string.top_bar_full_title)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            modifier.widthIn(max = maxWidth).fillMaxWidth().padding(top = 8.dp).heightIn(min = 56.dp)
                .padding(start = if (onBack != null) 4.dp else 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onBack != null) EolssuBackButton(onBack, size = 44.dp)
            Column(
                Modifier.weight(1f).heightIn(min = 44.dp)
                    .clickable(onClickLabel = showFullTitle, role = Role.Button) { fullTitleOpen = true },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
    if (fullTitleOpen) {
        EolssuDialog(title = title, message = subtitle, onDismissRequest = { fullTitleOpen = false }) {
            EolssuButton(stringResource(Res.string.common_close), { fullTitleOpen = false }, style = EolssuButtonStyle.Secondary)
        }
    }
}

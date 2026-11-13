package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout

/**
 * 페이지형 상단 바(`.page-head`, SONG-01 · SONG-02 · PRACTICE-01): 왼쪽 뒤로 가기 꺽쇠([EolssuBackButton]) · 가운데 제목 · 오른쪽 버튼 자리 · 아래 구분선.
 * 제목은 다른 화면의 [EolssuTopBar]처럼 줄 가운데에 두되([CenteredTitleRow]) 보조 문구가 없고, 큰 제목은 본문의 [PageHeading]이 맡는다.
 * 버튼은 보이는 자리 36dp, 누르는 영역 48dp라 버튼 바깥 여백(6dp)만큼 줄 양옆 여백을 줄여 그 자리가 화면 끝에서 20dp에 오게 한다.
 * 상태 표시줄 영역은 감싸는 쪽(`EolssuScaffold` 등)이 비켜 간다.
 */
@Composable
fun EolssuPageTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    maxWidth: Dp = CompactContentMaxWidth,
    divider: Boolean = true,
    right: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            CenteredTitleRow(
                Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .heightIn(min = 62.dp)
                    .padding(horizontal = PageButtonInset),
                start = { if (onBack != null) EolssuBackButton(onBack) },
                end = { Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = right) },
            ) {
                // 글꼴·크기도 가운데 제목형 [EolssuTopBar]와 같게 둔다(휴대폰 16sp · 태블릿 17sp).
                Text(
                    title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = if (currentWindowLayout().widthClass == WindowWidthClass.Compact) 16.sp else 17.sp),
                    color = EolssuTheme.colors.text,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (divider) HorizontalDivider(color = EolssuTheme.colors.surfaceStrong)
    }
}

/**
 * 왼쪽 [start] · 가운데 [title] · 오른쪽 [end]를 한 줄에 두는 상단 바 줄. 제목은 양옆 칸 너비와 관계없이 줄 가운데에 오고,
 * 양옆 중 넓은 쪽 너비와 [gap]만큼 양쪽을 비운 폭 안에서 줄인다(오른쪽 버튼이 여러 개여도 겹치지 않는다).
 */
@Composable
fun CenteredTitleRow(
    modifier: Modifier = Modifier,
    start: @Composable () -> Unit = {},
    end: @Composable () -> Unit = {},
    gap: Dp = 8.dp,
    title: @Composable () -> Unit,
) {
    Layout(contents = listOf(start, title, end), modifier = modifier) { (startItems, titleItems, endItems), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val starts = startItems.map { it.measure(loose) }
        val ends = endItems.map { it.measure(loose) }
        val width = constraints.maxWidth
        val side = maxOf(starts.sumOf { it.width }, ends.sumOf { it.width })
        val inset = if (side > 0) side + gap.roundToPx() else 0
        val titles = titleItems.map { it.measure(loose.copy(maxWidth = (width - inset * 2).coerceAtLeast(0))) }
        val height = maxOf(constraints.minHeight, (starts + ends + titles).maxOfOrNull { it.height } ?: 0)
        layout(width, height) {
            var x = 0
            starts.forEach { it.placeRelative(x, (height - it.height) / 2); x += it.width }
            x = width - ends.sumOf { it.width }
            ends.forEach { it.placeRelative(x, (height - it.height) / 2); x += it.width }
            titles.forEach { it.placeRelative((width - it.width) / 2, (height - it.height) / 2) }
        }
    }
}

/** 누르는 영역(48dp)과 보이는 원(36dp)의 차이 절반. */
private val PageButtonInset: Dp = 6.dp

/**
 * [EolssuPageTopBar]의 원형 버튼(`.back-btn`): 흰 바탕 · 옅은 테두리 36dp 원, 누르는 영역 48dp.
 * [enabled]가 false면 바탕 회색 · 흐린 아이콘(연주 중 잠긴 설정)이다. [bordered]가 false면 테두리 없는 흰 원이다.
 */
@Composable
fun EolssuPageIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bordered: Boolean = true,
) {
    val colors = EolssuTheme.colors
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(if (enabled) colors.surface else colors.background, CircleShape)
                .then(if (bordered) Modifier.border(1.dp, colors.lineSoft, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, Modifier.size(18.dp), tint = if (enabled) colors.text else colors.textDisabled)
        }
    }
}

/**
 * 본문 맨 위 페이지 머리(SONG-01 · SONG-02 · PRACTICE-01): Song Myung 25sp 큰 제목(`.page-title`) · 설명(`.page-intro`).
 * 줄바꿈이 필요한 제목은 문구에 `\n`을 넣는다. 아래 여백은 부르는 쪽이 정한다.
 * 보드의 영문 작은 제목(`.eyebrow`, 예: PLAY & DISCOVER)은 쓰지 않는다(S15P31E102-561).
 */
@Composable
fun PageHeading(
    title: String,
    modifier: Modifier = Modifier,
    intro: String? = null,
    /** 연습 화면의 ‘운지법과 함께 보기’처럼 자리를 아끼는 22sp 제목(보드 `.paged-fingering .page-title`). */
    compact: Boolean = false,
    /**
     * 곡 제목처럼 줄바꿈하면 안 되는 제목(S15P31E102-730): 한 줄로 고정하고, 다 들어가지 않을 때만 왼쪽으로 흘려 끝까지 보인다.
     * 끝에 닿으면 처음부터 다시 흘리고, 다 들어가면 움직이지 않는다.
     */
    singleLine: Boolean = false,
) {
    val colors = EolssuTheme.colors
    Column(modifier) {
        Text(
            title,
            modifier = Modifier.semantics { heading() }
                .then(if (singleLine) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            softWrap = !singleLine,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = if (compact) 22.sp else 25.sp,
                lineHeight = if (compact) 33.sp else 37.5.sp,
                letterSpacing = (-0.5).sp,
                fontWeight = FontWeight.Normal,
            ),
            color = colors.text,
        )
        if (intro != null) {
            Text(
                intro,
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 22.sp),
                color = colors.textSub,
            )
        }
    }
}

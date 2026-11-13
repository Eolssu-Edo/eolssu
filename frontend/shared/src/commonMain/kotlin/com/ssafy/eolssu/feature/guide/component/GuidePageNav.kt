package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons

/**
 * 기초 배우기 단계 상세와 정간보 읽기 아래의 넘김 줄(S15P31E102-877). 양옆에 이전·다음 동그란 화살표, 가운데에 위치 표시([center])를 둔다.
 * 기초 배우기는 단계 점과 'n / 전체', 정간보 읽기는 쪽 점이다. 두 화면이 같은 버튼 크기·색·흐린 상태를 쓴다.
 *
 * @param previousLabel 이전 화살표의 화면 읽기 문구. null이면 갈 곳이 없어 화살표를 옅게 두고 누를 수 없다(숨기지 않아 가운데 표시가 늘 같은 자리다).
 * @param nextLabel 다음 화살표의 화면 읽기 문구. null이면 이전과 같이 옅게 둔다.
 */
@Composable
internal fun GuidePageNav(
    previousLabel: String?,
    nextLabel: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GuideNavArrow(previousLabel, forward = false, onClick = onPrevious)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { center() }
        GuideNavArrow(nextLabel, forward = true, onClick = onNext)
    }
}

/** 넘김 화살표: 흰 바탕 · 옅은 테두리의 52dp 원. 갈 곳이 없으면 옅게 두고 누를 수 없다. */
@Composable
private fun GuideNavArrow(label: String?, forward: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val enabled = label != null
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(GuideNavArrowSize)
            .graphicsLayer { alpha = if (enabled) 1f else DisabledArrowAlpha }
            .semantics { contentDescription = label.orEmpty() },
        shape = CircleShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (forward) EolssuIcons.Chevron else EolssuIcons.Back,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = colors.text,
            )
        }
    }
}

private val GuideNavArrowSize = 52.dp

private const val DisabledArrowAlpha = 0.35f

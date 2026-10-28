package com.ssafy.eolssu.feature.home.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.home.HomeFeature
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_section_practice
import org.jetbrains.compose.resources.stringResource

/**
 * '연습·학습하기' 제목과 카드 묶음.
 *
 * @param columns 한 줄의 카드 수. 휴대폰·태블릿 가로는 2, 태블릿 세로는 4.
 * @param large 태블릿 배치용 카드 여백·글자 크기.
 */
@Composable
internal fun FeatureSection(
    onFeatureClick: (HomeFeature) -> Unit,
    columns: Int,
    cardMinHeight: Dp,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val gap = if (large) 12.dp else 10.dp
    Column(modifier) {
        Text(
            stringResource(Res.string.home_section_practice),
            style = MaterialTheme.typography.labelLarge,
            color = EolssuTheme.colors.textMuted,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            HomeFeature.entries.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { feature ->
                        FeatureCard(
                            feature,
                            onClick = { onFeatureClick(feature) },
                            minHeight = cardMinHeight,
                            large = large,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(feature: HomeFeature, onClick: () -> Unit, minHeight: Dp, large: Boolean, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        shape = MaterialTheme.shapes.medium,
        color = EolssuTheme.colors.background,
        border = BorderStroke(1.dp, EolssuTheme.colors.line),
    ) {
        Column(
            Modifier.padding(horizontal = if (large) 16.dp else 14.dp, vertical = if (large) 18.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(34.dp).background(EolssuTheme.colors.surface, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(feature.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            val label = MaterialTheme.typography.titleSmall
            Text(stringResource(feature.label), style = if (large) label.copy(fontSize = 15.sp) else label)
        }
    }
}

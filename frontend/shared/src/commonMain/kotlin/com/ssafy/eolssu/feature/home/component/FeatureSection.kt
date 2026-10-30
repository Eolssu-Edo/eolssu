package com.ssafy.eolssu.feature.home.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.home.HomeFeature
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_section_practice
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import eolssu.shared.generated.resources.home_tiger
import eolssu.shared.generated.resources.home_song_art
import eolssu.shared.generated.resources.home_fingering_art
import eolssu.shared.generated.resources.home_score_art
import eolssu.shared.generated.resources.home_feature_song_desc
import eolssu.shared.generated.resources.home_feature_fingering_desc
import eolssu.shared.generated.resources.home_feature_reading_desc
import eolssu.shared.generated.resources.home_feature_learn_desc

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
        color = EolssuTheme.colors.surface,
        border = BorderStroke(1.dp, EolssuTheme.colors.line),
    ) {
        Column(
            Modifier.padding(horizontal = if (large) 16.dp else 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val illustration = when (feature) {
                HomeFeature.SongSelect -> Res.drawable.home_song_art
                HomeFeature.Fingering -> Res.drawable.home_fingering_art
                HomeFeature.Reading -> Res.drawable.home_score_art
                HomeFeature.Learn -> Res.drawable.home_tiger
            }
            Image(painterResource(illustration), contentDescription = null, modifier = Modifier.fillMaxWidth().height(80.dp))
            val label = MaterialTheme.typography.titleSmall
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(feature.label), modifier = Modifier.weight(1f), style = if (large) label.copy(fontSize = 15.sp) else label)
                Icon(EolssuIcons.ArrowUpRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = EolssuTheme.colors.textMuted)
            }
            val description = when (feature) {
                HomeFeature.SongSelect -> Res.string.home_feature_song_desc
                HomeFeature.Fingering -> Res.string.home_feature_fingering_desc
                HomeFeature.Reading -> Res.string.home_feature_reading_desc
                HomeFeature.Learn -> Res.string.home_feature_learn_desc
            }
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = EolssuTheme.colors.textMuted)
        }
    }
}

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
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
 * '연습·학습하기' 제목 줄(`.lhead`: 제목 · 가는 선)과 카드 묶음(`.lgrid`, 간격 12dp).
 *
 * @param columns 한 줄의 카드 수. 휴대폰·태블릿 가로는 2, 태블릿 세로는 4.
 * @param large 태블릿 배치용 카드 글자 크기.
 */
@Composable
internal fun FeatureSection(
    onFeatureClick: (HomeFeature) -> Unit,
    columns: Int,
    cardMinHeight: Dp,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val gap = 12.dp
    Column(modifier) {
        Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(Res.string.home_section_practice),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
                color = SectionInk,
            )
            Box(Modifier.weight(1f).height(1.dp).background(SectionLine))
        }
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

/** 카드(`.lcard`): 흰 바탕, 옅은 초록빛 테두리, 모서리 13dp, 그림 80dp 아래 제목 13sp · ↗ · 설명 11sp. 내용은 세로 가운데. */
@Composable
private fun FeatureCard(feature: HomeFeature, onClick: () -> Unit, minHeight: Dp, large: Boolean, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        shape = RoundedCornerShape(13.dp),
        color = EolssuTheme.colors.surface,
        border = BorderStroke(1.dp, CardLine),
    ) {
        Column(
            Modifier.padding(start = 15.dp, end = 15.dp, top = 10.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            val illustration = when (feature) {
                HomeFeature.SongSelect -> Res.drawable.home_song_art
                HomeFeature.Fingering -> Res.drawable.home_fingering_art
                HomeFeature.Reading -> Res.drawable.home_score_art
                HomeFeature.Learn -> Res.drawable.home_tiger
            }
            Image(painterResource(illustration), contentDescription = null, modifier = Modifier.padding(bottom = 7.dp).fillMaxWidth().height(80.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(feature.label),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = if (large) 15.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.4).sp,
                    ),
                    color = CardInk,
                )
                Text("↗", modifier = Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = CardArrow)
            }
            val description = when (feature) {
                HomeFeature.SongSelect -> Res.string.home_feature_song_desc
                HomeFeature.Fingering -> Res.string.home_feature_fingering_desc
                HomeFeature.Reading -> Res.string.home_feature_reading_desc
                HomeFeature.Learn -> Res.string.home_feature_learn_desc
            }
            Text(
                stringResource(description),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = if (large) 12.sp else 11.sp,
                    lineHeight = if (large) 18.sp else 16.5.sp,
                    letterSpacing = (-0.3).sp,
                ),
                color = CardDescription,
            )
        }
    }
}

// 홈 보드만 쓰는 옅은 초록빛 먹색들(테마에 없음).
private val SectionInk = Color(0xFF29372E)
private val SectionLine = Color(0xFFE5EAE5)
private val CardLine = Color(0xFFE3E9E3)
private val CardInk = Color(0xFF2E3E32)
private val CardArrow = Color(0xFF738774)
private val CardDescription = Color(0xFF5F6C63)

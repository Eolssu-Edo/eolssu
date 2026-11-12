package com.ssafy.eolssu.feature.home.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.home.HomeFeature
import com.ssafy.eolssu.feature.home.HomeFeatureGrid
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_edit_art
import eolssu.shared.generated.resources.home_fingering_art
import eolssu.shared.generated.resources.home_learn_art
import eolssu.shared.generated.resources.home_practice_tiger
import eolssu.shared.generated.resources.home_records_art
import eolssu.shared.generated.resources.home_score_art
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 배너 아래 기능 격자. [HomeFeatureGrid] 순서를 [columns]열로 나누고,
 * 칸마다 그림과 제목을 한 상자 안에 둔다. 마이페이지처럼 선 없이 바탕과 흰 상자의 명도 차이로 나눈다(S15P31E102-768).
 *
 * @param columns 한 줄의 칸 수. 왼쪽 학습 · 오른쪽 연습·관리 구분을 지키려고 2(2×3)를 쓴다.
 * @param cardMinHeight 상자의 최소 높이.
 * @param large 태블릿 배치용 제목 글자 크기.
 */
@Composable
internal fun FeatureSection(
    onFeatureClick: (HomeFeature) -> Unit,
    columns: Int,
    cardMinHeight: Dp,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    Column(modifier.padding(top = 4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeFeatureGrid.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { feature ->
                        FeatureTile(
                            feature,
                            onClick = { onFeatureClick(feature) },
                            boxMinHeight = cardMinHeight,
                            large = large,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 마지막 줄이 짧으면 빈 칸으로 칸 너비를 맞춘다.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * 기능 칸: 마이페이지 카드와 같은 테두리 없는 흰 바탕 · 모서리 20dp의 상자(칸 너비 전체, 높이 [boxMinHeight]) 안에
 * 높이 112dp 그림과 그 아래 가운데 정렬 제목을 함께 둔다. 상자 전체가 누르는 영역이다.
 */
@Composable
private fun FeatureTile(feature: HomeFeature, onClick: () -> Unit, boxMinHeight: Dp, large: Boolean, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().heightIn(min = boxMinHeight)
            .clip(shape)
            .background(EolssuTheme.colors.surface)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painterResource(feature.illustration),
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(112.dp),
        )
        Text(
            stringResource(feature.label),
            modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp).fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = if (large) 17.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            ),
            color = EolssuTheme.colors.cardInk,
            maxLines = 2,
        )
    }
}

private val HomeFeature.illustration: DrawableResource
    get() = when (this) {
        HomeFeature.SongSelect -> Res.drawable.home_practice_tiger
        HomeFeature.Records -> Res.drawable.home_records_art
        HomeFeature.Reading -> Res.drawable.home_score_art
        HomeFeature.Fingering -> Res.drawable.home_fingering_art
        HomeFeature.Learn -> Res.drawable.home_learn_art
        HomeFeature.EditSheets -> Res.drawable.home_edit_art
    }

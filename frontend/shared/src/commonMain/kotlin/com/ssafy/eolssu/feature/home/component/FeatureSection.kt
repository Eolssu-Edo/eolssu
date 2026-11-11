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
import androidx.compose.foundation.layout.offset
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
import eolssu.shared.generated.resources.home_records_art
import eolssu.shared.generated.resources.home_score_art
import eolssu.shared.generated.resources.home_song_art
import eolssu.shared.generated.resources.home_tiger
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 배너 아래 가는 구분선과 기능 격자. [HomeFeatureGrid] 순서를 [columns]열로 나누고,
 * 칸마다 그림 상자 아래에 제목을 둔다.
 *
 * @param columns 한 줄의 칸 수. 왼쪽 학습 · 오른쪽 연습·관리 구분을 지키려고 2(2×3)를 쓴다.
 * @param cardMinHeight 그림 상자의 최소 높이. 제목을 상자 안에 두던 카드와 같은 높이를 쓴다.
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
    Column(modifier) {
        Box(Modifier.padding(bottom = 16.dp).fillMaxWidth().height(1.dp).background(EolssuTheme.colors.sectionLine))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
 * 기능 칸: 흰 바탕 · 옅은 초록빛 테두리 · 모서리 13dp의 그림 상자(칸 너비 전체, 높이 [boxMinHeight])와
 * 그 아래 가운데 정렬 제목. 그림은 높이 80dp로 상자 가운데에 둔다.
 * 상자와 제목을 합친 칸 전체가 누르는 영역이고, 누름 물결은 상자 안에만 그린다.
 */
@Composable
private fun FeatureTile(feature: HomeFeature, onClick: () -> Unit, boxMinHeight: Dp, large: Boolean, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(13.dp)
    Column(
        modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = boxMinHeight)
                .clip(shape)
                .background(EolssuTheme.colors.surface)
                .border(1.dp, EolssuTheme.colors.cardLine, shape)
                .indication(interaction, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(feature.illustration),
                contentDescription = null,
                modifier = Modifier.padding(horizontal = 15.dp).fillMaxWidth().height(80.dp).offset(x = feature.illustrationNudge),
            )
        }
        Text(
            stringResource(feature.label),
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = if (large) 15.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
            ),
            color = EolssuTheme.colors.cardInk,
            maxLines = 2,
        )
    }
}

private val HomeFeature.illustration: DrawableResource
    get() = when (this) {
        HomeFeature.SongSelect -> Res.drawable.home_song_art
        HomeFeature.Records -> Res.drawable.home_records_art
        HomeFeature.Reading -> Res.drawable.home_score_art
        HomeFeature.Fingering -> Res.drawable.home_fingering_art
        HomeFeature.Learn -> Res.drawable.home_tiger
        HomeFeature.EditSheets -> Res.drawable.home_edit_art
    }

/** 그림 가로 보정. 호랑이 그림은 원본 여백 때문에 왼쪽으로 치우쳐 보여 오른쪽으로 3dp 민다. */
private val HomeFeature.illustrationNudge: Dp
    get() = if (this == HomeFeature.Learn) 3.dp else 0.dp

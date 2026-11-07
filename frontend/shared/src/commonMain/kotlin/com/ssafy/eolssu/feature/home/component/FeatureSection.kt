package com.ssafy.eolssu.feature.home.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * 배너 아래 가는 구분선과 카드 격자(`.lgrid`, 간격 12dp). [HomeFeatureGrid] 순서를 [columns]열로 나눈다.
 * 한 줄의 카드는 가장 긴 제목에 맞춰 높이를 같게 둔다.
 *
 * @param columns 한 줄의 카드 수. 왼쪽 학습 · 오른쪽 연습·관리 구분을 지키려고 2(2×3)를 쓴다.
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
        Box(Modifier.padding(bottom = 16.dp).fillMaxWidth().height(1.dp).background(SectionLine))
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            HomeFeatureGrid.chunked(columns).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { feature ->
                        FeatureCard(
                            feature,
                            onClick = { onFeatureClick(feature) },
                            minHeight = cardMinHeight,
                            large = large,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    // 마지막 줄이 짧으면 빈 칸으로 카드 너비를 맞춘다.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * 카드(`.lcard`): 흰 바탕, 옅은 초록빛 테두리, 모서리 13dp, 그림 80dp 아래 가운데 정렬 제목 13sp. 카드 전체가 누르는 영역이다.
 * 좁은 화면에서 제목이 길면 두 줄(각 줄 가운데)로 내려 잘리지 않게 한다.
 */
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
            Modifier.padding(start = 15.dp, end = 15.dp, top = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(feature.illustration), contentDescription = null, modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth().height(80.dp))
            Text(
                stringResource(feature.label),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = if (large) 15.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.4).sp,
                ),
                color = CardInk,
                maxLines = 2,
            )
        }
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

// 홈 보드만 쓰는 옅은 초록빛 먹색들(테마에 없음).
private val SectionLine = Color(0xFFE5EAE5)
private val CardLine = Color(0xFFE3E9E3)
private val CardInk = Color(0xFF2E3E32)

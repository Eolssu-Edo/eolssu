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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.DpSize
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
 * 칸마다 그림 상자를 가운데에 두고 제목을 상자 아래에 둔다.
 *
 * @param columns 한 줄의 칸 수. 왼쪽 학습 · 오른쪽 연습·관리 구분을 지키려고 2(2×3)를 쓴다.
 * @param large 태블릿 배치용 제목 글자 크기.
 */
@Composable
internal fun FeatureSection(
    onFeatureClick: (HomeFeature) -> Unit,
    columns: Int,
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
 * 기능 칸: 흰 바탕 · 옅은 초록빛 테두리 · 모서리 13dp의 그림 상자와 그 아래 가운데 정렬 제목.
 * 상자는 모든 그림이 들어가는 가장 작은 크기([FeatureArtBoxSize])로 모든 칸이 같다.
 * 상자와 제목을 합친 칸 전체가 누르는 영역이고, 누름 물결은 상자 안에만 그린다.
 */
@Composable
private fun FeatureTile(feature: HomeFeature, onClick: () -> Unit, large: Boolean, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(13.dp)
    Column(
        modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(FeatureArtBoxSize)
                .clip(shape)
                .background(EolssuTheme.colors.surface)
                .border(1.dp, EolssuTheme.colors.cardLine, shape)
                .indication(interaction, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            val art = feature.art
            // 그림 파일은 실제 그림보다 넓은 투명 여백을 가진다. 원본 비율 그대로 그리고(상자보다 커도 됨),
            // 실제 그림 영역의 가운데가 상자 가운데에 오도록 옮긴다. 상자 밖으로 나간 투명 여백은 clip으로 잘린다.
            Image(
                painterResource(art.resource),
                contentDescription = null,
                modifier = Modifier.requiredSize(art.drawnWidth, FeatureArtHeight)
                    .offset(x = art.centerShiftX + art.nudgeX, y = art.centerShiftY),
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

/**
 * 그림 한 장의 원본 좌표계(벡터는 viewport, 사진은 픽셀)와 그 안에서 실제로 칠해진 영역.
 * 그림을 바꾸면 칠해진 영역(left·top·right·bottom)도 다시 재어 고친다.
 */
private class FeatureArt(
    val resource: DrawableResource,
    val sourceWidth: Float,
    val sourceHeight: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** 실제 영역 가운데에 맞춘 뒤 눈으로 보정하는 가로 이동. */
    val nudgeX: Dp = 0.dp,
) {
    /** 원본 높이 전체를 [FeatureArtHeight]로 그리는 배율(dp / 원본 단위). 모든 그림을 같은 배율 규칙으로 그린다. */
    private val scale = FeatureArtHeight.value / sourceHeight
    val drawnWidth = (sourceWidth * scale).dp
    val paintedWidth = ((right - left) * scale).dp
    val paintedHeight = ((bottom - top) * scale).dp
    /** 그림 파일 가운데에서 실제 영역 가운데까지의 거리를 반대로 옮겨, 실제 영역을 상자 가운데에 둔다. */
    val centerShiftX = ((sourceWidth / 2 - (left + right) / 2) * scale).dp
    val centerShiftY = ((sourceHeight / 2 - (top + bottom) / 2) * scale).dp
}

/** 그림 파일 전체(투명 여백 포함)를 그리는 높이. 예전 카드의 그림 높이 80dp를 그대로 쓴다. */
private val FeatureArtHeight = 80.dp

/** 그림과 상자 테두리 사이의 최소 여백. */
private val FeatureArtPadding = 6.dp

// 칠해진 영역은 벡터 경로(회전·선 굵기 포함)와 호랑이 그림의 불투명 픽셀을 재어 바깥쪽으로 반올림했다.
private val SongArt = FeatureArt(Res.drawable.home_song_art, 144f, 88f, 27.5f, 3f, 127f, 84f)
private val RecordsArt = FeatureArt(Res.drawable.home_records_art, 144f, 88f, 29f, 9.5f, 127f, 80f)
private val ScoreArt = FeatureArt(Res.drawable.home_score_art, 144f, 88f, 34f, 9.5f, 123f, 85f)
private val FingeringArt = FeatureArt(Res.drawable.home_fingering_art, 144f, 88f, 24f, 9f, 121f, 75f)
private val EditArt = FeatureArt(Res.drawable.home_edit_art, 144f, 88f, 30.5f, 7.5f, 133.5f, 80.5f)
/** 호랑이는 원본 그림이 왼쪽으로 치우쳐 보여 오른쪽으로 3dp 민다. */
private val TigerArt = FeatureArt(Res.drawable.home_tiger, 289f, 313f, 4f, 3f, 285f, 313f, nudgeX = 3.dp)

private val AllFeatureArts = listOf(SongArt, RecordsArt, ScoreArt, FingeringArt, EditArt, TigerArt)

/** 모든 그림의 실제 영역(+보정 이동)이 들어가는 가장 작은 상자. 가장 넓은 그림과 가장 높은 그림이 정한다. */
private val FeatureArtBoxSize = DpSize(
    width = AllFeatureArts.maxOf { it.paintedWidth + it.nudgeX * 2 } + FeatureArtPadding * 2,
    height = AllFeatureArts.maxOf { it.paintedHeight } + FeatureArtPadding * 2,
)

private val HomeFeature.art: FeatureArt
    get() = when (this) {
        HomeFeature.SongSelect -> SongArt
        HomeFeature.Records -> RecordsArt
        HomeFeature.Reading -> ScoreArt
        HomeFeature.Fingering -> FingeringArt
        HomeFeature.Learn -> TigerArt
        HomeFeature.EditSheets -> EditArt
    }

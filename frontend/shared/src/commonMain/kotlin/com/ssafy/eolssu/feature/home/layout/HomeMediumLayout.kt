package com.ssafy.eolssu.feature.home.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuLogo
import com.ssafy.eolssu.feature.home.HomeFeature
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.feature.home.component.FeatureSection
import com.ssafy.eolssu.feature.home.component.HomeHero
import com.ssafy.eolssu.feature.home.component.InstrumentLabel
import com.ssafy.eolssu.feature.home.component.MyPageButton
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_brand
import org.jetbrains.compose.resources.stringResource

/**
 * 태블릿 세로 배치(와이어프레임 `Tab-Main-Port`). 대표 일러스트를 크게 두고 카드 4개를 한 줄에 놓는다.
 * 대표 일러스트와 카드는 남은 높이의 가운데에 두고, 창이 낮으면 세로로 스크롤한다.
 */
@Composable
internal fun HomeMediumLayout(
    instrument: Instrument?,
    onOpenMyPage: () -> Unit,
    onStartEvaluation: () -> Unit,
    onFeatureClick: (HomeFeature) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 머리글·본문·빈 칸을 SpaceBetween으로 놓아 본문이 남은 높이의 가운데에 오게 한다.
            Column(
                Modifier
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = 40.dp)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { EolssuLogo() }
                        MyPageButton(onOpenMyPage)
                    }
                    InstrumentLabel(instrument, Modifier.padding(top = 6.dp))
                }
                Column(Modifier.padding(vertical = 24.dp)) {
                    HomeHero(onStartEvaluation, large = true)
                    FeatureSection(
                        onFeatureClick,
                        columns = 4,
                        cardMinHeight = 124.dp,
                        large = true,
                        modifier = Modifier.padding(top = 64.dp),
                    )
                }
                Spacer(Modifier)
            }
        }
    }
}

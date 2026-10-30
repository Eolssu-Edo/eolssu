package com.ssafy.eolssu.feature.home.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
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
 * 태블릿 가로·넓은 웹 창 배치(와이어프레임 `Tab-Main-Land`). 왼쪽에 대표 일러스트, 오른쪽에 카드 2×2를 둔다.
 * 내용은 최대 [WindowBreakpoints.ContentMaxWidth]로 가운데에 두고, 창이 낮으면 세로로 스크롤한다.
 */
@Composable
internal fun HomeExpandedLayout(
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
            Column(
                Modifier
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = 40.dp)
                    .widthIn(max = WindowBreakpoints.ContentMaxWidth)
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    EolssuLogo()
                    InstrumentLabel(instrument, Modifier.weight(1f))
                    MyPageButton(onOpenMyPage)
                }
                BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    // 와이어프레임은 1120dp 너비에서 카드 열 440dp. 좁은 가로 창에서는 대표 일러스트가 너무 좁아지지 않게 줄인다.
                    val featureWidth = (maxWidth * 0.4f).coerceIn(320.dp, 440.dp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                        HomeHero(onStartEvaluation, Modifier.weight(1f), large = true)
                        FeatureSection(
                            onFeatureClick,
                            columns = 2,
                            cardMinHeight = 140.dp,
                            large = true,
                            modifier = Modifier.width(featureWidth),
                        )
                    }
                }
                Spacer(Modifier)
            }
        }
    }
}

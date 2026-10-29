package com.ssafy.eolssu.feature.home.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** 휴대폰 배치(와이어프레임 `Main`). 넓은 창이 아니면 이 배치를 쓴다. */
@Composable
internal fun HomeCompactLayout(
    instrument: Instrument?,
    onOpenMyPage: () -> Unit,
    onStartEvaluation: () -> Unit,
    onFeatureClick: (HomeFeature) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            HomeHero(onStartEvaluation, Modifier.padding(bottom = 18.dp), edgeToEdge = true) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = EolssuTheme.colors.surface.copy(alpha = 0.94f), shape = RoundedCornerShape(12.dp)) {
                        EolssuLogo(Modifier.padding(horizontal = 10.dp, vertical = 6.dp).size(58.dp, 30.dp))
                    }
                    Surface(modifier = Modifier.weight(1f), color = EolssuTheme.colors.surface.copy(alpha = 0.94f), shape = RoundedCornerShape(12.dp)) {
                        InstrumentLabel(instrument, Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    MyPageButton(onOpenMyPage)
                }
            }
            FeatureSection(
                onFeatureClick,
                columns = 2,
                cardMinHeight = 150.dp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

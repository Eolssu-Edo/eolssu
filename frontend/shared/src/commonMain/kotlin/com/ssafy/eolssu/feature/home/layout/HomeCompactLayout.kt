package com.ssafy.eolssu.feature.home.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.ssafy.eolssu.feature.home.HomeFeature
import com.ssafy.eolssu.feature.home.Instrument
import com.ssafy.eolssu.feature.home.component.FeatureSection
import com.ssafy.eolssu.feature.home.component.HomeHero
import com.ssafy.eolssu.feature.home.component.InstrumentChip
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_brand
import org.jetbrains.compose.resources.stringResource

/** 휴대폰 배치(와이어프레임 `Main`). 넓은 창이 아니면 이 배치를 쓴다. */
@Composable
internal fun HomeCompactLayout(
    instrument: Instrument,
    onOpenInstrumentSheet: () -> Unit,
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
                .padding(horizontal = 20.dp)
                .padding(top = 30.dp, bottom = 24.dp),
        ) {
            Text(stringResource(Res.string.home_brand), style = EolssuTheme.serif.brand)
            InstrumentChip(instrument, onOpenInstrumentSheet, Modifier.padding(top = 6.dp))
            HomeHero(onStartEvaluation, Modifier.padding(top = 30.dp, bottom = 18.dp))
            FeatureSection(
                onFeatureClick,
                columns = 2,
                cardMinHeight = 104.dp,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

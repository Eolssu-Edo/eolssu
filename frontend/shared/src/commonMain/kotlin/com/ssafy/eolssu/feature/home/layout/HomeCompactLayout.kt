package com.ssafy.eolssu.feature.home.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.ssafy.eolssu.feature.home.HomeFeature
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.feature.home.component.FeatureSection
import com.ssafy.eolssu.feature.home.component.HomeHero
import com.ssafy.eolssu.feature.home.component.InstrumentPlate
import com.ssafy.eolssu.feature.home.component.LogoPlate
import com.ssafy.eolssu.feature.home.component.MyPagePlate
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_brand
import org.jetbrains.compose.resources.stringResource

/** 휴대폰 배치(보드 `Main`): 대표 그림 위에 로고 · 현재 악기 · 마이페이지 판을 얹는다. 넓은 창이 아니면 이 배치를 쓴다. */
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
            HomeHero(onStartEvaluation, Modifier.padding(bottom = 16.dp), edgeToEdge = true) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LogoPlate()
                    Spacer(Modifier.weight(1f))
                    InstrumentPlate(instrument, Modifier.padding(end = 8.dp))
                    MyPagePlate(onOpenMyPage)
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

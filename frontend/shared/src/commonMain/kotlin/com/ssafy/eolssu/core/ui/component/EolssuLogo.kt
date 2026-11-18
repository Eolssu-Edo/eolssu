package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.eolssu_logo
import eolssu.shared.generated.resources.home_brand
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 제공 디자인 HTML에서 그대로 추출한 투명 붓글씨 로고. [tint]를 주면 붓글씨를 그 색 하나로 칠한다(홈 히어로의 흰 로고). */
@Composable
fun EolssuLogo(modifier: Modifier = Modifier, tint: Color? = null) {
    Image(
        painterResource(Res.drawable.eolssu_logo), stringResource(Res.string.home_brand),
        modifier.size(width = 112.dp, height = 58.dp), contentScale = ContentScale.Fit,
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}

package com.ssafy.eolssu.feature.home.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuLogo
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_current_instrument
import eolssu.shared.generated.resources.instrument_unknown
import eolssu.shared.generated.resources.home_my_page
import org.jetbrains.compose.resources.stringResource

/** 태블릿 홈 '현재 악기' 표시. 바꾸기는 마이페이지(MY-01)로 옮겨 누를 수 없다. */
@Composable
internal fun InstrumentLabel(instrument: Instrument?, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.home_current_instrument),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.weight(1f, fill = false),
        )
        Surface(shape = CircleShape, color = colors.surface, contentColor = colors.text) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(EolssuIcons.Flute, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(stringResource(instrument?.label ?: Res.string.instrument_unknown), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** 대표 그림 위 판(`.plate`): 반투명 흰 바탕, 모서리 12dp, 옅은 그림자. [onClick]이 있으면 누를 수 있다. */
@Composable
private fun HeroPlate(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = EolssuTheme.colors
    val color = colors.background.copy(alpha = 0.93f)
    if (onClick == null) {
        Surface(modifier, shape = shape, color = color, contentColor = colors.text, shadowElevation = 1.dp, content = content)
    } else {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, contentColor = colors.text, shadowElevation = 1.dp, content = content)
    }
}

/** 휴대폰 홈 왼쪽 위 로고 판. */
@Composable
internal fun LogoPlate(modifier: Modifier = Modifier) {
    HeroPlate(modifier) { EolssuLogo(Modifier.padding(horizontal = 10.dp, vertical = 6.dp).size(58.dp, 30.dp)) }
}

/** 휴대폰 홈 '현재 악기 대금' 판: 회색 제목과 굵은 악기 이름(13sp)을 한 줄로 읽는다. */
@Composable
internal fun InstrumentPlate(instrument: Instrument?, modifier: Modifier = Modifier) {
    HeroPlate(modifier.semantics(mergeDescendants = true) {}) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
            Text(stringResource(Res.string.home_current_instrument), style = style.copy(fontWeight = FontWeight.Medium), color = EolssuTheme.colors.textMuted)
            Text(stringResource(instrument?.label ?: Res.string.instrument_unknown), style = style.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

/** 휴대폰 홈 마이페이지 판: 38dp 원, 사람 아이콘 16dp. 누르는 영역은 Material 최소 크기(48dp)를 유지한다. */
@Composable
internal fun MyPagePlate(onClick: () -> Unit, modifier: Modifier = Modifier) {
    HeroPlate(modifier.size(38.dp), CircleShape, onClick) {
        Box(contentAlignment = Alignment.Center) {
            Icon(EolssuIcons.User, contentDescription = stringResource(Res.string.home_my_page), modifier = Modifier.size(16.dp))
        }
    }
}

/** 태블릿 홈 오른쪽 위 마이페이지 입구: 48dp 터치 영역. */
@Composable
internal fun MyPageButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = colors.background,
        contentColor = colors.text,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Icon(EolssuIcons.User, contentDescription = stringResource(Res.string.home_my_page), modifier = Modifier.padding(12.dp))
    }
}

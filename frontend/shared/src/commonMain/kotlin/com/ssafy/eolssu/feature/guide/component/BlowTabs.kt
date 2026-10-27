package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.guide.Blow
import com.ssafy.eolssu.feature.guide.GuideInstrument
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_blow_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * 취법 탭(`.blow`). 이 악기에 없는 취법(단소·소금의 저취)은 누를 수 없다.
 *
 * @param large 태블릿 탭(높이 44dp · 모서리 12dp · 14sp, `.tabapp .blow button`).
 * @param vertical 태블릿 가로 배치의 왼쪽 칸처럼 탭을 세로로 쌓는다.
 */
@Composable
internal fun BlowTabs(
    instrument: GuideInstrument,
    shown: Blow,
    onSelect: (Blow) -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    vertical: Boolean = false,
) {
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Blow.entries.forEach { blow -> BlowTab(instrument, blow, shown, onSelect, large, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Blow.entries.forEach { blow -> BlowTab(instrument, blow, shown, onSelect, large, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun BlowTab(instrument: GuideInstrument, blow: Blow, shown: Blow, onSelect: (Blow) -> Unit, large: Boolean, modifier: Modifier) {
    val colors = EolssuTheme.colors
    val unavailable = stringResource(Res.string.guide_blow_unavailable)
    val enabled = blow in instrument.blows
    val on = blow == shown
    Surface(
        onClick = { onSelect(blow) },
        enabled = enabled,
        modifier = modifier
            .height(if (large) 44.dp else 34.dp)
            .semantics {
                role = Role.Tab
                selected = on
                if (!enabled) stateDescription = unavailable
            },
        shape = RoundedCornerShape(if (large) 12.dp else 10.dp),
        color = if (on) colors.text else colors.background,
        contentColor = when {
            on -> colors.onPrimary
            enabled -> colors.text
            else -> colors.lineStrong
        },
        border = BorderStroke(1.dp, if (on) colors.text else colors.line),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                stringResource(blow.label),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = if (large) 14.sp else 13.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

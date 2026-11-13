package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.core.model.fingering.BlowTab
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_blow_unavailable
import org.jetbrains.compose.resources.stringResource

/** 이 악기에 없는 취법 탭의 글자(보드 `--faint` #B9B3A6). 테마에 없는 옅은 색이라 이 탭에만 둔다. */
private val UnavailableBlowText = Color(0xFFB9B3A6)

/**
 * 취법 탭(`.blow`). 가로 배치는 회색 틀 안에서 고른 탭만 흰 칸으로 띄우고(다른 두 칸 탭과 같은 모양, S15P31E102-849), 이 악기에 없는 취법(단소·소금의 저취)은 누를 수 없다.
 *
 * @param large 태블릿 탭(높이 44dp · 모서리 12dp · 14sp).
 * @param vertical 태블릿 가로 배치의 왼쪽 칸처럼 탭을 세로로 쌓는다.
 */
@Composable
internal fun BlowTabs(
    tabs: List<BlowTab>,
    onSelect: (Blow) -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    vertical: Boolean = false,
) {
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEach { tab -> BlowTabButton(tab, onSelect, large, inTrack = false, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(
            modifier.background(EolssuTheme.colors.surfaceStrong, RoundedCornerShape(if (large) 16.dp else 14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEach { tab -> BlowTabButton(tab, onSelect, large, inTrack = true, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun BlowTabButton(tab: BlowTab, onSelect: (Blow) -> Unit, large: Boolean, inTrack: Boolean, modifier: Modifier) {
    val colors = EolssuTheme.colors
    val unavailable = stringResource(Res.string.guide_blow_unavailable)
    Surface(
        onClick = { onSelect(tab.blow) },
        enabled = tab.enabled,
        modifier = modifier
            .height(if (large) 44.dp else 34.dp)
            .semantics {
                role = Role.Tab
                selected = tab.selected
                if (!tab.enabled) stateDescription = unavailable
            },
        shape = RoundedCornerShape(if (large) 12.dp else 10.dp),
        // 세로로 쌓는 태블릿 탭은 틀이 없어 고른 탭을 예전처럼 연두로 칠한다.
        color = when {
            inTrack -> if (tab.selected) colors.surface else Color.Transparent
            else -> if (tab.selected) colors.primary else colors.surface
        },
        contentColor = when {
            tab.selected -> if (inTrack) colors.text else colors.onPrimary
            tab.enabled -> if (inTrack) colors.textSub else colors.text
            else -> UnavailableBlowText
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                stringResource(tab.blow.label),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = if (large) 14.sp else 13.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

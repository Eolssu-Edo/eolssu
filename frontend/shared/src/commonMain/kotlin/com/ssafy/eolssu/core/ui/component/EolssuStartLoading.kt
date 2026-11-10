package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.app_loading
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * 앱을 여는 동안(로그인 회원 확인 등) 보이는 화면. 가운데 로고만 두고 처리 과정을 설명하는 문구는 보이지 않는다.
 * 금방 끝나는 확인에서 도는 표시가 깜빡이지 않도록 [indicatorDelayMillis]가 지나도 끝나지 않을 때만 로고 아래에 작게 띄운다.
 * 화면 읽기 프로그램에는 '불러오는 중'으로 읽힌다.
 */
@Composable
fun EolssuStartLoading(modifier: Modifier = Modifier, indicatorDelayMillis: Long = 400) {
    var showIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(indicatorDelayMillis) {
        delay(indicatorDelayMillis)
        showIndicator = true
    }
    val description = stringResource(Res.string.app_loading)
    Column(
        modifier
            .fillMaxSize()
            .background(EolssuTheme.colors.background)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        EolssuLogo()
        // 표시 전후로 로고 위치가 바뀌지 않게 자리는 늘 둔다.
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (showIndicator) {
                CircularProgressIndicator(
                    Modifier.size(22.dp),
                    color = EolssuTheme.colors.radical,
                    trackColor = EolssuTheme.colors.surfaceStrong,
                    strokeWidth = 2.5.dp,
                )
            }
        }
    }
}

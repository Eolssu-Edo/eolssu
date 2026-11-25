package com.ssafy.eolssu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ssafy.eolssu.core.diagnostics.QaTarget
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.update.StoreUpdate
import com.ssafy.eolssu.core.update.StoreUpdateCheck
import com.ssafy.eolssu.core.update.StoreUpdates
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.store_update_body
import eolssu.shared.generated.resources.store_update_later
import eolssu.shared.generated.resources.store_update_now
import eolssu.shared.generated.resources.store_update_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

/**
 * 구버전 앱을 켤 때마다 띄우는 업데이트 안내(S15P31E102-1237, 실행마다 안내는 S15P31E102-1257).
 * 앱을 처음 그릴 때와 백그라운드에서 다시 앞으로 가져올 때(ON_START)마다 스토어를 확인한다.
 * '업데이트하기'는 앱 안 업데이트 화면(Android Play 즉시 업데이트, iOS App Store 시트)을 열고, '나중에'는 이번에만 닫는다.
 * 확인이 실패하거나 스토어 구현이 없는 플랫폼(Web)은 아무것도 하지 않는다.
 */
@Composable
internal fun StoreUpdateNotice() {
    val koin = getKoin()
    val check = remember { koin.getOrNull<StoreUpdates>()?.let(::StoreUpdateCheck) } ?: return
    var update by remember { mutableStateOf<StoreUpdate?>(null) }
    var starts by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { starts++ }
    LaunchedEffect(check, starts) {
        if (starts > 0) update = check.pending()
    }
    val shown = update ?: return
    EolssuDialog(
        title = stringResource(Res.string.store_update_title),
        message = stringResource(Res.string.store_update_body),
    ) {
        EolssuButton(stringResource(Res.string.store_update_now), {
            update = null
            check.open(shown)
        }, qaTarget = QaTarget.STORE_UPDATE_NOTICE_BUTTON_01)
        EolssuButton(stringResource(Res.string.store_update_later), {
            update = null
        }, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.STORE_UPDATE_NOTICE_BUTTON_02)
    }
}

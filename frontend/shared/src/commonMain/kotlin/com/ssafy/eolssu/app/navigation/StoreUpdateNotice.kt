package com.ssafy.eolssu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ssafy.eolssu.core.diagnostics.QaTarget
import com.ssafy.eolssu.core.storage.KeyValueStore
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
 * 앱을 켤 때 스토어에 새 버전이 있으면 한 번 알리는 선택 업데이트 안내(S15P31E102-1237).
 * '업데이트'는 스토어 업데이트 화면을 열고, '나중에'를 누른 버전은 다시 묻지 않는다.
 * 확인은 앱 실행마다 한 번이며, 실패하거나 스토어 구현이 없는 플랫폼(Web)은 아무것도 하지 않는다.
 */
@Composable
internal fun StoreUpdateNotice() {
    val koin = getKoin()
    val check = remember { koin.getOrNull<StoreUpdates>()?.let { StoreUpdateCheck(it, koin.get<KeyValueStore>()) } } ?: return
    var update by remember { mutableStateOf<StoreUpdate?>(null) }
    LaunchedEffect(check) { update = check.pending() }
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
            check.later(shown)
        }, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.STORE_UPDATE_NOTICE_BUTTON_02)
    }
}

package com.ssafy.eolssu.feature.song.component

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.component.PracticeInstrumentDropdown
import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.feature.song.SongInstrumentSelection
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.member_api_instrument_unavailable
import eolssu.shared.generated.resources.member_api_rate_limited
import eolssu.shared.generated.resources.member_api_unavailable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * 곡 고르기 화면 상단 바 오른쪽의 연습 악기 드롭다운. 저장 실패는 마이페이지와 같은 문구를 [snackbarHostState]에 띄우고,
 * 세션이 끝났으면 [onSessionExpired]로 로그인에 간다.
 */
@Composable
internal fun SongInstrumentPicker(
    selection: SongInstrumentSelection,
    snackbarHostState: SnackbarHostState,
    onSessionExpired: () -> Unit,
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
) {
    val instrument by selection.instrument.collectAsStateWithLifecycle()
    val saving by selection.saving.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    PracticeInstrumentDropdown(
        instrument = instrument,
        enabled = !saving,
        modifier = modifier,
        bordered = bordered,
        onSelect = { selected ->
            scope.launch {
                val message = when (selection.select(selected)) {
                    null, MemberResult.Done -> return@launch
                    MemberResult.Unauthenticated -> return@launch onSessionExpired()
                    MemberResult.RateLimited -> getString(Res.string.member_api_rate_limited)
                    MemberResult.InvalidInput -> getString(Res.string.member_api_instrument_unavailable)
                    else -> getString(Res.string.member_api_unavailable)
                }
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        },
    )
}

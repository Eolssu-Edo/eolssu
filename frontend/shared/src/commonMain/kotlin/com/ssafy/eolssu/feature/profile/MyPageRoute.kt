package com.ssafy.eolssu.feature.profile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.ssafy.eolssu.data.user.MemberResult
import eolssu.shared.generated.resources.member_api_unavailable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import eolssu.shared.generated.resources.legal_open_failed
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.StartMessage
import com.ssafy.eolssu.core.ui.component.StartMessageEffect
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.member_api_instrument_unavailable
import eolssu.shared.generated.resources.my_unlinked
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

/**
 * MY-01 연결. 닉네임 수정·비밀번호 변경·탈퇴는 [onOpen], 간편 로그인 연결은 [onLink], 로그아웃 확인 뒤에는 [onLoggedOut]으로 간다.
 * [startMessage]는 닉네임 수정·연결처럼 다녀온 화면이 남긴 안내다.
 */
@Composable
fun MyPageRoute(
    onBack: () -> Unit,
    onOpen: (MyPageTarget) -> Unit,
    onLink: (SocialProvider) -> Unit,
    onLoggedOut: () -> Unit,
    startMessage: StartMessage? = null,
    onStartMessageShown: () -> Unit = {},
    viewModel: MyPageViewModel = koinViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val instrument by viewModel.instrument.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    StartMessageEffect(snackbarHostState, startMessage, onStartMessageShown)
    val show: (suspend () -> String) -> Unit = { message ->
        scope.launch {
            val text = message()
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text)
        }
    }

    LaunchedEffect(viewModel) {
        if (viewModel.refresh() != MemberResult.Done) snackbarHostState.showSnackbar(getString(Res.string.member_api_unavailable))
    }
    MyPageScreen(
        profile = profile,
        instrument = instrument,
        dialog = dialog,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpen = onOpen,
        onSelectInstrument = { selected ->
            scope.launch {
                if (selected != instrument && !viewModel.selectInstrument(selected)) show { getString(Res.string.member_api_instrument_unavailable) }
            }
        },
        onLink = onLink,
        onUnlink = viewModel::askUnlink,
        onConfirmUnlink = {
            val provider = (dialog as? MyPageDialog.Unlink)?.provider
            scope.launch {
                when (viewModel.confirmUnlink()) {
                    null -> Unit
                    MemberResult.Done -> provider?.let { show { getString(Res.string.my_unlinked, getString(it.displayName)) } }
                    else -> show { getString(Res.string.member_api_unavailable) }
                }
            }
        },
        onLogOut = viewModel::askLogOut,
        onConfirmLogOut = {
            scope.launch {
                viewModel.confirmLogOut().join()
                onLoggedOut()
            }
        },
        onDismissDialog = viewModel::dismissDialog,
        onOpenLegal = { document ->
            if (runCatching { uriHandler.openUri(document.url) }.isFailure) {
                show { getString(Res.string.legal_open_failed) }
            }
        },
    )
}

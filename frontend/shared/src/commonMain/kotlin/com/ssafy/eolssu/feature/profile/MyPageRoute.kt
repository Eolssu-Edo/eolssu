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
import com.ssafy.eolssu.data.user.LearnProgressRepository
import eolssu.shared.generated.resources.my_inquiry_preparing
import eolssu.shared.generated.resources.my_feedback_open_failed
import eolssu.shared.generated.resources.my_password_none
import eolssu.shared.generated.resources.my_unlink_last
import eolssu.shared.generated.resources.my_unlinked
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject

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
    onSessionExpired: () -> Unit,
    startMessage: StartMessage? = null,
    onStartMessageShown: () -> Unit = {},
    viewModel: MyPageViewModel = koinViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val instrument by viewModel.instrument.collectAsStateWithLifecycle()
    val loadingProfile by viewModel.loadingProfile.collectAsStateWithLifecycle()
    val progressRepository = koinInject<LearnProgressRepository>()
    val progressRevision by progressRepository.saves.collectAsStateWithLifecycle()
    val learningProgress = remember(instrument, progressRepository, progressRevision) {
        instrument?.let { myLearningProgress(it, progressRepository) }
    }
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
        when (viewModel.refresh()) {
            MemberResult.Done -> Unit
            MemberResult.Unauthenticated -> onSessionExpired()
            else -> snackbarHostState.showSnackbar(getString(Res.string.member_api_unavailable))
        }
    }
    MyPageScreen(
        profile = profile,
        instrument = instrument,
        learningProgress = learningProgress,
        loadingProfile = loadingProfile,
        dialog = dialog,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpen = { target ->
            if (target == MyPageTarget.Password && !viewModel.canChangePassword()) show { getString(Res.string.my_password_none) } else onOpen(target)
        },
        onLink = onLink,
        onUnlink = { provider -> if (!viewModel.askUnlink(provider)) show { getString(Res.string.my_unlink_last) } },
        onConfirmUnlink = {
            val provider = (dialog as? MyPageDialog.Unlink)?.provider
            scope.launch {
                when (viewModel.confirmUnlink()) {
                    null -> Unit
                    MemberResult.Done -> provider?.let { show { getString(Res.string.my_unlinked, getString(it.displayName)) } }
                    MemberResult.LastLoginMethod -> show { getString(Res.string.my_unlink_last) }
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
        onOpenFeedback = {
            if (runCatching { uriHandler.openUri(FEEDBACK_FORM_URL) }.isFailure) {
                show { getString(Res.string.my_feedback_open_failed) }
            }
        },
        onOpenInquiry = { show { getString(Res.string.my_inquiry_preparing) } },
    )
}

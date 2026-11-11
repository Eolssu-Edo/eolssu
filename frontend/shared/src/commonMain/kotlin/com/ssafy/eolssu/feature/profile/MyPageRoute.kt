package com.ssafy.eolssu.feature.profile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.ssafy.eolssu.data.user.MemberResult
import eolssu.shared.generated.resources.member_api_unavailable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import com.ssafy.eolssu.data.user.PublishedLegalDocument
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.StartMessage
import com.ssafy.eolssu.core.ui.component.StartMessageEffect
import eolssu.shared.generated.resources.Res
import com.ssafy.eolssu.data.user.LearnProgressRepository
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
 * 하단 법적 문서는 [onOpenLegal]로 앱 안 문서 화면을 연다.
 * [startMessage]는 닉네임 수정·연결처럼 다녀온 화면이 남긴 안내다.
 */
@Composable
fun MyPageRoute(
    onBack: () -> Unit,
    onOpen: (MyPageTarget) -> Unit,
    onLink: (SocialProvider) -> Unit,
    onLoggedOut: () -> Unit,
    onSessionExpired: () -> Unit,
    onOpenLegal: (PublishedLegalDocument) -> Unit,
    startMessage: StartMessage? = null,
    onStartMessageShown: () -> Unit = {},
    providers: List<SocialProvider> = SocialProvider.entries - SocialProvider.Apple,
    viewModel: MyPageViewModel = koinViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val instrument by viewModel.instrument.collectAsStateWithLifecycle()
    val progressRepository = koinInject<LearnProgressRepository>()
    val progressRevision by progressRepository.saves.collectAsStateWithLifecycle()
    // 학습 진도는 서버에서 받는다. 악기·완료 표시가 바뀌면 다시 받고, 받는 동안·받지 못하면 진도 칸을 비워 둔다.
    val learningProgress by produceState<MyLearningProgress?>(null, instrument, progressRepository, progressRevision) {
        value = instrument?.let { myLearningProgress(it, progressRepository) }
    }
    // 최근 연주는 연습 기록 화면과 같은 저장소(REC-02)에서 받는다. 이미 받은 기록이 있으면 받는 동안·실패해도 그대로 보인다.
    val recordRepository = koinInject<PracticeRecordRepository>()
    val now = koinInject<() -> Long>()
    val records by recordRepository.records.collectAsStateWithLifecycle()
    val today = remember(now) { KoreanDateTime.fromEpochMillis(now()) }
    val recordsLoad by produceState(if (recordRepository.available) RecordsLoad.Loading else RecordsLoad.Done, recordRepository) {
        if (recordRepository.available) value = if (recordRepository.refresh()) RecordsLoad.Done else RecordsLoad.Failed
    }
    val recentPractice = remember(records, today) { MyRecentPractice.from(records, today) }
    val recentStatus = myRecentPracticeStatus(
        available = recordRepository.available,
        hasRecords = records.isNotEmpty(),
        loading = recordsLoad == RecordsLoad.Loading,
        failed = recordsLoad == RecordsLoad.Failed,
    )
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
        providers = providers,
        learningProgress = learningProgress,
        recentPractice = recentPractice,
        recentStatus = recentStatus,
        today = today,
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
        onOpenLegal = onOpenLegal,
        onOpenFeedback = {
            if (runCatching { uriHandler.openUri(FEEDBACK_FORM_URL) }.isFailure) {
                show { getString(Res.string.my_feedback_open_failed) }
            }
        },
    )
}

private enum class RecordsLoad { Loading, Done, Failed }

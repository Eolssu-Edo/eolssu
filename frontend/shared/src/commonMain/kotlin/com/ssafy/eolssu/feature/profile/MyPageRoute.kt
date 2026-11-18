package com.ssafy.eolssu.feature.profile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
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
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.RecentPracticeSummary
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.StartMessage
import com.ssafy.eolssu.core.ui.component.StartMessageEffect
import eolssu.shared.generated.resources.Res
import com.ssafy.eolssu.data.user.LearnProgressRepository
import eolssu.shared.generated.resources.my_feedback_open_failed
import eolssu.shared.generated.resources.my_password_none
import eolssu.shared.generated.resources.my_unlink_last
import eolssu.shared.generated.resources.my_unlinked
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
    onLogOutStarted: () -> Unit,
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
    // 학습 진도는 서버에서 받는다. 완료 표시가 바뀌면 다시 받고, 받지 못하면 진도 칸을 비워 둔다.
    // 내 정보(연습 악기)를 기다리지 않고 바로 받는다. 연습 악기는 완료 기록이 하나도 없을 때만 기다려 쓴다(S15P31E102-768).
    // 다른 화면에서 돌아와 다시 받는 동안에는 마지막 진도를 그대로 보여 화면이 흔들리지 않게 한다.
    // 처음 들어올 때도 이 기기에 남긴 마지막 진도부터 바로 보이고, 서버 응답이 오면 바꾼다(S15P31E102-768).
    val initialProgress = remember(progressRepository) {
        viewModel.lastLearningProgress() ?: progressRepository.cachedRecent()?.let { MyLearningProgress(it.stages, it.instrument) }
    }
    val learningProgress by produceState(initialProgress, progressRepository, progressRevision) {
        value = myLearningProgress({ snapshotFlow { instrument }.filterNotNull().first() }, progressRepository)
            .also(viewModel::rememberLearningProgress)
    }
    val recordRepository = koinInject<PracticeRecordRepository>()
    val member = koinInject<SignedInMember>()
    val session = member.session.collectAsStateWithLifecycle().value
    val now = koinInject<() -> Long>()
    val today = remember(now) { KoreanDateTime.fromEpochMillis(now()) }
    val signingOut = remember { mutableStateOf(false) }
    val recent by key(session, today) {
        val initial = remember(recordRepository) { RecentPracticeLoad(recordRepository.cachedRecentPractice(today), RecordsLoad.Loading) }
        produceState(initial, recordRepository) {
            if (signingOut.value || !recordRepository.available || member.session.value != session) return@produceState
            val summary = recordRepository.loadRecentPractice(today) { !signingOut.value }
            if (signingOut.value || member.session.value != session) return@produceState
            value = if (summary != null) {
                RecentPracticeLoad(summary, RecordsLoad.Done)
            } else {
                RecentPracticeLoad(recordRepository.cachedRecentPractice(today), RecordsLoad.Failed)
            }
        }
    }
    val recentStatus = myRecentPracticeStatus(
        available = recordRepository.available,
        hasRecords = recent.summary?.latest != null,
        loading = recent.load == RecordsLoad.Loading,
        failed = recent.load == RecordsLoad.Failed,
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
        recentPractice = recent.summary,
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
            // 선조회 뒤에 대기하던 마이페이지 조회는 새로 시작하지 않는다.
            signingOut.value = true
            onLogOutStarted()
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

private data class RecentPracticeLoad(val summary: RecentPracticeSummary?, val load: RecordsLoad)

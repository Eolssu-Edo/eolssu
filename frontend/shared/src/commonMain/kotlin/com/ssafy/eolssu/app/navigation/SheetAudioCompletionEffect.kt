package com.ssafy.eolssu.app.navigation

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ssafy.eolssu.data.song.NotifyingSheetAudioRepository
import com.ssafy.eolssu.data.song.SheetAudioCompletion
import com.ssafy.eolssu.core.network.SignedInMember
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheet_audio_completed
import eolssu.shared.generated.resources.sheet_audio_open
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/** This effect stays above destinations; screen navigation does not restart delivery. */
@Composable
internal fun SheetAudioCompletionEffect(
    repository: NotifyingSheetAudioRepository,
    member: SignedInMember,
    snackbar: SnackbarHostState,
    onOpen: (String) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val open = rememberUpdatedState(onOpen)
    LaunchedEffect(repository, member, snackbar, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            launch { repository.monitor() }
            // Queue changes must not cancel a visible notice. Account changes must.
            member.session.collectLatest { session ->
                if (session.memberId == null) return@collectLatest
                while (currentCoroutineContext().isActive) {
                    val completion = repository.completions.first { it.isNotEmpty() }.first()
                    showSheetAudioCompletion(repository, snackbar, completion,
                        getString(Res.string.sheet_audio_completed), getString(Res.string.sheet_audio_open)) { open.value(it) }
                }
            }
        }
    }
}

/** The top banner remains pending until the user opens or dismisses it, including across pause. */
internal suspend fun showSheetAudioCompletion(
    repository: NotifyingSheetAudioRepository,
    snackbar: SnackbarHostState,
    completion: SheetAudioCompletion,
    message: String,
    actionLabel: String,
    onOpen: (String) -> Unit,
) = showSheetCompletion(snackbar, completion.sheetId, message, actionLabel,
    isCurrent = { repository.isCurrent(completion) }, consume = { repository.consume(completion) }, onOpen = onOpen)

/** Audio and recognition notices share visibility/acknowledgement semantics on the same host. */
internal suspend fun showSheetCompletion(
    snackbar: SnackbarHostState,
    sheetId: String,
    message: String,
    actionLabel: String,
    isCurrent: () -> Boolean,
    consume: () -> Unit,
    onOpen: (String) -> Unit,
) = coroutineScope {
    if (!isCurrent()) return@coroutineScope
    val visuals = object : SnackbarVisuals {
        override val message = message
        override val actionLabel = actionLabel
        override val duration = SnackbarDuration.Indefinite
        override val withDismissAction = true
    }
    try {
        val result = snackbar.showSnackbar(visuals)
        consume()
        if (result == SnackbarResult.ActionPerformed && isCurrent()) onOpen(sheetId)
    } finally {
        snackbar.currentSnackbarData?.takeIf { it.visuals === visuals }?.dismiss()
    }
}

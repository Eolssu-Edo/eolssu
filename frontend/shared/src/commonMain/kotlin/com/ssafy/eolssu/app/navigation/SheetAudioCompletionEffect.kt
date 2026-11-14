package com.ssafy.eolssu.app.navigation

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.CoroutineStart
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

/** Acknowledge when actually visible, so pause neither repeats shown notices nor loses queued ones. */
internal suspend fun showSheetAudioCompletion(
    repository: NotifyingSheetAudioRepository,
    snackbar: SnackbarHostState,
    completion: SheetAudioCompletion,
    message: String,
    actionLabel: String,
    onOpen: (String) -> Unit,
) = coroutineScope {
    if (!repository.isCurrent(completion)) return@coroutineScope
    val visuals = object : SnackbarVisuals {
        override val message = message
        override val actionLabel = actionLabel
        override val duration = SnackbarDuration.Long
        override val withDismissAction = false
    }
    val delivery = launch(start = CoroutineStart.UNDISPATCHED) {
        snapshotFlow { snackbar.currentSnackbarData?.visuals === visuals }.first { it }
        repository.consume(completion)
    }
    try {
        val result = snackbar.showSnackbar(visuals)
        repository.consume(completion)
        if (result == SnackbarResult.ActionPerformed && repository.isCurrent(completion)) onOpen(completion.sheetId)
    } finally {
        delivery.cancel()
        snackbar.currentSnackbarData?.takeIf { it.visuals === visuals }?.dismiss()
    }
}

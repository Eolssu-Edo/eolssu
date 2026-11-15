package com.ssafy.eolssu.app.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.data.song.NotifyingSheetConversionRepository
import com.ssafy.eolssu.data.song.SheetConversionCompletion
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheet_audio_open
import eolssu.shared.generated.resources.sheet_conversion_completed
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Composable
internal fun SheetConversionCompletionEffect(
    repository: NotifyingSheetConversionRepository,
    member: SignedInMember,
    snackbar: SnackbarHostState,
    onOpen: (String) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val open = rememberUpdatedState(onOpen)
    LaunchedEffect(repository, member, snackbar, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            launch { repository.monitor() }
            member.session.collectLatest { session ->
                if (session.memberId == null) return@collectLatest
                while (currentCoroutineContext().isActive) {
                    val completion = repository.completions.first { it.isNotEmpty() }.first()
                    showSheetConversionCompletion(repository, snackbar, completion,
                        getString(Res.string.sheet_conversion_completed), getString(Res.string.sheet_audio_open),
                    ) { open.value(it) }
                }
            }
        }
    }
}

internal suspend fun showSheetConversionCompletion(
    repository: NotifyingSheetConversionRepository,
    snackbar: SnackbarHostState,
    completion: SheetConversionCompletion,
    message: String,
    actionLabel: String,
    onOpen: (String) -> Unit,
) = coroutineScope {
    val showing = launch {
        showSheetCompletion(snackbar, completion.sheetId, message, actionLabel,
            isCurrent = { repository.isCurrent(completion) }, consume = { repository.consume(completion) }, onOpen = onOpen)
    }
    val invalidation = launch { repository.awaitInvalidation(completion); showing.cancel() }
    try { showing.join() } finally { invalidation.cancel() }
}

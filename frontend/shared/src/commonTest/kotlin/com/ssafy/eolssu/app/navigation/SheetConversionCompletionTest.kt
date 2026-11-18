package com.ssafy.eolssu.app.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.snapshots.Snapshot
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SheetConversionCompletionTest {
    @Test fun deletingWhileQueuedBehindAnotherNoticeNeverDisplaysOrOpensTheDeletedResult() = runTest {
        val api = ConversionApi()
        val repository = repository(api)
        repository.upload(input, "upload-1")
        repository.status("12")
        val completion = repository.completions.first().single()
        val snackbar = SnackbarHostState()
        val other = backgroundScope.launch { snackbar.showSnackbar("audio ready") }
        runCurrent()
        val opened = mutableListOf<String>()
        backgroundScope.launch { showSheetConversionCompletion(repository, snackbar, completion, "score ready", "open", opened::add) }
        runCurrent(); Snapshot.sendApplyNotifications(); runCurrent()
        api.deleted = true
        repository.status("12")
        runCurrent()
        other.cancelAndJoin()
        runCurrent(); Snapshot.sendApplyNotifications(); runCurrent()
        assertNull(snackbar.currentSnackbarData, "404 must cancel a recognition notice waiting behind audio")
        assertTrue(opened.isEmpty())
    }

    @Test fun visibleRecognitionNoticeOpensItsOwnSheetAndIsNotReplayed() = runTest {
        val api = ConversionApi()
        val repository = repository(api)
        repository.upload(input, "upload-1"); repository.status("12")
        val snackbar = SnackbarHostState()
        val opened = mutableListOf<String>()
        backgroundScope.launch { showSheetConversionCompletion(repository, snackbar,
            repository.completions.first().single(), "score ready", "open", opened::add) }
        runCurrent(); Snapshot.sendApplyNotifications(); runCurrent()
        assertEquals("score ready", snackbar.currentSnackbarData?.visuals?.message)
        assertEquals(SnackbarDuration.Short, snackbar.currentSnackbarData?.visuals?.duration)
        assertTrue(snackbar.currentSnackbarData!!.visuals.withDismissAction)
        assertEquals(1, repository.completions.first().size)
        snackbar.currentSnackbarData!!.performAction(); runCurrent()
        assertEquals(listOf("12"), opened)
        assertTrue(repository.completions.first().isEmpty())
    }

    private fun repository(api: ConversionApi) = NotifyingSheetConversionRepository(api, api,
        SignedInMember(FakeKeyValueStore()).apply { set("8") })

    private class ConversionApi : SheetConversionRepository, SheetImportRepository {
        var deleted = false
        override suspend fun upload(input: SheetImportInput, key: String) = SheetImportResult.Accepted("12")
        override suspend fun status(sheetId: String) = if (deleted) SheetConversionLookup.NotFound
            else SheetConversionLookup.Found(SheetConversion("12", SheetConversionStatus.Done))
        override suspend fun retry(sheetId: String) = SheetConversionRetry.NotRetryable
    }

    companion object { private val input = SheetImportInput("알림 확인", "STAFF", "1", null, null, emptyList()) }
}

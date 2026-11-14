package com.ssafy.eolssu.app.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.snapshots.Snapshot
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.NotifyingSheetAudioRepository
import com.ssafy.eolssu.data.song.SheetAudio
import com.ssafy.eolssu.data.song.SheetAudioFile
import com.ssafy.eolssu.data.song.SheetAudioLookup
import com.ssafy.eolssu.data.song.SheetAudioRepository
import com.ssafy.eolssu.data.song.SheetAudioRequest
import com.ssafy.eolssu.data.song.SheetAudioSettings
import com.ssafy.eolssu.data.song.SheetAudioStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SheetAudioCompletionTest {
    @Test fun visibleNoticeIsConsumedBeforePauseAndActionOpensItsSheet() = runTest {
        val fixture = fixture()
        val snackbar = SnackbarHostState()
        val opened = mutableListOf<String>()
        val notice = fixture.repository.completions.first().single()
        val showing = backgroundScope.launch {
            showSheetAudioCompletion(fixture.repository, snackbar, notice, "ready", "open", opened::add)
        }
        runCurrent(); Snapshot.sendApplyNotifications(); runCurrent()
        assertTrue(fixture.repository.completions.first().isEmpty(), "visible notice must not be replayed after resume")
        assertEquals("ready", snackbar.currentSnackbarData?.visuals?.message)
        snackbar.currentSnackbarData!!.performAction()
        runCurrent()
        assertEquals(listOf("12"), opened)
        showing.cancelAndJoin()
        assertTrue(fixture.repository.completions.first().isEmpty())
    }

    @Test fun pauseWhileWaitingForAnotherNoticePreservesPendingCompletion() = runTest {
        val fixture = fixture()
        val snackbar = SnackbarHostState()
        val other = backgroundScope.launch { snackbar.showSnackbar("other") }
        runCurrent()
        val otherData = snackbar.currentSnackbarData
        val notice = fixture.repository.completions.first().single()
        val showing = backgroundScope.launch {
            showSheetAudioCompletion(fixture.repository, snackbar, notice, "ready", "open") {}
        }
        runCurrent(); Snapshot.sendApplyNotifications(); runCurrent()
        showing.cancelAndJoin()
        assertSame(otherData, snackbar.currentSnackbarData)
        assertEquals(listOf(notice), fixture.repository.completions.first())
        other.cancelAndJoin()
        val resumed = backgroundScope.launch {
            showSheetAudioCompletion(fixture.repository, snackbar, notice, "ready", "open") {}
        }
        runCurrent(); Snapshot.sendApplyNotifications(); runCurrent()
        assertEquals("ready", snackbar.currentSnackbarData?.visuals?.message)
        assertTrue(fixture.repository.completions.first().isEmpty())
        resumed.cancelAndJoin()
    }

    @Test fun accountChangeInvalidatesVisibleNoticeAction() = runTest {
        val fixture = fixture()
        val snackbar = SnackbarHostState()
        val opened = mutableListOf<String>()
        val notice = fixture.repository.completions.first().single()
        backgroundScope.launch {
            showSheetAudioCompletion(fixture.repository, snackbar, notice, "ready", "open", opened::add)
        }
        runCurrent(); Snapshot.sendApplyNotifications(); runCurrent()
        fixture.member.set("9")
        snackbar.currentSnackbarData!!.performAction()
        runCurrent()
        assertTrue(opened.isEmpty())
        assertTrue(fixture.repository.completions.first().isEmpty())
    }

    private data class Fixture(val member: SignedInMember, val repository: NotifyingSheetAudioRepository)
    private suspend fun fixture(): Fixture {
        val member = SignedInMember(FakeKeyValueStore()).apply { set("8") }
        val queued = SheetAudio("12", SheetAudioStatus.Queued, "2", "2", false, "1", false, 60)
        val api = object : SheetAudioRepository {
            override suspend fun request(sheetId: String, settings: SheetAudioSettings) = SheetAudioRequest.Accepted(queued)
            override suspend fun status(sheetId: String) = SheetAudioLookup.Found(queued.copy(status = SheetAudioStatus.Done,
                audio = SheetAudioFile("https://example.test/a.m4a", Long.MAX_VALUE, "audio/mp4", 1, "a.m4a")))
        }
        val repository = NotifyingSheetAudioRepository(api, member)
        repository.request("12")
        repository.status("12")
        return Fixture(member, repository)
    }
}

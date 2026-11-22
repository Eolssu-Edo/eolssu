package com.ssafy.eolssu.core.update

import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreUpdateCheckTest {
    private class FakeStoreUpdates(var latest: StoreUpdate?, var failure: Exception? = null) : StoreUpdates {
        val opened = mutableListOf<StoreUpdate>()

        override suspend fun latest(): StoreUpdate? {
            failure?.let { throw it }
            return latest
        }

        override fun open(update: StoreUpdate) {
            opened += update
        }
    }

    @Test
    fun newStoreVersionIsShown() = runTest {
        val check = StoreUpdateCheck(FakeStoreUpdates(StoreUpdate("12")), FakeKeyValueStore())

        assertEquals(StoreUpdate("12"), check.pending())
    }

    @Test
    fun nothingIsShownWithoutStoreUpdate() = runTest {
        val check = StoreUpdateCheck(FakeStoreUpdates(null), FakeKeyValueStore())

        assertNull(check.pending())
    }

    @Test
    fun laterHidesSameVersionAfterRestart() = runTest {
        val updates = FakeStoreUpdates(StoreUpdate("12"))
        val store = FakeKeyValueStore()
        StoreUpdateCheck(updates, store).later(StoreUpdate("12"))

        // 같은 저장소로 다시 만들면 앱을 다시 켠 것과 같다.
        assertNull(StoreUpdateCheck(updates, store).pending())
    }

    @Test
    fun newerVersionIsShownAgainAfterLater() = runTest {
        val updates = FakeStoreUpdates(StoreUpdate("12"))
        val store = FakeKeyValueStore()
        StoreUpdateCheck(updates, store).later(StoreUpdate("12"))
        updates.latest = StoreUpdate("13")

        assertEquals(StoreUpdate("13"), StoreUpdateCheck(updates, store).pending())
    }

    @Test
    fun failedCheckStartsWithoutNotice() = runTest {
        val check = StoreUpdateCheck(FakeStoreUpdates(StoreUpdate("12"), failure = IllegalStateException("offline")), FakeKeyValueStore())

        assertNull(check.pending())
    }

    @Test
    fun openAsksPlatformToOpenStore() {
        val updates = FakeStoreUpdates(StoreUpdate("12"))

        StoreUpdateCheck(updates, FakeKeyValueStore()).open(StoreUpdate("12"))

        assertEquals(listOf(StoreUpdate("12")), updates.opened)
    }

    @Test
    fun comparesDottedVersionsByNumber() {
        assertTrue(isNewerVersion("1.0.1", "1.0.0"))
        assertTrue(isNewerVersion("1.0.1", "1.0"))
        assertTrue(isNewerVersion("1.10", "1.9"))
        assertTrue(isNewerVersion("2", "1.9.9"))
        assertFalse(isNewerVersion("1.0", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0", "1.0.1"))
        assertFalse(isNewerVersion("1.9", "1.10"))
    }

    @Test
    fun unreadableVersionIsNotNewer() {
        assertFalse(isNewerVersion("1.0.1-beta", "1.0.0"))
        assertFalse(isNewerVersion("1.0.1", "1.0.0-qa"))
        assertFalse(isNewerVersion("", "1.0.0"))
        assertFalse(isNewerVersion("1..1", "1.0"))
    }
}

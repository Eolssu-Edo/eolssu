package com.ssafy.eolssu.core.update

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
        val check = StoreUpdateCheck(FakeStoreUpdates(StoreUpdate("12")))

        assertEquals(StoreUpdate("12"), check.pending())
    }

    @Test
    fun nothingIsShownWithoutStoreUpdate() = runTest {
        val check = StoreUpdateCheck(FakeStoreUpdates(null))

        assertNull(check.pending())
    }

    @Test
    fun sameVersionIsShownAgainOnEveryCheck() = runTest {
        // '나중에'를 눌러도 저장하지 않으므로, 다시 켜면(다시 확인하면) 같은 버전을 또 안내한다(S15P31E102-1257).
        val check = StoreUpdateCheck(FakeStoreUpdates(StoreUpdate("12")))

        assertEquals(StoreUpdate("12"), check.pending())
        assertEquals(StoreUpdate("12"), StoreUpdateCheck(FakeStoreUpdates(StoreUpdate("12"))).pending())
        assertEquals(StoreUpdate("12"), check.pending())
    }

    @Test
    fun failedCheckStartsWithoutNotice() = runTest {
        val check = StoreUpdateCheck(FakeStoreUpdates(StoreUpdate("12"), failure = IllegalStateException("offline")))

        assertNull(check.pending())
    }

    @Test
    fun openAsksPlatformToOpenStore() {
        val updates = FakeStoreUpdates(StoreUpdate("12"))

        StoreUpdateCheck(updates).open(StoreUpdate("12"))

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

package com.ssafy.eolssu.app.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecentPracticePrefetchTest {
    @Test
    fun keepsRequestOnMyPageAndCancelsWhenAnotherDestinationOpens() = runTest {
        val prefetch = RecentPracticePrefetch(this)
        var starts = 0
        var cancelled = false
        prefetch.start {
            starts++
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        runCurrent()

        prefetch.onDestinationChanged(EolssuDestination.HOME)
        prefetch.onDestinationChanged(ProfileDestination.MY_PAGE)
        prefetch.start { starts++ }
        runCurrent()
        assertEquals(1, starts)
        assertFalse(cancelled)

        prefetch.onDestinationChanged(ProfileDestination.PASSWORD)
        runCurrent()
        assertTrue(cancelled)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun explicitCancellationStopsARequestBeforeItStarts() = runTest {
        val prefetch = RecentPracticePrefetch(this)
        var started = false
        prefetch.start { started = true }
        prefetch.cancel()
        runCurrent()
        assertFalse(started)
    }
}

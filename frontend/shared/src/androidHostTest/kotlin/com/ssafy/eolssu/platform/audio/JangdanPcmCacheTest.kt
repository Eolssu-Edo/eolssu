package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.JangdanSample
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest

class JangdanPcmCacheTest {
    @Test
    fun releaseRejectsBothLateAndQueuedPreparationButTheSameCacheCanPrepareAgain() = runTest {
        val clip = JangdanClip(JangdanSample.Deong)
        val decoding = CompletableDeferred<Unit>()
        var calls = 0
        val cache = JangdanPcmCache(
            decode = {
                calls++
                if (calls == 1) decoding.await()
                shortArrayOf(calls.toShort())
            },
            stretch = { pcm, _ -> pcm },
        )
        val old = async(start = CoroutineStart.UNDISPATCHED) { cache.prepare(setOf(clip)) }
        val queued = async(start = CoroutineStart.UNDISPATCHED) { cache.prepare(setOf(clip)) }
        cache.clear()
        assertNull(cache.get(clip))
        val resumed = async(start = CoroutineStart.UNDISPATCHED) { cache.prepare(setOf(clip)) }
        decoding.complete(Unit)
        assertFalse(old.await())
        assertFalse(queued.await())
        assertTrue(resumed.await())
        assertEquals(2, calls)
        assertContentEquals(shortArrayOf(2), cache.get(clip))
        cache.clear()
        assertNull(cache.get(clip))
        assertTrue(cache.prepare(setOf(clip)))
        assertContentEquals(shortArrayOf(3), cache.get(clip))
    }
}

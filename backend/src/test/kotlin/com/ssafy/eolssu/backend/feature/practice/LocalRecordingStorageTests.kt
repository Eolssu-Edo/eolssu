package com.ssafy.eolssu.backend.feature.practice

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorageException
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.LocalRecordingStorage
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LocalRecordingStorageTests {
    @TempDir lateinit var temp: Path
    private val now = Instant.parse("2026-10-30T00:00:00Z")
    private val key = "recordings/1/01234567-89ab-cdef-0123-456789abcdef.m4a"
    private fun storage(at: Instant = now) = LocalRecordingStorage(temp.resolve("private"), "http://backend:8080/api/recordings/read", "test-secret-at-least-thirty-two-bytes", Clock.fixed(at, ZoneOffset.UTC))
    private fun signed(store: LocalRecordingStorage): Pair<Long, String> {
        val values = URI(store.readUrl(key, Duration.ofMinutes(15))).query.split("&").associate { it.substringBefore("=") to it.substringAfter("=") }
        return values.getValue("expires").toLong() to values.getValue("signature")
    }
    private fun upload(store: LocalRecordingStorage) {
        val source = temp.resolve("sample.m4a")
        Files.write(source, byteArrayOf(1, 2, 3))
        store.put(key, source)
    }
    @Test fun `signed reads reject tampering expiry and deletion`() {
        val store = storage()
        upload(store)
        val (expires, sig) = signed(store)
        assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(assertNotNull(store.readable(key, expires, sig))))
        assertNull(store.readable(key, expires, "bad"))
        assertNull(store.readable(key.replace("/1/", "/2/"), expires, sig))
        assertNull(storage(now.plusSeconds(900)).readable(key, expires, sig))
        store.delete(key)
        store.delete(key)
        assertNull(store.readable(key, expires, sig))
    }
    @Test fun `thirty day boundary denies reads and removes expired originals only`() {
        upload(storage())
        val later = storage(now.plus(Duration.ofDays(30)))
        val (expires, sig) = signed(later)
        assertNull(later.readable(key, expires, sig))
        later.deleteExpired()
        assertFalse(Files.exists(temp.resolve("private").resolve(key)))
        assertTrue(Files.exists(temp.resolve("sample.m4a")))
    }
    @Test fun `traversal overwrite and excessive signing lifetime fail closed`() {
        val store = storage()
        upload(store)
        assertFailsWith<RecordingStorageException> { store.put(key, temp.resolve("sample.m4a")) }
        assertFailsWith<IllegalArgumentException> { store.readUrl("../sample.m4a", Duration.ofMinutes(1)) }
        assertFailsWith<RecordingStorageException> { store.delete("recordings/../sample.m4a") }
        assertTrue(Files.exists(temp.resolve("sample.m4a")))
        assertFailsWith<IllegalArgumentException> { store.readUrl(key, Duration.ofMinutes(16)) }
    }
}

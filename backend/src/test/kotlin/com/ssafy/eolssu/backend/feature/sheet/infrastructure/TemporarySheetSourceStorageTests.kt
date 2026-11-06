package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class TemporarySheetSourceStorageTests {
    @TempDir lateinit var root: Path
    private class Time : Clock() {
        var now=Instant.parse("2026-11-06T00:00:00Z")
        override fun instant()=now
        override fun getZone(): ZoneId=ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock=this
    }
    @Test fun `temporary copy is private and read capability stops working after deletion`() {
        val time=Time()
        TemporarySheetSourceStorage(root.resolve("spool"),"http://backend:8080/api/internal/sheet-imports",time).use { store ->
            val input=root.resolve("input");Files.write(input,byteArrayOf(1,2,3))
            store.put("sheet-import-temp/user/file",input,"image/png")
            val token=store.readUrl("sheet-import-temp/user/file",Duration.ofMinutes(15)).substringAfterLast('/')
            val read=assertNotNull(store.readable(token))
            assertContentEquals(byteArrayOf(1,2,3),Files.readAllBytes(read.path))
            assertEquals("image/png",read.contentType)
            assertNull(store.readable("guessed"))
            store.delete("sheet-import-temp/user/file")
            assertNull(store.readable(token))
            assertFalse(Files.exists(read.path))
            assertTrue(Files.exists(input))
        }
    }
    @Test fun `read tickets expire and source lifetime never exceeds thirty minutes`() {
        val time=Time()
        TemporarySheetSourceStorage(root.resolve("spool"),"http://backend:8080/api/internal/sheet-imports",time).use { store ->
            val input=root.resolve("input");Files.writeString(input,"source")
            store.put("key",input,"application/pdf")
            val token=store.readUrl("key",Duration.ofMinutes(15)).substringAfterLast('/')
            time.now=time.now.plusSeconds(901)
            assertNull(store.readable(token))
            val refreshed=store.readUrl("key",Duration.ofHours(1)).substringAfterLast('/')
            val path=assertNotNull(store.readable(refreshed)).path
            time.now=Instant.parse("2026-11-06T00:30:00Z")
            assertNull(store.readable(refreshed))
            store.purgeExpired()
            assertFalse(Files.exists(path))
        }
    }
    @Test fun `startup reset removes abandoned bytes`() {
        val directory=root.resolve("spool");Files.createDirectories(directory)
        Files.writeString(directory.resolve("abandoned"),"raw")
        TemporarySheetSourceStorage(directory,"http://backend:8080/api/internal/sheet-imports",Time()).use { store ->
            store.reset()
            Files.list(directory).use { assertEquals(0,it.count()) }
        }
    }
    @Test fun `file creation failure is reported as storage unavailable`() {
        val directory=root.resolve("unavailable")
        TemporarySheetSourceStorage(directory,"http://backend:8080/api/internal/sheet-imports",Time()).use { store ->
            val input=root.resolve("input");Files.writeString(input,"raw")
            Files.delete(directory)
            kotlin.test.assertFailsWith<com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorageException> {
                store.put("key",input,"application/pdf")
            }
        }
    }
    @Test fun `inspection copies are removed on release expiry and startup`() {
        val time=Time()
        TemporarySheetSourceStorage(root.resolve("staging"),"http://backend:8080/api/internal/sheet-imports",time).use { store ->
            val released=store.createUpload();Files.writeString(released,"private")
            store.releaseUpload(released);assertFalse(Files.exists(released))
            val expired=store.createUpload();Files.writeString(expired,"private")
            time.now=time.now.plusSeconds(1800);store.purgeExpired();assertFalse(Files.exists(expired))
            val abandoned=store.createUpload();Files.writeString(abandoned,"private")
            store.reset();assertFalse(Files.exists(abandoned))
        }
    }
}

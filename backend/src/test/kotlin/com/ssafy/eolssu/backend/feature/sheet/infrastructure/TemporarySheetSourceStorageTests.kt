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
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
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
    @Test fun `stalled multipart file is removed thirty minutes after it is first seen`() {
        val time=Time();val multipart=root.resolve("multipart")
        TemporarySheetSourceStorage(root.resolve("spool"),"http://backend:8080/api/internal/sheet-imports",time,multipart).use { store ->
            val stalled=multipart.resolve("upload_stalled.tmp");Files.writeString(stalled,"raw")
            store.purgeExpired()
            time.now=time.now.plusSeconds(1799);store.purgeExpired()
            assertTrue(Files.exists(stalled))
            val younger=multipart.resolve("upload_younger.tmp");Files.writeString(younger,"raw")
            store.purgeExpired()
            time.now=time.now.plusSeconds(1);store.purgeExpired()
            assertFalse(Files.exists(stalled));assertTrue(Files.exists(younger))
        }
    }
    @Test fun `abandoned multipart bytes go before the server starts and reset leaves requests in flight alone`() {
        val multipart=Files.createDirectories(root.resolve("multipart"))
        val abandoned=multipart.resolve("upload_abandoned.tmp");Files.writeString(abandoned,"raw")
        TemporarySheetSourceStorage(root.resolve("spool"),"http://backend:8080/api/internal/sheet-imports",Time(),multipart).use { store ->
            assertFalse(Files.exists(abandoned))
            val inFlight=multipart.resolve("upload_in_flight.tmp");Files.writeString(inFlight,"raw")
            store.reset()
            assertTrue(Files.exists(inFlight))
        }
    }
    @Test fun `missing multipart spool is logged and does not stop source cleanup`(output: CapturedOutput) {
        val time=Time();val multipart=root.resolve("multipart")
        TemporarySheetSourceStorage(root.resolve("spool"),"http://backend:8080/api/internal/sheet-imports",time,multipart).use { store ->
            val input=root.resolve("input");Files.writeString(input,"raw")
            store.put("key",input,"image/png")
            val path=assertNotNull(store.readable(store.readUrl("key",Duration.ofMinutes(15)).substringAfterLast('/'))).path
            Files.delete(multipart)
            time.now=time.now.plusSeconds(1800);store.purgeExpired()
            assertFalse(Files.exists(path))
            assertTrue(output.out.contains("Temporary multipart cleanup will be retried"))
        }
    }
    @Test fun `queued copy keeps the deadline of its request upload`() {
        val time=Time()
        TemporarySheetSourceStorage(root.resolve("spool"),"http://backend:8080/api/internal/sheet-imports",time).use { store ->
            val upload=store.createUpload();Files.writeString(upload,"raw")
            time.now=time.now.plusSeconds(1200)
            store.put("key",upload,"image/png")
            val path=assertNotNull(store.readable(store.readUrl("key",Duration.ofMinutes(15)).substringAfterLast('/'))).path
            store.releaseUpload(upload)
            time.now=time.now.plusSeconds(600);store.purgeExpired()
            assertFalse(Files.exists(path))
        }
    }
}

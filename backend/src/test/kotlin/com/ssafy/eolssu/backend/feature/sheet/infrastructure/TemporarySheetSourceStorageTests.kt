package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorageException
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
    private val WARNING="Temporary multipart cleanup failed and will be retried"
    private val SOURCE_WARNING="Temporary source cleanup failed and will be retried"
    @TempDir lateinit var root: Path
    private class Time : Clock() {
        var now=Instant.parse("2026-11-06T00:00:00Z")
        override fun instant()=now
        override fun getZone(): ZoneId=ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock=this
    }
    @Test fun `peer can delete source bytes without inheriting its read tickets`() {
        val shared = root.resolve("shared")
        val key = "sheet-import-temp/backend/7/request/0.png"
        val input = Files.writeString(root.resolve("input"), "source")
        TemporarySheetSourceStorage(shared.resolve("backend"), "http://backend:8080", Time(), sharedDirectory = shared).use { blue ->
            blue.put(key, input, "image/png")
            val token = blue.readUrl(key, Duration.ofMinutes(1)).substringAfterLast('/')
            TemporarySheetSourceStorage(shared.resolve("backend-green"), "http://backend-green:8080", Time(), sharedDirectory = shared).use { green ->
                assertNull(green.readable(token))
                assertNotNull(blue.readable(token))
                green.delete(key)
                assertNull(blue.readable(token))
            }
        }
    }

    @Test fun `running slot sweeps expired sources left by its peer`() {
        val clock = Time()
        val shared = root.resolve("shared")
        val orphan = Files.createDirectories(shared.resolve("backend")).resolve("source-abandoned.tmp")
        Files.writeString(orphan, "source")
        Files.setLastModifiedTime(orphan, java.nio.file.attribute.FileTime.from(clock.now))
        TemporarySheetSourceStorage(shared.resolve("backend-green"), "http://backend-green:8080", clock, sharedDirectory = shared).use { green ->
            clock.now = clock.now.plusSeconds(1799)
            green.purgeExpired()
            assertTrue(Files.exists(orphan))
            clock.now = clock.now.plusSeconds(1)
            green.purgeExpired()
            assertFalse(Files.exists(orphan))
        }
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
    @Test fun `new storage removes abandoned bytes before the server starts`() {
        val directory=root.resolve("spool");Files.createDirectories(directory)
        Files.writeString(directory.resolve("abandoned"),"raw")
        TemporarySheetSourceStorage(directory,"http://backend:8080/api/internal/sheet-imports",Time()).use {
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
    @Test fun `inspection copies are removed on release and expiry`() {
        val time=Time()
        TemporarySheetSourceStorage(root.resolve("staging"),"http://backend:8080/api/internal/sheet-imports",time).use { store ->
            val released=store.createUpload();Files.writeString(released,"private")
            store.releaseUpload(released);assertFalse(Files.exists(released))
            val expired=store.createUpload();Files.writeString(expired,"private")
            time.now=time.now.plusSeconds(1800);store.purgeExpired();assertFalse(Files.exists(expired))
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
    @Test fun `abandoned multipart bytes go before the server starts`() {
        val multipart=Files.createDirectories(root.resolve("multipart"))
        val abandoned=multipart.resolve("upload_abandoned.tmp");Files.writeString(abandoned,"raw")
        TemporarySheetSourceStorage(root.resolve("spool"),"http://backend:8080/api/internal/sheet-imports",Time(),multipart).use {
            assertFalse(Files.exists(abandoned))
        }
    }
    @Test fun `deleted multipart spool directory is recreated`() {
        val multipart=root.resolve("multipart")
        TemporarySheetSourceStorage(root.resolve("spool"),"http://backend:8080/api/internal/sheet-imports",Time(),multipart).use { store ->
            Files.delete(multipart)
            store.purgeExpired()
            assertTrue(Files.isDirectory(multipart))
        }
    }
    @Test fun `expiry stays known after the sweep and a lost file is not expiry`() {
        val time=Time()
        TemporarySheetSourceStorage(root.resolve("spool"),"http://backend:8080/api/internal/sheet-imports",time).use { store ->
            val input=root.resolve("input");Files.writeString(input,"raw")
            store.put("expiring",input,"image/png")
            store.put("lost",input,"image/png")
            val path=assertNotNull(store.readable(store.readUrl("expiring",Duration.ofMinutes(15)).substringAfterLast('/'))).path
            Files.delete(assertNotNull(store.readable(store.readUrl("lost",Duration.ofMinutes(15)).substringAfterLast('/'))).path)
            assertFalse(store.expired("expiring"));assertFalse(store.expired("lost"))
            time.now=time.now.plusSeconds(1800);store.purgeExpired()
            assertFalse(Files.exists(path))
            assertTrue(store.expired("expiring"))
            assertFailsWith<SheetSourceStorageException> { store.readUrl("expiring",Duration.ofMinutes(15)) }
            store.delete("expiring");assertFalse(store.expired("expiring"))
        }
    }
    @Test fun `undeletable copies are logged and a lasting source failure does not hide a multipart one`(output: CapturedOutput) {
        val time=Time();val multipart=root.resolve("multipart")
        TemporarySheetSourceStorage(root.resolve("spool"),"http://backend:8080/api/internal/sheet-imports",time,multipart).use { store ->
            val upload=store.createUpload()
            Files.delete(upload);Files.createDirectories(upload.resolve("child"))
            store.releaseUpload(upload)
            assertEquals(1,output.out.split(SOURCE_WARNING).size-1)
            Files.delete(multipart);Files.writeString(multipart,"not a directory")
            store.purgeExpired()
            assertEquals(1,output.out.split(SOURCE_WARNING).size-1)
            assertEquals(1,output.out.split(WARNING).size-1)
            Files.delete(upload.resolve("child"))
        }
    }
    @Test fun `blocked multipart spool warns once a minute and does not stop source cleanup`(output: CapturedOutput) {
        val time=Time();val multipart=root.resolve("multipart")
        TemporarySheetSourceStorage(root.resolve("spool"),"http://backend:8080/api/internal/sheet-imports",time,multipart).use { store ->
            val input=root.resolve("input");Files.writeString(input,"raw")
            store.put("key",input,"image/png")
            val path=assertNotNull(store.readable(store.readUrl("key",Duration.ofMinutes(15)).substringAfterLast('/'))).path
            Files.delete(multipart);Files.writeString(multipart,"not a directory")
            time.now=time.now.plusSeconds(1800);store.purgeExpired();store.purgeExpired()
            assertFalse(Files.exists(path))
            assertEquals(1,output.out.split(WARNING).size-1)
            time.now=time.now.plusSeconds(60);store.purgeExpired()
            assertEquals(2,output.out.split(WARNING).size-1)
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

package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.ImportJangdan
import com.ssafy.eolssu.backend.feature.sheet.domain.PdfPages
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetConversion
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportRecord
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportReplay
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportRepository
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportSave
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportStatus
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetNotation
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorageException
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetUpload
import com.ssafy.eolssu.backend.feature.sheet.sheetImageBytes
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SheetImportsTests {
    @TempDir lateinit var dir: Path
    private val png = sheetImageBytes()

    private class FakeStorage : SheetSourceStorage {
        override var available = true
        val objects = mutableSetOf<String>()
        var failPutAt = -1
        var puts = 0
        var failAfterStore = false
        override fun put(key: String, file: Path, contentType: String) {
            if (puts++ == failPutAt) throw SheetSourceStorageException("down")
            objects += key
            if (failAfterStore) throw SheetSourceStorageException("response lost after remote commit")
        }
        override fun delete(key: String) {
            objects -= key
        }
        override fun readUrl(key: String, ttl: Duration) = error("unused")
    }

    private class FakeRepository : SheetImportRepository {
        val saved = mutableListOf<SheetImportRecord>()
        var saveResult: SheetImportSave? = null
        var blocker: SheetImportSave? = null
        override fun findReplay(userId: Long, idempotencyKey: String) =
            saved.firstOrNull { it.userId == userId && it.idempotencyKey == idempotencyKey }?.let { SheetImportReplay(saved.indexOf(it) + 1L, it.requestHash) }
        override fun blocker(userId: Long) = blocker
        override fun instrumentIsActive(instrumentId: Long) = instrumentId == 1L
        override fun jangdan(jangdanId: Long) = ImportJangdan("JANGDAN_BEAT", 72).takeIf { jangdanId == 3L }
        override fun save(record: SheetImportRecord): SheetImportSave {
            saveResult?.let { return it }
            saved += record
            return SheetImportSave.Created(saved.size.toLong())
        }
        override fun findConversion(sheetId: Long, userId: Long) = saved.getOrNull(sheetId.toInt() - 1)?.let {
            SheetConversion(
                sheetId, it.notation, SheetImportStatus.QUEUED, null, it.pageCount, 0, it.jangdanId, it.requestedBpm, null, 1,
                it.acceptedAt, null, null, 0,
            )
        }
        override fun sourceKeys(sheetId: Long, userId: Long) = saved.getOrNull(sheetId.toInt() - 1)?.files?.map { it.objectKey }
    }

    private val storage = FakeStorage()
    private val repository = FakeRepository()
    private val imports = SheetImports(SheetImportFiles { PdfPages.Count(1) }, repository, storage, Clock.fixed(Instant.parse("2026-11-04T06:00:00Z"), ZoneOffset.UTC))

    private fun uploads(count: Int = 2, seed: Byte = 1) = List(count) { SheetUpload(Files.write(dir.resolve("u$seed-$it"), png + seed), (png.size + 1).toLong(), "../쪽$it.png") }
    private fun metadata(notation: SheetNotation = SheetNotation.JEONGGANBO, jangdanId: Long? = 3, bpm: Int? = null, hwang: String? = null, title: String = " 아리랑 ") =
        SheetImportMetadata(title, notation, 1, jangdanId, bpm, hwang)
    private fun accept(metadata: SheetImportMetadata = metadata(), uploads: List<SheetUpload> = uploads(), key: String = "k1") =
        imports.accept(SheetImportCommand(7, key, uploads, metadata))

    @Test
    fun `accepting stores every file then one record with the jangdan defaults`() {
        val result = accept() as SheetImportResult.Accepted
        assertEquals(SheetImportStatus.QUEUED, result.conversion.status)
        assertEquals(2, result.conversion.pageCount)
        val record = repository.saved.single()
        assertEquals("아리랑", record.title)
        assertEquals(72, record.sheetBpm)
        assertEquals("JANGDAN_BEAT", record.sheetBpmUnit)
        assertEquals(null, record.requestedBpm)
        assertEquals(listOf("쪽0.png", "쪽1.png"), record.files.map { it.fileName })
        assertEquals(record.files.map { it.objectKey }.toSet(), storage.objects)
        assertTrue(record.files.all { it.objectKey.startsWith("sheet-import-temp/7/") && it.objectKey.endsWith(".png") })
    }

    @Test
    fun `the same key replays only the same content and uploads nothing new`() {
        val files = uploads()
        accept(uploads = files)
        val objects = storage.objects.toSet()

        assertTrue(accept(uploads = files) is SheetImportResult.Accepted)
        assertEquals(1, repository.saved.size)
        assertEquals(objects, storage.objects)
        assertEquals(SheetImportFailure.IDEMPOTENCY_CONFLICT, accept(uploads = uploads(seed = 2)).failure())
        assertEquals(SheetImportFailure.IDEMPOTENCY_CONFLICT, accept(metadata(bpm = 90), uploads = files).failure())
    }

    @Test
    fun `rejections leave no stored object`() {
        repository.blocker = SheetImportSave.LimitReached
        assertEquals(SheetImportFailure.LIMIT, accept().failure())
        repository.blocker = null

        repository.saveResult = SheetImportSave.InProgress
        assertEquals(SheetImportFailure.IN_PROGRESS, accept().failure())
        repository.saveResult = null

        storage.failPutAt = storage.puts + 1
        assertEquals(SheetImportFailure.UNAVAILABLE, accept().failure())
        storage.available = false
        assertEquals(SheetImportFailure.UNAVAILABLE, accept().failure())
        assertEquals(emptySet(), storage.objects)
        assertEquals(0, repository.saved.size)
    }

    @Test
    fun `a failed PUT is cleaned even when the remote object was committed`() {
        storage.failAfterStore = true
        assertEquals(SheetImportFailure.UNAVAILABLE, accept().failure())
        assertEquals(emptySet(), storage.objects)
        assertEquals(0, repository.saved.size)
    }

    @Test
    fun `metadata outside SHT-07 is invalid and unknown references are not found`() {
        assertEquals(SheetImportFailure.INVALID, accept(metadata(jangdanId = null)).failure())
        assertEquals(SheetImportFailure.INVALID, accept(metadata(hwang = "Eb4")).failure())
        assertEquals(SheetImportFailure.INVALID, accept(metadata(SheetNotation.STAFF, hwang = "H4")).failure())
        assertEquals(SheetImportFailure.INVALID, accept(metadata(bpm = 0)).failure())
        assertEquals(SheetImportFailure.INVALID, accept(metadata(title = "   ")).failure())
        assertEquals(SheetImportFailure.INVALID, accept(metadata(title = "가".repeat(51))).failure())
        assertEquals(SheetImportFailure.NOT_FOUND, accept(metadata(jangdanId = 99)).failure())
        assertEquals(SheetImportFailure.NOT_FOUND, accept(metadata().copy(instrumentId = 2)).failure())
        assertEquals(0, repository.saved.size)

        val staff = accept(metadata(SheetNotation.STAFF, jangdanId = null, bpm = 480, hwang = "F#3")) as SheetImportResult.Accepted
        assertEquals(SheetNotation.STAFF, staff.conversion.notation)
        assertEquals(480, repository.saved.single().sheetBpm)
        assertEquals("QUARTER", repository.saved.single().sheetBpmUnit)
    }

    @Test
    fun `deleting sources removes every object of the owner's sheet`() {
        accept()
        assertTrue(imports.deleteSources(7, 1))
        assertEquals(emptySet(), storage.objects)
        assertEquals(false, imports.deleteSources(7, 99))
    }

    private fun SheetImportResult.failure() = (this as SheetImportResult.Rejected).failure
}

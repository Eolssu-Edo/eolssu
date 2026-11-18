package com.ssafy.eolssu.backend.feature.sheet.domain

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

// 내 악보 불러오기(SHT-07·10, S15P31E102-471). 계약: docs/specs/user-sheets.md, Notion SHT-07·10.

/** 파일·합계 20MiB, PDF 10쪽, 이미지 10장(SHT-07 규칙 6). */
const val SHEET_IMPORT_MAX_BYTES = 20L * 1024 * 1024
const val SHEET_IMPORT_MAX_FILES = 10
const val SHEET_IMPORT_MAX_PAGES = 10

/** 악보 전용 버킷 안에서 원본을 두는 앞부분. 탈퇴 일괄 삭제가 이 앞부분으로 저장소를 고른다. */
const val SHEET_SOURCE_PREFIX = "sheet-sources/"
const val TEMP_SHEET_SOURCE_PREFIX = "sheet-import-temp/"

/** 회원당 내 악보 수(SHT-07 규칙 11). 변환 중·실패한 악보도 센다. */
const val SHEET_LIMIT_PER_MEMBER = 10

/** 확장자가 아니라 파일 앞부분(매직 바이트)으로 정한다. */
enum class SheetFileType(val contentType: String, val extension: String) {
    PDF("application/pdf", "pdf"),
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg");

    companion object {
        private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

        fun detect(file: Path): SheetFileType? {
            val head = Files.newInputStream(file).use { it.readNBytes(PNG_MAGIC.size) }
            fun starts(magic: ByteArray) = head.size >= magic.size && magic.indices.all { head[it] == magic[it] }
            return when {
                starts(PDF_MAGIC) -> PDF
                starts(PNG_MAGIC) -> PNG
                starts(JPEG_MAGIC) -> JPEG
                else -> null
            }
        }
    }
}

/** 요청으로 받은 파일 하나. [path]는 요청이 끝나면 지우는 임시 파일이다. */
data class SheetUpload(val path: Path, val size: Long, val originalName: String)

/** 검사를 통과한 파일. [type]은 내용으로 정한 형식이다. */
data class InspectedSheetFile(val upload: SheetUpload, val type: SheetFileType)

sealed interface PdfPages {
    data class Count(val pages: Int) : PdfPages
    /** 암호가 걸렸거나 깨져서 쪽을 셀 수 없다. */
    data object Unreadable : PdfPages
}

fun interface PdfPageCounter {
    fun count(file: Path): PdfPages
}

enum class SheetImportStatus { QUEUED, RUNNING, DONE, FAILED }

enum class SheetNotation(val source: String) { JEONGGANBO("JEONGGANBO_OMR"), STAFF("STAFF_OMR") }

/** 변환 한 건(sheet_conversions). 악보 하나에 하나다. */
data class SheetConversion(
    val sheetId: Long,
    val notation: SheetNotation,
    val status: SheetImportStatus,
    val stage: String?,
    val pageCount: Int,
    val processedPageCount: Int,
    /** 요청에서 장단·속도를 생략했으면 null(서버가 정한다). */
    val requestedJangdanId: Long?,
    val requestedBpm: Int?,
    val failureCode: String?,
    val attempt: Int,
    val acceptedAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    /** 변환이 끝나 악보 행에 저장한 음 수. 끝나기 전에는 의미가 없다. */
    val noteCount: Int,
    val failureCause: String? = null,
    val timeoutCount: Int = 0,
    val pendingCellCount: Int = 0,
)

/** 원본 파일 비공개 저장소(eolssu.sheet-source-storage). 실패하면 [SheetSourceStorageException]. */
interface SheetSourceStorage {
    /** 설정이 없으면 false. 업로드를 받기 전에 확인해 503으로 답한다. */
    val available: Boolean

    fun put(key: String, file: Path, contentType: String)

    fun delete(key: String)

    fun readUrl(key: String, ttl: Duration): String

    /** 임시 원본의 기한이 지났으면 true다. 파일 소실이나 그 밖의 저장소 실패는 만료가 아니다. */
    fun expired(key: String): Boolean = false

    fun downloadUrl(key: String, ttl: Duration, fileName: String): String = readUrl(key,ttl)

    /** Returns a signed download bounded by the artifact deadline, or null if it is too late to sign. */
    fun downloadUrlBefore(key: String, ttl: Duration, fileName: String, deadline: Instant): String? =
        downloadUrl(key,ttl,fileName)

    /** 작업자가 보관된 원본을 읽는다. URL은 이 저장소가 서명한 값만 사용한다. */
    fun download(key: String, target: Path) = download(key,target,Duration.ofSeconds(40))

    fun download(key: String,target: Path,timeout: Duration) {
        try {
            val request=java.net.http.HttpRequest.newBuilder(java.net.URI(readUrl(key,Duration.ofMinutes(15))))
                .timeout(timeout).GET().build()
            val future=java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                .sendAsync(request,java.net.http.HttpResponse.BodyHandlers.ofByteArray())
            val response=try { future.get(timeout.toMillis(),java.util.concurrent.TimeUnit.MILLISECONDS) }
                finally { if(!future.isDone) future.cancel(true) }
            if(response.statusCode()!=200) throw SheetSourceStorageException("Source download failed")
            if(response.body().size>SHEET_IMPORT_MAX_BYTES) throw SheetSourceStorageException("Source exceeds upload limit")
            Files.write(target,response.body())
        } catch(e: SheetSourceStorageException) { throw e
        } catch(e: InterruptedException) { Thread.currentThread().interrupt(); throw SheetSourceStorageException("Source download interrupted",e)
        } catch(e: Exception) { throw SheetSourceStorageException("Source download failed",e) }
    }
}

class SheetSourceStorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** 접수할 악보 하나. 원본 객체는 이미 저장소에 올린 뒤다. */
data class SheetImportRecord(
    val userId: Long,
    val idempotencyKey: String,
    val requestHash: String,
    val title: String,
    val notation: SheetNotation,
    val instrumentId: Long,
    val jangdanId: Long?,
    /** 악보 행에 둘 속도·단위. 요청 bpm이 없으면 장단 기본값이고, 변환(472)이 장단을 정하면 바꾼다. */
    val sheetBpm: Int,
    val sheetBpmUnit: String,
    val requestedBpm: Int?,
    val hwangPitch: String?,
    val pageCount: Int,
    val files: List<StoredSheetFile>,
    val acceptedAt: Instant,
)

data class StoredSheetFile(val objectKey: String, val type: SheetFileType, val byteSize: Long, val fileName: String)

/** 악보 행의 속도 단위와 bpm 생략 시 기본 속도. */
data class ImportJangdan(val bpmUnit: String, val defaultBpm: Int)

/** 같은 회원·키로 먼저 접수한 악보. */
data class SheetImportReplay(val sheetId: Long, val requestHash: String)

sealed interface SheetImportSave {
    data class Created(val sheetId: Long) : SheetImportSave
    data class Replayed(val replay: SheetImportReplay) : SheetImportSave
    data object LimitReached : SheetImportSave
    data object InProgress : SheetImportSave
}

interface SheetImportRepository {
    fun findReplay(userId: Long, idempotencyKey: String): SheetImportReplay?

    /** 접수를 막을 상태(악보 수 상한·진행 중 변환). 둘 다 아니면 null. 저장 전에 미리 보는 값이라 [save]가 다시 확인한다. */
    fun blocker(userId: Long): SheetImportSave?

    fun instrumentIsActive(instrumentId: Long): Boolean

    fun jangdan(jangdanId: Long): ImportJangdan?

    /** 회원 행을 잠근 한 트랜잭션에서 키 재전송·상한·진행 중 변환을 다시 보고 악보·변환·원본 행을 만든다. */
    fun save(record: SheetImportRecord): SheetImportSave

    /** 본인 악보의 변환. 불러오기로 만들지 않은 악보·다른 회원의 악보는 null. */
    fun findConversion(sheetId: Long, userId: Long): SheetConversion?

    /** 본인 악보의 원본 객체 키. 악보가 없으면 null, 불러오기 악보가 아니면 []. */
    fun sourceKeys(sheetId: Long, userId: Long): List<String>?
    fun stopConversion(sheetId: Long,userId: Long) {}
}

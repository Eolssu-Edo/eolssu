package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.SHEET_SOURCE_PREFIX
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetConversion
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportRecord
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportRepository
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetImportSave
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetNotation
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorageException
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetUpload
import com.ssafy.eolssu.backend.feature.sheet.domain.StoredSheetFile
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.UUID
import org.slf4j.LoggerFactory

/** SHT-07 metadata 파트. 형식은 컨트롤러가 JSON 타입까지 맞춰 넘긴다. 값 범위는 여기서 본다. */
data class SheetImportMetadata(
    val title: String,
    val notation: SheetNotation,
    val instrumentId: Long,
    val jangdanId: Long?,
    val bpm: Int?,
    val hwangPitch: String?,
)

data class SheetImportCommand(
    val userId: Long,
    val idempotencyKey: String,
    val uploads: List<SheetUpload>,
    val metadata: SheetImportMetadata,
)

/** SHT-07 오류 코드에 하나씩 대응한다. */
enum class SheetImportFailure {
    INVALID, TOO_LARGE, UNSUPPORTED_TYPE, TOO_MANY_PAGES, UNREADABLE, NOT_FOUND, IN_PROGRESS, LIMIT, IDEMPOTENCY_CONFLICT, UNAVAILABLE
}

sealed interface SheetImportResult {
    /** 새로 접수했거나 같은 키·같은 내용의 재전송이다. 둘 다 202. */
    data class Accepted(val conversion: SheetConversion) : SheetImportResult
    data class Rejected(val failure: SheetImportFailure) : SheetImportResult
}

/** SHT-02가 행을 지우기 전에 원본 객체를 지운다. 악보가 없으면 false, 저장소 실패는 [SheetSourceStorageException]. */
fun interface SheetSourceCleaner {
    fun deleteSources(userId: Long, sheetId: Long): Boolean
}

/**
 * SHT-07 접수·SHT-10 조회·SHT-02 원본 삭제(S15P31E102-471). 변환 자체는 작업자(S15P31E102-472)가 한다.
 * 원본은 DB에 행을 쓰기 전에 임시 저장소에 복사한다. 행을 쓰지 못하면 임시 파일을 지운다.
 */
class SheetImports(
    private val files: SheetImportFiles,
    private val repository: SheetImportRepository,
    private val storage: SheetSourceStorage,
    private val clock: Clock,
    private val workerAvailable: ()->Boolean = { true },
    private val legacyStorage: SheetSourceStorage = storage,
) : SheetSourceCleaner {
    fun accept(command: SheetImportCommand): SheetImportResult {
        val inspected = when (val result = files.inspect(command.uploads)) {
            is SheetImportFilesResult.Rejected -> return reject(result.reason.failure())
            is SheetImportFilesResult.Accepted -> result
        }
        val metadata = command.metadata.normalized() ?: return reject(SheetImportFailure.INVALID)
        val requestHash = requestHash(metadata, inspected)
        repository.findReplay(command.userId, command.idempotencyKey)?.let { return replay(command.userId, it.sheetId, it.requestHash, requestHash) }
        if (!storage.available || !workerAvailable()) return reject(SheetImportFailure.UNAVAILABLE)
        if (!repository.instrumentIsActive(metadata.instrumentId)) return reject(SheetImportFailure.NOT_FOUND)
        val jangdan = metadata.jangdanId?.let { repository.jangdan(it) ?: return reject(SheetImportFailure.NOT_FOUND) }
        repository.blocker(command.userId)?.let { return reject(it.failure()) }

        val folder = "${com.ssafy.eolssu.backend.feature.sheet.domain.TEMP_SHEET_SOURCE_PREFIX}${command.userId}/${UUID.randomUUID()}"
        val stored = mutableListOf<StoredSheetFile>()
        try {
            inspected.files.forEachIndexed { index, file ->
                val key = "$folder/$index.${file.type.extension}"
                // 응답이 실패해도 원격 PUT은 반영됐을 수 있다. 시도한 키부터 정리 대상에 넣는다.
                stored += StoredSheetFile(key, file.type, file.upload.size, fileName(file.upload.originalName, index, file.type.extension))
                storage.put(key, file.upload.path, file.type.contentType)
            }
        } catch (e: SheetSourceStorageException) {
            log.warn("Sheet import upload failed ({})", e.cause?.javaClass?.simpleName ?: e.javaClass.simpleName)
            discard(stored)
            return reject(SheetImportFailure.UNAVAILABLE)
        }

        val record = SheetImportRecord(
            userId = command.userId,
            idempotencyKey = command.idempotencyKey,
            requestHash = requestHash,
            title = metadata.title,
            notation = metadata.notation,
            instrumentId = metadata.instrumentId,
            jangdanId = metadata.jangdanId,
            // 장단을 생략한 오선보는 인식 서비스가 장단을 정한 뒤(472) 속도·단위를 바꾼다. 그때까지의 자리값이다.
            sheetBpm = metadata.bpm ?: jangdan?.defaultBpm ?: PLACEHOLDER_BPM,
            sheetBpmUnit = jangdan?.bpmUnit ?: PLACEHOLDER_BPM_UNIT,
            requestedBpm = metadata.bpm,
            hwangPitch = metadata.hwangPitch,
            pageCount = inspected.pageCount,
            files = stored,
            acceptedAt = clock.instant().truncatedTo(ChronoUnit.MICROS),
        )
        val saved = try {
            repository.save(record)
        } catch (e: RuntimeException) {
            discard(stored)
            throw e
        }
        return when (saved) {
            is SheetImportSave.Created -> SheetImportResult.Accepted(checkNotNull(repository.findConversion(saved.sheetId, command.userId)))
            is SheetImportSave.Replayed -> {
                discard(stored)
                replay(command.userId, saved.replay.sheetId, saved.replay.requestHash, requestHash)
            }
            else -> {
                discard(stored)
                reject(saved.failure())
            }
        }
    }

    /** SHT-10. 불러오기로 만들지 않은 악보·다른 회원의 악보는 null(404). */
    fun conversion(userId: Long, sheetId: Long): SheetConversion? = repository.findConversion(sheetId, userId)

    /**
     * SHT-02 앞단. 악보의 원본 객체를 먼저 지운다. 악보가 없으면 false.
     * 저장소가 실패하면 [SheetSourceStorageException]을 그대로 던져 DB 행을 남긴다(다시 지우면 이어서 지운다).
     */
    override fun deleteSources(userId: Long, sheetId: Long): Boolean {
        repository.sourceKeys(sheetId, userId) ?: return false
        repository.stopConversion(sheetId,userId)
        val keys = repository.sourceKeys(sheetId, userId) ?: return false
        keys.forEach { key ->
            if(key.startsWith(com.ssafy.eolssu.backend.feature.sheet.domain.TEMP_SHEET_SOURCE_PREFIX)) storage.delete(key) else legacyStorage.delete(key)
        }
        return true
    }

    private fun replay(userId: Long, sheetId: Long, storedHash: String, requestHash: String): SheetImportResult {
        if (storedHash != requestHash) return reject(SheetImportFailure.IDEMPOTENCY_CONFLICT)
        // 같은 키가 불러오기 아닌 악보(예전 SHT-03)를 가리키면 내용이 다른 것과 같다.
        val conversion = repository.findConversion(sheetId, userId) ?: return reject(SheetImportFailure.IDEMPOTENCY_CONFLICT)
        return SheetImportResult.Accepted(conversion)
    }

    private fun discard(stored: List<StoredSheetFile>) {
        for (file in stored) {
            try {
                storage.delete(file.objectKey)
            } catch (e: SheetSourceStorageException) {
                // 임시 저장소는 실패한 삭제를 표시해 다음 정리 주기에 다시 지운다.
                log.warn("Sheet import cleanup failed ({})", e.cause?.javaClass?.simpleName ?: e.javaClass.simpleName)
            }
        }
    }

    private fun reject(failure: SheetImportFailure) = SheetImportResult.Rejected(failure)

    private fun SheetImportRejection.failure() = when (this) {
        SheetImportRejection.INVALID -> SheetImportFailure.INVALID
        SheetImportRejection.TOO_LARGE -> SheetImportFailure.TOO_LARGE
        SheetImportRejection.UNSUPPORTED_TYPE -> SheetImportFailure.UNSUPPORTED_TYPE
        SheetImportRejection.TOO_MANY_PAGES -> SheetImportFailure.TOO_MANY_PAGES
        SheetImportRejection.UNREADABLE -> SheetImportFailure.UNREADABLE
    }

    private fun SheetImportSave.failure() = when (this) {
        SheetImportSave.LimitReached -> SheetImportFailure.LIMIT
        SheetImportSave.InProgress -> SheetImportFailure.IN_PROGRESS
        else -> error("Not a blocker: $this")
    }

    /** 범위를 벗어나면 null(400). 제목은 앞뒤 공백을 지운 1~50자다. */
    private fun SheetImportMetadata.normalized(): SheetImportMetadata? {
        val trimmed = title.trim()
        if (trimmed.codePointCount(0, trimmed.length) !in 1..MAX_TITLE_LENGTH) return null
        if (bpm != null && bpm !in MIN_BPM..MAX_BPM) return null
        if (hwangPitch != null && !HWANG_PITCH.matches(hwangPitch)) return null
        val valid = when (notation) {
            SheetNotation.JEONGGANBO -> jangdanId != null && hwangPitch == null
            SheetNotation.STAFF -> true
        }
        return if (valid) copy(title = trimmed) else null
    }

    /** 같은 키 재전송 판정용. 파일 이름은 넣지 않는다(내용이 같으면 같은 요청). */
    private fun requestHash(metadata: SheetImportMetadata, inspected: SheetImportFilesResult.Accepted): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun field(value: Any?) {
            val text = value?.toString() ?: "\u0000"
            digest.update("${text.length}:$text;".toByteArray())
        }
        listOf(metadata.title, metadata.notation, metadata.instrumentId, metadata.jangdanId, metadata.bpm, metadata.hwangPitch).forEach(::field)
        for (file in inspected.files) {
            field(file.type)
            field(file.upload.size)
            field(sha256(file.upload))
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private fun sha256(upload: SheetUpload): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(upload.path).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    /** 경로·제어 문자를 빼고 255자로 자른다. 남는 게 없으면 순서로 이름을 만든다. */
    private fun fileName(original: String, index: Int, extension: String): String {
        val base = original.substringAfterLast('/').substringAfterLast('\\').filterNot { it.isISOControl() }.trim()
        if (base.isEmpty()) return "file${index + 1}.$extension"
        val end = if (base.codePointCount(0, base.length) > MAX_FILE_NAME) base.offsetByCodePoints(0, MAX_FILE_NAME) else base.length
        return base.substring(0, end)
    }

    companion object {
        const val MAX_TITLE_LENGTH = 50
        const val MIN_BPM = 30
        const val MAX_BPM = 160
        private const val MAX_FILE_NAME = 255
        private const val PLACEHOLDER_BPM = 60
        private const val PLACEHOLDER_BPM_UNIT = "QUARTER"
        /** 과학적 음 이름: 음이름 A~G, 선택 # 또는 b, 옥타브 0~8(예 Eb4). */
        private val HWANG_PITCH = Regex("[A-G][#b]?[0-8]")
        private val log = LoggerFactory.getLogger(SheetImports::class.java)
    }
}

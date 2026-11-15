package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.SheetJangdan
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.HexFormat

data class PublishedSheetSource(
    val title: String,
    val jangdanId: Long?,
    val bpm: Int,
    val bpmUnit: String,
    val scoreVersion: Int,
    val scoreJson: String,
    val noteCount: Int,
)

data class SheetCopyRequest(val memberId: Long, val key: String, val requestHash: String)

data class PublishedSheetCopy(
    val request: SheetCopyRequest,
    val title: String,
    val jangdanId: Long?,
    val bpm: Int,
    val score: PreparedScore.Ready,
    val createdAt: Instant,
)

enum class SheetCopyFailure { NotFound, InvalidScore, IdempotencyConflict, Limit, ConversionInProgress }

sealed interface SheetCopyResult {
    data class Created(val sheetId: Long) : SheetCopyResult
    data class Rejected(val failure: SheetCopyFailure) : SheetCopyResult
}

interface PublishedSheetCopyRepository {
    fun replay(request: SheetCopyRequest): SheetCopyResult?
    fun findSource(songId: Long): PublishedSheetSource?
    fun save(copy: PublishedSheetCopy): SheetCopyResult
}

/**
 * 원곡 연습 반주가 쓰는 장단. 제공 곡 대부분은 곡 행의 장단 연결이 비어 있고
 * 반주는 악보·검수 목록으로만 정해지므로, 사본이 같은 장단을 이어받을 때 쓴다(S15P31E102-894).
 */
fun interface CopyJangdanSource {
    fun find(songId: Long): SheetJangdan?
}

/** 게시 곡 스냅샷을 복사한 회원의 첫 번째 저장 버전으로 등록한다. */
class CopyPublishedSheet(
    private val repository: PublishedSheetCopyRepository,
    private val scores: SheetScores,
    private val clock: Clock,
    private val accompaniment: CopyJangdanSource = CopyJangdanSource { null },
    private val jangdanValidation: SheetJangdanValidation? = null,
) {
    fun copy(memberId: Long, songId: Long, key: String): SheetCopyResult {
        // 가져오기나 과거 생성 경로의 해시와 서로 다른 요청으로 판정한다.
        val hash = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest("provided-song-copy:v1:$songId".toByteArray()),
        )
        val request = SheetCopyRequest(memberId, key, hash)
        repository.replay(request)?.let { return it }
        val song = repository.findSource(songId) ?: return SheetCopyResult.Rejected(SheetCopyFailure.NotFound)
        if (song.bpm !in EditUserSheet.BPM_RANGE ||
            scores.timeline(song.scoreJson, song.scoreVersion, song.noteCount, song.bpm, song.bpmUnit) == null
        ) {
            return SheetCopyResult.Rejected(SheetCopyFailure.InvalidScore)
        }
        val score = (scores.prepare(song.scoreJson, 1) as? PreparedScore.Ready)?.takeIf { it.columnCount > 0 }
            ?: return SheetCopyResult.Rejected(SheetCopyFailure.InvalidScore)
        val suffix = " (사본)"
        val originalTitle = song.title.trim()
        val titleLength = minOf(originalTitle.codePointCount(0, originalTitle.length), 50 - suffix.length)
        val title = originalTitle.substring(0, originalTitle.offsetByCodePoints(0, titleLength)) + suffix
        // 사본을 다시 저장할 때와 같은 이름·단위·격자 검사를 통과한 반주 장단만 넣는다.
        val jangdanId = song.jangdanId ?: accompaniment.find(songId)?.takeIf {
            it.name == score.jangdanName && it.bpmUnit == score.bpmUnit && jangdanValidation?.valid(score.json, it) != false
        }?.id
        return repository.save(PublishedSheetCopy(
            request, title, jangdanId, song.bpm, score, clock.instant().truncatedTo(ChronoUnit.MICROS),
        ))
    }
}

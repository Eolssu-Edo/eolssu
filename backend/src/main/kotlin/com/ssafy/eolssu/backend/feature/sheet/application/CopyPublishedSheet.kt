package com.ssafy.eolssu.backend.feature.sheet.application

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

/** 게시 곡 스냅샷을 복사한 회원의 첫 번째 저장 버전으로 등록한다. */
class CopyPublishedSheet(
    private val repository: PublishedSheetCopyRepository,
    private val scores: SheetScores,
    private val clock: Clock,
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
        val score = scores.prepare(song.scoreJson, 1) as? PreparedScore.Ready
            ?: return SheetCopyResult.Rejected(SheetCopyFailure.InvalidScore)
        val suffix = " (사본)"
        val originalTitle = song.title.trim()
        val titleLength = minOf(originalTitle.codePointCount(0, originalTitle.length), 50 - suffix.length)
        val title = originalTitle.substring(0, originalTitle.offsetByCodePoints(0, titleLength)) + suffix
        return repository.save(PublishedSheetCopy(
            request, title, song.jangdanId, song.bpm, score, clock.instant().truncatedTo(ChronoUnit.MICROS),
        ))
    }
}

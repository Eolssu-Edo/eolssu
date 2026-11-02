package com.ssafy.eolssu.backend.feature.sheet.domain

import java.time.Instant

// 계약: docs/specs/user-sheets.md. 변환·PDF 악보가 생기면 종류·출처·상태를 넓힌다.
enum class SheetStatus { NEEDS_REVIEW, EVALUABLE }

const val SHEET_KIND_JEONGGANBO = "JEONGGANBO"
const val SHEET_SOURCE_MANUAL = "MANUAL"

data class UserSheet(
    val id: Long,
    val userId: Long,
    val title: String,
    val kind: String,
    val source: String,
    val status: SheetStatus,
    val jangdanId: Long,
    val jangdanName: String,
    val bpm: Int,
    val bpmUnit: String,
    val columnCount: Int,
    val scoreVersion: Int,
    /** 공통 악보 v1. 한 번도 저장하지 않은 빈 악보는 null. */
    val scoreJson: String?,
    val noteCount: Int,
    val pendingCellCount: Int,
    val requestHash: String,
    val updatedAt: Instant,
)

data class NewUserSheet(
    val userId: Long,
    val title: String,
    val status: SheetStatus,
    val jangdanId: Long,
    val bpm: Int,
    val columnCount: Int,
    val scoreJson: String?,
    val noteCount: Int,
    val idempotencyKey: String,
    val requestHash: String,
    val createdAt: Instant,
)

data class SheetJangdan(val id: Long, val bpmUnit: String)

/** 복사할 수 있는 게시·미삭제 곡. */
data class SongCopySource(val jangdanId: Long, val bpmUnit: String, val scoreJson: String, val scorable: Boolean)

data class SheetPage(val items: List<UserSheet>, val totalElements: Long)

interface UserSheetRepository {
    fun findByIdempotencyKey(userId: Long, idempotencyKey: String): UserSheet?
    fun findJangdan(jangdanId: Long): SheetJangdan?
    fun findSongSource(songId: Long): SongCopySource?

    /** 본인 악보. 없거나 다른 회원의 악보면 null. */
    fun find(sheetId: Long, userId: Long): UserSheet?

    /** 저장한 악보 ID. 같은 회원·키의 악보가 이미 있으면(유일 제약) null. */
    fun insert(sheet: NewUserSheet): Long?

    /** kind가 null이면 모든 종류. 최근 수정 순. */
    fun page(userId: Long, kind: String?, page: Int, size: Int): SheetPage
}

package com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface UserSheetMapper {
    fun findByIdempotencyKey(@Param("userId") userId: Long, @Param("idempotencyKey") idempotencyKey: String): UserSheetRow?

    fun findJangdan(@Param("jangdanId") jangdanId: Long): SheetJangdanRow?

    /** 게시·미삭제 곡만. */
    fun findSongSource(@Param("songId") songId: Long): SongCopySourceRow?

    /** 본인 악보. 다른 회원의 악보면 null. */
    fun find(@Param("sheetId") sheetId: Long, @Param("userId") userId: Long): UserSheetRow?

    fun insert(sheet: UserSheetInsert): Int

    fun findPage(
        @Param("userId") userId: Long,
        @Param("kind") kind: String?,
        @Param("offset") offset: Long,
        @Param("size") size: Int,
    ): List<UserSheetRow>

    fun count(@Param("userId") userId: Long, @Param("kind") kind: String?): Long
}

data class UserSheetRow(
    val id: Long,
    val userId: Long,
    val title: String,
    val kind: String,
    val source: String,
    val status: String,
    val jangdanId: Long?,
    val jangdanName: String?,
    val bpm: Int,
    val bpmUnit: String,
    val columnCount: Int,
    val scoreVersion: Int,
    val scoreJson: String?,
    val noteCount: Int,
    val pendingCellCount: Int,
    val requestHash: String,
    val updatedAt: LocalDateTime,
)

data class SheetJangdanRow(val id: Long, val bpmUnit: String)

data class SongCopySourceRow(val jangdanId: Long?, val bpmUnit: String, val scoreJson: String, val scorable: Boolean)

data class UserSheetInsert(
    val userId: Long,
    val title: String,
    val kind: String,
    val source: String,
    val status: String,
    val jangdanId: Long?,
    val bpm: Int,
    val bpmUnit: String,
    val columnCount: Int,
    val scoreJson: String?,
    val noteCount: Int,
    val idempotencyKey: String,
    val requestHash: String,
    val createdAt: LocalDateTime,
) {
    var id: Long = 0
}

package com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface UserSheetMapper {
    fun findJangdan(@Param("jangdanId") jangdanId: Long): SheetJangdanRow?

    /** 본인 악보. 다른 회원의 악보면 null. */
    fun find(@Param("sheetId") sheetId: Long, @Param("userId") userId: Long): UserSheetRow?

    fun findPage(
        @Param("userId") userId: Long,
        @Param("kind") kind: String?,
        @Param("offset") offset: Long,
        @Param("size") size: Int,
    ): List<UserSheetRow>

    fun count(@Param("userId") userId: Long, @Param("kind") kind: String?): Long

    fun updateScore(update: SheetScoreUpdateRow): Int

    fun updateDraft(update: SheetDraftUpdateRow): Int

    fun delete(@Param("sheetId") sheetId: Long, @Param("userId") userId: Long): Int
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
    val updatedAt: LocalDateTime,
    val draftJson: String?,
    val draftRevision: Int,
    val draftBaseVersion: Int?,
    val draftSavedAt: LocalDateTime?,
    val conversionStatus: String?,
    val sourcePageCount: Int?,
    val hwangPitch: String?,
    val sourceFileName: String?,
)

data class SheetJangdanRow(val id: Long, val bpmUnit: String, val name: String)

data class SheetScoreUpdateRow(
    val sheetId: Long,
    val userId: Long,
    val baseScoreVersion: Int,
    val title: String,
    val status: String,
    val jangdanId: Long?,
    val bpm: Int,
    val bpmUnit: String,
    val columnCount: Int,
    val scoreJson: String,
    val noteCount: Int,
    val updatedAt: LocalDateTime,
)

data class SheetDraftUpdateRow(
    val sheetId: Long,
    val userId: Long,
    val baseScoreVersion: Int,
    val draftRevision: Int,
    val draftJson: String,
    val savedAt: LocalDateTime,
)

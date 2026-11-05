package com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface SheetImportMapper {
    fun findReplay(@Param("userId") userId: Long, @Param("key") key: String): SheetImportReplayRow?

    fun countSheets(@Param("userId") userId: Long): Long

    fun countActiveConversions(@Param("userId") userId: Long): Long

    fun countActiveInstruments(@Param("instrumentId") instrumentId: Long): Long

    fun findJangdan(@Param("jangdanId") jangdanId: Long): ImportJangdanRow?

    /** 회원 행 잠금. 접수를 회원 단위로 줄 세워 상한·진행 중 확인과 삽입 사이에 끼어들지 못하게 한다. */
    fun lockUser(@Param("userId") userId: Long): Long?

    fun insertSheet(row: NewImportSheetRow): Int

    fun insertConversion(row: NewConversionRow): Int

    fun insertSourceFile(row: NewSourceFileRow): Int

    fun findConversion(@Param("sheetId") sheetId: Long, @Param("userId") userId: Long): SheetConversionRow?

    /** 본인 악보면 원본마다 한 행(원본이 없으면 키가 NULL인 한 행), 아니면 빈 목록. */
    fun findSourceKeys(@Param("sheetId") sheetId: Long, @Param("userId") userId: Long): List<SheetSourceKeyRow>
}

data class SheetImportReplayRow(val sheetId: Long, val requestHash: String)

/** 악보 ID를 같이 읽어 원본이 없는 악보도 행이 null로 오지 않게 한다(MyBatis는 모든 칸이 NULL인 행을 null로 준다). */
data class SheetSourceKeyRow(val sheetId: Long, val objectKey: String?)

data class ImportJangdanRow(val bpmUnit: String, val defaultBpm: Int)

class NewImportSheetRow(
    val userId: Long,
    val title: String,
    val source: String,
    val jangdanId: Long?,
    val bpm: Int,
    val bpmUnit: String,
    val idempotencyKey: String,
    val requestHash: String,
    val acceptedAt: LocalDateTime,
) {
    /** INSERT 뒤 MyBatis가 채운다. */
    var id: Long? = null
}

data class NewConversionRow(
    val sheetId: Long,
    val userId: Long,
    val notation: String,
    val instrumentId: Long,
    val jangdanId: Long?,
    val bpm: Int?,
    val hwangPitch: String?,
    val pageCount: Int,
    val acceptedAt: LocalDateTime,
)

data class NewSourceFileRow(
    val sheetId: Long,
    val fileIdx: Int,
    val objectKey: String,
    val contentType: String,
    val byteSize: Long,
    val fileName: String,
)

data class SheetConversionRow(
    val sheetId: Long,
    val notation: String,
    val status: String,
    val stage: String?,
    val pageCount: Int,
    val processedPageCount: Int,
    val jangdanId: Long?,
    val bpm: Int?,
    val failureCode: String?,
    val attempt: Int,
    val acceptedAt: LocalDateTime,
    val startedAt: LocalDateTime?,
    val finishedAt: LocalDateTime?,
    val noteCount: Int,
)

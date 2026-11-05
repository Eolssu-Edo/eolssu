package com.ssafy.eolssu.backend.feature.sheet.domain

import java.time.Instant

// 계약: docs/specs/user-sheets.md. 변환·PDF 악보가 생기면 종류·출처·상태를 넓힌다.
enum class SheetStatus { NEEDS_REVIEW, EVALUABLE }

data class UserSheet(
    val id: Long,
    val userId: Long,
    val title: String,
    val kind: String,
    val source: String,
    val status: SheetStatus,
    /** 장단이 없는 악보(예: 장단 없는 곡의 복사본)면 null. */
    val jangdanId: Long?,
    val jangdanName: String?,
    val bpm: Int,
    val bpmUnit: String,
    val columnCount: Int,
    val scoreVersion: Int,
    /** 공통 악보 v1. 한 번도 저장하지 않은 빈 악보는 null. */
    val scoreJson: String?,
    val noteCount: Int,
    val pendingCellCount: Int,
    val updatedAt: Instant,
    /** SHT-11 자동 저장 초안. 없으면 null. */
    val draft: SheetDraft? = null,
    /** 마지막 초안 순번. 초안이 없거나 확정 저장 뒤면 0. */
    val draftRevision: Int = 0,
    /** 불러오기(SHT-07)로 만든 악보의 변환·원본 정보. MANUAL은 null. */
    val imported: SheetImportSource? = null,
)

/** [fileName]은 첫 원본 파일 이름, [hwangPitch]는 오선보 요청 황종 음(없으면 null). */
data class SheetImportSource(val conversionStatus: SheetImportStatus, val pageCount: Int, val fileName: String?, val hwangPitch: String?)

/** 확정 저장 전 편집 내용. [json]은 title·jangdanId·bpm·score를 담은 초안 문서다. */
data class SheetDraft(val json: String, val baseScoreVersion: Int, val savedAt: Instant)

data class SheetJangdan(val id: Long, val bpmUnit: String, val name: String)

data class SheetPage(val items: List<UserSheet>, val totalElements: Long)

interface UserSheetRepository {
    fun findJangdan(jangdanId: Long): SheetJangdan?

    /** 본인 악보. 없거나 다른 회원의 악보면 null. */
    fun find(sheetId: Long, userId: Long): UserSheet?

    /** kind가 null이면 모든 종류. 최근 수정 순. */
    fun page(userId: Long, kind: String?, page: Int, size: Int): SheetPage

    /** SHT-04. 악보 버전이 [SheetScoreUpdate.baseScoreVersion]일 때만 바꾸고 초안을 지운다. 바꿨으면 true. */
    fun updateScore(update: SheetScoreUpdate): Boolean

    /** SHT-11. 악보 버전과 초안 순번이 그대로일 때만 초안을 바꾸고 순번을 1 올린다. 목록 정렬 시각은 바꾸지 않는다. 바꿨으면 true. */
    fun updateDraft(update: SheetDraftUpdate): Boolean

    /** SHT-02. 본인 악보를 지웠으면 true. */
    fun delete(sheetId: Long, userId: Long): Boolean
}

data class SheetScoreUpdate(
    val sheetId: Long,
    val userId: Long,
    val baseScoreVersion: Int,
    val title: String,
    val jangdanId: Long?,
    val bpm: Int,
    val bpmUnit: String,
    val columnCount: Int,
    val scoreJson: String,
    val noteCount: Int,
    val updatedAt: Instant,
)

data class SheetDraftUpdate(
    val sheetId: Long,
    val userId: Long,
    val baseScoreVersion: Int,
    val draftRevision: Int,
    val draftJson: String,
    val savedAt: Instant,
)

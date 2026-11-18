package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.SheetPage
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheet
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheetRepository
import com.ssafy.eolssu.backend.feature.song.domain.ScoreTimelineEvent

sealed interface SheetScoreLookup {
    /** [events]는 저장한 악보의 CAT-04 형식 타임라인이다. 빈 악보는 []. */
    data class Found(val sheet: UserSheet, val events: List<ScoreTimelineEvent>) : SheetScoreLookup
    data object NotFound : SheetScoreLookup
    /** 저장된 악보가 CAT-04 검증을 통과하지 못한다(CAT-04 조회와 같은 처리). */
    data object Broken : SheetScoreLookup
}

/** SHT-08 본인 악보 조회와 SHT-01 본인 악보 목록. */
class GetUserSheets(private val repository: UserSheetRepository, private val scores: SheetScores) {
    fun get(userId: Long, sheetId: Long): SheetScoreLookup {
        val sheet = repository.find(sheetId, userId) ?: return SheetScoreLookup.NotFound
        val events = sheet.scoreJson?.let { scores.timeline(it, sheet.scoreVersion, sheet.noteCount, sheet.bpm, sheet.bpmUnit) ?: return SheetScoreLookup.Broken }
        return SheetScoreLookup.Found(sheet, events ?: emptyList())
    }

    fun list(userId: Long, kind: String?, page: Int, size: Int): SheetPage = repository.page(userId, kind, page, size)
}

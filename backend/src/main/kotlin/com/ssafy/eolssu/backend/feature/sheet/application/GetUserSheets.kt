package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.SheetPage
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheet
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheetRepository

/** SHT-08 본인 악보 조회와 SHT-01 본인 악보 목록. */
class GetUserSheets(private val repository: UserSheetRepository) {
    fun get(userId: Long, sheetId: Long): UserSheet? = repository.find(sheetId, userId)

    fun list(userId: Long, kind: String?, page: Int, size: Int): SheetPage = repository.page(userId, kind, page, size)
}

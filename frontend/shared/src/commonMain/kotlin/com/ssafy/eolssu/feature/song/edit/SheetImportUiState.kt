package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.storage.SheetFile
import com.ssafy.eolssu.data.song.CatalogInstrument
import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import com.ssafy.eolssu.data.song.SheetImportField
import com.ssafy.eolssu.data.song.SheetImportInput

data class SheetImportUiState(
    val title: String = "", val files: List<SheetFile> = emptyList(),
    val instruments: List<CatalogInstrument> = emptyList(), val jangdans: List<CatalogJangdanSummary> = emptyList(),
    val instrumentId: String? = null, val jangdanId: String? = null,
    val loading: Boolean = true, val busy: Boolean = false, val picking: Boolean = false,
    val error: String? = null, val catalogFailed: Boolean = false,
    val acceptedId: String? = null, val expired: Boolean = false,
    val validationRequested: Boolean = false,
) {
    /** 제출을 시도한 뒤에는 남은 필드 오류만 입력값에 따라 갱신한다. */
    fun inputErrors(kind: ImportKind): Set<SheetImportField> {
        if (!validationRequested || expired || acceptedId != null) return emptySet()
        return SheetImportInput(title, if (kind == ImportKind.Jeongganbo) "JEONGGANBO" else "STAFF",
            instrumentId.orEmpty(), jangdanId, null, files).invalidFields()
    }
}

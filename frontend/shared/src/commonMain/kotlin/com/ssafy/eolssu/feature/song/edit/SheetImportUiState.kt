package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.storage.SheetFile
import com.ssafy.eolssu.data.song.CatalogInstrument
import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import com.ssafy.eolssu.data.song.SheetImportField
import com.ssafy.eolssu.data.song.SheetImportInput

data class SheetImportUiState(
    /** 올리기 화면에서 고른 악보 종류. 처음 값은 들어온 경로의 종류다. */
    val kind: ImportKind = ImportKind.Jeongganbo,
    val title: String = "", val files: List<SheetFile> = emptyList(),
    val instruments: List<CatalogInstrument> = emptyList(), val jangdans: List<CatalogJangdanSummary> = emptyList(),
    val instrumentId: String? = null, val jangdanId: String? = null,
    val loading: Boolean = true, val busy: Boolean = false, val picking: Boolean = false,
    val error: String? = null, val catalogFailed: Boolean = false,
    val acceptedId: String? = null, val expired: Boolean = false,
    val validationRequested: Boolean = false,
) {
    /** 제출을 시도한 뒤에는 남은 필드 오류만 입력값에 따라 갱신한다. */
    fun inputErrors(kind: ImportKind = this.kind): Set<SheetImportField> {
        if (!validationRequested || expired || acceptedId != null) return emptySet()
        return SheetImportInput(title, if (kind == ImportKind.Jeongganbo) "JEONGGANBO" else "STAFF",
            instrumentId.orEmpty(), jangdanId.takeIf { kind == ImportKind.Jeongganbo }, null, files).invalidFields()
    }
}

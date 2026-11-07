package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.storage.SheetFile
import com.ssafy.eolssu.data.song.CatalogInstrument
import com.ssafy.eolssu.data.song.CatalogJangdanSummary

data class SheetImportUiState(
    val title: String = "", val files: List<SheetFile> = emptyList(),
    val instruments: List<CatalogInstrument> = emptyList(), val jangdans: List<CatalogJangdanSummary> = emptyList(),
    val instrumentId: String? = null, val jangdanId: String? = null,
    val loading: Boolean = true, val busy: Boolean = false, val picking: Boolean = false,
    val error: String? = null, val catalogFailed: Boolean = false,
    val acceptedId: String? = null, val expired: Boolean = false,
)

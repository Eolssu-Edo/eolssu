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
    /** 장단을 몰라 '잘 모르겠어요'를 고른 상태. 이때 [jangdanId]는 추천 장단 [RECOMMENDED_JANGDAN_ID]다. */
    val jangdanUnsure: Boolean = false,
    val loading: Boolean = true, val busy: Boolean = false, val picking: Boolean = false,
    val error: String? = null, val catalogFailed: Boolean = false,
    val acceptedId: String? = null, val expired: Boolean = false,
    val validationRequested: Boolean = false,
) {
    /** 추천 장단(굿거리)이 목록에 있을 때만 '잘 모르겠어요'를 보인다. */
    val canChooseJangdanUnsure: Boolean get() = jangdans.any { it.jangdanId == RECOMMENDED_JANGDAN_ID }

    /** 제출을 시도한 뒤에는 남은 필드 오류만 입력값에 따라 갱신한다. */
    fun inputErrors(kind: ImportKind = this.kind): Set<SheetImportField> {
        if (!validationRequested || expired || acceptedId != null) return emptySet()
        return SheetImportInput(title, if (kind == ImportKind.Jeongganbo) "JEONGGANBO" else "STAFF",
            instrumentId.orEmpty(), jangdanId.takeIf { kind == ImportKind.Jeongganbo }, null, files).invalidFields()
    }
}

/** 정간보 올리기에서 '잘 모르겠어요'를 고르면 싣는 추천 장단 굿거리(V6 시드 id 2, S15P31E102-1054). */
internal const val RECOMMENDED_JANGDAN_ID = "2"

/**
 * 정간보 올리기 장단 목록에서 빼는 '4박 기본 반주'(V40 id 12). 전통 장단이 아니라 오선보 편곡 반주라 기본 160 BPM이
 * 정간보에 맞지 않는다(S15P31E102-746·1054). 오선보 변환과 편집 화면의 장단 선택에는 그대로 남는다.
 */
internal const val FOUR_BEAT_ACCOMPANIMENT_JANGDAN_ID = "12"

/** 정간보 올리기에서 고를 수 있는 장단. 이름은 바뀔 수 있어 마이그레이션으로 고정된 id로 거른다. */
internal fun jeongganboUploadJangdans(all: List<CatalogJangdanSummary>) =
    all.filterNot { it.jangdanId == FOUR_BEAT_ACCOMPANIMENT_JANGDAN_ID }

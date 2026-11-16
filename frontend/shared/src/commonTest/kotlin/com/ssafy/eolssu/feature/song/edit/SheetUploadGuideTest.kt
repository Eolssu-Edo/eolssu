package com.ssafy.eolssu.feature.song.edit

import kotlin.test.Test
import kotlin.test.assertEquals

class SheetUploadGuideTest {
    @Test
    fun jeongganboShowsPdfAndPhotoTipsOnly() {
        assertEquals(listOf(SheetUploadTip.DigitalPdf, SheetUploadTip.PhotoFraming), sheetUploadTips(ImportKind.Jeongganbo))
    }

    @Test
    fun staffAddsWeakPhotoWarningLast() {
        assertEquals(
            listOf(SheetUploadTip.DigitalPdf, SheetUploadTip.PhotoFraming, SheetUploadTip.StaffPhotoWeak),
            sheetUploadTips(ImportKind.Staff),
        )
    }

    @Test
    fun sheetLimitMatchesServer() {
        // backend SheetImports.kt SHEET_LIMIT_PER_MEMBER = 10
        assertEquals(10, MY_SHEET_LIMIT)
    }
}

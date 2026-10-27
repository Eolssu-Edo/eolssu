package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MySheetKind
import com.ssafy.eolssu.core.model.MySheetStatus
import com.ssafy.eolssu.testing.runImmediately
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MySheetRepositoryTest {

    // 2026-10-20 15:20 KST
    private val oct20 = 1_792_477_200_000L
    private fun repository() = SampleMySheetRepository(now = { oct20 }, uploadStepMillis = 0)

    @Test
    fun startsWithWireframeSampleSheets() {
        val sheets = repository().sheets.value
        assertEquals(listOf(MySheetKind.Jeongganbo, MySheetKind.Midi, MySheetKind.Pdf), sheets.map { it.kind })
        assertEquals(listOf(MySheetStatus.Evaluable, MySheetStatus.NeedsReview, MySheetStatus.ViewOnly), sheets.map { it.status })
        assertEquals("arirang", sheets.first().songId)
    }

    @Test
    fun uploadReportsProgressUpToOne() = runImmediately {
        val progress = mutableListOf<Float>()
        val upload = repository().uploadPdf(SamplePdfFile) { progress += it }
        assertEquals(10, progress.size)
        assertEquals(1f, progress.last())
        assertEquals(progress.sorted(), progress)
        assertEquals("상령산 정간보.pdf", upload.fileName)
        assertEquals(3, upload.pageCount)
    }

    @Test
    fun savingUploadedPdfAddsViewOnlySheetOnTop() = runImmediately {
        val repository = repository()
        val upload = repository.uploadPdf(SamplePdfFile) {}
        val sheet = repository.savePdf(upload.id, "상령산", "세마치")

        val sheets = repository.sheets.value
        assertEquals(4, sheets.size)
        assertEquals(sheet, sheets.first())
        assertEquals(MySheetKind.Pdf, sheet.kind)
        assertEquals(MySheetStatus.ViewOnly, sheet.status)
        assertEquals(3, sheet.pageCount)
        assertEquals("세마치", sheet.jangdanName)
        assertEquals(KoreanDateTime(2026, 10, 20, 15, 20), sheet.updatedAt)
        assertNull(repository.getUploadedPdf(upload.id))
    }
}

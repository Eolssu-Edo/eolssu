package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetKind
import com.ssafy.eolssu.core.model.MySheetStatus
import com.ssafy.eolssu.core.model.UploadedPdf
import com.ssafy.eolssu.data.song.SamplePdfFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MySheetStateTest {

    private fun sheet(id: String) =
        MySheet(id, MySheetKind.Pdf, MySheetStatus.ViewOnly, id, pageCount = 3, updatedAt = KoreanDateTime(2026, 10, 17, 19, 0))

    private val upload = UploadedPdf("upload-1", "상령산 정간보.pdf", SamplePdfFile.sizeBytes, 3)

    @Test
    fun startsOnProvidedSongsAndFirstSheetsShowNoNotice() {
        val state = MySongsUiState().receiveSheets(listOf(sheet("a"), sheet("b")))
        assertEquals(MySongsTab.Songs, state.tab)
        assertEquals(2, state.sheetCount)
        assertNull(state.savedSheet)
    }

    @Test
    fun newlySavedSheetOpensSheetsTabWithNotice() {
        val loaded = MySongsUiState(addSheetOpen = true).receiveSheets(listOf(sheet("a")))
        val saved = loaded.receiveSheets(listOf(sheet("new"), sheet("a")))
        assertEquals("new", saved.savedSheet?.id)
        assertEquals(MySongsTab.Sheets, saved.tab)
        assertFalse(saved.addSheetOpen)
        assertEquals(2, saved.sheetCount)
    }

    @Test
    fun addSheetReopensWhenBackFromPdfUploadWithoutSaving() {
        val left = MySongsUiState(addSheetOpen = true).receiveSheets(listOf(sheet("a"))).leaveForPdfUpload()
        assertFalse(left.addSheetOpen, "다른 화면 위에 겹치지 않게 떠날 때 닫는다")

        val back = left.returnToScreen()
        assertTrue(back.addSheetOpen)
        assertFalse(back.copy(addSheetOpen = false).returnToScreen().addSheetOpen, "한 번만 다시 연다")
    }

    @Test
    fun addSheetStaysClosedWhenBackAfterSaving() {
        val left = MySongsUiState().receiveSheets(listOf(sheet("a"))).leaveForPdfUpload()
        val back = left.receiveSheets(listOf(sheet("new"), sheet("a"))).returnToScreen()
        assertFalse(back.addSheetOpen)
        assertEquals("new", back.savedSheet?.id)
    }

    @Test
    fun returningFromOtherScreensKeepsAddSheetClosed() {
        assertFalse(MySongsUiState().returnToScreen().addSheetOpen)
    }

    @Test
    fun sameSheetsAgainShowNoNotice() {
        val loaded = MySongsUiState().receiveSheets(listOf(sheet("a")))
        assertNull(loaded.receiveSheets(listOf(sheet("a"))).savedSheet)
    }

    @Test
    fun uploadGoesFromEmptyThroughUploadingToUploaded() {
        val empty = PdfUploadUiState()
        assertEquals(PdfUploadPhase.Empty, empty.phase)
        assertFalse(empty.canProceed)

        val uploading = empty.startUpload(SamplePdfFile).withProgress(0.4f)
        assertEquals(PdfUploadPhase.Uploading, uploading.phase)
        assertEquals(0.4f, uploading.progress)
        assertFalse(uploading.canProceed)
        assertEquals(0.4f, uploading.withProgress(0.2f).progress, "진행률은 뒤로 가지 않는다")
        assertEquals(1f, uploading.withProgress(3f).progress)

        val done = uploading.finishUpload(upload)
        assertEquals(PdfUploadPhase.Uploaded, done.phase)
        assertEquals(1f, done.progress)
        assertTrue(done.canProceed)
        assertEquals(done, done.withProgress(0.1f))
    }

    @Test
    fun removingFileReturnsToEmptyAndIgnoresLateUpload() {
        val removed = PdfUploadUiState().startUpload(SamplePdfFile).withProgress(0.5f).removeFile()
        assertEquals(PdfUploadPhase.Empty, removed.phase)
        assertEquals(removed, removed.finishUpload(upload))
        assertEquals(removed, removed.withProgress(0.7f))
    }

    @Test
    fun pickingAnotherFileRestartsUpload() {
        val done = PdfUploadUiState().startUpload(SamplePdfFile).finishUpload(upload)
        val again = done.startUpload(SamplePdfFile.copy(name = "다른 악보.pdf"))
        assertEquals(PdfUploadPhase.Uploading, again.phase)
        assertEquals(0f, again.progress)
    }

    @Test
    fun fileSizeIsShownInKbOrOneDecimalMb() {
        assertEquals("2.4MB", formatFileSize(SamplePdfFile.sizeBytes))
        assertEquals("1.0MB", formatFileSize(1_048_576))
        assertEquals("512KB", formatFileSize(524_288))
        assertEquals("1KB", formatFileSize(1))
    }

    @Test
    fun previewFillsTitleFromFileNameAndKeepsPageInRange() {
        val preview = PdfPreviewUiState().receiveUpload(upload)
        assertEquals("상령산 정간보", preview.title)
        assertTrue(preview.canSave)
        assertEquals(3, preview.selectPage(3).page)
        assertEquals(1, preview.selectPage(4).page)
        assertEquals(1, preview.selectPage(0).page)
    }

    @Test
    fun blankTitleOrSavingBlocksSave() {
        val preview = PdfPreviewUiState().receiveUpload(upload)
        assertFalse(preview.copy(title = "  ").canSave)
        assertFalse(preview.copy(saving = true).canSave)
        assertFalse(PdfPreviewUiState(title = "제목").canSave, "올린 파일이 없으면 저장할 수 없다")
    }

    @Test
    fun defaultTitleDropsPdfExtensionOnly() {
        assertEquals("상령산 정간보", defaultSheetTitle("상령산 정간보.PDF"))
        assertEquals("악보", defaultSheetTitle("악보"))
        assertEquals(".pdf", defaultSheetTitle(".pdf"))
    }
}

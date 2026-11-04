package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.InspectedSheetFile
import com.ssafy.eolssu.backend.feature.sheet.domain.PdfPageCounter
import com.ssafy.eolssu.backend.feature.sheet.domain.PdfPages
import com.ssafy.eolssu.backend.feature.sheet.domain.SHEET_IMPORT_MAX_BYTES
import com.ssafy.eolssu.backend.feature.sheet.domain.SHEET_IMPORT_MAX_FILES
import com.ssafy.eolssu.backend.feature.sheet.domain.SHEET_IMPORT_MAX_PAGES
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetFileType
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetUpload

/** SHT-07 오류 코드에 대응한다(INVALID_REQUEST·FILE_TOO_LARGE·UNSUPPORTED_FILE_TYPE·TOO_MANY_PAGES·UNREADABLE_FILE). */
enum class SheetImportRejection { INVALID, TOO_LARGE, UNSUPPORTED_TYPE, TOO_MANY_PAGES, UNREADABLE }

sealed interface SheetImportFilesResult {
    /** [pageCount]는 PDF 쪽 수 또는 이미지 장 수다. */
    data class Accepted(val files: List<InspectedSheetFile>, val pageCount: Int) : SheetImportFilesResult
    data class Rejected(val reason: SheetImportRejection) : SheetImportFilesResult
}

/** SHT-07 파일 검사: PDF 1개 또는 PNG·JPEG 1~10장, 파일·합계 20MiB, PDF 10쪽. 크기 → 형식 → 구성 → 쪽 수 순으로 본다. */
class SheetImportFiles(private val pdfPages: PdfPageCounter) {
    fun inspect(uploads: List<SheetUpload>): SheetImportFilesResult {
        if (uploads.isEmpty() || uploads.size > SHEET_IMPORT_MAX_FILES) return reject(SheetImportRejection.INVALID)
        if (uploads.any { it.size > SHEET_IMPORT_MAX_BYTES } || uploads.sumOf { it.size } > SHEET_IMPORT_MAX_BYTES) {
            return reject(SheetImportRejection.TOO_LARGE)
        }
        val files = uploads.map { upload ->
            InspectedSheetFile(upload, SheetFileType.detect(upload.path) ?: return reject(SheetImportRejection.UNSUPPORTED_TYPE))
        }
        val pdf = files.filter { it.type == SheetFileType.PDF }
        if (pdf.isEmpty()) return SheetImportFilesResult.Accepted(files, files.size)
        if (files.size != 1) return reject(SheetImportRejection.INVALID)
        return when (val counted = pdfPages.count(pdf.single().upload.path)) {
            PdfPages.Unreadable -> reject(SheetImportRejection.UNREADABLE)
            is PdfPages.Count -> when {
                counted.pages < 1 -> reject(SheetImportRejection.UNREADABLE)
                counted.pages > SHEET_IMPORT_MAX_PAGES -> reject(SheetImportRejection.TOO_MANY_PAGES)
                else -> SheetImportFilesResult.Accepted(files, counted.pages)
            }
        }
    }

    private fun reject(reason: SheetImportRejection) = SheetImportFilesResult.Rejected(reason)
}

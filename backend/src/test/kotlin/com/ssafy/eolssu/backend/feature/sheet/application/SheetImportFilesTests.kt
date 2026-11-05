package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.PdfPages
import com.ssafy.eolssu.backend.feature.sheet.domain.SHEET_IMPORT_MAX_BYTES
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetFileType
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetUpload
import com.ssafy.eolssu.backend.feature.sheet.sheetImageBytes
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SheetImportFilesTests {
    @TempDir lateinit var dir: Path
    private var pdfPages: PdfPages = PdfPages.Count(2)
    private val files = SheetImportFiles { pdfPages }

    private val pdf = "%PDF-1.7\n".toByteArray()
    private val png = sheetImageBytes()
    private val jpeg = sheetImageBytes("JPEG")

    private fun upload(bytes: ByteArray, name: String, size: Long = bytes.size.toLong()): SheetUpload {
        val path = Files.write(dir.resolve("f${Files.list(dir).count()}"), bytes)
        return SheetUpload(path, size, name)
    }

    @Test
    fun `type comes from the file head not the name and pages come from the pdf`() {
        val result = files.inspect(listOf(upload(pdf, "score.png"))) as SheetImportFilesResult.Accepted
        assertEquals(listOf(SheetFileType.PDF), result.files.map { it.type })
        assertEquals(2, result.pageCount)

        val images = files.inspect(listOf(upload(jpeg, "a.pdf"), upload(png, "b"))) as SheetImportFilesResult.Accepted
        assertEquals(listOf(SheetFileType.JPEG, SheetFileType.PNG), images.files.map { it.type })
        assertEquals(2, images.pageCount)
    }

    @Test
    fun `rejections follow SHT-07`() {
        assertEquals(SheetImportRejection.INVALID, files.inspect(emptyList()).rejection())
        assertEquals(SheetImportRejection.INVALID, files.inspect(List(11) { upload(png, "p$it.png") }).rejection())
        assertEquals(SheetImportRejection.INVALID, files.inspect(listOf(upload(pdf, "a.pdf"), upload(png, "b.png"))).rejection())
        assertEquals(SheetImportRejection.INVALID, files.inspect(listOf(upload(pdf, "a.pdf"), upload(pdf, "b.pdf"))).rejection())
        assertEquals(SheetImportRejection.UNSUPPORTED_TYPE, files.inspect(listOf(upload("GIF89a".toByteArray(), "a.png"))).rejection())
        assertEquals(SheetImportRejection.UNSUPPORTED_TYPE, files.inspect(listOf(upload(ByteArray(0), "empty.pdf"))).rejection())
        assertEquals(SheetImportRejection.TOO_LARGE, files.inspect(listOf(upload(png, "big.png", SHEET_IMPORT_MAX_BYTES + 1))).rejection())
        val half = SHEET_IMPORT_MAX_BYTES / 2
        assertEquals(SheetImportRejection.TOO_LARGE, files.inspect(listOf(upload(png, "a.png", half), upload(png, "b.png", half + 1))).rejection())
        pdfPages = PdfPages.Count(11)
        assertEquals(SheetImportRejection.TOO_MANY_PAGES, files.inspect(listOf(upload(pdf, "a.pdf"))).rejection())
        pdfPages = PdfPages.Count(0)
        assertEquals(SheetImportRejection.UNREADABLE, files.inspect(listOf(upload(pdf, "a.pdf"))).rejection())
        pdfPages = PdfPages.Unreadable
        assertEquals(SheetImportRejection.UNREADABLE, files.inspect(listOf(upload(pdf, "a.pdf"))).rejection())
    }

    @Test
    fun `exactly the limits are accepted`() {
        val half = SHEET_IMPORT_MAX_BYTES / 2
        assertEquals(2, (files.inspect(listOf(upload(png, "a.png", half), upload(png, "b.png", half))) as SheetImportFilesResult.Accepted).pageCount)
        assertEquals(10, (files.inspect(List(10) { upload(png, "p$it.png") }) as SheetImportFilesResult.Accepted).pageCount)
        pdfPages = PdfPages.Count(10)
        assertEquals(10, (files.inspect(listOf(upload(pdf, "a.pdf"))) as SheetImportFilesResult.Accepted).pageCount)
    }

    @Test
    fun `magic bytes without a decodable image are unreadable`() {
        for (bytes in listOf(png.copyOf(11), jpeg.copyOf(4))) {
            assertEquals(SheetImportRejection.UNREADABLE, files.inspect(listOf(upload(bytes, "broken.png"))).rejection())
        }
    }

    private fun SheetImportFilesResult.rejection() = (this as SheetImportFilesResult.Rejected).reason
}

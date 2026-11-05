package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.domain.PdfPages
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PdfBoxPageCounterTests {
    @TempDir lateinit var dir: Path
    private val counter = PdfBoxPageCounter()

    private fun pdf(pages: Int, userPassword: String? = null): Path {
        val path = dir.resolve("p$pages-${userPassword != null}.pdf")
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage()) }
            if (userPassword != null) document.protect(StandardProtectionPolicy("owner", userPassword, AccessPermission()).apply { encryptionKeyLength = 128 })
            document.save(path.toFile())
        }
        return path
    }

    @Test
    fun `counts pages of a plain pdf`() {
        assertEquals(PdfPages.Count(3), counter.count(pdf(3)))
    }

    @Test
    fun `a password protected or broken pdf is unreadable`() {
        assertEquals(PdfPages.Unreadable, counter.count(pdf(1, userPassword = "secret")))
        assertEquals(PdfPages.Unreadable, counter.count(Files.write(dir.resolve("broken.pdf"), "%PDF-1.7\nnot really".toByteArray())))
    }

    @Test
    fun `an owner-only restriction is still readable`() {
        // 열기 암호 없이 권한만 막은 PDF는 쪽을 그릴 수 있다.
        assertEquals(PdfPages.Count(2), counter.count(pdf(2, userPassword = "")))
    }
}

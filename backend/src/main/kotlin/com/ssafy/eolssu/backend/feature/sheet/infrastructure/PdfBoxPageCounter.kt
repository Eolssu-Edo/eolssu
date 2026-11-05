package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.domain.PdfPageCounter
import com.ssafy.eolssu.backend.feature.sheet.domain.PdfPages
import java.io.IOException
import java.nio.file.Path
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils

/**
 * PDF 쪽 수만 센다(SHT-07). 열기 암호가 걸렸거나 구조를 읽을 수 없으면 [PdfPages.Unreadable]이다.
 * 버퍼는 임시 파일만 써서 20MiB PDF도 힙에 통째로 올리지 않는다.
 */
class PdfBoxPageCounter : PdfPageCounter {
    override fun count(file: Path): PdfPages = try {
        Loader.loadPDF(file.toFile(), "", null, null, IOUtils.createTempFileOnlyStreamCache()).use { PdfPages.Count(it.numberOfPages) }
    } catch (_: IOException) {
        // 암호(InvalidPasswordException)·손상 모두 IOException이다. 파일 내용은 로그에 남기지 않는다.
        PdfPages.Unreadable
    }
}

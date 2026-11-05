package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionJob
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionFile
import java.nio.file.Path
import java.nio.file.Files
import java.time.LocalDateTime
import javax.imageio.ImageIO
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.json.JsonMapper

class SourcePageRendererTests {
    @TempDir lateinit var dir: Path
    @Test fun `renders PDF pages to stored PNGs in original page order`() {
        val source=dir.resolve("source.pdf")
        PDDocument().use { pdf -> repeat(2) { pdf.addPage(PDPage()) }; pdf.save(source.toFile()) }
        val storage=LocalSheetSourceStorage(dir.resolve("objects"))
        storage.put("sheet-sources/1/original.pdf",source,"application/pdf")
        val job=ConversionJob(712,1,"악보","JEONGGANBO","DANSO","굿거리",60,null,"RUNNING",1,null,0,LocalDateTime.now())
        val pages=SourcePageRenderer(storage).render(job,listOf(ConversionFile("sheet-sources/1/original.pdf","application/pdf")),JsonMapper.builder().build().createObjectNode())
        assertEquals(listOf(0,1),pages.map { it.pageIdx })
        for(page in pages) {
            assertTrue(page.objectKey.startsWith("sheet-sources/1/"))
            val image=ImageIO.read(dir.resolve("objects").resolve(page.objectKey).toFile())
            assertEquals(page.width,image.width); assertEquals(page.height,image.height)
        }
    }
    @Test fun `phone photo EXIF rotation matches the recognition page orientation`() {
        val jpeg=java.io.ByteArrayOutputStream()
        val image=java.awt.image.BufferedImage(30,20,java.awt.image.BufferedImage.TYPE_INT_RGB)
        ImageIO.write(image,"JPEG",jpeg)
        val exif=byteArrayOf(69,120,105,102,0,0,73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0)
        val raw=jpeg.toByteArray()
        val rotated=raw.copyOfRange(0,2)+byteArrayOf(0xff.toByte(),0xe1.toByte(),0,(exif.size+2).toByte())+exif+raw.copyOfRange(2,raw.size)
        val source=Files.write(dir.resolve("phone.jpg"),rotated)
        val storage=LocalSheetSourceStorage(dir.resolve("objects"))
        storage.put("sheet-sources/1/photo.jpg",source,"image/jpeg")
        val job=ConversionJob(712,1,"사진","STAFF","DANSO",null,null,null,"RUNNING",1,null,0,LocalDateTime.now())
        val pages=SourcePageRenderer(storage).render(job,listOf(ConversionFile("sheet-sources/1/photo.jpg","image/jpeg")),JsonMapper.builder().build().createObjectNode())
        assertEquals(20,pages.single().width)
        assertEquals(30,pages.single().height)
    }
}

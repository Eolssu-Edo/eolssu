package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionFile
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionJob
import java.nio.file.Files
import java.util.UUID
import javax.imageio.ImageIO
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import tools.jackson.databind.JsonNode

/** 서명 URL로 내려줄 원본 쪽 PNG를 생성한다. 이미지 크기와 bbox는 응답에서 정규화하므로 독립적이다. */
class SourcePageRenderer(private val storage: SheetSourceStorage): ConversionPageRenderer {
    override fun render(job: ConversionJob,files: List<ConversionFile>,response: JsonNode): List<StoredConversionPage> {
        val temporary=Files.createTempDirectory("sheet-pages-")
        val objects=mutableListOf<StoredConversionPage>()
        val folder="sheet-sources/${job.userId}/pages/${job.sheetId}-${job.attempt}-${UUID.randomUUID()}"
        fun store(image: java.awt.image.BufferedImage) {
            try {
                val index=objects.size
                val path=temporary.resolve("page.png")
                check(ImageIO.write(image,"PNG",path.toFile()))
                val page=StoredConversionPage(index,"$folder/$index.png",image.width,image.height)
                objects+=page // PUT이 부분 성공한 경우도 정리 대상이다.
                storage.put(page.objectKey,path,"image/png")
            } finally { image.flush() }
        }
        try {
            for((index,file) in files.withIndex()) {
                val path=temporary.resolve("source-$index")
                storage.download(file.objectKey,path)
                if(file.contentType=="application/pdf") {
                    Loader.loadPDF(path.toFile()).use { pdf ->
                        val renderer=PDFRenderer(pdf)
                        repeat(pdf.numberOfPages) { store(renderer.renderImageWithDPI(it,144f)) }
                    }
                } else store(oriented(path))
            }
            return objects
        } catch(e: Exception) {
            objects.forEach { try { storage.delete(it.objectKey) } catch(_: Exception) { /* 원본은 보존하고 작업 실패로 남긴다. */ } }
            throw e
        } finally {
            Files.list(temporary).use { paths -> paths.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(temporary)
        }
    }

    /** Pillow ImageOps.exif_transpose(OMR)와 같은 8가지 EXIF 방향을 적용한 뒤 PNG로 만든다. */
    private fun oriented(path: java.nio.file.Path): java.awt.image.BufferedImage {
        val image=ImageIO.read(path.toFile()) ?: throw RecognitionFailure("UNSUPPORTED_FILE")
        val orientation=orientation(Files.readAllBytes(path))
        if(orientation==1) return image
        val w=image.width.toDouble(); val h=image.height.toDouble()
        val matrix=when(orientation) {
            2->doubleArrayOf(-1.0,0.0,0.0,1.0,w,0.0)
            3->doubleArrayOf(-1.0,0.0,0.0,-1.0,w,h)
            4->doubleArrayOf(1.0,0.0,0.0,-1.0,0.0,h)
            5->doubleArrayOf(0.0,1.0,1.0,0.0,0.0,0.0)
            6->doubleArrayOf(0.0,1.0,-1.0,0.0,h,0.0)
            7->doubleArrayOf(0.0,-1.0,-1.0,0.0,h,w)
            else->doubleArrayOf(0.0,-1.0,1.0,0.0,0.0,w)
        }
        val result=java.awt.image.BufferedImage(if(orientation>=5) image.height else image.width,
            if(orientation>=5) image.width else image.height,java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val graphics=result.createGraphics()
        try { graphics.drawImage(image,java.awt.geom.AffineTransform(matrix),null) } finally { graphics.dispose(); image.flush() }
        return result
    }

    private fun orientation(bytes: ByteArray): Int {
        fun tiff(offset: Int,length: Int): Int {
            try {
                val buffer=java.nio.ByteBuffer.wrap(bytes,offset,length).slice()
                buffer.order(if(buffer.get(0)=='I'.code.toByte() && buffer.get(1)=='I'.code.toByte()) java.nio.ByteOrder.LITTLE_ENDIAN else java.nio.ByteOrder.BIG_ENDIAN)
                if(buffer.getShort(2).toInt()!=42) return 1
                val ifd=buffer.getInt(4); val count=buffer.getShort(ifd).toInt() and 0xffff
                repeat(count) { index ->
                    val at=ifd+2+12*index
                    if(buffer.getShort(at).toInt()==0x112 && buffer.getShort(at+2).toInt()==3 && buffer.getInt(at+4)==1) {
                        return (buffer.getShort(at+8).toInt() and 0xffff).takeIf { it in 1..8 } ?: 1
                    }
                }
            } catch(_: IndexOutOfBoundsException) { return 1 }
            return 1
        }
        fun u(at: Int)=bytes[at].toInt() and 0xff
        if(bytes.size>2 && u(0)==0xff && u(1)==0xd8) {
            var at=2
            while(at+4<=bytes.size && u(at)==0xff) {
                val marker=u(at+1); if(marker==0xda || marker==0xd9) break
                val length=(u(at+2) shl 8)+u(at+3)
                if(length<2 || at+2+length>bytes.size) break
                if(marker==0xe1 && length>=8 && String(bytes,at+4,6,Charsets.US_ASCII)=="Exif\u0000\u0000") return tiff(at+10,length-8)
                at+=2+length
            }
        } else if(bytes.size>8 && u(0)==0x89) {
            var at=8
            while(at+12<=bytes.size) {
                val length=java.nio.ByteBuffer.wrap(bytes,at,4).int
                if(length<0 || length>bytes.size-at-12) break
                if(String(bytes,at+4,4,Charsets.US_ASCII)=="eXIf") return tiff(at+8,length)
                at+=12+length
            }
        }
        return 1
    }
}

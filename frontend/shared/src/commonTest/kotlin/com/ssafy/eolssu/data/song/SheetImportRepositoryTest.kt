package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.SheetFile
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SheetImportRepositoryTest {
    private val pdf = SheetFile("악보.pdf", "%PDF-1.7\nscore".encodeToByteArray())

    @Test fun fieldValidationKeepsUnicodeTitleAndNotationRules() {
        val input = SheetImportInput("😀".repeat(50), "STAFF", "4", null, "Eb4", listOf(pdf))
        assertTrue(input.valid())
        assertTrue(input.invalidFields().isEmpty())
        assertEquals(setOf(SheetImportField.Title), input.copy(title = "😀".repeat(51)).invalidFields())
        assertEquals(setOf(SheetImportField.Title), input.copy(title = "  ").invalidFields())
        assertEquals(setOf(SheetImportField.Instrument), input.copy(instrumentId = "0").invalidFields())
        assertEquals(setOf(SheetImportField.Jangdan), input.copy(notation = "JEONGGANBO", hwangPitch = null).invalidFields())
        assertEquals(setOf(SheetImportField.Files), input.copy(files = listOf(pdf, pdf)).invalidFields())
        assertFalse(input.copy(notation = "UNKNOWN").valid())
        assertFalse(input.copy(hwangPitch = "not-a-pitch").valid())
        assertFalse(input.copy(notation = "JEONGGANBO", jangdanId = "12").valid())
    }

    @Test fun sendsRealFileAndMetadataWithStableKeyAndRequires202() = runTest {
        var status = HttpStatusCode.Accepted
        var requests = 0
        val repository = ApiSheetImportRepository(createHttpClient(MockEngine { request ->
            requests++
            assertEquals("/api/users/me/sheets/imports", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("submission-1", request.headers["Idempotency-Key"])
            val channel = ByteChannel(autoFlush = true)
            (request.body as io.ktor.http.content.OutgoingContent.WriteChannelContent).writeTo(channel)
            channel.close()
            val body = channel.readRemaining().readText()
            assertTrue(body.contains("name=files") || body.contains("name=\"files\""))
            assertTrue(body.contains("%PDF-1.7\nscore"))
            assertTrue(body.contains("\"notation\":\"JEONGGANBO\""))
            assertTrue(body.contains("\"jangdanId\":\"12\""))
            respond("""{"isSuccess":true,"code":202,"result":{"sheetId":"91","notation":"JEONGGANBO","source":"JEONGGANBO_OMR","conversionStatus":"QUEUED","pageCount":1,"acceptedAt":"2026-11-06T06:00:00Z"}}""", status, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production))
        val input = SheetImportInput("실제 악보", "JEONGGANBO", "4", "12", null, listOf(pdf))
        assertEquals(SheetImportResult.Accepted("91"), repository.upload(input, "submission-1"))
        status = HttpStatusCode.OK
        assertIs<SheetImportResult.Failed>(repository.upload(input, "submission-1"))
        assertEquals(2, requests)
    }

    @Test fun rejectsMissingJangdanAndMixedPdfBeforeSending() = runTest {
        var requests = 0
        val repository = ApiSheetImportRepository(createHttpClient(MockEngine { requests++; error("must not send") }, ServerAddress.Production))
        val input = SheetImportInput("악보", "JEONGGANBO", "4", null, null, listOf(pdf))
        assertIs<SheetImportResult.Failed>(repository.upload(input, "key"))
        assertIs<SheetImportResult.Failed>(repository.upload(input.copy(jangdanId = "12", files = listOf(pdf, pdf)), "key"))
        assertEquals(0, requests)
    }

    @Test fun sendsOrderedImagesAndStaffPitchWithoutGuessingJangdan() = runTest {
        val png = SheetFile("first.png", byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10) + "first-image".encodeToByteArray())
        val jpeg = SheetFile("second.jpg", byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()) + "second-image".encodeToByteArray())
        val repository = ApiSheetImportRepository(createHttpClient(MockEngine { request ->
            val channel = ByteChannel(autoFlush = true)
            (request.body as io.ktor.http.content.OutgoingContent.WriteChannelContent).writeTo(channel)
            channel.close()
            val body = channel.readRemaining().readText()
            assertTrue(body.contains("image/png")); assertTrue(body.contains("image/jpeg"))
            assertTrue(body.indexOf("first-image") >= 0)
            assertTrue(body.indexOf("second-image") > body.indexOf("first-image"))
            assertTrue(body.contains("\"hwangPitch\":\"Eb4\""))
            assertFalse(body.contains("\"jangdanId\":\""))
            respond("""{"isSuccess":false,"code":409,"result":{"errorCode":"CONVERSION_IN_PROGRESS"}}""", HttpStatusCode.Conflict,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production))
        val input = SheetImportInput("이미지 악보", "STAFF", "4", null, "Eb4", listOf(png, jpeg))
        assertEquals(SheetImportResult.Failed("CONVERSION_IN_PROGRESS"), repository.upload(input, "images-1"))
        assertFalse(input.copy(files = listOf(pdf, png)).valid())
        assertFalse(input.copy(files = List(11) { png }).valid())
        assertFalse(input.copy(files = listOf(SheetFile("broken.png", "not an image".encodeToByteArray()))).valid())
    }
}

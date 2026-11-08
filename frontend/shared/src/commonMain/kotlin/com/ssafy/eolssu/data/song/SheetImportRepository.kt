package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.*
import com.ssafy.eolssu.core.storage.SheetFile
import com.ssafy.eolssu.core.storage.validSheetFiles
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SheetImportInput(val title: String, val notation: String, val instrumentId: String,
    val jangdanId: String?, val hwangPitch: String?, val files: List<SheetFile>) {
    fun valid() = title.trim().count { !it.isLowSurrogate() } in 1..50 && validCatalogId(instrumentId) &&
        (jangdanId == null || validCatalogId(jangdanId)) && validSheetFiles(files) &&
        when (notation) {
            "JEONGGANBO" -> jangdanId != null && hwangPitch == null
            "STAFF" -> hwangPitch == null || Regex("[A-G][#b]?[0-8]").matches(hwangPitch)
            else -> false
        }
}

sealed interface SheetImportResult {
    data class Accepted(val sheetId: String) : SheetImportResult
    data class Failed(val code: String? = null) : SheetImportResult
}

interface SheetImportRepository {
    suspend fun upload(input: SheetImportInput, key: String): SheetImportResult
}

/** SHT-07만 접수한다. 접수 성공은 인식 완료가 아니다. */
class ApiSheetImportRepository(private val client: HttpClient, private val member: SignedInMember? = null) : SheetImportRepository {
    override suspend fun upload(input: SheetImportInput, key: String): SheetImportResult = member.inSession {
        if (!input.valid() || !Regex("[!-~]{1,100}").matches(key)) return@inSession SheetImportResult.Failed("INVALID_REQUEST")
        val response = client.sendOrNull {
            post("/api/users/me/sheets/imports") {
                header("Idempotency-Key", key)
                setBody(MultiPartFormDataContent(formData {
                    append("metadata", Json.encodeToString(ImportMetadata(input.title.trim(), input.notation, input.instrumentId, input.jangdanId, input.hwangPitch)),
                        Headers.build { append(HttpHeaders.ContentType, "application/json") })
                    input.files.forEachIndexed { index, file ->
                        val type = requireNotNull(file.contentType)
                        val extension = when (type) { "application/pdf" -> "pdf"; "image/png" -> "png"; else -> "jpg" }
                        val name = file.name.map { if (it.isISOControl() || it in "\"\\/;") '_' else it }.joinToString("")
                            .take(150).ifBlank { "sheet-${index + 1}.$extension" }
                        // 선택한 이름의 따옴표·개행이 multipart 헤더에 들어가지 않게 한다.
                        append("files", file.bytes, Headers.build {
                            append(HttpHeaders.ContentType, type)
                            append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
                        })
                    }
                }))
            }
        } ?: return@inSession SheetImportResult.Failed()
        if (response.status.value != 202) return@inSession SheetImportResult.Failed(response.apiResponse<ApiError>()?.result?.errorCode)
        val result = response.apiResponse<ImportAccepted>()?.takeIf { it.isSuccess }?.result
        if (result == null || !validCatalogId(result.sheetId) || result.notation != input.notation ||
            result.conversionStatus !in setOf("QUEUED", "RUNNING", "DONE", "FAILED")) SheetImportResult.Failed()
        else SheetImportResult.Accepted(result.sheetId)
    }
}

@Serializable private class ImportMetadata(val title: String, val notation: String, val instrumentId: String,
    val jangdanId: String?, val hwangPitch: String?)
@Serializable private class ImportAccepted(val sheetId: String, val notation: String, val conversionStatus: String)

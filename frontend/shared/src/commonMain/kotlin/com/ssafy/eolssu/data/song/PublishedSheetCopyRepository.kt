package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ApiError
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.inSession
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

fun interface PublishedSheetCopyRepository {
    suspend fun copy(songId: String, key: String): PublishedSheetCopyResult
}

enum class PublishedSheetCopyProblem { Limit, ConversionInProgress, NotFound, Failed }

sealed interface PublishedSheetCopyResult {
    data class Created(val sheetId: String) : PublishedSheetCopyResult
    data class Rejected(val problem: PublishedSheetCopyProblem) : PublishedSheetCopyResult
}

class ApiPublishedSheetCopyRepository(
    private val client: HttpClient,
    private val member: SignedInMember? = null,
) : PublishedSheetCopyRepository {
    override suspend fun copy(songId: String, key: String): PublishedSheetCopyResult = member.inSession {
        val failed = PublishedSheetCopyResult.Rejected(PublishedSheetCopyProblem.Failed)
        if (!validCatalogId(songId)) return@inSession failed
        val response = client.sendOrNull {
            post("/api/users/me/sheets/copies") {
                header("Idempotency-Key", key)
                contentType(ContentType.Application.Json)
                setBody(CopyRequest(songId))
            }
        } ?: return@inSession failed
        if (response.status.value != 201) {
            val code = response.apiResponse<ApiError>()?.result?.errorCode
            return@inSession PublishedSheetCopyResult.Rejected(when (code) {
                "SHEET_LIMIT_EXCEEDED" -> PublishedSheetCopyProblem.Limit
                "CONVERSION_IN_PROGRESS" -> PublishedSheetCopyProblem.ConversionInProgress
                "RESOURCE_NOT_FOUND" -> PublishedSheetCopyProblem.NotFound
                else -> PublishedSheetCopyProblem.Failed
            })
        }
        val id = response.apiResponse<CopyResponse>()?.takeIf { it.isSuccess }?.result?.sheetId
        if (id != null && validCatalogId(id)) PublishedSheetCopyResult.Created(id) else failed
    }

    @Serializable private data class CopyRequest(val songId: String)
    @Serializable private data class CopyResponse(val sheetId: String)
}

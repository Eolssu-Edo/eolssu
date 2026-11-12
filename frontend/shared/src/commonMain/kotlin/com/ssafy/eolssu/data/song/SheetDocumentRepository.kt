package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ApiError
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.inSession
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

/** 확정 문서와 저장 중인 초안을 구분한다. 버전은 서버 문자열 그대로 유지한다. */
data class SheetDocument(
    val id: String, val title: String, val jangdanId: String?, val bpm: Int,
    val scoreVersion: String, val score: CommonScoreData?, val draft: SheetWorkingCopy?,
    val reviewStatus: String, val conversionStatus: String?, val issues: List<JsonObject>,
    val notices: List<JsonObject> = emptyList(),
    val authoring: JsonObject? = null,
    val expressions: JsonObject? = null,
)
data class SheetWorkingCopy(val title: String, val jangdanId: String?, val bpm: Int,
    val score: CommonScoreData, val baseScoreVersion: String, val revision: Int,
    val resolvedIssueIds: List<String> = emptyList(), val authoring: JsonObject? = null, val expressions: JsonObject? = null)

/** The server may capture a legacy source while storing the draft; only requested metadata is an edit. */
internal fun SheetWorkingCopy.matchesDraftAcknowledgement(request: SheetWorkingCopy): Boolean =
    this.copy(authoring = request.authoring) == request &&
        (request.authoring == null || authoring?.writeReference() == request.authoring.writeReference())

sealed interface SheetWrite {
    data class Saved(val scoreVersion: String, val revision: Int = 0, val reviewStatus: String? = null, val expressions: JsonObject? = null) : SheetWrite
    data class UnsupportedPitches(val details: AuthoringPitchRejection) : SheetWrite
    data object Conflict : SheetWrite
    data object Unfilled : SheetWrite
    data object TooLarge : SheetWrite
    data object Failed : SheetWrite
    data class TempoRejected(val issue: SheetTempoIssue) : SheetWrite
}
interface SheetDocumentRepository {
    suspend fun get(sheetId: String): SheetDocument?
    suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite
}

/** SHT-08·04·11. 서버 응답 전에는 성공을 만들거나 버전·revision을 올리지 않는다. */
class ApiSheetDocumentRepository(private val client: HttpClient, private val member: SignedInMember? = null) : SheetDocumentRepository {
    override suspend fun get(sheetId: String): SheetDocument? = member.inSession {
        if (!validCatalogId(sheetId)) return@inSession null
        val response = client.sendOrNull { get("/api/users/me/sheets/$sheetId/score") } ?: return@inSession null
        if (response.status.value != 200) return@inSession null
        val dto = response.apiResponse<DocumentDto>()?.takeIf { it.isSuccess }?.result ?: return@inSession null
        if (dto.sheetId != sheetId || dto.scoreVersion.toIntOrNull()?.let { it > 0 } != true || dto.bpm <= 0) return@inSession null
        val original = dto.scoreData?.let { runCatching { CommonScoreData.decode(it.toString()) }.getOrNull() ?: return@inSession null }
        val copy = dto.draft?.let {
            val data = runCatching { CommonScoreData.decode(it.score.toString()) }.getOrNull() ?: return@inSession null
            if (it.baseScoreVersion != dto.scoreVersion || it.draftRevision < 0 || it.bpm <= 0) return@inSession null
            SheetWorkingCopy(it.title, it.jangdanId, it.bpm, data, it.baseScoreVersion, it.draftRevision,
                authoring = it.authoring, expressions = it.expressions ?: dto.expressions)
        }
        SheetDocument(dto.sheetId, dto.title, dto.jangdanId, dto.bpm, dto.scoreVersion, original, copy,
            dto.reviewStatus, dto.conversionStatus, dto.issues, dto.notices, authoring = dto.authoring, expressions = dto.expressions)
    }

    override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite = member.inSession {
        if (!validCatalogId(sheetId) || copy.baseScoreVersion.toIntOrNull()?.let { it in 1 until Int.MAX_VALUE } != true || copy.revision !in 0 until Int.MAX_VALUE) return@inSession SheetWrite.Failed
        val response = client.sendOrNull {
            put("/api/users/me/sheets/$sheetId/score" + if (draft) "/draft" else "") {
                contentType(ContentType.Application.Json)
                setBody(WriteBody(copy.baseScoreVersion, copy.title, copy.jangdanId, copy.bpm,
                    Json.encodeToJsonElement(copy.score) as JsonObject, copy.revision.takeIf { draft },
                    copy.resolvedIssueIds.takeIf { !draft && it.isNotEmpty() }, authoring = copy.authoring?.writeReference(), expressions = copy.expressions))
            }
        } ?: return@inSession SheetWrite.Failed
        when (response.status.value) {
            200 -> {
                val result = response.apiResponse<WrittenDto>()?.takeIf { it.isSuccess }?.result
                if (result?.sheetId != sheetId) SheetWrite.Failed
                else if (draft && result.draftRevision == copy.revision + 1) SheetWrite.Saved(copy.baseScoreVersion, requireNotNull(result.draftRevision))
                else if (!draft && result.scoreVersion?.toIntOrNull() == copy.baseScoreVersion.toInt() + 1) {
                    SheetWrite.Saved(requireNotNull(result.scoreVersion), reviewStatus = result.reviewStatus, expressions = result.expressions)
                }
                else SheetWrite.Failed
            }
            409 -> SheetWrite.Conflict
            413 -> SheetWrite.TooLarge
            400 -> {
                val error = response.apiResponse<ApiError>()?.result
                val rejection = error?.let { AuthoringError(it.errorCode, it.details).authoringRejection }
                when {
                    rejection != null -> SheetWrite.UnsupportedPitches(rejection)
                    error?.errorCode == "UNFILLED_CELLS" -> SheetWrite.Unfilled
                    error?.errorCode in setOf("TEMPO_UNRENDERABLE", "EXPORT_TOO_LONG") -> SheetWrite.TempoRejected(SheetTempoIssue.from(requireNotNull(error)))
                    else -> SheetWrite.Failed
                }
            }
            else -> SheetWrite.Failed
        }
    }
}

/** The server owns the immutable snapshot; echo only its identity so autosaves do not double score size. */
private fun JsonObject.writeReference(): JsonObject = kotlinx.serialization.json.buildJsonObject {
    this@writeReference["schema"]?.let { put("schema", it) }
    val source = this@writeReference["source"] as? JsonObject
    put("source", kotlinx.serialization.json.buildJsonObject { source?.get("scoreSha256")?.let { put("scoreSha256", it) } })
    this@writeReference["adaptation"]?.let { put("adaptation", it) }
}

@Serializable private class DocumentDto(val sheetId: String, val title: String, val jangdanId: String? = null,
    val bpm: Int, val scoreVersion: String, val scoreData: JsonObject? = null, val draft: DraftDto? = null,
    val reviewStatus: String, val conversionStatus: String? = null, val issues: List<JsonObject> = emptyList(),
    val notices: List<JsonObject> = emptyList(), val authoring: JsonObject? = null, val expressions: JsonObject? = null)
@Serializable private class DraftDto(val title: String, val jangdanId: String? = null, val bpm: Int,
    val score: JsonObject, val baseScoreVersion: String, val draftRevision: Int, val authoring: JsonObject? = null, val expressions: JsonObject? = null)
@Serializable private class WriteBody(val baseScoreVersion: String, val title: String, val jangdanId: String?,
    val bpm: Int, val score: JsonObject, val draftRevision: Int? = null, val resolvedIssueIds: List<String>? = null,
    val authoring: JsonObject? = null, val expressions: JsonObject? = null)
@Serializable private class WrittenDto(val sheetId: String, val scoreVersion: String? = null,
    val draftRevision: Int? = null, val reviewStatus: String? = null, val expressions: JsonObject? = null)

@Serializable internal data class AuthoringError(val errorCode: String? = null, val details: JsonObject? = null) {
    val authoringRejection: AuthoringPitchRejection? get() = details?.let {
        runCatching { com.ssafy.eolssu.core.network.ApiJson.decodeFromString<AuthoringPitchRejection>(it.toString()) }.getOrNull()
    }?.takeIf { it.reason == "UNSUPPORTED_PITCHES" }
}

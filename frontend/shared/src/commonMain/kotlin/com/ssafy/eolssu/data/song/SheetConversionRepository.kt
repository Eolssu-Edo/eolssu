package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.inSession
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable

/** SHT-10 `status`. 접수 뒤 QUEUED → RUNNING → DONE/FAILED 순서로 바뀐다. */
enum class SheetConversionStatus {
    Queued, Running, Done, Failed;

    /** 아직 변환 중이라 `pollAfterMs` 뒤에 다시 물어야 한다. */
    val inProgress: Boolean get() = this == Queued || this == Running

    companion object {
        fun fromCode(code: String?): SheetConversionStatus? = when (code) {
            "QUEUED" -> Queued
            "RUNNING" -> Running
            "DONE" -> Done
            "FAILED" -> Failed
            else -> null
        }
    }
}

/** SHT-10 `notation`. 실패 원인(정간·오선)과 읽는 데 걸리는 시간을 가르는 데 쓴다. */
enum class SheetNotation {
    Jeongganbo, Staff;

    companion object {
        fun fromCode(code: String?): SheetNotation? = when (code) {
            "JEONGGANBO" -> Jeongganbo
            "STAFF" -> Staff
            else -> null
        }
    }
}

/** SHT-10 `stage`. RUNNING일 때만 온다. [Recognizing]은 쪽마다 인식(OMR), [BuildingScore]는 인식 결과로 악보를 만드는 중. */
enum class SheetConversionStage {
    Recognizing, BuildingScore;

    companion object {
        fun fromCode(code: String?): SheetConversionStage? = when (code) {
            "OMR" -> Recognizing
            "BUILDING_SCORE" -> BuildingScore
            else -> null
        }
    }
}

/**
 * SHT-10 `failureCode`. FAILED일 때만 오고, 모르는 값은 [ServerError]로 본다.
 * 서버는 인식기의 `NO_GRID_FOUND`·`NO_STAFF_FOUND`를 `NO_SCORE_FOUND`로, `ENGINE_ERROR`를 `SERVER_ERROR`로 바꿔 준다.
 * 정간·오선 구분은 [SheetConversion.notation]으로 한다.
 */
enum class SheetConversionFailure {
    /** 정간·오선을 찾지 못했다. */
    NoScoreFound,

    /** 인식기가 읽을 수 없는 파일이다. */
    UnsupportedFile,

    /** 시간이 너무 오래 걸렸다(원본 보관 기간이 지난 경우 포함). */
    Timeout,

    ServerError;

    companion object {
        fun fromCode(code: String?): SheetConversionFailure = when (code) {
            "NO_SCORE_FOUND" -> NoScoreFound
            "UNSUPPORTED_FILE" -> UnsupportedFile
            "TIMEOUT" -> Timeout
            else -> ServerError
        }
    }
}

/**
 * 불러오기로 만든 내 악보의 변환 상태(SHT-10 `result`).
 *
 * @property notation 정간보·오선보. 서버가 모르는 값을 주면 null.
 * @property pollAfterMs 변환 중일 때 다음 조회까지 기다릴 시간. 그 밖에는 null.
 * @property issueCount DONE일 때 확인이 남은 정간 수. 그 밖에는 null.
 * @property retryable FAILED이고 서버가 같은 원본으로 다시 시도할 수 있다고 답했을 때만 true(SHT-14).
 *   원본을 보관하지 않는 지금 서버는 항상 false라 새 파일로 다시 불러와야 한다.
 */
data class SheetConversion(
    val sheetId: String,
    val status: SheetConversionStatus,
    val stage: SheetConversionStage? = null,
    val processedPageCount: Int = 0,
    val pageCount: Int = 0,
    val pollAfterMs: Long? = null,
    val issueCount: Int? = null,
    val failure: SheetConversionFailure? = null,
    val retryable: Boolean = false,
    val notation: SheetNotation? = null,
)

/** SHT-10 결과. */
sealed interface SheetConversionLookup {
    data class Found(val conversion: SheetConversion) : SheetConversionLookup

    /** 404: 지워졌거나 불러오기로 만든 악보가 아니다. 목록을 다시 받으면 사라지거나 변환 상태가 없다. */
    data object NotFound : SheetConversionLookup

    /** 그 밖의 오류, 응답 없음, 계약과 다른 응답. 다음 간격에 다시 묻는다. */
    data object Unavailable : SheetConversionLookup
}

/** SHT-14 결과. */
sealed interface SheetConversionRetry {
    /** 202: 다시 줄을 섰다(같은 시도가 이미 진행 중이면 그 시도). */
    data class Accepted(val conversion: SheetConversion) : SheetConversionRetry

    /** 409 `STATE_CONFLICT`: 다시 시도할 수 없다. 원본을 보관하지 않아 새 파일로 다시 불러와야 한다. */
    data object NotRetryable : SheetConversionRetry

    /** 404: 악보가 없거나 남의 것, 불러오기로 만든 악보가 아니다. */
    data object NotFound : SheetConversionRetry

    /** 그 밖의 오류, 응답 없음, 계약과 다른 응답. */
    data object Unavailable : SheetConversionRetry
}

/** 내 악보 불러오기의 변환 상태 조회(SHT-10)와 실패한 변환 다시 시도(SHT-14). */
interface SheetConversionRepository {
    suspend fun status(sheetId: String): SheetConversionLookup

    suspend fun retry(sheetId: String): SheetConversionRetry

    /** 대기 화면이 완료를 보고 편집으로 데려갔다. 같은 완료를 앱 전체 배너로 다시 알리지 않는다. */
    fun acknowledgeCompletion(sheetId: String) {}
}

/** 응답 코드로만 분기한다. 서버 ID가 아닌 악보는 서버에 묻지 않는다. 상태 응답은 `no-store`라 캐시하지 않는다. */
class ApiSheetConversionRepository(private val client: HttpClient, private val member: SignedInMember? = null) : SheetConversionRepository {
    override suspend fun status(sheetId: String): SheetConversionLookup = member.inSession {
        if (!validCatalogId(sheetId)) return@inSession SheetConversionLookup.NotFound
        val response = client.sendOrNull { get("/api/users/me/sheets/$sheetId/conversion") } ?: return@inSession SheetConversionLookup.Unavailable
        when (response.status.value) {
            200 -> response.conversion(sheetId)?.let { SheetConversionLookup.Found(it) } ?: SheetConversionLookup.Unavailable
            404 -> SheetConversionLookup.NotFound
            else -> SheetConversionLookup.Unavailable
        }
    }

    override suspend fun retry(sheetId: String): SheetConversionRetry = member.inSession {
        if (!validCatalogId(sheetId)) return@inSession SheetConversionRetry.NotFound
        val response = client.sendOrNull { post("/api/users/me/sheets/$sheetId/conversion/retry") } ?: return@inSession SheetConversionRetry.Unavailable
        when (response.status.value) {
            200, 202 -> response.conversion(sheetId)?.let { SheetConversionRetry.Accepted(it) } ?: SheetConversionRetry.Unavailable
            404 -> SheetConversionRetry.NotFound
            409 -> SheetConversionRetry.NotRetryable
            else -> SheetConversionRetry.Unavailable
        }
    }

    /** 모르는 상태이거나 다른 악보의 응답이면 null이다. */
    private suspend fun HttpResponse.conversion(sheetId: String): SheetConversion? =
        apiResponse<ConversionDto>()?.takeIf { it.isSuccess }?.result?.toModel()?.takeIf { it.sheetId == sheetId }
}

@Serializable
private class ConversionDto(
    val sheetId: String,
    val notation: String? = null,
    val status: String,
    val stage: String? = null,
    val processedPageCount: Int = 0,
    val pageCount: Int = 0,
    val pollAfterMs: Long? = null,
    val issueCount: Int? = null,
    val failureCode: String? = null,
    val retryable: Boolean = false,
) {
    fun toModel(): SheetConversion? {
        val status = SheetConversionStatus.fromCode(status) ?: return null
        if (pageCount < 0 || processedPageCount !in 0..maxOf(pageCount, 0)) return null
        return SheetConversion(
            sheetId = sheetId,
            status = status,
            stage = if (status == SheetConversionStatus.Running) SheetConversionStage.fromCode(stage) else null,
            processedPageCount = processedPageCount,
            pageCount = pageCount,
            pollAfterMs = pollAfterMs?.takeIf { status.inProgress && it > 0 },
            issueCount = issueCount?.takeIf { status == SheetConversionStatus.Done && it >= 0 },
            failure = if (status == SheetConversionStatus.Failed) SheetConversionFailure.fromCode(failureCode) else null,
            retryable = status == SheetConversionStatus.Failed && retryable,
            notation = SheetNotation.fromCode(notation),
        )
    }
}

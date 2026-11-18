package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SheetConversionRepositoryTest {
    private fun repository(status: HttpStatusCode, body: String, check: (io.ktor.client.request.HttpRequestData) -> Unit = {}) =
        ApiSheetConversionRepository(createHttpClient(MockEngine { request ->
            check(request)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production))

    private fun conversion(status: String, extra: String = "") =
        """{"isSuccess":true,"code":200,"result":{"sheetId":"91","notation":"JEONGGANBO","status":"$status","processedPageCount":1,"pageCount":3,"jangdanAuto":false,"bpmAuto":true,"attempt":1,"acceptedAt":"2026-11-09T00:00:00Z"$extra}}"""

    @Test fun readsRunningStageAndPagesWithPollInterval() = runTest {
        val repository = repository(HttpStatusCode.OK, conversion("RUNNING", ""","stage":"OMR","pollAfterMs":2000,"retryable":false""")) { request ->
            assertEquals("/api/users/me/sheets/91/conversion", request.url.encodedPath)
            assertEquals(HttpMethod.Get, request.method)
        }
        val found = assertIs<SheetConversionLookup.Found>(repository.status("91")).conversion
        assertEquals(SheetConversionStatus.Running, found.status)
        assertEquals(SheetConversionStage.Recognizing, found.stage)
        assertEquals(1 to 3, found.processedPageCount to found.pageCount)
        assertEquals(2000L, found.pollAfterMs)
        assertNull(found.failure)
        assertEquals(SheetNotation.Jeongganbo, found.notation)
    }

    @Test fun readsServerReadingStartOnlyWhileConverting() = runTest {
        // 진행 막대가 화면 진입이 아니라 서버 시작부터 세도록 startedAt을 받는다(S15P31E102-1099).
        val running = assertIs<SheetConversionLookup.Found>(repository(HttpStatusCode.OK,
            conversion("RUNNING", ""","stage":"OMR","startedAt":"2026-11-09T00:00:30Z"""")).status("91")).conversion
        assertEquals(1_794_182_430_000L, running.startedAtMs)
        val done = assertIs<SheetConversionLookup.Found>(repository(HttpStatusCode.OK,
            conversion("DONE", ""","startedAt":"2026-11-09T00:00:30Z"""")).status("91")).conversion
        assertNull(done.startedAtMs, "끝난 변환은 막대가 없어 시작 시각을 쓰지 않는다")
        val broken = assertIs<SheetConversionLookup.Found>(repository(HttpStatusCode.OK,
            conversion("RUNNING", ""","stage":"OMR","startedAt":"어제"""")).status("91")).conversion
        assertNull(broken.startedAtMs, "읽을 수 없는 시각은 무시하고 변환 상태는 그대로 받는다")
    }

    @Test fun readsStaffNotationAndIgnoresUnknownNotation() = runTest {
        val staff = repository(HttpStatusCode.OK, conversion("FAILED", ""","failureCode":"NO_SCORE_FOUND"""").replace("JEONGGANBO", "STAFF"))
        assertEquals(SheetNotation.Staff, assertIs<SheetConversionLookup.Found>(staff.status("91")).conversion.notation)
        val unknown = repository(HttpStatusCode.OK, conversion("QUEUED").replace("JEONGGANBO", "MIDI"))
        assertNull(assertIs<SheetConversionLookup.Found>(unknown.status("91")).conversion.notation, "모르는 표기법은 비워 둔다")
    }

    @Test fun failedKeepsCauseAndServerRetryableFlag() = runTest {
        val notRetryable = repository(HttpStatusCode.OK, conversion("FAILED", ""","failureCode":"NO_SCORE_FOUND","retryable":false"""))
        val failed = assertIs<SheetConversionLookup.Found>(notRetryable.status("91")).conversion
        assertEquals(SheetConversionFailure.NoScoreFound, failed.failure)
        assertFalse(failed.retryable)
        assertNull(failed.pollAfterMs, "끝난 변환은 다시 묻지 않는다")

        val retryable = repository(HttpStatusCode.OK, conversion("FAILED", ""","failureCode":"SOMETHING_NEW","retryable":true"""))
        val unknown = assertIs<SheetConversionLookup.Found>(retryable.status("91")).conversion
        assertEquals(SheetConversionFailure.ServerError, unknown.failure, "모르는 실패 코드는 서버 오류로 본다")
        assertTrue(unknown.retryable)
    }

    @Test fun doneCarriesIssueCount() = runTest {
        val repository = repository(HttpStatusCode.OK, conversion("DONE", ""","issueCount":4,"noteCount":120"""))
        val done = assertIs<SheetConversionLookup.Found>(repository.status("91")).conversion
        assertEquals(4, done.issueCount)
        assertNull(done.stage)
    }

    @Test fun separatesMissingFromUnavailableAndRejectsOtherSheetsOrUnknownStatus() = runTest {
        assertEquals(SheetConversionLookup.NotFound, repository(HttpStatusCode.NotFound, """{"isSuccess":false,"code":404,"result":{"errorCode":"RESOURCE_NOT_FOUND"}}""").status("91"))
        assertEquals(SheetConversionLookup.Unavailable, repository(HttpStatusCode.InternalServerError, "{}").status("91"))
        assertEquals(SheetConversionLookup.Unavailable, repository(HttpStatusCode.OK, conversion("RUNNING").replace("\"91\"", "\"92\"")).status("91"))
        assertEquals(SheetConversionLookup.Unavailable, repository(HttpStatusCode.OK, conversion("PAUSED")).status("91"))
    }

    @Test fun neverAsksServerForNonServerIds() = runTest {
        var requests = 0
        val repository = ApiSheetConversionRepository(createHttpClient(MockEngine { requests++; error("must not send") }, ServerAddress.Production))
        assertEquals(SheetConversionLookup.NotFound, repository.status("sample-1"))
        assertEquals(SheetConversionRetry.NotFound, repository.retry("sample-1"))
        assertEquals(0, requests)
    }

    @Test fun retryPostsAndMapsConflictToReimport() = runTest {
        val conflict = repository(HttpStatusCode.Conflict, """{"isSuccess":false,"code":409,"result":{"errorCode":"STATE_CONFLICT"}}""") { request ->
            assertEquals("/api/users/me/sheets/91/conversion/retry", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
        }
        assertEquals(SheetConversionRetry.NotRetryable, conflict.retry("91"))

        val accepted = repository(HttpStatusCode.Accepted, conversion("QUEUED", ""","pollAfterMs":2000"""))
        assertEquals(SheetConversionStatus.Queued, assertIs<SheetConversionRetry.Accepted>(accepted.retry("91")).conversion.status)
        assertEquals(SheetConversionRetry.NotFound, repository(HttpStatusCode.NotFound, "{}").retry("91"))
        assertEquals(SheetConversionRetry.Unavailable, repository(HttpStatusCode.ServiceUnavailable, "{}").retry("91"))
    }
}

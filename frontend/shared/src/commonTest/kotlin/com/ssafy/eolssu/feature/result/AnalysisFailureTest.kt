package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.model.AnalysisFailure
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.analysis_failed_decode_error
import eolssu.shared.generated.resources.analysis_failed_no_sound
import eolssu.shared.generated.resources.analysis_failed_server_error
import eolssu.shared.generated.resources.analysis_failed_timeout
import eolssu.shared.generated.resources.analysis_failed_too_short
import eolssu.shared.generated.resources.analysis_failed_upload_timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalysisFailureTest {
    @Test
    fun serverCodesMapToFailures() {
        assertEquals(AnalysisFailure.NoSound, AnalysisFailure.fromCode("NO_SOUND"))
        assertEquals(AnalysisFailure.TooShort, AnalysisFailure.fromCode("TOO_SHORT"))
        assertEquals(AnalysisFailure.DecodeError, AnalysisFailure.fromCode("DECODE_ERROR"))
        assertEquals(AnalysisFailure.UploadTimeout, AnalysisFailure.fromCode("UPLOAD_TIMEOUT"))
        assertEquals(AnalysisFailure.Timeout, AnalysisFailure.fromCode("TIMEOUT"))
        assertEquals(AnalysisFailure.ServerError, AnalysisFailure.fromCode("SERVER_ERROR"))
    }

    @Test
    fun unknownOrEmptyCodeIsServerError() {
        assertEquals(AnalysisFailure.ServerError, AnalysisFailure.fromCode("DECODE_FAILED"))
        assertEquals(AnalysisFailure.ServerError, AnalysisFailure.fromCode(""))
        assertEquals(AnalysisFailure.ServerError, AnalysisFailure.fromCode(null))
    }

    @Test
    fun onlyTimeoutAndServerErrorCanBeAnalyzedAgain() {
        assertTrue(AnalysisFailure.Timeout.retryable)
        assertTrue(AnalysisFailure.ServerError.retryable)
        listOf(AnalysisFailure.NoSound, AnalysisFailure.TooShort, AnalysisFailure.DecodeError, AnalysisFailure.UploadTimeout)
            .forEach { assertFalse(it.retryable, "$it") }
    }

    @Test
    fun eachFailureShowsItsOwnMessage() {
        assertEquals(Res.string.analysis_failed_no_sound, AnalysisFailure.NoSound.message())
        assertEquals(Res.string.analysis_failed_too_short, AnalysisFailure.TooShort.message())
        assertEquals(Res.string.analysis_failed_decode_error, AnalysisFailure.DecodeError.message())
        assertEquals(Res.string.analysis_failed_upload_timeout, AnalysisFailure.UploadTimeout.message())
        assertEquals(Res.string.analysis_failed_timeout, AnalysisFailure.Timeout.message())
        assertEquals(Res.string.analysis_failed_server_error, AnalysisFailure.ServerError.message())
    }

    @Test
    fun retryIsAllowedOnlyAfterRetryableFailureAndNotWhileRetrying() {
        assertTrue(AnalysisUiState(failure = AnalysisFailure.Timeout).canRetry())
        assertTrue(AnalysisUiState(failure = AnalysisFailure.ServerError).canRetry())
        assertFalse(AnalysisUiState(failure = AnalysisFailure.ServerError, retrying = true).canRetry())
        assertFalse(AnalysisUiState(failure = AnalysisFailure.NoSound).canRetry())
        assertFalse(AnalysisUiState(failure = AnalysisFailure.UploadTimeout).canRetry())
        assertFalse(AnalysisUiState().canRetry())
    }
}

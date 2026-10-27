package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.data.practice.AnalysisFailedException
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
    fun analysisErrorKeepsItsReasonAndOtherErrorsAreServerError() {
        assertEquals(AnalysisFailure.NoSound, AnalysisFailedException(AnalysisFailure.NoSound).toAnalysisFailure())
        assertEquals(AnalysisFailure.ServerError, IllegalStateException("network").toAnalysisFailure())
    }
}

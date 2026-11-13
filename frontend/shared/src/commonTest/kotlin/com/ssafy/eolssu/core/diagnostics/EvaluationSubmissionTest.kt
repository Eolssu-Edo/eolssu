package com.ssafy.eolssu.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EvaluationSubmissionTest {
    @Test
    fun repeatedSubmissionsFromSameFlowPairWithTheirOwnScore() {
        val events = mutableListOf<PerformanceEvent>()
        val previousSink = PerformanceLog.sink
        PerformanceLog.sink = { events += it }
        try {
            val practice = PerformanceVisit(PerformanceFlow(), PerfTarget.WEAK_LOOP)
            val submissions = List(2) {
                val submission = assertNotNull(practice.submissionFor(PerfTarget.ANALYSIS))
                practice.action(PerfTarget.ANALYSIS, submission)
                val analysis = PerformanceVisit(practice.flow, PerfTarget.ANALYSIS, evaluationSubmission = submission)
                val result = PerformanceVisit(
                    practice.flow, PerfTarget.SECTION_RESULT,
                    evaluationSubmission = analysis.submissionFor(PerfTarget.SECTION_RESULT),
                )
                repeat(2) { result.milestone(PerfPhase.SCORE_DRAWN) }
                assertNull(result.submissionFor(PerfTarget.WEAK_LOOP))
                submission
            }
            assertNotEquals(submissions.first(), submissions.last())
            assertEquals(1, events.map { it.flow }.distinct().size)
            assertEquals(submissions, events.filter { it.kind == PerfKind.ACTION }.map { it.evaluationSubmission })
            assertEquals(submissions, events.filter { it.phase == PerfPhase.SCORE_DRAWN }.map { it.evaluationSubmission })
        } finally {
            PerformanceLog.sink = previousSink
        }
    }
}

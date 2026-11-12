package com.ssafy.eolssu.backend.common

/** An optional QA correlation ID, independent of account, session and idempotency identifiers. */
@JvmInline
value class EvaluationTimingId private constructor(val value: String) {
    companion object {
        const val HEADER = "X-Evaluation-Timing-Id"
        const val RETRY_STATUS_HEADER = "X-Evaluation-Retry-Status"
        private val uuidV4 = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

        fun parse(value: String?): EvaluationTimingId? =
            value?.takeIf(uuidV4::matches)?.let(::EvaluationTimingId)
    }
}

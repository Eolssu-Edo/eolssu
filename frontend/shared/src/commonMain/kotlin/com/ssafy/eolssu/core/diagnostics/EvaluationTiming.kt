package com.ssafy.eolssu.core.diagnostics

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A random QA correlation ID with no account, device or session data. */
internal class EvaluationTiming : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<EvaluationTiming> {
        const val HEADER = "X-Evaluation-Timing-Id"
        const val RETRY_STATUS_HEADER = "X-Evaluation-Retry-Status"
        private val uuidV4 = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

        fun parseId(value: String?): String? = value?.takeIf(uuidV4::matches)
    }

    @OptIn(ExperimentalUuidApi::class)
    val id: String = Uuid.random().toString()
}

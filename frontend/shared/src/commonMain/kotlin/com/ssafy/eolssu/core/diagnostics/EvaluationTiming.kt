package com.ssafy.eolssu.core.diagnostics

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A random QA correlation ID with no account, device or session data. */
internal class EvaluationTiming : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<EvaluationTiming> {
        const val HEADER = "X-Evaluation-Timing-Id"
    }

    @OptIn(ExperimentalUuidApi::class)
    val id: String = Uuid.random().toString()
}

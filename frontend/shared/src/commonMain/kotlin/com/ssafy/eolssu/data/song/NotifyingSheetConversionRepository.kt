package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.AccountSession
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.inSession
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SheetConversionCompletion(val sheetId: String, val session: AccountSession, val sequence: Long)

/** Upload acceptance and SHT-10 observation share app lifetime, rather than a destination's lifetime. */
class NotifyingSheetConversionRepository(
    private val conversion: SheetConversionRepository,
    private val imports: SheetImportRepository,
    private val member: SignedInMember,
) : SheetConversionRepository, SheetImportRepository {
    private data class State(
        val session: AccountSession,
        val tracked: Map<String, SheetConversion> = emptyMap(),
        val finished: Set<String> = emptySet(),
        val deleted: Set<String> = emptySet(),
        val completions: List<SheetConversionCompletion> = emptyList(),
        /** 대기 화면이 이미 보여 준 완료의 순번. 띄우던 배너도 닫는다. */
        val acknowledged: Set<Long> = emptySet(),
        val sequence: Long = 0,
    )
    private val state = MutableStateFlow(State(member.session.value))
    private val requests = Mutex()
    val completions = state.map { it.completions }.distinctUntilChanged()

    init { member.onChange { state.value = State(member.session.value) } }

    override suspend fun upload(input: SheetImportInput, key: String): SheetImportResult = member.inSession {
        requests.withLock {
            val session = member.session.value
            imports.upload(input, key).also { result ->
                // Even if the first GET is already DONE, this is a newly accepted upload we know about.
                if (result is SheetImportResult.Accepted && result.sheetId !in state.value.finished)
                    observe(session, SheetConversion(result.sheetId, SheetConversionStatus.Queued))
            }
        }
    }

    override suspend fun status(sheetId: String): SheetConversionLookup = member.inSession {
        requests.withLock {
            val session = member.session.value
            conversion.status(sheetId).also { result ->
                when (result) {
                    is SheetConversionLookup.Found -> observe(session, result.conversion)
                    SheetConversionLookup.NotFound -> forget(session, sheetId, deleted = true)
                    SheetConversionLookup.Unavailable -> Unit
                }
            }
        }
    }

    override suspend fun retry(sheetId: String): SheetConversionRetry = member.inSession {
        requests.withLock {
            val session = member.session.value
            conversion.retry(sheetId).also { result ->
                if (result is SheetConversionRetry.Accepted) {
                    state.update { current ->
                        if (current.session != session || member.session.value != session) current
                        else current.copy(finished = current.finished - sheetId,
                            tracked = current.tracked + (sheetId to SheetConversion(sheetId, SheetConversionStatus.Queued)))
                    }
                    observe(session, result.conversion)
                } else if (result == SheetConversionRetry.NotFound) forget(session, sheetId, deleted = true)
            }
        }
    }

    /** Run while the app is resumed. Pausing preserves observed work and unshown completions. */
    suspend fun monitor() {
        state.map { it.session to it.tracked.keys }.distinctUntilChanged().collectLatest { (session, ids) ->
            coroutineScope {
                ids.forEach { sheetId -> launch {
                    member.inSession {
                        var misses = 0
                        while (state.value.session == session) {
                            val current = state.value.tracked[sheetId] ?: break
                            delay((current.pollAfterMs ?: 3_000).coerceIn(500, 30_000))
                            if (status(sheetId) == SheetConversionLookup.Unavailable) {
                                if (++misses >= 5) { forget(session, sheetId); break }
                            } else misses = 0
                        }
                    }
                } }
            }
        }
    }

    fun consume(completion: SheetConversionCompletion) {
        state.update { it.copy(completions = it.completions - completion) }
    }

    override fun acknowledgeCompletion(sheetId: String) {
        state.update { current ->
            val seen = current.completions.filter { it.sheetId == sheetId }
            if (seen.isEmpty()) current
            else current.copy(completions = current.completions - seen.toSet(), acknowledged = current.acknowledged + seen.map { it.sequence })
        }
    }

    fun isCurrent(completion: SheetConversionCompletion): Boolean = member.session.value == completion.session &&
        completion.sheetId !in state.value.deleted && completion.sequence !in state.value.acknowledged

    suspend fun awaitInvalidation(completion: SheetConversionCompletion) {
        state.first { it.session != completion.session || completion.sheetId in it.deleted || completion.sequence in it.acknowledged }
    }

    /** The confirmed DELETE path invalidates a notice even after polling has finished. */
    fun deleted(sheetId: String) = forget(member.session.value, sheetId, deleted = true)

    private fun forget(session: AccountSession, sheetId: String, deleted: Boolean = false) {
        state.update { current ->
            if (current.session != session) current else current.copy(
                tracked = current.tracked - sheetId,
                finished = if (deleted) current.finished + sheetId else current.finished,
                deleted = if (deleted) current.deleted + sheetId else current.deleted,
                completions = if (deleted) current.completions.filterNot { it.sheetId == sheetId } else current.completions,
            )
        }
    }

    private fun observe(session: AccountSession, result: SheetConversion) {
        state.update { current ->
            if (session.memberId == null || current.session != session || member.session.value != session) return@update current
            if (result.sheetId in current.deleted) return@update current
            if (result.status.inProgress) return@update current.copy(
                tracked = current.tracked + (result.sheetId to result), finished = current.finished - result.sheetId)
            if (result.sheetId in current.finished) return@update current
            val completed = result.status == SheetConversionStatus.Done && result.sheetId in current.tracked
            current.copy(
                tracked = current.tracked - result.sheetId,
                finished = current.finished + result.sheetId,
                completions = if (completed) current.completions + SheetConversionCompletion(result.sheetId, session, current.sequence + 1) else current.completions,
                sequence = current.sequence + if (completed) 1 else 0,
            )
        }
    }
}

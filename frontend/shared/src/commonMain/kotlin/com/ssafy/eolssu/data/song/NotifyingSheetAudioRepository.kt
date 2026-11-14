package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.AccountSession
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.inSession
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A completion holds no signed download URL; the destination reloads the result. */
data class SheetAudioCompletion(val sheetId: String, val session: AccountSession, val sequence: Long)

/** App-lifetime observation of the existing audio API, shared by editor and practice. */
class NotifyingSheetAudioRepository(
    private val delegate: SheetAudioRepository,
    private val member: SignedInMember,
) : SheetAudioRepository {
    private data class State(
        val session: AccountSession,
        val tracked: Map<String, SheetAudio> = emptyMap(),
        val completions: List<SheetAudioCompletion> = emptyList(),
        val sequence: Long = 0,
    )
    private val state = MutableStateFlow(State(member.session.value))
    // Editor, practice and app polling must not apply an older response after DONE.
    private val requests = Mutex()
    val completions = state.map { it.completions }.distinctUntilChanged()

    init { member.onChange { state.value = State(member.session.value) } }

    override suspend fun request(sheetId: String, settings: SheetAudioSettings): SheetAudioRequest = member.inSession {
        requests.withLock {
            val session = member.session.value
            delegate.request(sheetId, settings).also { result ->
                if (result is SheetAudioRequest.Accepted) observe(session, result.audio)
            }
        }
    }

    override suspend fun status(sheetId: String): SheetAudioLookup = member.inSession {
        requests.withLock {
            val session = member.session.value
            delegate.status(sheetId).also { result ->
                when (result) {
                    is SheetAudioLookup.Found -> observe(session, result.audio)
                    SheetAudioLookup.None -> forget(session, sheetId)
                    SheetAudioLookup.Unavailable -> Unit
                }
            }
        }
    }

    /** Called only while the app is resumed. Cancelling this does not lose observed work. */
    suspend fun monitor() {
        state.map { it.session to it.tracked.keys }.distinctUntilChanged().collectLatest { (session, ids) ->
            coroutineScope {
                ids.forEach { sheetId -> launch {
                    member.inSession {
                        var misses = 0
                        while (state.value.session == session) {
                            val audio = state.value.tracked[sheetId] ?: break
                            delay((audio.pollAfterMs ?: 3_000).coerceIn(500, 30_000))
                            if (status(sheetId) == SheetAudioLookup.Unavailable) {
                                if (++misses >= 5) { forget(session, sheetId); break }
                            } else misses = 0
                        }
                    }
                } }
            }
        }
    }

    fun consume(completion: SheetAudioCompletion) {
        state.update { it.copy(completions = it.completions - completion) }
    }

    fun isCurrent(completion: SheetAudioCompletion): Boolean = member.session.value == completion.session

    private fun forget(session: AccountSession, sheetId: String) {
        state.update { if (it.session == session) it.copy(tracked = it.tracked - sheetId) else it }
    }

    private fun observe(session: AccountSession, audio: SheetAudio) {
        state.update { current ->
            if (session.memberId == null || current.session != session || member.session.value != session) return@update current
            if (audio.status.inProgress) return@update current.copy(tracked = current.tracked + (audio.sheetId to audio))
            val previous = current.tracked[audio.sheetId]
            val completed = audio.status == SheetAudioStatus.Done && audio.audio != null && previous != null &&
                previous.scoreVersion == audio.scoreVersion && previous.instrumentId == audio.instrumentId &&
                previous.bpm == audio.bpm && previous.withJangdan == audio.withJangdan
            current.copy(
                tracked = current.tracked - audio.sheetId,
                completions = if (completed) current.completions + SheetAudioCompletion(audio.sheetId, session, current.sequence + 1) else current.completions,
                sequence = current.sequence + if (completed) 1 else 0,
            )
        }
    }
}

package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.KeyValueStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * 기초부터 배우기 진도. 완료 표시는 회원 계정(LRN-01·03)에 남겨 재설치·다른 기기에서도 이어진다.
 *
 * 서버에 올리기 전(S15P31E102-370 이전)에 이 기기에만 남긴 완료 표시는, 그 트랙을 처음 불러올 때 단계마다 LRN-03으로 올려 합친다.
 * 올린 단계는 기기에서 지우고, 올리지 못한 단계만 남겨 다음에 불러올 때 다시 올린다.
 * 기기 표시는 그 기기에서 로그인했던 회원 것만 올린다. 회원을 가리지 않던 예전 공용 표시는 어느 회원에게도 올리지 않는다.
 */
class LearnProgressRepository(
    private val api: LessonApi,
    private val store: KeyValueStore,
    private val member: SignedInMember? = null,
) {
    private val _saves = MutableStateFlow(0)
    private val upload = Mutex()

    /** 서버 완료 표시가 바뀔 때마다 늘어난다. 다른 화면(연습 기록지·마이페이지)이 완료 수를 다시 셀 때 쓴다. */
    val saves: StateFlow<Int> = _saves.asStateFlow()

    init { member?.onChange { _saves.update { it + 1 } } }

    /**
     * [trackId](학습 화면 악기 식별자)의 단계와 본인 완료 표시. 이 기기에 남은 완료 표시가 있으면 먼저 올려 합친 결과다.
     * 목록을 받지 못하면 null이고, 기기 표시는 그대로 남는다.
     */
    suspend fun track(trackId: String): LessonTrack? {
        // 올릴 기기 표시가 없으면 잠그지 않아 여러 트랙을 한꺼번에 받을 수 있다(마이페이지 진도 뱃지, S15P31E102-768).
        if (deviceStages(trackId).isEmpty()) return api.track(trackId)
        return upload.withLock { uploadDeviceStages(trackId) }
    }

    private suspend fun uploadDeviceStages(trackId: String): LessonTrack? {
        val track = api.track(trackId) ?: return null
        val pending = deviceStages(trackId)
        if (pending.isEmpty()) return track
        val left = mutableSetOf<Int>()
        var uploaded = false
        val items = track.items.map { item ->
            if (item.seq !in pending || item.completed) return@map item
            if (api.setCompleted(item.lessonId, true) == true) {
                uploaded = true
                item.copy(completed = true)
            } else {
                left += item.seq
                item
            }
        }
        // 서버에 없는 단계 번호는 올릴 곳이 없어 버린다.
        saveDeviceStages(trackId, left)
        if (uploaded) _saves.update { it + 1 }
        return track.copy(items = items, completedCount = items.count { it.completed && !it.optional })
    }

    /** LRN-02 단계 상세. */
    suspend fun lesson(lessonId: String): LessonDetail? = api.lesson(lessonId)

    /** 완료 표시·해제를 서버에 남긴다. 저장했으면 true. */
    suspend fun setCompleted(lessonId: String, completed: Boolean): Boolean {
        val saved = api.setCompleted(lessonId, completed) == completed
        if (saved) _saves.update { it + 1 }
        return saved
    }

    /**
     * 연습 악기로 완료 표시한 단계 번호(마이페이지·연습 기록지 '완료한 단계', 선택 단계 포함). 학습 화면은 대금을 정악·산조로 나누므로
     * 정악·산조의 완료 단계는 별도로 센다. 받지 못하면 null.
     */
    suspend fun doneStages(instrument: Instrument): Set<Int>? {
        val tracks = instrument.learnIds.map { track(it) ?: return null }
        return tracks.flatMap { track -> track.items.filter { it.completed }.map { it.seq } }.filter { it in 1..STAGE_COUNT }.toSet()
    }

    /**
     * 가장 최근에 완료 표시한 기초 배우기의 악기와 그 악기의 완료 단계(마이페이지 진도 뱃지, S15P31E102-759).
     * 네 악기 진도를 모두 받아 서버 완료 시각이 가장 늦은 단계의 악기를 고른다. 완료 기록이 없으면 [fallback](연습 악기)이다.
     * 한 악기라도 받지 못하면 null이다.
     */
    suspend fun recentProgress(fallback: Instrument): RecentLearning? = recentProgress { fallback }

    /**
     * [recentProgress]와 같되, 연습 악기는 완료 기록이 하나도 없을 때만 [fallback]으로 묻는다(내 정보를 받기 전에 진도부터 받는다).
     * 트랙은 한꺼번에 받는다. [fallback]이 null이면 null이다.
     */
    suspend fun recentProgress(fallback: suspend () -> Instrument?): RecentLearning? {
        val ids = Instrument.entries.flatMap { it.learnIds }.distinct()
        val loaded = coroutineScope { ids.map { id -> async { id to track(id) } }.awaitAll() }.toMap()
        if (loaded.values.any { it == null }) return null
        val tracks = Instrument.entries.associateWith { instrument -> instrument.learnIds.map { loaded.getValue(it)!! } }
        val latest = tracks.mapNotNull { (instrument, list) ->
            list.flatMap { it.items }.mapNotNull { item -> item.completedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } }
                .maxOrNull()?.let { instrument to it }
        }.maxByOrNull { it.second }?.first
        val instrument = latest ?: fallback() ?: return null
        val stages = tracks.getValue(instrument).flatMap { track -> track.items.filter { it.completed }.map { it.seq } }
        return RecentLearning(instrument, stages.filter { it in 1..STAGE_COUNT }.toSet()).also(::saveRecent)
    }

    /**
     * 마지막으로 받은 [recentProgress] 결과(S15P31E102-768). 마이페이지가 서버 응답을 기다리지 않고 진도 뱃지를 바로 그릴 때 쓴다.
     * 이 기기에 회원마다 남기며, 받은 적이 없거나 로그인 정보가 없으면 null이다.
     */
    fun cachedRecent(): RecentLearning? {
        val value = recentKey()?.let(store::getString) ?: return null
        val instrument = Instrument.entries.firstOrNull { it.name == value.substringBefore(':') } ?: return null
        val stages = value.substringAfter(':', "").split(SEPARATOR).mapNotNull { it.toIntOrNull() }.filter { it in 1..STAGE_COUNT }.toSet()
        return RecentLearning(instrument, stages)
    }

    private fun saveRecent(recent: RecentLearning) {
        val key = recentKey() ?: return
        store.putString(key, recent.instrument.name + ":" + recent.stages.sorted().joinToString(SEPARATOR))
    }

    private fun recentKey(): String? = if (member == null) "learn_recent"
        else member.id?.let { "learn_recent_member_${it.length}_$it" }

    /** 이 기기에 남아 아직 올리지 못한 완료 표시. */
    internal fun deviceStages(trackId: String): Set<Int> =
        key(trackId)?.let(store::getString)
            ?.split(SEPARATOR)
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            .orEmpty()

    private fun saveDeviceStages(trackId: String, stages: Set<Int>) {
        val key = key(trackId) ?: return
        store.putString(key, stages.sorted().joinToString(SEPARATOR))
    }

    /**
     * [trackId] [stage]단계 안에서 앞에서부터 마친 할 일 박스 수(S15P31E102-749). 단계를 나갔다 돌아와도 이어 하도록 이 기기에만 남긴다.
     * 회원마다 따로 두고, 로그인 정보가 아직 없으면 0이다.
     */
    fun stepsDone(trackId: String, stage: Int): Int =
        stepsKey(trackId)?.let(store::getString)?.let { parseSteps(it)[stage] } ?: 0

    fun saveStepsDone(trackId: String, stage: Int, count: Int) {
        val key = stepsKey(trackId) ?: return
        val steps = store.getString(key)?.let(::parseSteps).orEmpty().toMutableMap()
        if (count > 0) steps[stage] = count else steps.remove(stage)
        store.putString(key, steps.entries.sortedBy { it.key }.joinToString(SEPARATOR) { "${it.key}:${it.value}" })
    }

    /** '처음부터 다시 하기': [trackId]의 단계 안 진행을 모두 지운다. */
    fun clearSteps(trackId: String) {
        val key = stepsKey(trackId) ?: return
        store.putString(key, "")
    }

    private fun parseSteps(value: String): Map<Int, Int> = value.split(SEPARATOR).mapNotNull { entry ->
        val (stage, count) = entry.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
        val s = stage.toIntOrNull() ?: return@mapNotNull null
        val c = count.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        s to c
    }.toMap()

    private fun stepsKey(trackId: String): String? = if (member == null) "learn_steps_$trackId"
        else member.id?.let { "learn_steps_member_${it.length}_${it}_$trackId" }

    // 회원을 가리지 않던 예전 공용 키는 보존하되 어느 회원에게도 올리지 않는다.
    private fun key(trackId: String): String? = if (member == null) "learn_done_$trackId"
        else member.id?.let { "learn_done_member_${it.length}_${it}_$trackId" }

    companion object {
        /** 기초부터 배우기 공개 단계 수. V37로 8·9단계를 숨겨 7이다(S15P31E102-657·658). 단계 그림도 7장이다. */
        const val STAGE_COUNT = 7

        private const val SEPARATOR = ","
    }
}

/** 최근 기초 배우기 악기와 그 악기의 완료 단계 번호. */
data class RecentLearning(val instrument: Instrument, val stages: Set<Int>)

/** 학습 화면 악기 식별자(`GuideInstrument.id`). */
private val Instrument.learnIds: List<String>
    get() = when (this) {
        Instrument.DaegeumJeongak -> listOf("daegeum-jeongak")
        Instrument.DaegeumSanjo -> listOf("daegeum-sanjo")
        Instrument.Sogeum -> listOf("sogeum")
        Instrument.Danso -> listOf("danso")
    }

package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.PracticeTimeSegment
import com.ssafy.eolssu.core.model.PracticeTimeSnapshot
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 연습 시간 구간 기록을 기기에 남기고 서버로 보낸다(`docs/specs/practice-time.md`의 '기기 저장과 전송').
 *
 * - 열린 구간은 30초마다 [saveOpen]으로 덮어쓴다. 네트워크는 쓰지 않는다.
 * - 닫힌 구간은 [close]로 대기열에 넣고 바로 보낸다. 같은 구간은 늘 같은 키·같은 값으로 다시 보낸다.
 * - 앱이 앞으로 올 때마다 [onAppStart]로 남은 열린 구간(갑작스러운 종료)을 마지막 저장 시각에 닫고 대기열을 보낸다.
 * - 로그아웃하면 [logOut]으로 한 번 보내 보고 남은 기록을 지운다. 탈퇴하면 [discard]로 보내지 않고 지운다.
 *
 * 기록마다 저장할 때 로그인한 회원([member])을 붙이고, 지금 회원과 같은 기록만 보낸다. 로그아웃을 거치지 않고 회원이
 * 바뀌어도(세션 만료 뒤 다른 회원 로그인, 앱 재실행) 다른 회원의 기록은 보내지 않고 지운다. 지금 회원을 모르면 보내지 않고 남겨 둔다.
 * 측정기(`PracticeTimeMeter`)는 회원을 모르고, 회원은 여기서만 붙인다.
 *
 * [api]가 없으면(HTTP 구현 전) 보내지 않고 쌓아 둔다. 전송은 화면이 사라져도 이어지도록 앱 전체의 [scope]에서 한다.
 */
class PracticeTimeQueue(
    private val store: KeyValueStore,
    private val api: ScoreViewApi?,
    private val member: SignedInMember,
    private val scope: CoroutineScope,
    private val logError: (String) -> Unit = { println(it) },
) {
    private val sending = Mutex()

    /** 보내지 못한 닫힌 구간. 넣은 순서대로다. */
    val pending: List<PracticeTimeSegment> get() = queued.map { it.segment }

    private val queued: List<Queued> get() = store.getString(QUEUE_KEY).orEmpty().lines().mapNotNull(::decodeQueued)

    /** 열린 구간의 상태를 덮어쓴다. */
    fun saveOpen(snapshot: PracticeTimeSnapshot) = store.putString(OPEN_KEY, encodeOpen(memberId(), snapshot))

    /** 구간이 닫힘. 인정 시간이 짧아 버린 구간([segment]가 null)이면 열린 상태만 지운다. */
    fun close(segment: PracticeTimeSegment?) {
        if (segment != null) enqueue(Queued(memberId(), segment))
        store.putString(OPEN_KEY, "")
        sendInBackground()
    }

    /** 앱 시작·앞으로 돌아옴. 이때 남아 있는 열린 구간은 이전 실행이 갑자기 끝나 닫지 못한 것이다. */
    fun onAppStart() {
        closeLeftover()
        sendInBackground()
    }

    /** 로그아웃 직전. 한 번 보내 보고 남은 기록을 모두 지운 뒤 [then]을 부른다. */
    fun logOut(then: () -> Unit = {}) = scope.launch {
            closeLeftover()
            send()
            clear()
            then()
    }

    /** 탈퇴. 서버가 로그인 쿠키를 지워 더는 보낼 수 없으므로 남은 기록을 보내지 않고 지운다. 보내던 중이면 끝난 뒤 지운다. */
    fun discard() {
        scope.launch { sending.withLock { clear() } }
    }

    /**
     * 대기열을 앞에서부터 보낸다. 201·200은 저장됨이다. 401은 HTTP 클라이언트가 이미 토큰을 갱신해 한 번 더 보낸 결과라
     * (갱신 실패) 남겨 둔다. 그 밖의 4xx는 다시 보내도 결과가 같으므로 오류를 남기고 지운다. 응답이 없거나 5xx면 남겨 두고 멈춘다.
     * 지금 회원을 모르면 멈추고, 다른 회원의 기록은 오류를 남기고 지운다.
     */
    suspend fun send() = sending.withLock {
        val api = api ?: return@withLock
        for (item in queued) {
            val status = member.whileSignedIn { current ->
                when (current) {
                    null -> SIGNED_OUT
                    item.memberId -> api.post(item.segment)
                    else -> OTHER_MEMBER
                }
            }
            when (status) {
                SIGNED_OUT, null, 401 -> return@withLock
                OTHER_MEMBER -> {
                    logError("다른 회원의 연습 시간 기록이라 보내지 않고 지움: ${item.segment.key}")
                    remove(item.segment.key)
                }
                in 200..299 -> remove(item.segment.key)
                in 400..499 -> {
                    logError("연습 시간 기록을 보내지 못해 지움: $status ${item.segment.key}")
                    remove(item.segment.key)
                }
                else -> return@withLock
            }
        }
    }

    private fun sendInBackground() {
        if (api != null) scope.launch { send() }
    }

    /** 회원을 모를 때는 빈 값을 붙인다. 이런 기록은 어느 회원의 것도 아니어서 로그인한 회원이 생기면 지운다. */
    private fun memberId() = member.id.orEmpty()

    private fun closeLeftover() {
        val (memberId, leftover) = store.getString(OPEN_KEY)?.let(::decodeOpen) ?: return
        val segment = leftover.close()
        if (segment != null && pending.none { it.key == segment.key }) enqueue(Queued(memberId, segment))
        store.putString(OPEN_KEY, "")
    }

    private fun clear() {
        store.putString(OPEN_KEY, "")
        store.putString(QUEUE_KEY, "")
    }

    private fun enqueue(item: Queued) = save(queued + item)

    private fun remove(key: String) = save(queued.filterNot { it.segment.key == key })

    private fun save(items: List<Queued>) = store.putString(QUEUE_KEY, items.joinToString("\n", transform = ::encodeQueued))

    private companion object {
        const val OPEN_KEY = "practice_time.open"
        const val QUEUE_KEY = "practice_time.queue"

        // [send]에서 응답 코드 대신 쓰는 값. HTTP 응답 코드와 겹치지 않는다.
        const val SIGNED_OUT = -1
        const val OTHER_MEMBER = -2
    }
}

/** 대기열의 구간 하나와 그 구간을 잰 회원. */
private class Queued(val memberId: String, val segment: PracticeTimeSegment)

/*
 * 저장 형식: 한 줄에 구간 하나, 칸은 탭으로 나눈다. 첫 칸은 잰 회원 ID다. 칸 안의 `%`·탭·줄바꿈은 `%25`·`%09`·`%0A`로 바꾼다.
 * 칸 수나 숫자가 맞지 않는 줄(회원 칸이 없는 이전 형식 포함)은 읽지 않는다.
 */

private fun encodeQueued(q: Queued): String = q.segment.let { s ->
    fields(q.memberId, s.key, s.songId, s.startedAtMillis, s.endedAtMillis, s.visibleSeconds, s.countedSeconds)
}

private fun decodeQueued(line: String): Queued? {
    val f = split(line, 7) ?: return null
    val segment = PracticeTimeSegment(
        key = f[1],
        songId = f[2],
        startedAtMillis = f[3].toLongOrNull() ?: return null,
        endedAtMillis = f[4].toLongOrNull() ?: return null,
        visibleSeconds = f[5].toIntOrNull() ?: return null,
        countedSeconds = f[6].toIntOrNull() ?: return null,
    )
    return Queued(f[0], segment)
}

private fun encodeOpen(memberId: String, s: PracticeTimeSnapshot): String =
    fields(memberId, s.key, s.songId, s.startedAtMillis, s.visibleMillis, s.countedMillis, s.savedAtMillis)

private fun decodeOpen(line: String): Pair<String, PracticeTimeSnapshot>? {
    val f = split(line, 7) ?: return null
    val snapshot = PracticeTimeSnapshot(
        key = f[1],
        songId = f[2],
        startedAtMillis = f[3].toLongOrNull() ?: return null,
        visibleMillis = f[4].toLongOrNull() ?: return null,
        countedMillis = f[5].toLongOrNull() ?: return null,
        savedAtMillis = f[6].toLongOrNull() ?: return null,
    )
    return f[0] to snapshot
}

private fun fields(vararg values: Any): String = values.joinToString("\t") {
    it.toString().replace("%", "%25").replace("\t", "%09").replace("\n", "%0A")
}

private fun split(line: String, count: Int): List<String>? {
    val parts = line.split('\t')
    if (parts.size != count) return null
    return parts.map { it.replace("%0A", "\n").replace("%09", "\t").replace("%25", "%") }
}

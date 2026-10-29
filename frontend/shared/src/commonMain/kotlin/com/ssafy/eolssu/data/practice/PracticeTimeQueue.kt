package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.PracticeTimeSegment
import com.ssafy.eolssu.core.model.PracticeTimeSnapshot
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
 * - 로그아웃하면 [logOut]으로 한 번 보내 보고 남은 기록을 지운다. 그래서 대기열에는 지금 로그인한 회원의 기록만 있다.
 *
 * [api]가 없으면(HTTP 구현 전) 보내지 않고 쌓아 둔다. 전송은 화면이 사라져도 이어지도록 앱 전체의 [scope]에서 한다.
 */
class PracticeTimeQueue(
    private val store: KeyValueStore,
    private val api: ScoreViewApi?,
    private val scope: CoroutineScope,
    private val logError: (String) -> Unit = { println(it) },
) {
    private val sending = Mutex()

    /** 보내지 못한 닫힌 구간. 넣은 순서대로다. */
    val pending: List<PracticeTimeSegment> get() = store.getString(QUEUE_KEY).orEmpty().lines().mapNotNull(::decodeSegment)

    /** 열린 구간의 상태를 덮어쓴다. */
    fun saveOpen(snapshot: PracticeTimeSnapshot) = store.putString(OPEN_KEY, encodeSnapshot(snapshot))

    /** 구간이 닫힘. 인정 시간이 짧아 버린 구간([segment]가 null)이면 열린 상태만 지운다. */
    fun close(segment: PracticeTimeSegment?) {
        if (segment != null) enqueue(segment)
        store.putString(OPEN_KEY, "")
        sendInBackground()
    }

    /** 앱 시작·앞으로 돌아옴. 이때 남아 있는 열린 구간은 이전 실행이 갑자기 끝나 닫지 못한 것이다. */
    fun onAppStart() {
        closeLeftover()
        sendInBackground()
    }

    /** 로그아웃 직전. 한 번 보내 보고 남은 기록을 모두 지운 뒤 [then]을 부른다. */
    fun logOut(then: () -> Unit = {}) {
        scope.launch {
            closeLeftover()
            send()
            store.putString(QUEUE_KEY, "")
            then()
        }
    }

    /**
     * 대기열을 앞에서부터 보낸다. 201·200은 저장됨, 401은 토큰을 갱신해 한 번 더 보낸다.
     * 그 밖의 4xx는 다시 보내도 결과가 같으므로 오류를 남기고 지운다. 응답이 없거나 5xx·갱신 실패면 남겨 두고 멈춘다.
     */
    suspend fun send() = sending.withLock {
        val api = api ?: return@withLock
        for (segment in pending) {
            var status = api.post(segment)
            if (status == 401 && api.refreshToken()) status = api.post(segment)
            when (status) {
                null, 401 -> return@withLock
                in 200..299 -> remove(segment.key)
                in 400..499 -> {
                    logError("연습 시간 기록을 보내지 못해 지움: $status ${segment.key}")
                    remove(segment.key)
                }
                else -> return@withLock
            }
        }
    }

    private fun sendInBackground() {
        if (api != null) scope.launch { send() }
    }

    private fun closeLeftover() {
        val leftover = store.getString(OPEN_KEY)?.let(::decodeSnapshot) ?: return
        val segment = leftover.close()
        if (segment != null && pending.none { it.key == segment.key }) enqueue(segment)
        store.putString(OPEN_KEY, "")
    }

    private fun enqueue(segment: PracticeTimeSegment) = save(pending + segment)

    private fun remove(key: String) = save(pending.filterNot { it.key == key })

    private fun save(segments: List<PracticeTimeSegment>) =
        store.putString(QUEUE_KEY, segments.joinToString("\n", transform = ::encodeSegment))

    private companion object {
        const val OPEN_KEY = "practice_time.open"
        const val QUEUE_KEY = "practice_time.queue"
    }
}

/*
 * 저장 형식: 한 줄에 구간 하나, 칸은 탭으로 나눈다. 칸 안의 `%`·탭·줄바꿈은 `%25`·`%09`·`%0A`로 바꾼다.
 * 칸 수나 숫자가 맞지 않는 줄은 읽지 않는다.
 */

private fun encodeSegment(s: PracticeTimeSegment): String =
    fields(s.key, s.songId, s.startedAtMillis, s.endedAtMillis, s.visibleSeconds, s.countedSeconds)

private fun decodeSegment(line: String): PracticeTimeSegment? {
    val f = split(line, 6) ?: return null
    return PracticeTimeSegment(
        key = f[0],
        songId = f[1],
        startedAtMillis = f[2].toLongOrNull() ?: return null,
        endedAtMillis = f[3].toLongOrNull() ?: return null,
        visibleSeconds = f[4].toIntOrNull() ?: return null,
        countedSeconds = f[5].toIntOrNull() ?: return null,
    )
}

private fun encodeSnapshot(s: PracticeTimeSnapshot): String =
    fields(s.key, s.songId, s.startedAtMillis, s.visibleMillis, s.countedMillis, s.savedAtMillis)

private fun decodeSnapshot(line: String): PracticeTimeSnapshot? {
    val f = split(line, 6) ?: return null
    return PracticeTimeSnapshot(
        key = f[0],
        songId = f[1],
        startedAtMillis = f[2].toLongOrNull() ?: return null,
        visibleMillis = f[3].toLongOrNull() ?: return null,
        countedMillis = f[4].toLongOrNull() ?: return null,
        savedAtMillis = f[5].toLongOrNull() ?: return null,
    )
}

private fun fields(vararg values: Any): String = values.joinToString("\t") {
    it.toString().replace("%", "%25").replace("\t", "%09").replace("\n", "%0A")
}

private fun split(line: String, count: Int): List<String>? {
    val parts = line.split('\t')
    if (parts.size != count) return null
    return parts.map { it.replace("%0A", "\n").replace("%09", "\t").replace("%25", "%") }
}

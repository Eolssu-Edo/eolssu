package com.ssafy.eolssu.core.model

/**
 * 악보 화면을 본 구간 하나의 연습 시간. 서버 `score_views` 한 행이 된다(GRO-04).
 * 닫힌 구간은 다시 바뀌지 않으므로, 다시 보낼 때도 같은 [key]와 같은 값으로 보낸다.
 *
 * @property key 구간 키(UUID). 서버의 `Idempotency-Key`.
 * @property userId 측정한 회원. 다른 회원으로 로그인한 상태에서는 보내지 않는다.
 * @property startedAtMillis 구간 시작(기기 시각, 유닉스 ms).
 * @property endedAtMillis 구간 끝. 시작 시각에 보인 시간을 더한 값이라 기기 시계가 바뀌어도 보인 시간보다 짧아지지 않는다.
 * @property visibleSeconds 화면이 앞에 보인 초(내림).
 * @property countedSeconds 보인 시간에서 자리 비움을 뺀 인정 초(내림). 연습 시간으로 센다.
 */
data class PracticeTimeSegment(
    val key: String,
    val userId: String,
    val songId: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val visibleSeconds: Int,
    val countedSeconds: Int,
) {
    companion object {
        /** 인정 시간이 이보다 짧은 구간은 보내지 않고 버린다. */
        const val MIN_COUNTED_SECONDS = 10
    }
}

/**
 * 아직 닫히지 않은 구간의 상태. 30초마다 기기에 덮어써 두고, 앱이 갑자기 종료되면
 * 다음 실행 때 [close]로 마지막 저장 시각에 닫아 보낸다.
 *
 * @property savedAtMillis 이 상태를 만든 시각(기기 시각, 유닉스 ms). 닫으면 구간 끝이 된다.
 */
data class PracticeTimeSnapshot(
    val key: String,
    val userId: String,
    val songId: String,
    val startedAtMillis: Long,
    val visibleMillis: Long,
    val countedMillis: Long,
    val savedAtMillis: Long,
) {
    /** 저장한 값으로 닫은 구간. 인정 시간이 [PracticeTimeSegment.MIN_COUNTED_SECONDS]초 미만이면 null(버림). */
    fun close(): PracticeTimeSegment? {
        val counted = (countedMillis / 1000).toInt()
        if (counted < PracticeTimeSegment.MIN_COUNTED_SECONDS) return null
        return PracticeTimeSegment(
            key = key,
            userId = userId,
            songId = songId,
            startedAtMillis = startedAtMillis,
            endedAtMillis = savedAtMillis,
            visibleSeconds = (visibleMillis / 1000).toInt(),
            countedSeconds = counted,
        )
    }
}

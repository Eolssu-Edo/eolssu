package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.PracticeTimeSegment
import com.ssafy.eolssu.core.model.PracticeTimeSnapshot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/** 입력이 없어도 연습으로 인정하는, 돌아가는 동안의 활동. */
enum class ContinuousActivity { PLAYBACK, RECORDING, METRONOME }

/**
 * 악보 화면의 연습 시간을 잰다(`docs/specs/practice-time.md`의 '측정 규칙').
 * 화면 보임·가려짐, 활동, 시간 경과만 받아 구간과 인정 시간을 정한다.
 * 생명주기·입력·재생 상태를 넘기는 화면 연결과 기기 저장·전송은 부르는 쪽이 맡는다.
 *
 * - 화면이 보이면 [show]로 새 구간을 열고, 가려지면 [hide]로 닫는다.
 * - [ContinuousActivity]가 하나라도 돌아가는 동안은 계속 인정한다.
 * - 그 밖에는 마지막 활동 뒤 [IdleLimit]까지만 인정한다.
 *
 * @param now 기기 시각(유닉스 ms). 구간 시작 시각에만 쓰고, 길이는 [timeSource]로 잰다.
 */
class PracticeTimeMeter(
    private val now: () -> Long,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val newKey: () -> String = { Uuid.random().toString() },
) {
    private var open: OpenSegment? = null
    private val running = mutableSetOf<ContinuousActivity>()

    val isOpen: Boolean get() = open != null

    /** 새 구간을 연다. 이미 열려 있으면 그대로 둔다. 화면이 보이기 시작한 때를 활동으로 본다. */
    fun show(songId: String) {
        if (open != null) return
        open = OpenSegment(newKey(), songId, now(), timeSource.markNow())
    }

    /** 구간을 닫는다. 인정 시간이 10초 미만이거나 열린 구간이 없으면 null. */
    fun hide(): PracticeTimeSegment? {
        val closed = snapshot()?.close()
        open = null
        return closed
    }

    /** 터치·스크롤·키 입력. 자리 비움 시간을 처음부터 다시 센다. */
    fun onActivity() {
        val segment = open ?: return
        segment.lastActivityAt = segment.settle()
    }

    /** 재생·녹음·메트로놈의 시작과 정지. 멈춘 때부터 자리 비움 시간을 센다. */
    fun setRunning(activity: ContinuousActivity, isRunning: Boolean) {
        val at = open?.settle()
        val changed = if (isRunning) running.add(activity) else running.remove(activity)
        if (changed && at != null) open?.lastActivityAt = at
    }

    /** 기기에 덮어써 둘 열린 구간의 상태. 열린 구간이 없으면 null. */
    fun snapshot(): PracticeTimeSnapshot? {
        val segment = open ?: return null
        val visibleMillis = segment.settle().inWholeMilliseconds
        return PracticeTimeSnapshot(
            key = segment.key,
            songId = segment.songId,
            startedAtMillis = segment.startedAtMillis,
            visibleMillis = visibleMillis,
            countedMillis = segment.counted.inWholeMilliseconds,
            savedAtMillis = segment.startedAtMillis + visibleMillis,
        )
    }

    /** 구간 시작부터 [settledAt]까지 인정한 시간을 [counted]에 쌓아 두고, 그 뒤만 새로 계산한다. */
    private inner class OpenSegment(
        val key: String,
        val songId: String,
        val startedAtMillis: Long,
        val start: TimeMark,
    ) {
        var counted = Duration.ZERO
        var settledAt = Duration.ZERO
        var lastActivityAt = Duration.ZERO

        /** 지금까지의 인정 시간을 정산하고 구간 시작부터 지난 시간을 돌려준다. */
        fun settle(): Duration {
            val elapsed = start.elapsedNow()
            val countUntil = if (running.isNotEmpty()) elapsed else minOf(elapsed, lastActivityAt + IdleLimit)
            if (countUntil > settledAt) counted += countUntil - settledAt
            settledAt = elapsed
            return elapsed
        }
    }

    companion object {
        /** 마지막 활동 뒤 이만큼 지나면 인정 시간을 멈춘다. */
        val IdleLimit = 10.minutes

        /** 열린 구간을 기기에 덮어쓰는 간격. */
        val SnapshotInterval = 30.seconds
    }
}

package com.ssafy.eolssu.feature.guide

import com.ssafy.eolssu.core.diagnostics.*
import com.ssafy.eolssu.core.audio.SoundPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 이 수업의 예시에서만 한 칸을 한 박(1초)으로 센다. 음원 생성기도 같은 시간표를 사용한다. */
internal const val ReadingBeatMs = 1_000L

internal enum class ReadingAudioClip(val file: String, val durationMs: Long) {
    Each("length-each", ReadingBeatMs * 2),
    Split("length-split", ReadingBeatMs * 2),
    Hold("length-hold", ReadingBeatMs * 2),
    Rest("length-rest", ReadingBeatMs * 2),
    LowHwang("pitch-low", 1_500),
    Hwang("pitch-base", 1_500),
    HighHwang("pitch-high", 1_500),
    ;

    val path: String get() = "files/reading/$file.m4a"
}

internal val ReadingLengthCells = listOf(
    listOf(listOf("林"), listOf("南")),
    listOf(listOf("林", "南"), listOf("太")),
    listOf(listOf("林"), listOf("ㅡ")),
    listOf(listOf("林"), listOf("△")),
)
internal val ReadingLengthClips = listOf(ReadingAudioClip.Each, ReadingAudioClip.Split, ReadingAudioClip.Hold, ReadingAudioClip.Rest)
internal val ReadingPitchClips = listOf(ReadingAudioClip.LowHwang, ReadingAudioClip.Hwang, ReadingAudioClip.HighHwang)

/** 준비 중에는 글자를 미리 켜지 않고, 실제 소리가 시작됐을 때만 시간표를 시작한다. */
internal data class ReadingAudioState(
    val clip: ReadingAudioClip? = null,
    val positionMs: Long = 0,
    val loading: Boolean = false,
    val playing: Boolean = false,
    val failed: Boolean = false,
) {
    fun active(clip: ReadingAudioClip): Boolean = this.clip == clip && (loading || playing)
}

/** 반 박 경계·이음줄·쉼표도 같은 시간표로 밝힌다. 쉼표가 밝아져도 음은 쉬고 박 소리만 난다. */
internal fun readingActiveNote(cells: List<List<String>>, positionMs: Long): Pair<Int, Int>? {
    if (positionMs < 0 || positionMs >= cells.size * ReadingBeatMs) return null
    val cell = (positionMs / ReadingBeatMs).toInt()
    val note = ((positionMs % ReadingBeatMs) * cells[cell].size / ReadingBeatMs).toInt()
    return cell to note
}

/** 화면 안에서 한 예시만 재생한다. 중지/새 선택/화면 이탈은 준비 작업과 강조 타이머도 함께 취소한다. */
internal class ReadingAudioSession(
    private val player: SoundPlayer,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
) {
    private val mutable = MutableStateFlow(ReadingAudioState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var qaInteraction = QaInteraction(0, true)

    fun toggle(clip: ReadingAudioClip) {
        if (mutable.value.active(clip)) { stop(); return }
        stop()
        qaInteraction = UserActionLog.capture()
        UserActionLog.event(QaKind.AUDIO_REQUESTED, QaTarget.READING_AUDIO, interaction = qaInteraction,
            content = when (clip) {
                ReadingAudioClip.Each -> QaContent.EACH; ReadingAudioClip.Split -> QaContent.SPLIT
                ReadingAudioClip.Hold -> QaContent.HOLD; ReadingAudioClip.Rest -> QaContent.REST
                ReadingAudioClip.LowHwang -> QaContent.LOW_HWANG; ReadingAudioClip.Hwang -> QaContent.HWANG
                ReadingAudioClip.HighHwang -> QaContent.HIGH_HWANG
            })
        mutable.value = ReadingAudioState(clip = clip, loading = true)
        job = scope.launch {
            val started = try { player.play(clip.path) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { false }
            currentCoroutineContext().ensureActive()
            if (!started) {
                player.stop()
                UserActionLog.event(QaKind.AUDIO_STATE, QaTarget.READING_AUDIO, QaOutcome.FAILED, qaInteraction)
                mutable.value = ReadingAudioState(clip = clip, failed = true)
                return@launch
            }
            val start = nowMs()
            mutable.value = ReadingAudioState(clip = clip, playing = true)
            while (true) {
                delay(16)
                val elapsed = (nowMs() - start).coerceAtLeast(0)
                if (elapsed >= clip.durationMs) break
                mutable.value = ReadingAudioState(clip = clip, positionMs = elapsed, playing = true)
            }
            player.stop()
            mutable.value = ReadingAudioState()
        }
    }

    fun stop() {
        if (mutable.value.loading || mutable.value.playing) UserActionLog.event(QaKind.AUDIO_STATE, QaTarget.READING_AUDIO, QaOutcome.STOPPED, qaInteraction)
        job?.cancel()
        job = null
        player.stop()
        mutable.value = ReadingAudioState()
    }
}

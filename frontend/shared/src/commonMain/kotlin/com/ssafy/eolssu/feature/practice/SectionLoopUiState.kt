package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.data.practice.PracticeSettings
import com.ssafy.eolssu.data.practice.SessionSection
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song

/**
 * PRACTICE-03 구간 반복 상태. 연습 중에는 점수를 매기지 않고, 고른 구간을 [bpm]으로 [repeats]번(null이면 계속) 따라간다.
 * 반복에는 소리·녹음이 없다. ‘이 구간 재평가’를 누르면 3·2·1 뒤 구간을 한 번만 따라가며 녹음하고([recording]),
 * 마치면 녹음([take])을 분석에 넘긴다.
 *
 * @property cells 구간의 정간(연주 순서).
 * @property round 지금 반복 회차(1부터).
 * @property step 지금 정간의 [cells] 안 순서.
 * @property countdown 다시 시작 전 3·2·1에서 보일 숫자. null이면 세지 않는다.
 * @property resumed 처음 시작이 아니라 멈췄다가 이어 가는 카운트다운인지(안내 문구가 다르다).
 * @property recording 재평가 녹음 중(카운트다운 포함). 한 번만 따라가고 반복하지 않는다.
 * @property take 마친 재평가 녹음. 화면이 분석으로 넘긴 뒤 지운다.
 * @property notice 재평가를 시작하지 못했거나 녹음을 버린 이유. 화면이 안내한 뒤 지운다.
 * @property settings 연습 화면과 함께 쓰는 악보 설정(크게 보기·한글·계이름). 구간 반복 악보도 같은 모양으로 그린다.
 * @property micLevel 재평가 녹음 중 마이크 입력 크기(0~1). 연주 평가 하단의 입력 표시에 쓴다.
 * @property panel·draft 연습 화면과 같은 설정 시트(PANEL-00~05)와 그 안에서 고치는 값(S15P31E102-776).
 */
data class SectionLoopUiState(
    val loading: Boolean = true,
    val song: Song? = null,
    val section: ScoreSection? = null,
    val cells: List<ScorePosition> = emptyList(),
    val bpm: Int = 60,
    val repeats: Int? = null,
    val round: Int = 1,
    val step: Int = 0,
    val running: Boolean = false,
    val countdown: Int? = null,
    val resumed: Boolean = false,
    val finished: Boolean = false,
    val recording: Boolean = false,
    /** 재평가 녹음 중 ‘연주 중단’을 눌러 녹음·진행을 멈추고 ‘연주를 마칠까요?’를 묻는 중. */
    val abortAsked: Boolean = false,
    val take: SectionTake? = null,
    val notice: SectionLoopNotice? = null,
    val settings: PracticeSettings = PracticeSettings(),
    val micLevel: Float = 0f,
    val panel: PracticePanel? = null,
    val draft: PracticeDraft = PracticeDraft(),
    val jangdanStatus: JangdanStatus = JangdanStatus.Idle,
    val jangdanSupported: Boolean = false,
) {
    val current: ScorePosition? get() = cells.getOrNull(step)

    /** 정간 하나의 길이(ms). */
    val cellMillis: Double get() = song?.cellDurationMs(bpm) ?: 0.0

    /** 이번 회차에서 흐른 시간과 한 회차 길이(초). */
    val elapsedSeconds: Double get() = step * cellMillis / 1000
    val roundSeconds: Double get() = cells.size * cellMillis / 1000

    /** 이번 회차 진행률(0~100). 마친 뒤에는 100. 기본 평가 화면의 ‘0:20 / 0:48 · 42%’와 같은 자리에 쓴다. */
    val roundPercent: Int
        get() = when {
            cells.isEmpty() -> 0
            finished -> 100
            else -> step * 100 / cells.size
        }

    /** 회차마다의 진행 막대 비율(0~1). ‘계속’이면 이번 회차 하나만 보인다. */
    val roundProgress: List<Float>
        get() {
            val fraction = if (cells.isEmpty()) 0f else if (finished) 1f else step.toFloat() / cells.size
            val total = repeats ?: return listOf(fraction)
            return List(total) { i ->
                when {
                    i + 1 < round -> 1f
                    i + 1 == round -> fraction
                    else -> 0f
                }
            }
        }

    /** 지금 정간의 연주 순서(첫 음). 구간 밖이거나 없으면 null. */
    val currentIndex: Int?
        get() {
            val c = current ?: return null
            return song?.timeline?.firstOrNull { it.column == c.column && it.row == c.row }?.index
        }

    /** 지금 정간이 속한 대강 번호(장구 박 표시). 장단이 없거나 연주 중이 아니면 -1. */
    val beatIndex: Int
        get() {
            val jangdan = song?.displayJangdan ?: return -1
            val c = current ?: return -1
            if (!(running || recording) || countdown != null || jangdan.gak <= 0) return -1
            return jangdan.daegangIndexOfRow(c.row % jangdan.gak)
        }

    /**
     * 연습 화면 부품(설정 시트·운지법 페이지)에 넘길 연습 상태. 속도는 설정의 ‘속도’로 바꾸고 녹음 중에는 잠긴다.
     * [evaluation]이면 연주 평가처럼 쉬운 표기·운지법을 제목 옆 스위치로 두고 시트에서는 뺀다.
     */
    fun asPracticeState(evaluation: Boolean): PracticeUiState {
        val index = currentIndex ?: song?.timeline?.firstOrNull { section?.contains(it.column, it.row) == true }?.index ?: 0
        // 설정 시트·상태 패널이 지금 속도를 보이도록 곡 속도에 [bpm]을 둔다.
        val sectionSettings = song?.let { settings.copy(bpmBySong = settings.bpmBySong + (it.id to bpm)) } ?: settings
        return PracticeUiState(
            song = song,
            solo = !evaluation,
            direct = evaluation,
            settings = sectionSettings,
            mode = if (recording) PracticeMode.Recording else PracticeMode.Ready,
            panel = panel,
            draft = draft,
            jangdanStatus = jangdanStatus,
            jangdanSupported = jangdanSupported,
            position = index,
            selected = index,
            micLevel = micLevel,
        )
    }

    /** 운지법 페이지용 연습 상태: 지금 정간을 고른 음으로 두어 그 음이 든 페이지를 보인다. */
    fun asFingeringState(): PracticeUiState {
        val index = currentIndex ?: 0
        return asPracticeState(evaluation = false).copy(mode = PracticeMode.Ready, selectedCell = current, selected = index, position = index)
    }

    /** 앞뒤 각(흐리게 보일 맥락). */
    val contextColumns: Set<Int>
        get() {
            val s = section ?: return emptySet()
            val last = song?.score?.columns?.lastIndex ?: return emptySet()
            return setOfNotNull((s.start.column - 1).takeIf { it >= 0 }, (s.end.column + 1).takeIf { it <= last })
        }

    /** 화면에 그릴 각: 구간 각과 앞뒤 한 각씩. */
    val shownColumns: IntRange?
        get() {
            val s = section ?: return null
            val last = song?.score?.columns?.lastIndex ?: return null
            return (s.start.column - 1).coerceAtLeast(0)..(s.end.column + 1).coerceAtMost(last)
        }
}

/**
 * 한 정간 나아간다. 구간 끝이면 다음 회차의 처음으로 돌아가고, 정한 횟수를 마치면 멈추고 [SectionLoopUiState.finished]가 된다.
 */
fun SectionLoopUiState.advance(): SectionLoopUiState {
    // 재평가 녹음은 한 번만 따라간다.
    val total = if (recording) 1 else repeats
    return when {
        cells.isEmpty() || finished -> this
        step + 1 < cells.size -> copy(step = step + 1)
        total == null || round < total -> copy(round = round + 1, step = 0)
        else -> copy(running = false, finished = true)
    }
}

/** 재평가 녹음을 시작한다: 구간 처음부터 3·2·1을 센 뒤 한 번 따라간다. */
fun SectionLoopUiState.beginRecording(): SectionLoopUiState =
    copy(recording = true, abortAsked = false, running = false, countdown = CountdownBeats, resumed = false, finished = false, round = 1, step = 0, take = null, notice = null)

/** 재평가 녹음을 그만두거나 버렸다. 반복은 멈춘 채로 처음부터 다시 할 수 있다. [notice]가 있으면 안내한다. */
fun SectionLoopUiState.endRecording(notice: SectionLoopNotice? = null): SectionLoopUiState =
    copy(recording = false, abortAsked = false, running = false, countdown = null, finished = false, round = 1, step = 0, notice = notice)

/** 재평가 녹음 중 ‘연주 중단’: 그 자리에서 멈추고 확인 창을 띄운다. */
fun SectionLoopUiState.askAbort(): SectionLoopUiState = copy(running = false, abortAsked = true, micLevel = 0f)

/** ‘계속 연주’: 3·2·1 뒤 멈춘 자리부터 녹음을 잇는다. */
fun SectionLoopUiState.keepRecording(): SectionLoopUiState = copy(abortAsked = false, countdown = CountdownBeats, resumed = true)

/**
 * [song]에서 [section] 안의 율명 음 번호 범위(양 끝 포함, 구간 재평가 세션 PRA-01의 `startNoteIdx`·`endNoteIdx`).
 * 음 번호는 서버와 같이 악보의 율명 음만 연주 순서로 센다. 구간에 율명 음이 없으면 null.
 */
fun noteRange(song: Song, section: ScoreSection): IntRange? {
    val notes = song.timeline.filter { it.noteIndex != null && section.contains(it.column, it.row) }.mapNotNull { it.noteIndex }
    return if (notes.isEmpty()) null else notes.min()..notes.max()
}

/** 3·2·1 카운트다운을 시작한다. 반복을 마친 뒤라면 처음 회차부터 다시 한다. */
fun SectionLoopUiState.beginCountdown(): SectionLoopUiState {
    val restart = finished
    return copy(
        running = false,
        countdown = CountdownBeats,
        resumed = !restart && (round > 1 || step > 0),
        finished = false,
        round = if (restart) 1 else round,
        step = if (restart) 0 else step,
    )
}

/** 카운트다운 한 숫자(1초). 1 다음에는 막을 걷고 반복을 이어 간다. */
fun SectionLoopUiState.countdownTick(): SectionLoopUiState {
    val count = countdown ?: return this
    return if (count > 1) copy(countdown = count - 1) else copy(countdown = null, running = true)
}

fun SectionLoopUiState.pause(): SectionLoopUiState = copy(running = false, countdown = null)

/** 다시 시작할 때 세는 박 수. */
const val CountdownBeats = 3

/** 카운트다운 숫자 하나의 길이(ms). 구간 속도와 상관없이 1초씩 센다. */
const val CountdownTickMillis = 1_000L

/** 마친 재평가 녹음과 그 구간(음 번호, 바탕 세션). */
data class SectionTake(val take: RecordedTake, val section: SessionSection)

/** 재평가를 시작하지 못했거나 녹음을 버린 이유. */
enum class SectionLoopNotice {
    /** 결과를 만든 뒤 공개 악보가 바뀌었다. 최신 연습에서 다시 시작해야 한다. */
    ScoreChanged,

    /** 현재 공개 판본을 확인하지 못해 녹음을 시작하지 않는다. */
    ScoreUnavailable,

    /** 녹음기가 없는 플랫폼(웹). */
    NoRecorder,

    /** 구간에 연주할 율명 음이 없다(쉼표만). */
    NoNotes,

    /** 마이크를 허용하지 않았다. */
    MicDenied,

    /** 마이크를 다시 물을 수 없다. 설정에서 허용해야 한다. */
    MicBlocked,

    /** 녹음을 시작·저장하지 못했다(너무 큰 파일 포함). */
    RecordFailed,
}

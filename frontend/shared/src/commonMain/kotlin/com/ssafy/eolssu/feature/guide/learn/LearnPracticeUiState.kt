package com.ssafy.eolssu.feature.guide.learn

/*
 * 기초부터 배우기 3~7단계의 단계 연습(S15P31E102-391). 와이어프레임·연두 시안에 보드가 없어 단계 상세와 같은 조각으로 그린다.
 * 앱에는 실시간 음높이가 없어(녹음기는 소리 크기만 준다) 음높이를 묻지 않는 3단계 '첫 소리'만 소리 크기로 판정한다.
 * 4~7단계는 소리 크기만 보여 주고 스스로 확인해 완료로 표시한다. 음높이 판정은 S15P31E102-341이 생기면 잇는다.
 * 판정 수치는 단계 상세의 통과 기준과 같은 예시이며 팀 확정 사항이 아니다.
 */

/** 이 크기(0~1, -35dBFS쯤) 이상이면 소리가 난 것으로 본다. 숨소리·주변 소음보다 크고 관악기 소리보다 작게 잡은 예시. */
const val LEARN_SOUND_LEVEL = 0.3f

/** 3단계: 한 소리를 이만큼 끊김 없이 이어야 한 번으로 센다. */
const val FIRST_SOUND_HOLD_MS = 2_000.0

/** 3단계: 통과에 필요한 횟수. */
const val FIRST_SOUND_TARGET = 3

/** 소리가 이보다 짧게 끊기면 같은 소리로 본다(입김 흔들림·측정 간격). */
const val LEARN_SOUND_GAP_MS = 250.0

/** 듣기를 시작한 뒤 이 시간이 지나면 저절로 멈춘다. 녹음 파일이 계속 커지지 않게 한다. */
const val LEARN_LISTEN_LIMIT_MS = 3 * 60 * 1_000.0

/** 단계 연습 진행. [Passed]는 3단계를 판정으로 통과한 상태다. */
enum class LearnPracticePhase { Ready, Listening, Passed }

/**
 * 마이크 상태. [Unsupported]는 녹음기가 없는 플랫폼(현재 Web), [Denied]·[Blocked]는 OS 권한을 거부함
 * (Blocked는 다시 묻지 못해 설정에서 허용해야 함), [Failed]는 녹음을 시작하지 못함.
 */
enum class LearnMicStatus { Available, Unsupported, Denied, Blocked, Failed }

/** 듣는 동안 한 줄 안내. [Counted]는 한 번을 셌음, [TooShort]는 2초가 되기 전에 끊김. */
enum class LearnPracticeCue { Counted, TooShort }

/**
 * 단계 연습 상태.
 *
 * @property holdMs 지금 이어지는 소리의 길이. 소리가 [LEARN_SOUND_GAP_MS]보다 오래 끊기면 0으로 돌아간다.
 * @property gapMs 소리가 끊긴 뒤 지난 시간.
 * @property counted 지금 이어지는 소리를 이미 한 번으로 셌는지. 한 소리를 길게 불어도 한 번만 센다.
 * @property successes 3단계에서 센 횟수.
 * @property listenedMs 이번 듣기를 시작한 뒤 지난 시간.
 */
data class LearnPracticeUiState(
    val stage: Int,
    val mic: LearnMicStatus = LearnMicStatus.Available,
    val phase: LearnPracticePhase = LearnPracticePhase.Ready,
    val level: Float = 0f,
    val holdMs: Double = 0.0,
    val gapMs: Double = 0.0,
    val counted: Boolean = false,
    val successes: Int = 0,
    val listenedMs: Double = 0.0,
    val cue: LearnPracticeCue? = null,
) {
    /** 소리 크기로 통과를 판정하는 단계(3단계). 나머지는 스스로 확인한다. */
    val judgesBySound: Boolean get() = stage == 3

    val listening: Boolean get() = phase == LearnPracticePhase.Listening

    /** '듣기 시작'을 누를 수 있는지. 거부·실패 뒤에도 다시 묻거나 다시 켤 수 있다. 녹음기가 없거나 다시 묻지 못하면 false. */
    val canListen: Boolean get() = mic != LearnMicStatus.Unsupported && mic != LearnMicStatus.Blocked

    /** 지금 소리가 들리는지. */
    val sounding: Boolean get() = listening && level >= LEARN_SOUND_LEVEL

    /** 이번 소리의 2초 진행(0~1). 이미 센 소리는 가득 찬 채로 둔다. */
    val holdProgress: Float get() = if (counted) 1f else (holdMs / FIRST_SOUND_HOLD_MS).toFloat().coerceIn(0f, 1f)
}

/** 듣기 시작: 횟수는 이어 가고 지금 소리만 새로 잰다. 통과한 뒤 다시 하면 처음부터 센다. */
internal fun LearnPracticeUiState.startListening(): LearnPracticeUiState = copy(
    mic = LearnMicStatus.Available,
    phase = LearnPracticePhase.Listening,
    level = 0f,
    holdMs = 0.0,
    gapMs = 0.0,
    counted = false,
    successes = if (phase == LearnPracticePhase.Passed) 0 else successes,
    listenedMs = 0.0,
    cue = null,
)

/** 듣기를 멈춤. 센 횟수는 남긴다. */
internal fun LearnPracticeUiState.stopListening(): LearnPracticeUiState =
    if (!listening) this else copy(phase = LearnPracticePhase.Ready, level = 0f, holdMs = 0.0, gapMs = 0.0, counted = false)

/** 처음부터 다시: 센 횟수까지 지운다. */
internal fun LearnPracticeUiState.reset(): LearnPracticeUiState =
    stopListening().copy(phase = LearnPracticePhase.Ready, successes = 0, cue = null)

internal fun LearnPracticeUiState.micUnavailable(status: LearnMicStatus): LearnPracticeUiState =
    stopListening().copy(mic = status)

/**
 * 듣는 동안 [sinceMs]마다 들어온 소리 크기 [level]을 반영한다.
 * 3단계는 [FIRST_SOUND_HOLD_MS] 넘게 이어진 소리를 한 번으로 세고, [FIRST_SOUND_TARGET]번이 되면 통과한다.
 * [LEARN_SOUND_GAP_MS]보다 짧은 끊김은 같은 소리로 본다. 듣는 중이 아니면 그대로다.
 */
internal fun LearnPracticeUiState.recordLevel(level: Float, sinceMs: Double): LearnPracticeUiState {
    if (!listening) return this
    val listened = listenedMs + sinceMs
    if (level >= LEARN_SOUND_LEVEL) {
        val hold = holdMs + sinceMs
        val count = judgesBySound && !counted && hold >= FIRST_SOUND_HOLD_MS
        val next = copy(level = level, holdMs = hold, gapMs = 0.0, listenedMs = listened)
        if (!count) return next
        val total = successes + 1
        val passed = total >= FIRST_SOUND_TARGET
        return next.copy(
            level = if (passed) 0f else level,
            counted = true,
            successes = total,
            cue = LearnPracticeCue.Counted,
            phase = if (passed) LearnPracticePhase.Passed else phase,
        )
    }
    val gap = gapMs + sinceMs
    if (gap <= LEARN_SOUND_GAP_MS) return copy(level = level, gapMs = gap, listenedMs = listened)
    // 소리가 끊김: 세지 못한 소리였으면 짧았다고 알린다.
    val tooShort = judgesBySound && holdMs > 0.0 && !counted
    return copy(
        level = level,
        holdMs = 0.0,
        gapMs = gap,
        counted = false,
        listenedMs = listened,
        cue = if (tooShort) LearnPracticeCue.TooShort else cue,
    )
}

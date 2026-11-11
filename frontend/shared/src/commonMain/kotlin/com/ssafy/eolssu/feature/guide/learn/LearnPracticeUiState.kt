package com.ssafy.eolssu.feature.guide.learn

/*
 * 기초부터 배우기 3~7단계의 단계 연습(S15P31E102-391). 와이어프레임·연두 시안에 보드가 없어 단계 상세와 같은 조각으로 그린다.
 * 3단계 '첫 소리'는 소리 크기로, 4~6단계는 기기 음높이 관측으로 판정한다(S15P31E102-611, [LearnPitchTracker]).
 * 7단계 혀치기는 소리가 끊겼다 다시 나는 횟수('투')를 센다(S15P31E102-749).
 * 음높이 관측이 없는 기기(iOS·Web)·판정 기준을 받지 못했을 때는 소리 크기만 보여 주고 스스로 확인해 완료로 표시한다.
 * 판정 수치는 단계 상세의 통과 기준과 같은 예시이며 팀 확정 사항이 아니다.
 */

/** 이 크기(0~1, -35dBFS쯤) 이상이면 소리가 난 것으로 본다. 숨소리·주변 소음보다 크고 관악기 소리보다 작게 잡은 예시. */
const val LEARN_SOUND_LEVEL = 0.3f

/** 3단계: 한 소리를 이만큼 끊김 없이 이어야 한 번으로 센다. */
const val FIRST_SOUND_HOLD_MS = 1_000.0

/** 3단계: 통과에 필요한 횟수. */
const val FIRST_SOUND_TARGET = 1

/** 7단계: 같은 음을 '투'로 끊어 낼 횟수. */
const val TONGUE_TARGET = 4

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

/**
 * 듣는 동안 한 줄 안내. [Counted]는 한 번을 셌음, [TooShort]는 1초가 되기 전에 끊김.
 * 음높이 판정: [NoteHit] 맞는 음, [NoteMiss] 다른 음을 세고 넘어감, [NotPassed] 모든 음을 냈지만(또는 더 맞혀도) 통과 수에 못 미침.
 */
enum class LearnPracticeCue { Counted, TooShort, NoteHit, NoteMiss, NotPassed }

/**
 * 4~6단계 음높이 판정 진행. [notes]를 차례로 내고 [required]개 맞히면 통과한다.
 *
 * @property holdTargetMs 한 음을 이어 내야 하는 시간.
 * @property holdMs 지금 음을 맞게 이어 낸 시간(100ms 단위로 내림).
 * @property results 지금까지 낸 음의 판정(앞에서부터).
 */
data class LearnPitchProgress(
    val notes: List<String>,
    val required: Int,
    val holdTargetMs: Int,
    val index: Int = 0,
    val hits: Int = 0,
    val holdMs: Double = 0.0,
    val results: List<LearnNoteResult> = emptyList(),
    val finished: Boolean = false,
) {
    val holdProgress: Float get() = (holdMs / holdTargetMs).toFloat().coerceIn(0f, 1f)

    /** 지금 낼 음. 끝났으면 null. */
    val current: String? get() = notes.getOrNull(index).takeUnless { finished }

    internal fun restart() = copy(index = 0, hits = 0, holdMs = 0.0, results = emptyList(), finished = false)
}

/**
 * 단계 연습 상태.
 *
 * @property holdMs 지금 이어지는 소리의 길이. 소리가 [LEARN_SOUND_GAP_MS]보다 오래 끊기면 0으로 돌아간다.
 * @property gapMs 소리가 끊긴 뒤 지난 시간.
 * @property counted 지금 이어지는 소리를 이미 한 번으로 셌는지. 한 소리를 길게 불어도 한 번만 센다.
 * @property successes 3단계에서 센 횟수.
 * @property listenedMs 이번 듣기를 시작한 뒤 지난 시간.
 * @property tongues 7단계에서 센 '투' 횟수. 소리가 한 번이라도 기준 아래로 내려갔다가 다시 나면 하나로 센다.
 * @property quiet 마지막 측정이 기준 아래였는지(다음 소리를 새 '투'로 셀 수 있음).
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
    val pitch: LearnPitchProgress? = null,
    val tongues: Int = 0,
    val quiet: Boolean = true,
) {
    /** 소리 크기로 통과를 판정하는 단계(3단계). */
    val judgesBySound: Boolean get() = stage == 3

    /** 음높이로 통과를 판정하는지(4~6단계, 음높이 관측과 판정 기준이 있을 때). */
    val judgesByPitch: Boolean get() = pitch != null

    /** 혀치기 횟수로 통과를 판정하는 단계(7단계). */
    val judgesByTongue: Boolean get() = stage == 7

    /** 앱이 통과를 판정해 '스스로 확인했어요'가 필요 없는지. */
    val autoJudged: Boolean get() = judgesBySound || judgesByPitch || judgesByTongue

    val listening: Boolean get() = phase == LearnPracticePhase.Listening

    /** '듣기 시작'을 누를 수 있는지. 거부·실패 뒤에도 다시 묻거나 다시 켤 수 있다. 녹음기가 없거나 다시 묻지 못하면 false. */
    val canListen: Boolean get() = mic != LearnMicStatus.Unsupported && mic != LearnMicStatus.Blocked

    /** 지금 소리가 들리는지. */
    val sounding: Boolean get() = listening && level >= LEARN_SOUND_LEVEL

    /** 이번 소리의 1초 진행(0~1). 이미 센 소리는 가득 찬 채로 둔다. */
    val holdProgress: Float get() = if (counted) 1f else (holdMs / FIRST_SOUND_HOLD_MS).toFloat().coerceIn(0f, 1f)
}

/** 듣기 시작: 횟수는 이어 가고 지금 소리만 새로 잰다. 통과한 뒤나 음높이 판정이 끝난 뒤 다시 하면 처음부터 센다. */
internal fun LearnPracticeUiState.startListening(): LearnPracticeUiState = copy(
    pitch = pitch?.let { if (phase == LearnPracticePhase.Passed || it.finished) it.restart() else it.copy(holdMs = 0.0) },
    mic = LearnMicStatus.Available,
    phase = LearnPracticePhase.Listening,
    level = 0f,
    holdMs = 0.0,
    gapMs = 0.0,
    counted = false,
    successes = if (phase == LearnPracticePhase.Passed) 0 else successes,
    tongues = if (phase == LearnPracticePhase.Passed) 0 else tongues,
    quiet = true,
    listenedMs = 0.0,
    cue = null,
)

/** 듣기를 멈춤. 센 횟수는 남긴다. */
internal fun LearnPracticeUiState.stopListening(): LearnPracticeUiState =
    if (!listening) this else copy(phase = LearnPracticePhase.Ready, level = 0f, holdMs = 0.0, gapMs = 0.0, counted = false,
        pitch = pitch?.copy(holdMs = 0.0))

/** 처음부터 다시: 센 횟수까지 지운다. */
internal fun LearnPracticeUiState.reset(): LearnPracticeUiState =
    stopListening().copy(phase = LearnPracticePhase.Ready, successes = 0, tongues = 0, quiet = true, cue = null, pitch = pitch?.restart())

/** [to]번째 음부터 다시 낸다(박스 체크 해제, S15P31E102-749). 통과는 거두고 앞 음의 결과는 남긴다. 듣기는 그대로 둔다. */
internal fun LearnPracticeUiState.rewindTo(to: Int): LearnPracticeUiState {
    val p = pitch ?: return this
    val at = to.coerceIn(0, p.notes.size)
    return copy(
        phase = if (phase == LearnPracticePhase.Passed) LearnPracticePhase.Ready else phase,
        pitch = p.copy(index = at, hits = p.hits.coerceAtMost(at), holdMs = 0.0, results = p.results.take(at), finished = false),
    )
}

internal fun LearnPracticeUiState.micUnavailable(status: LearnMicStatus): LearnPracticeUiState =
    stopListening().copy(mic = status)

/**
 * 듣는 동안 [sinceMs]마다 들어온 소리 크기 [level]을 반영한다.
 * 3단계는 [FIRST_SOUND_HOLD_MS] 넘게 이어진 소리를 한 번으로 세고, [FIRST_SOUND_TARGET]번이 되면 통과한다.
 * [LEARN_SOUND_GAP_MS]보다 짧은 끊김은 같은 소리로 본다. 7단계는 기준 아래로 내려갔다 다시 난 소리를 '투' 하나로 세고
 * [TONGUE_TARGET]번이 되면 통과한다. 듣는 중이 아니면 그대로다.
 */
internal fun LearnPracticeUiState.recordLevel(level: Float, sinceMs: Double): LearnPracticeUiState {
    if (!listening) return this
    val listened = listenedMs + sinceMs
    if (judgesByTongue) {
        if (level < LEARN_SOUND_LEVEL) return copy(level = level, quiet = true, listenedMs = listened)
        if (!quiet) return copy(level = level, listenedMs = listened)
        val total = tongues + 1
        val passed = total >= TONGUE_TARGET
        return copy(
            level = if (passed) 0f else level,
            quiet = false,
            tongues = total,
            listenedMs = listened,
            phase = if (passed) LearnPracticePhase.Passed else phase,
        )
    }
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

/** 음높이 판정을 켠다. 듣는 중이 아닐 때만 바꾼다(듣는 도중 판정 방식이 바뀌지 않게). */
internal fun LearnPracticeUiState.withPitchPlan(plan: LearnPitchPlan): LearnPracticeUiState =
    if (listening || phase == LearnPracticePhase.Passed) this
    else copy(pitch = LearnPitchProgress(plan.notes, plan.required, plan.rule.holdMs))

/**
 * 음높이 판정기([LearnPitchTracker])의 진행을 반영한다. [result]는 이번 관측에서 난 판정이다.
 * 통과하면 [LearnPracticePhase.Passed], 통과하지 못한 채 끝나면 듣기를 멈추고 [LearnPracticeCue.NotPassed]를 둔다. 듣는 중이 아니면 그대로다.
 */
internal fun LearnPracticeUiState.recordPitch(
    index: Int,
    hits: Int,
    holdMs: Double,
    result: LearnNoteResult?,
    passed: Boolean,
    finished: Boolean,
): LearnPracticeUiState {
    val progress = pitch ?: return this
    if (!listening) return this
    val next = progress.copy(
        index = index,
        hits = hits,
        holdMs = kotlin.math.floor(holdMs / 100) * 100,
        results = if (result == null) progress.results else progress.results + result,
        finished = finished,
    )
    val cue = when (result) {
        LearnNoteResult.Hit -> LearnPracticeCue.NoteHit
        LearnNoteResult.Miss -> LearnPracticeCue.NoteMiss
        null -> cue
    }
    return when {
        passed -> copy(pitch = next.copy(holdMs = 0.0), phase = LearnPracticePhase.Passed, level = 0f, cue = cue)
        finished -> copy(pitch = next, cue = LearnPracticeCue.NotPassed).stopListening()
        else -> copy(pitch = next, cue = cue)
    }
}

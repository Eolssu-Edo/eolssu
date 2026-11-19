package com.ssafy.eolssu.feature.guide.learn

/*
 * 기초부터 배우기 3~7단계의 단계 연습(S15P31E102-391). 와이어프레임·연두 시안에 보드가 없어 단계 상세와 같은 조각으로 그린다.
 * 3단계 '첫 소리'는 처음 낸 높이를 이어 내는지(S15P31E102-1216, [learnFirstSoundPlan]), 4~6단계는 낼 음을 기기 음높이 관측으로 판정한다
 * (S15P31E102-611, [LearnPitchTracker]).
 * 7단계 혀치기는 소리가 끊겼다 다시 나는 횟수('투')를 센다(S15P31E102-749). 음높이 관측이 있으면 첫 '투'와 같은 음만 센다
 * (S15P31E102-845, [LearnTongueTracker]).
 * 음높이 관측이 없는 기기(iOS·Web)·판정 기준을 받지 못했을 때는 소리 크기만 보여 주고 스스로 확인해 완료로 표시한다.
 * 판정 수치는 단계 상세의 통과 기준과 같은 예시이며 팀 확정 사항이 아니다.
 */

/** 이 크기(0~1, -35dBFS쯤) 이상이면 소리가 난 것으로 본다. 숨소리·주변 소음보다 크고 관악기 소리보다 작게 잡은 예시. */
const val LEARN_SOUND_LEVEL = 0.3f

/** 3단계: 같은 높이를 이만큼 이어 내야 통과한다. '길게 내기'라 4단계 직접 불기와 같은 1초로 둔다(S15P31E102-1072·1216). */
const val FIRST_SOUND_HOLD_MS = 1_000

/** 통과 기준 문구에 넣을 초. 300ms는 "0.3", 1,000ms는 "1"처럼 끝의 0을 뺀다(1ms 단위까지). */
internal fun holdSecondsLabel(ms: Double): String {
    val whole = ms.toLong().coerceAtLeast(0)
    val fraction = (whole % 1_000).toString().padStart(3, '0').trimEnd('0')
    return if (fraction.isEmpty()) "${whole / 1_000}" else "${whole / 1_000}.$fraction"
}

/** 7단계: 같은 음을 '투'로 끊어 낼 횟수. */
const val TONGUE_TARGET = 4

/** 듣기를 시작한 뒤 이 시간이 지나면 저절로 멈춘다. 녹음 파일이 계속 커지지 않게 한다. */
const val LEARN_LISTEN_LIMIT_MS = 3 * 60 * 1_000.0

/** 단계 연습 진행. [Passed]는 앱 판정으로 통과한 상태다. */
enum class LearnPracticePhase { Ready, Listening, Passed }

/**
 * 마이크 상태. [Unsupported]는 녹음기가 없는 플랫폼(현재 Web), [Denied]·[Blocked]는 OS 권한을 거부함
 * (Blocked는 다시 묻지 못해 설정에서 허용해야 함), [Failed]는 녹음을 시작하지 못함.
 */
enum class LearnMicStatus { Available, Unsupported, Denied, Blocked, Failed }

/**
 * 듣는 동안 한 줄 안내(음높이 판정). [NoteHit] 맞는 음, [NoteMiss] 다른 음을 세고 넘어감, [NotPassed] 모든 음을 냈지만(또는 더 맞혀도) 통과 수에 못 미침.
 */
enum class LearnPracticeCue { NoteHit, NoteMiss, NotPassed }

/**
 * 3~6단계 음높이 판정 진행(3단계는 정해진 음 없이 한 번). [notes]를 차례로 내고 [required]개 맞히면 통과한다.
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
 * @property listenedMs 이번 듣기를 시작한 뒤 지난 시간.
 * @property tongues 7단계에서 센 '투' 횟수. 소리가 한 번이라도 기준 아래로 내려갔다가 다시 나면 하나로 센다.
 * @property quiet 마지막 측정이 기준 아래였는지(다음 소리를 새 '투'로 셀 수 있음).
 * @property tonguesByPitch 7단계에서 '투'를 바로 세지 않고 높이를 재어 첫 '투'와 같은 음만 센다([recordTongue]). 음높이 관측이 있을 때다.
 */
data class LearnPracticeUiState(
    val stage: Int,
    val mic: LearnMicStatus = LearnMicStatus.Available,
    val phase: LearnPracticePhase = LearnPracticePhase.Ready,
    val level: Float = 0f,
    val listenedMs: Double = 0.0,
    val cue: LearnPracticeCue? = null,
    val pitch: LearnPitchProgress? = null,
    val tongues: Int = 0,
    val quiet: Boolean = true,
    val tonguesByPitch: Boolean = false,
) {
    /** 음높이로 통과를 판정하는지(3단계는 음높이 관측이 있을 때, 4~6단계는 판정 기준까지 있을 때). 아니면 스스로 확인한다. */
    val judgesByPitch: Boolean get() = pitch != null

    /** 혀치기 횟수로 통과를 판정하는 단계(7단계). */
    val judgesByTongue: Boolean get() = stage == 7

    /** 앱이 통과를 판정해 '스스로 확인했어요'가 필요 없는지. */
    val autoJudged: Boolean get() = judgesByPitch || judgesByTongue

    val listening: Boolean get() = phase == LearnPracticePhase.Listening

    /** '듣기 시작'을 누를 수 있는지. 거부·실패 뒤에도 다시 묻거나 다시 켤 수 있다. 녹음기가 없거나 다시 묻지 못하면 false. */
    val canListen: Boolean get() = mic != LearnMicStatus.Unsupported && mic != LearnMicStatus.Blocked

    /** 지금 소리가 들리는지. */
    val sounding: Boolean get() = listening && level >= LEARN_SOUND_LEVEL
}

/** 듣기 시작: 횟수는 이어 가고 지금 소리만 새로 잰다. 통과한 뒤나 음높이 판정이 끝난 뒤 다시 하면 처음부터 센다. */
internal fun LearnPracticeUiState.startListening(): LearnPracticeUiState = copy(
    pitch = pitch?.let { if (phase == LearnPracticePhase.Passed || it.finished) it.restart() else it.copy(holdMs = 0.0) },
    mic = LearnMicStatus.Available,
    phase = LearnPracticePhase.Listening,
    level = 0f,
    tongues = if (phase == LearnPracticePhase.Passed) 0 else tongues,
    quiet = true,
    listenedMs = 0.0,
    cue = null,
)

/** 듣기를 멈춤. 센 횟수는 남긴다. */
internal fun LearnPracticeUiState.stopListening(): LearnPracticeUiState =
    if (!listening) this else copy(phase = LearnPracticePhase.Ready, level = 0f, pitch = pitch?.copy(holdMs = 0.0))

/** 처음부터 다시: 센 횟수까지 지운다. */
internal fun LearnPracticeUiState.reset(): LearnPracticeUiState =
    stopListening().copy(phase = LearnPracticePhase.Ready, tongues = 0, quiet = true, cue = null, pitch = pitch?.restart())

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
 * 듣는 동안 [sinceMs]마다 들어온 소리 크기 [level]을 반영한다. 7단계는 기준 아래로 내려갔다 다시 난 소리를 '투' 하나로 세고
 * [TONGUE_TARGET]번이 되면 통과한다. 다른 단계는 크기만 남긴다(3~6단계 통과는 음높이로 판정한다). 듣는 중이 아니면 그대로다.
 */
internal fun LearnPracticeUiState.recordLevel(level: Float, sinceMs: Double): LearnPracticeUiState {
    if (!listening) return this
    val listened = listenedMs + sinceMs
    if (!judgesByTongue) return copy(level = level, listenedMs = listened)
    if (level < LEARN_SOUND_LEVEL) return copy(level = level, quiet = true, listenedMs = listened)
    if (!quiet) return copy(level = level, listenedMs = listened)
    // 높이로 같은 음인지 볼 때는 새 '투'가 시작됐다고만 남기고, 높이가 정해지면 [recordTongue]로 센다.
    if (tonguesByPitch) return copy(level = level, quiet = false, listenedMs = listened)
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

/**
 * 7단계에서 높이로 센 '투' 횟수([LearnTongueTracker.count])를 반영한다. 다른 음으로 새로 세면 줄어들 수 있다.
 * [TONGUE_TARGET]번이 되면 통과한다. 듣는 중이 아니면 그대로다.
 */
internal fun LearnPracticeUiState.recordTongue(count: Int): LearnPracticeUiState {
    if (!listening || !judgesByTongue) return this
    val passed = count >= TONGUE_TARGET
    return copy(tongues = count, level = if (passed) 0f else level, phase = if (passed) LearnPracticePhase.Passed else phase)
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

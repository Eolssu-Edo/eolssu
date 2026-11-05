package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.ScorePerformance
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingeringYul
import com.ssafy.eolssu.core.model.displayUnitAt
import com.ssafy.eolssu.data.practice.PracticeSettings
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.TimeMark

/** 연습 화면 상태(와이어프레임 PRACTICE-01의 상태 A~D와 중단). */
enum class PracticeMode { Ready, Model, Rhythm, Countdown, Recording, Interrupted }

/** 상세 API가 없는 곡과 일시적인 조회·악보 검증 실패를 구분한다. */
enum class PracticeSongError { NotFound, Failed }

/** 아래에서 올라오는 보조 패널. */
enum class PracticePanel { Settings, Notation, Fingering, Jangdan, Tempo }

/**
 * 연습 화면 위에 뜨는 확인 창.
 * [EndNoInput]은 끝까지 마이크 입력이 없어 평가하지 않음,
 * [RecordFailed]는 녹음을 시작·저장하지 못함, [TooLarge]는 녹음 파일이 보낼 수 있는 크기(20MiB)를 넘음.
 * [PermDenied]는 OS 권한 창에서 허용하지 않음, [PermBlocked]는 ‘다시 묻지 않음’이라 기기 설정에서만 허용할 수 있음(보드 Dialog-PermDenied·PermBlocked).
 */
enum class PracticePopup { Permission, Abort, End, EndNoInput, Restart, Exit, Interrupted, RecordFailed, TooLarge, PermDenied, PermBlocked }

/** 연주 중 입력이 이 시간(ms)보다 오래 없으면 마이크를 확인하라고 안내한다(와이어프레임 `noInputMs > 1500`). */
const val NO_INPUT_HINT_MS = 1500.0

/** 연습 속도 5단계 — 곡 기준 속도에 대한 비율. 정해진 칸으로만 움직인다. */
enum class TempoStep(val ratio: Double) {
    Slowest(0.6), Slow(0.8), Normal(1.0), Fast(1.1), Fastest(1.2);

    fun bpm(baseBpm: Int): Int = (baseBpm * ratio).roundToInt()

    companion object {
        /** 저장된 BPM에 가장 가까운 단계. 같은 거리면 보통 쪽. */
        fun nearest(baseBpm: Int, bpm: Int): TempoStep =
            entries.fold(Normal) { best, step -> if (abs(step.bpm(baseBpm) - bpm) < abs(best.bpm(baseBpm) - bpm)) step else best }
    }
}

/** 운지법 패널의 이전·다음 음 순서(낮은 음 → 높은 음). 예시 곡과 서버 단소 곡에 쓰인 율명만 둔다. */
val FingeringOrder: List<Yul> = listOf(
    Yul.Hwangjong, Yul.Taeju, Yul.Jungnyeo, Yul.Imjong, Yul.Namnyeo, Yul.Muyeok, Yul.CheongHwangjong, Yul.CheongTaeju,
    Yul.CheongJungnyeo, Yul.CheongImjong, Yul.CheongMuyeok,
)

/**
 * 패널 표시 값. 사용자 변경은 즉시 [PracticeSettings]에 저장된다.
 *
 * @property note 운지법 패널이 보여 주는 율명. 고른 정간이 이음이면 앞 음, 쉼이면 [noteRest]가 true다.
 */
data class PracticeDraft(
    val showHangul: Boolean = false,
    val showSolfege: Boolean = false,
    val jangdanSound: Boolean = false,
    val beatVisual: Boolean = true,
    val step: TempoStep = TempoStep.Normal,
    val note: Yul = FingeringOrder.first(),
    val noteRest: Boolean = false,
    val previewing: Boolean = false,
    val previewBeat: Int = 0,
)

/**
 * PRACTICE-01 상태.
 *
 * @property position 현재 음의 연주 순서(재생·연주 중 위치).
 * @property eventStartedAt 현재 음이 시작된 시각. 멈춰 있으면 null이고 [pausedFraction]만큼 지난 상태다.
 * @property selected 준비 상태에서 고른 음의 연주 순서.
 * @property micLevel 연주 중 마이크 입력 크기(0~1).
 * @property recordingSupported 실제 녹음기가 등록되어 평가 연주를 시작할 수 있는지.
 * @property inputMeasured [micLevel]이 실제 입력 크기인지(현재 Android). 아니면 입력 없음 안내·팝업 없이 녹음을 보내
 *   서버 `NO_SOUND` 판정에 맡긴다.
 * @property hadInput 이번 연주에서 입력이 한 번이라도 있었는지.
 * @property noInputMs 마지막 입력 뒤로 입력 없이 녹음한 시간(ms). 일시정지한 동안은 세지 않는다.
 * @property follow 재생·연주 중 악보가 현재 위치를 따라가는지. 사용자가 악보를 끌면 꺼진다.
 * @property idleReminder 마지막 활동 뒤 9분 30초가 지나 "계속 연습 중인가요?"를 묻는 중.
 * @property mine 내 악보 연습(Practice-Mine). 혼자 연습과 같되 평가로 넘어가는 버튼이 없다.
 * @property sheetTitle 내 악보 제목(예: ‘아리랑 (내 편곡)’). 있으면 곡 제목 대신 보인다.
 * @property step 2차 '한 음씩 따라 불기' 상태(`PracticeStep.kt`).
 */
data class PracticeUiState(
    val song: Song? = null,
    val solo: Boolean = false,
    val mine: Boolean = false,
    val sheetTitle: String? = null,
    val step: PracticeStep = PracticeStep(),
    val settings: PracticeSettings = PracticeSettings(),
    val mode: PracticeMode = PracticeMode.Ready,
    val playing: Boolean = false,
    val modelAudioStatus: ModelAudioStatus = ModelAudioStatus.Idle,
    val jangdanStatus: JangdanStatus = JangdanStatus.Idle,
    val jangdanSupported: Boolean = false,
    val position: Int = 0,
    val elapsedMs: Double = 0.0,
    val eventStartedAt: TimeMark? = null,
    val pausedFraction: Float = 0f,
    val selected: Int = 0,
    val panel: PracticePanel? = null,
    val popup: PracticePopup? = null,
    val draft: PracticeDraft = PracticeDraft(),
    val count: Int = 3,
    val resume: Boolean = false,
    val micLevel: Float = 0f,
    val recordingSupported: Boolean = false,
    val inputMeasured: Boolean = false,
    val hadInput: Boolean = false,
    val noInputMs: Double = 0.0,
    val finished: Boolean = false,
    val follow: Boolean = true,
    val listening: Yul? = null,
    val idleReminder: Boolean = false,
    val isSongLoading: Boolean = false,
    val songError: PracticeSongError? = null,
    /** 현재 원본 이벤트 안에서 지난 시간. 긴 음/이음 중에도 표시 시계가 갱신한다. */
    val eventElapsedMs: Double = 0.0,
    /** Optional local preview timing; ignored outside Model mode, including recording/scoring. */
    val modelPerformance: ScorePerformance? = null,
    val modelPerformancePositionMs: Double = 0.0,
    /** 모범 음원이 재생 중일 때만 있다. 진행 선을 음원 위치에 맞춰 매 프레임 이어 그린다. */
    val modelClock: ModelClock? = null,
) {
    val jangdanProblem: Boolean get() = !jangdanSupported || jangdanStatus in setOf(JangdanStatus.Failed, JangdanStatus.Unsupported, JangdanStatus.Interrupted)

    val bpm: Int get() = song?.let { settings.bpmBySong[it.id] ?: it.baseBpm } ?: 0

    /** 화면 제목. 내 악보면 그 악보 제목, 아니면 곡 제목. */
    val title: String get() = sheetTitle ?: song?.title.orEmpty()

    /** ‘운지법과 함께 보기’(PANEL-00)가 켜져 정간 옆에 가로 악기 그림을 함께 보는 중. */
    val fingeringView: Boolean get() = settings.fingeringView

    /**
     * 운지 그림과 제목 옆 표시에 쓰는 악기. 연습 설정에서 고른 값이 없으면 곡 악보의 악기 코드를 따른다.
     * 정악·산조대금을 구분하며 기존 DAEGEUM은 정악대금 안공표를 쓴다. 프로필의 악기는 바꾸지 않는다.
     */
    val instrument: GuideInstrument
        get() = GuideInstrument.fromId(settings.instrumentId) ?: when (song?.score?.source?.instrument) {
            "SOGEUM" -> GuideInstrument.Sogeum
            "DANSO" -> GuideInstrument.Danso
            "SANJO_DAEGEUM" -> GuideInstrument.DaegeumSanjo
            "JEONGAK_DAEGEUM", "DAEGEUM" -> GuideInstrument.DaegeumJeongak
            else -> GuideInstrument.DaegeumJeongak
        }

    val eventCount: Int get() = song?.timeline?.size ?: 0

    val totalSeconds: Int get() = if (mode == PracticeMode.Model && modelPerformance != null && bpm > 0)
        (modelPerformance.durationMs * (song?.baseBpm ?: bpm) / bpm / 1000).roundToInt()
        else song?.durationSeconds(bpm) ?: 0

    /**
     * 연습 속도를 바꿀 수 없는 상태(평가 연주의 카운트다운·녹음·중단). 속도는 채점 기준이므로 평가 중에는 고정한다. 장단 소리는 바꿀 수 있다.
     * 악기·표기·운지법과 함께 보기 같은 보기 설정은 이때도 바꿀 수 있다.
     */
    val playbackSettingsLocked: Boolean
        get() = mode == PracticeMode.Recording || mode == PracticeMode.Countdown || mode == PracticeMode.Interrupted

    /** 이어서 연주하려고 카운트다운하는 중. 하단은 연주 중 모습을 유지한다. */
    val resuming: Boolean get() = mode == PracticeMode.Countdown && resume

    /** 연주 중 입력이 [NO_INPUT_HINT_MS]보다 오래 없어 마이크를 확인하라고 안내하는 상태. */
    val noInputHint: Boolean
        get() = inputMeasured && mode == PracticeMode.Recording && !finished && noInputMs > NO_INPUT_HINT_MS

    /**
     * 입력이 없어도 연습 시간으로 세는 활동. 모범 연주·음 듣기는 재생, 카운트다운과 멈추지 않은 연주는 녹음,
     * 장단 미리듣기는 메트로놈으로 본다. 연주 중단 확인 창이 떠 있는 동안은 녹음이 멈춰 있다.
     */
    val runningActivities: Set<ContinuousActivity>
        get() = buildSet {
            if ((mode in setOf(PracticeMode.Model, PracticeMode.Rhythm) && playing) || listening != null) add(ContinuousActivity.PLAYBACK)
            if (mode == PracticeMode.Countdown || (mode == PracticeMode.Recording && !finished && popup == null)) {
                add(ContinuousActivity.RECORDING)
            }
            // 한 음씩 따라 불기에서 '내 연주'를 듣는 중·다시 시작 3·2·1도 연주로 센다.
            if (stepBusy) add(ContinuousActivity.RECORDING)
            if (draft.previewing) add(ContinuousActivity.METRONOME)
        }

    /** 진행 선을 그리는 상태(모범 연주·연주 중). */
    val live: Boolean get() = mode == PracticeMode.Model || mode == PracticeMode.Rhythm || mode == PracticeMode.Recording || resuming

    val currentEventFraction: Double get() = if (eventCount == 0) 0.0 else (eventElapsedMs / eventDurationMs()).coerceIn(0.0, 1.0)

    /** 현재 원본 이벤트의 정수 tick에 실제 경과분을 더한 표시용 tick. 원본 event 자체는 바꾸지 않는다. */
    val currentTick: Double?
        get() = song?.timeline?.getOrNull(position)?.let { event ->
            event.startTick?.let { it + requireNotNull(event.durationTick) * currentEventFraction }
        }

    val currentScoreUnit get() = song?.displayUnitAt(position, currentEventFraction.toFloat())

    /** 정간 단위 진행률(0~100). 이벤트 중간의 경과분도 반영한다. */
    val progressPercent: Float
        get() {
            if (mode == PracticeMode.Model && modelPerformance != null) {
                return (modelPerformancePositionMs / modelPerformance.durationMs * 100).toFloat().coerceIn(0f, 100f)
            }
            val song = song ?: return 0f
            if (finished || position >= eventCount) return 100f
            val event = song.timeline.getOrNull(position) ?: return 0f
            return ((event.unitStart + event.unitDuration * currentEventFraction) / song.score.totalUnits * 100).toFloat()
        }

    /** 현재 위치가 속한 대강 번호(현재 박 표시). */
    val beatIndex: Int
        get() {
            val song = song ?: return 0
            val jangdan = song.displayJangdan ?: return -1
            song.jangdanPlayback?.let { mapping ->
                val cell = mapping.cellAt(currentTick ?: return -1)
                return if (cell < 0) -1 else jangdan.daegangIndexOfRow(cell)
            }
            if (eventCount == 0) return -1
            val event = song.timeline[position.coerceIn(0, eventCount - 1)]
            song.score.timing?.let { timing ->
                val patternCell = ((requireNotNull(currentTick) * jangdan.subdivision / timing.ticksPerBeat) % jangdan.gak).toInt()
                return jangdan.daegangIndexOfRow(patternCell)
            }
            return jangdan.daegangIndexOfRow(currentScoreUnit?.row ?: event.row)
        }

    /** 현재 음의 실제 길이(ms). */
    fun eventDurationMs(index: Int = position): Double {
        val song = song ?: return 0.0
        return song.eventDurationMs(song.timeline[index.coerceIn(0, eventCount - 1)], bpm)
    }
}

/*
 * 상태 전환. ViewModel은 타이머만 맡고 상태를 바꾸는 규칙은 여기 둔다(와이어프레임 app.js의 actions).
 */

/** 선택만 바꾸고 재생하지 않는다. 현재 음과 그 안의 진행 위치를 보존한다. */
fun PracticeUiState.selectPlaybackMode(target: PracticeMode): PracticeUiState {
    if (mode !in setOf(PracticeMode.Ready, PracticeMode.Model, PracticeMode.Rhythm) ||
        target !in setOf(PracticeMode.Model, PracticeMode.Rhythm) || mode == target) return this
    return copy(mode = target, playing = false, eventStartedAt = null, modelClock = null,
        modelPerformance = null, modelPerformancePositionMs = 0.0,
        pausedFraction = currentEventFraction.toFloat(), follow = true)
}

/** 모범 연주 시작. 준비 상태면 처음부터, 이미 모범 연주 중이면 그 자리부터. */
fun PracticeUiState.startModel(): PracticeUiState {
    val base = if (mode == PracticeMode.Ready) copy(mode = PracticeMode.Model, position = 0, elapsedMs = 0.0, eventElapsedMs = 0.0, pausedFraction = 0f) else this
    return base.copy(panel = null, playing = true, follow = true)
}

/** 모범 연주 일시정지·다시 재생. 끝까지 들은 뒤 재생하면 처음부터. */
fun PracticeUiState.toggleModel(): PracticeUiState = when {
    playing -> copy(playing = false, eventStartedAt = null, pausedFraction = currentEventFraction.toFloat())
    position >= eventCount - 1 && currentEventFraction >= 1.0 -> copy(playing = true, position = 0, elapsedMs = 0.0, eventElapsedMs = 0.0, pausedFraction = 0f)
    else -> copy(playing = true)
}

fun PracticeUiState.restartModel(): PracticeUiState = copy(position = 0, elapsedMs = 0.0, eventElapsedMs = 0.0, eventStartedAt = null, follow = true, pausedFraction = 0f)

/** 듣기 종료: 준비 상태로 돌아가고 듣던 자리의 음을 골라 둔다. */
fun PracticeUiState.stopModel(): PracticeUiState = copy(
    mode = PracticeMode.Ready, playing = false, selected = position.coerceAtMost(eventCount - 1), position = 0, elapsedMs = 0.0,
    eventStartedAt = null, eventElapsedMs = 0.0, pausedFraction = 0f,
)

/** 연주 평가 시작. 모범 연주 중이면 멈추고, 마이크 권한이 없으면 안내 창을 띄운다. */
fun PracticeUiState.requestEvaluation(micGranted: Boolean): PracticeUiState {
    val base = if (mode == PracticeMode.Model || mode == PracticeMode.Rhythm) {
        copy(mode = PracticeMode.Ready, playing = false, position = 0, elapsedMs = 0.0, eventStartedAt = null, eventElapsedMs = 0.0, pausedFraction = 0f)
    } else {
        this
    }
    return if (micGranted) base.beginCountdown() else base.copy(panel = null, popup = PracticePopup.Permission)
}

/** 3·2·1 카운트다운. [resume]이면 멈춘 자리부터 이어서 연주한다. */
fun PracticeUiState.beginCountdown(resume: Boolean = false): PracticeUiState = copy(
    mode = PracticeMode.Countdown, resume = resume, popup = null, panel = null, count = 3, playing = false, eventStartedAt = null,
)

fun PracticeUiState.beginRecording(): PracticeUiState = copy(
    mode = PracticeMode.Recording, position = 0, elapsedMs = 0.0, micLevel = 0f, finished = false, popup = null, follow = true,
    resume = false, pausedFraction = 0f, eventElapsedMs = 0.0, hadInput = false, noInputMs = 0.0,
)

fun PracticeUiState.resumeRecording(): PracticeUiState = copy(mode = PracticeMode.Recording, popup = null, resume = false, follow = true)

/** 카운트다운 취소. 이어서 연주하려던 중이면 '연주 중단' 창으로 되돌린다. */
fun PracticeUiState.cancelCountdown(): PracticeUiState =
    if (resume) copy(mode = PracticeMode.Recording, resume = false, popup = PracticePopup.Abort) else toReady()

fun PracticeUiState.toReady(): PracticeUiState = copy(
    mode = PracticeMode.Ready, position = 0, elapsedMs = 0.0, popup = null, playing = false, finished = false, micLevel = 0f,
    eventStartedAt = null, pausedFraction = 0f, eventElapsedMs = 0.0, resume = false, hadInput = false, noInputMs = 0.0,
)

/**
 * 현재 음을 마치고 다음 음으로. 곡 끝이면 모범 연주는 멈추고, 연주는 '연주를 마쳤어요' 창을 띄운다.
 * [doneMs]는 현재 음에서 실제로 흐른 시간, [micLevel]은 연주 중 다음 마이크 입력 크기.
 */
fun PracticeUiState.advance(doneMs: Double, micLevel: Float): PracticeUiState {
    val next = position + 1
    val elapsed = elapsedMs + doneMs
    if (next < eventCount) {
        return copy(position = next, elapsedMs = elapsed, eventElapsedMs = 0.0, micLevel = if (mode == PracticeMode.Recording) micLevel else this.micLevel, pausedFraction = 0f)
    }
    return when (mode) {
        PracticeMode.Model, PracticeMode.Rhythm -> copy(playing = false, position = eventCount - 1, elapsedMs = elapsed, eventElapsedMs = eventDurationMs(), pausedFraction = 1f, eventStartedAt = null)
        PracticeMode.Recording -> copy(position = eventCount, elapsedMs = elapsed).endRecording()
        else -> this
    }
}

/**
 * 연주 중 측정한 입력 크기를 반영한다. [level]이 0보다 크면 입력이 있었던 것으로 보고 무입력 시간을 0으로 되돌리고,
 * 0이면 [sinceMs](지난 측정 뒤 녹음한 시간)만큼 무입력 시간을 늘린다. 연주 중이 아니면 그대로다.
 */
fun PracticeUiState.recordLevel(level: Float, sinceMs: Double): PracticeUiState = when {
    mode != PracticeMode.Recording || finished -> this
    level > 0f -> copy(micLevel = level, hadInput = true, noInputMs = 0.0)
    else -> copy(micLevel = 0f, noInputMs = noInputMs + sinceMs)
}

/**
 * 연주를 마침: 곡 끝에 닿았거나 녹음이 최대 길이(10분)에 닿음. '연주를 마쳤어요' 창을 띄운다.
 * 입력 크기를 재는 플랫폼에서 끝까지 입력이 없었으면 평가하지 않고 '소리가 입력되지 않았어요' 창을 띄운다.
 */
fun PracticeUiState.endRecording(): PracticeUiState {
    val popup = if (inputMeasured && !hadInput) PracticePopup.EndNoInput else PracticePopup.End
    return copy(finished = true, micLevel = 0f, popup = popup, eventStartedAt = null)
}

/**
 * 마친 녹음을 확인한다. 저장하지 못했으면 녹음 실패, 보낼 수 있는 크기를 넘으면 크기 초과 창으로 바꾼다.
 * 입력이 없어 평가하지 않는 녹음은 크기를 따지지 않는다.
 * [saved]는 저장된 파일이 있는지, [tooLarge]는 그 파일이 20MiB를 넘는지.
 */
fun PracticeUiState.checkRecording(saved: Boolean, tooLarge: Boolean): PracticeUiState = when {
    !saved -> failRecording()
    popup == PracticePopup.EndNoInput -> this
    tooLarge -> copy(popup = PracticePopup.TooLarge)
    else -> this
}

/** 녹음을 시작하거나 저장하지 못함. 끊긴 연주처럼 평가하지 않는다. */
fun PracticeUiState.failRecording(): PracticeUiState = copy(
    mode = PracticeMode.Interrupted, popup = PracticePopup.RecordFailed, eventStartedAt = null, resume = false, micLevel = 0f,
    finished = false,
)

/** 연주 중단을 누르면 진행을 그 자리에서 멈추고 확인 창을 띄운다. */
fun PracticeUiState.askAbort(fraction: Float, elapsedInEventMs: Double = eventDurationMs() * fraction): PracticeUiState =
    copy(popup = PracticePopup.Abort, eventStartedAt = null, pausedFraction = fraction, eventElapsedMs = elapsedInEventMs)

/** 앱을 벗어나는 등으로 연주가 끊김. 끊긴 연주는 평가하지 않는다. */
fun PracticeUiState.interrupt(): PracticeUiState =
    if (mode == PracticeMode.Recording || mode == PracticeMode.Countdown) {
        copy(mode = PracticeMode.Interrupted, popup = PracticePopup.Interrupted, eventStartedAt = null, resume = false, micLevel = 0f)
    } else {
        this
    }

/** 준비 상태에서 음을 누르면 고르고, 율명이면 운지법 패널을 연다. 지속·쉼 기호는 고르기만 한다. */
fun PracticeUiState.selectEvent(index: Int): PracticeUiState {
    if (mode != PracticeMode.Ready || stepBusy) return this
    val yul = song?.timeline?.getOrNull(index)?.note?.fingeringYul
    val base = copy(selected = index, listening = null)
    return if (yul != null) base.copy(panel = PracticePanel.Fingering, draft = draft.copy(note = yul, noteRest = false))
        else base.copy(panel = base.panel.takeUnless { it == PracticePanel.Fingering })
}

/** [index] 음의 운지 율명. 이음(ㅡ)·빈 자리는 앞 음을 따르고, 쉼(△)이면 null이다(보드 운지법 패널 `fcNote`). */
fun PracticeUiState.fingeringYulAt(index: Int): Yul? {
    val events = song?.timeline ?: return null
    for (i in index.coerceAtMost(events.lastIndex) downTo 0) {
        val note = events[i].note
        if (note == ScoreNote.Rest) return null
        note.fingeringYul?.let { return it }
        if (note !is ScoreNote.Hold && note !is ScoreNote.Blank) return null
    }
    return null
}

/** OS 권한 창에서 허용하지 않음. 화면에 머물며 [PracticePopup.PermDenied]·[PracticePopup.PermBlocked]를 띄운다. */
fun PracticeUiState.denyMic(blocked: Boolean): PracticeUiState =
    toReady().copy(panel = null, popup = if (blocked) PracticePopup.PermBlocked else PracticePopup.PermDenied)

/**
 * 준비 상태의 플레이어 −5초·+5초: 고른 음을 그 시간만큼 앞뒤의 음으로 옮긴다. 모범 연주 중에는 음원 위치를 옮긴다(ViewModel).
 */
fun PracticeUiState.seekSelection(deltaMs: Long): PracticeUiState {
    val song = song ?: return this
    if (song.timeline.isEmpty()) return this
    var acc = 0.0
    val starts = song.timeline.map { event -> acc.also { acc += song.eventDurationMs(event, bpm) } }
    val target = (starts.getOrElse(selected) { 0.0 } + deltaMs).coerceIn(0.0, starts.last())
    val index = starts.indexOfLast { it <= target + 0.000001 }.coerceAtLeast(0)
    return copy(selected = index, listening = null)
}

/** 패널 열기. 평가 중 속도 패널만 제한하고 저장된 값을 표시한다. */
fun PracticeUiState.openPanel(panel: PracticePanel): PracticeUiState {
    if (playbackSettingsLocked && panel == PracticePanel.Tempo) return this
    val song = song
    val draft = when (panel) {
        PracticePanel.Notation -> draft.copy(showHangul = settings.showHangul, showSolfege = settings.showSolfege)
        PracticePanel.Jangdan -> draft.copy(jangdanSound = settings.jangdanSound, beatVisual = settings.beatVisual, previewing = false, previewBeat = 0)
        PracticePanel.Tempo -> draft.copy(step = if (song != null) TempoStep.nearest(song.baseBpm, bpm) else TempoStep.Normal)
        else -> draft
    }
    return copy(panel = panel, draft = draft)
}

/** 하위 설정의 뒤로가기. 변경값은 이미 저장됐으며 상위 설정으로 돌아간다. */
fun PracticeUiState.closePanel(): PracticeUiState = copy(
    panel = if (panel == PracticePanel.Notation || panel == PracticePanel.Jangdan || panel == PracticePanel.Tempo || panel == PracticePanel.Fingering) PracticePanel.Settings else null,
    listening = null,
    draft = draft.copy(previewing = false, previewBeat = 0),
)

/**
 * 운지법 패널에서 이전(-1)·다음(+1) 음으로: 악보의 앞뒤 정간을 고른다(보드 `prevNote`·`nextNote`). 끝이면 그대로.
 * 이음은 앞 음의 운지를, 쉼은 ‘쉼’을 보여 준다.
 */
fun PracticeUiState.moveFingering(delta: Int): PracticeUiState {
    val events = song?.timeline ?: return this
    if (events.isEmpty()) return this
    val next = (selected + delta).coerceIn(0, events.lastIndex)
    if (next == selected) return this
    val yul = fingeringYulAt(next)
    return copy(selected = next, listening = null, draft = draft.copy(note = yul ?: draft.note, noteRest = yul == null))
}

/** 표기 패널 적용. 계이름 데이터가 없는 곡은 계이름을 켜지 않는다. */
fun PracticeSettings.applyNotation(draft: PracticeDraft, song: Song): PracticeSettings =
    copy(showHangul = draft.showHangul, showSolfege = draft.showSolfege && song.solfege != null)

fun PracticeSettings.applyJangdan(draft: PracticeDraft): PracticeSettings =
    copy(jangdanSound = draft.jangdanSound, beatVisual = draft.beatVisual)

fun PracticeSettings.applyTempo(draft: PracticeDraft, song: Song): PracticeSettings =
    copy(bpmBySong = bpmBySong + (song.id to draft.step.bpm(song.baseBpm)))

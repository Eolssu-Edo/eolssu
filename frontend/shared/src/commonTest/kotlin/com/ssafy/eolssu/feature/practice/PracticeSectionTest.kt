package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreBpmUnit
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.ScoreTiming
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.model.Yul
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 연습 화면의 구간 연습(S15P31E102-794): 고정 구간, 구간 안 타임라인·한 음씩, 구간 끝에서 멈춤. 속도는 설정에서 정한다. */
class PracticeSectionTest {
    private fun pitch(yul: Yul) = ScoreNote.Pitch(yul)

    /**
     * 연주 순서: 0 黃 · 1 ㅡ · 2 △ (1각) · 3 太 · 4 ㅡ(太와 같은 정간) · 5 林 · 6 △ (2각).
     * 기다리는 음은 0·3·5뿐이다.
     */
    private val song = Song(
        id = "section-test", title = "구간 검사", level = SongLevel.Intro,
        jangdan = Jangdan("세마치", listOf(3, 3, 3), 3, 3), baseBpm = 60, description = "", evaluable = false,
        score = Score(
            listOf(
                listOf(ScoreCell(pitch(Yul.Hwangjong)), ScoreCell(ScoreNote.Hold), ScoreCell(ScoreNote.Rest)),
                listOf(ScoreCell(pitch(Yul.Taeju), ScoreNote.Hold), ScoreCell(pitch(Yul.Imjong)), ScoreCell(ScoreNote.Rest)),
            ),
        ),
    )
    private val secondGak = ScoreSection(ScorePosition(1, 0), ScorePosition(1, 2))
    private val solo = PracticeUiState(song = song, solo = true)

    @Test fun openingFixesTheSectionAndStartsAtTheSectionStart() {
        val on = solo.openSectionPractice(secondGak)
        assertEquals(3..6, on.sectionEvents)
        assertEquals(3, on.position)
        assertEquals(3, on.step.target)
        assertEquals(listOf(3, 5), on.stepTargetsInSection())
        // 속도는 연습 화면 설정 그대로라 설정에서 바꿀 수 있다.
        assertFalse(on.playbackSettingsLocked)
        assertEquals(PracticePanel.Tempo, on.openPanel(PracticePanel.Tempo).panel)
        // 구간은 이 화면에서 바꾸지 않는다.
        assertFalse(on.canSelectUnit)
        // 곡 전체 연습은 그대로다.
        assertNull(solo.sectionEvents)
        assertEquals(listOf(0, 3, 5), solo.stepTargetsInSection())
    }

    @Test fun timelineCoversOnlyTheSection() {
        // 정간마다 3박(박당 4tick)씩 이어지는 악보: 연주 순서마다 tick을 붙인다.
        val timed = song.copy(
            score = song.score.copy(
                timing = ScoreTiming(hwangHz = 311.0, jangdan = "세마치", bpmUnit = ScoreBpmUnit.entries.first(), ticksPerBeat = 4, ticksPerJeonggan = 4),
                events = song.timeline.mapIndexed { i, event -> event.copy(startTick = i * 4L, durationTick = 4L) },
            ),
        )
        val on = PracticeUiState(song = timed, solo = true).openSectionPractice(secondGak)
        // 구간은 연주 순서 3~6(tick 12~28)이다. 시간과 막대는 구간 처음에서 0이다.
        assertEquals(12.0..28.0, on.sectionTicks)
        assertEquals(0f, on.playerFraction)
        assertEquals(0.0, on.playerElapsedSeconds)
        assertTrue(on.playerTotalSeconds > 0.0 && on.playerTotalSeconds < on.totalSeconds)
        // 막대 끝은 구간 끝이고, ±5초도 구간 밖으로 나가지 않는다.
        assertEquals(28.0, on.sectionTickAt(1f))
        assertEquals(12.0, on.sectionTickAt(-1f))
        assertEquals(12.0, on.sectionTickBy(-60_000))
        assertEquals(28.0, on.sectionTickBy(60_000))
    }

    @Test fun reachingTheSectionEndStopsAtTheSectionStart() {
        val playing = solo.openSectionPractice(secondGak).copy(mode = PracticeMode.Rhythm, playing = true, position = 6)
        assertFalse(playing.passedSectionEnd)
        val past = playing.copy(position = 7)
        assertTrue(past.passedSectionEnd)
        val stopped = past.stopAtSectionStart()
        assertFalse(stopped.playing)
        assertEquals(3, stopped.position)
        assertEquals(3, stopped.sectionReadyStart)
    }

    @Test fun stepModeFinishesAtTheLastNoteOfTheSection() {
        var state = solo.openSectionPractice(secondGak).toggleStep().tapStepPlaying()
        assertEquals(3, state.step.target)
        state = state.matchStepNote(3)
        assertEquals(5, state.step.target)
        state = state.matchStepNote(5)
        assertEquals(StepPhase.Finished, state.step.phase)
    }

    @Test fun modelStartsAndRestartsFromTheSectionStart() {
        val on = solo.openSectionPractice(secondGak)
        val playing = on.startModel()
        assertEquals(PracticeMode.Model, playing.mode)
        assertEquals(3, playing.position)
        assertEquals(3, playing.copy(position = 5).restartModel().position)
    }

    @Test fun sectionEvaluationTempoIsChangedInSettingsUntilRecording() {
        // 구간 재평가는 연주 평가 화면이라 녹음 전에는 속도를 설정에서 바꾼다(S15P31E102-794·824).
        val waiting = PracticeUiState(song = song, direct = true).openSectionPractice(secondGak)
        assertTrue(waiting.sectionEvaluation)
        assertTrue(waiting.awaitingStart)
        assertFalse(waiting.playbackSettingsLocked)
        assertEquals(PracticePanel.Tempo, waiting.openPanel(PracticePanel.Tempo).panel)
        // 3·2·1과 녹음 중에는 연주 평가처럼 속도가 잠긴다.
        val recording = waiting.beginCountdown().beginRecording()
        assertTrue(recording.playbackSettingsLocked)
        assertNull(recording.openPanel(PracticePanel.Tempo).panel)
    }

    @Test fun sectionEvaluationRecordsFromTheSectionStartToItsEnd() {
        val waiting = PracticeUiState(song = song, direct = true).openSectionPractice(secondGak)
        // 3·2·1 동안에도 구간 처음을 보인다.
        assertEquals(3, waiting.beginCountdown().sectionReadyStart)
        var state = waiting.beginCountdown().beginRecording()
        assertEquals(3, state.position)
        // 시계는 곡 처음 기준이다: 1각(정간 3개)을 지난 시간에서 시작한다.
        val cellMs = song.cellDurationMs(state.bpm)
        assertEquals(3 * cellMs, state.elapsedMs)
        assertEquals(0.0, state.recordingElapsedSeconds)
        assertEquals(3 * cellMs / 1000, state.recordingTotalSeconds)
        assertEquals(0f, state.recordingProgress)
        // 구간의 음(3·4·5·6)을 차례로 지나 마지막 음에서 끝난다.
        repeat(3) {
            state = state.advance(state.eventDurationMs(), 0f)
            assertFalse(state.finished)
        }
        assertEquals(6, state.position)
        state = state.advance(state.eventDurationMs(), 0f)
        assertTrue(state.finished)
        assertEquals(PracticePopup.End, state.popup)
        assertEquals(7, state.position)
        assertEquals(1f, state.recordingProgress)
    }

    @Test fun wholeSongEvaluationStillRunsFromTheFirstNoteToTheLast() {
        // 구간이 없는 연주 평가는 그대로 곡 처음부터 끝까지 간다.
        var state = PracticeUiState(song = song, direct = true).beginCountdown().beginRecording()
        assertFalse(state.sectionEvaluation)
        assertEquals(0, state.position)
        assertEquals(0.0, state.elapsedMs)
        repeat(song.timeline.size - 1) { state = state.advance(state.eventDurationMs(), 0f) }
        assertFalse(state.finished)
        state = state.advance(state.eventDurationMs(), 0f)
        assertTrue(state.finished)
        assertEquals(song.timeline.size, state.position)
        assertEquals(state.totalSeconds.toDouble(), state.recordingTotalSeconds)
        assertEquals(state.progressPercent / 100f, state.recordingProgress)
        // 구간 연습(혼자 연습)은 평가 연주가 아니다.
        assertFalse(solo.openSectionPractice(secondGak).sectionEvaluation)
    }
}

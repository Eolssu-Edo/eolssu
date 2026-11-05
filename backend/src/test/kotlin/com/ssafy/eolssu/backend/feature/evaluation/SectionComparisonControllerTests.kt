package com.ssafy.eolssu.backend.feature.evaluation

import com.ssafy.eolssu.backend.feature.evaluation.api.SectionComparisonController
import com.ssafy.eolssu.backend.feature.evaluation.application.GetSectionComparison
import com.ssafy.eolssu.backend.feature.evaluation.domain.*
import java.time.Instant
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class SectionComparisonControllerTests {
    private val time = Instant.parse("2026-10-29T00:00:00Z")
    private val sessions = mutableMapOf<Long, PracticeEvaluationSnapshot>()
    private val owners = mutableMapOf<Long, Long>()
    private val notes = mutableMapOf<Long, List<PracticeEvaluationNote>>()
    private var scoreChanged = false
    private var previous: Long? = null
    private fun save(id: Long, start: Int, end: Int, total: Int, parent: Long? = null, songId: Long = 1, status: String = "DONE", model: String = "m1",
        startedAt: Instant = time, scoreVersion: Int = 1, owner: Long = 2, bpm: Int = 48) {
        owners[id] = owner
        sessions[id] = PracticeEvaluationSnapshot(id, songId, scoreVersion, bpm, "DOTTED_QUARTER", status, startedAt, startedAt, id, total, total + 1,
            total - 1, total, startedAt, null, start, end, parentSessionId = parent, modelVersion = model)
    }
    private val repository = object : PracticeEvaluations {
        override fun findOwned(sessionId: Long, userId: Long) = sessions[sessionId]?.takeIf { owners[sessionId] == userId }
        override fun notes(evaluationId: Long) = notes[evaluationId].orEmpty()
        override fun findPreviousSessionId(userId: Long, current: PracticeEvaluationSnapshot) = previous
    }
    // 각 0: 음표 0·1, 각 1: 음표 2·3, 각 2: 음표 4·5.
    private val cells = EvaluationScoreCells { _, _ -> if (scoreChanged) null else (0..5).map { NoteCell(it / 2, "c${it / 2}-j${it % 2}") } }
    private val mvc = MockMvcBuilders.standaloneSetup(SectionComparisonController(GetSectionComparison(repository, cells)))
        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver()).build()
    private fun login() { SecurityContextHolder.getContext().authentication=JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg","HS256").subject("2").build()) }
    private fun note(idx: Int, verdict: String) = PracticeEvaluationNote(idx, verdict, null, null, null, 1, null)
    @AfterEach fun clear()=SecurityContextHolder.clearContext()

    init {
        save(10, 0, 5, 60)
        notes[10] = listOf(note(0,"PITCH"), note(1,"CORRECT"), note(2,"PITCH"), note(3,"TIMING"), note(4,"CORRECT"), note(5,"CORRECT"))
        save(11, 2, 3, 70)
        notes[11] = listOf(note(2,"PITCH"), note(3,"MISSED"))
        save(12, 2, 3, 90, parent = 11)
        notes[12] = listOf(note(2,"CORRECT"), note(3,"PITCH"))
    }

    @Test fun `section scores fill before and delta when both sides have note scores`() {
        login()
        val weights = ScoreWeights(0.5, 0.25, 0.25)
        for (id in listOf(11L, 12L)) sessions[id] = sessions.getValue(id).copy(scoringVersion = "note-mean-1", scoreWeights = weights)
        notes[11] = listOf(2, 3).map { PracticeEvaluationNote(it, "PITCH", null, null, null, 1, null, null, 60.0, 80.0, 40.0, 1.0) }
        notes[12] = listOf(2, 3).map { PracticeEvaluationNote(it, "CORRECT", null, null, null, 1, null, null, 90.0, 90.0, 90.0, 1.0) }
        mvc.perform(get("/api/practice-sessions/12/comparison")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.scoreComparable").value(true))
            .andExpect(jsonPath("$.result.notComparableReasons.length()").value(0))
            .andExpect(jsonPath("$.result.before.totalScore").value(60))
            .andExpect(jsonPath("$.result.before.pitchScore").value(60))
            .andExpect(jsonPath("$.result.before.timingScore").value(80))
            .andExpect(jsonPath("$.result.before.durationScore").value(40))
            .andExpect(jsonPath("$.result.after.totalScore").value(90))
            .andExpect(jsonPath("$.result.delta.totalScore").value(30))
            .andExpect(jsonPath("$.result.delta.pitchScore").value(31))
            .andExpect(jsonPath("$.result.delta.timingScore").value(9))
            .andExpect(jsonPath("$.result.delta.durationScore").value(50))
    }

    @Test fun `parent section retake compares errors and leaves scores null`() {
        login()
        mvc.perform(get("/api/practice-sessions/12/comparison")).andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("연주 결과 비교 성공"))
            .andExpect(jsonPath("$.result.baselineSessionId").value("11"))
            .andExpect(jsonPath("$.result.baselineStartedAt").value("2026-10-29T00:00:00Z"))
            .andExpect(jsonPath("$.result.baselineSource").value("PARENT"))
            .andExpect(jsonPath("$.result.startNoteIdx").value(2))
            .andExpect(jsonPath("$.result.endNoteIdx").value(3))
            .andExpect(jsonPath("$.result.startColumnIdx").value(1))
            .andExpect(jsonPath("$.result.endColumnIdx").value(1))
            .andExpect(jsonPath("$.result.isFullSong").value(false))
            .andExpect(jsonPath("$.result.scoreComparable").value(false))
            .andExpect(jsonPath("$.result.errorComparable").value(true))
            .andExpect(jsonPath("$.result.notComparableReasons[0]").value("NO_NOTE_SCORES"))
            .andExpect(jsonPath("$.result.notComparableReasons.length()").value(1))
            .andExpect(jsonPath("$.result.bpmChanged").value(false))
            .andExpect(jsonPath("$.result.comparable").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.result.reason").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.result.before.totalScore").hasJsonPath())
            .andExpect(jsonPath("$.result.before.totalScore").value(nullValue()))
            .andExpect(jsonPath("$.result.before.errorCount").value(2))
            .andExpect(jsonPath("$.result.before.bpm").value(48))
            .andExpect(jsonPath("$.result.before.bpmUnit").value("DOTTED_QUARTER"))
            .andExpect(jsonPath("$.result.after.pitchScore").value(91))
            .andExpect(jsonPath("$.result.after.errorCount").value(1))
            .andExpect(jsonPath("$.result.after.bpm").value(48))
            .andExpect(jsonPath("$.result.after.bpmUnit").value("DOTTED_QUARTER"))
            .andExpect(jsonPath("$.result.delta.totalScore").hasJsonPath())
            .andExpect(jsonPath("$.result.delta.totalScore").value(nullValue()))
            .andExpect(jsonPath("$.result.delta.errorCount").value(-1))
            .andExpect(jsonPath("$.result.correctedNoteIndices[0]").value(2))
            .andExpect(jsonPath("$.result.remainingErrors[0].noteIdx").value(3))
            .andExpect(jsonPath("$.result.remainingErrors[0].verdict").value("PITCH"))
    }

    @Test fun `query baseline wins over the parent and whole song baseline is compared on the section notes`() {
        login()
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "10")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.baselineSessionId").value("10"))
            .andExpect(jsonPath("$.result.baselineSource").value("QUERY"))
            .andExpect(jsonPath("$.result.before.errorCount").value(2))
            .andExpect(jsonPath("$.result.delta.errorCount").value(-1))
            .andExpect(jsonPath("$.result.correctedNoteIndices[0]").value(2))
    }

    @Test fun `parent wins over the previous session`() {
        login();previous = 10
        mvc.perform(get("/api/practice-sessions/12/comparison")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.baselineSessionId").value("11"))
            .andExpect(jsonPath("$.result.baselineSource").value("PARENT"))
    }

    @Test fun `without query or parent the previous session is compared`() {
        login();previous = 11
        save(17, 2, 3, 80, startedAt = time.plusSeconds(60))
        notes[17] = listOf(note(2,"CORRECT"), note(3,"CORRECT"))
        mvc.perform(get("/api/practice-sessions/17/comparison")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.baselineSessionId").value("11"))
            .andExpect(jsonPath("$.result.baselineSource").value("PREVIOUS"))
            .andExpect(jsonPath("$.result.delta.errorCount").value(-2))
    }

    @Test fun `no previous session is a first performance with null comparison fields`() {
        login()
        mvc.perform(get("/api/practice-sessions/10/comparison")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.baselineSessionId").hasJsonPath())
            .andExpect(jsonPath("$.result.baselineSessionId").value(nullValue()))
            .andExpect(jsonPath("$.result.baselineStartedAt").value(nullValue()))
            .andExpect(jsonPath("$.result.baselineSource").value(nullValue()))
            .andExpect(jsonPath("$.result.isFullSong").value(true))
            .andExpect(jsonPath("$.result.scoreComparable").value(false))
            .andExpect(jsonPath("$.result.errorComparable").value(false))
            .andExpect(jsonPath("$.result.notComparableReasons.length()").value(0))
            .andExpect(jsonPath("$.result.bpmChanged").value(false))
            .andExpect(jsonPath("$.result.before").hasJsonPath())
            .andExpect(jsonPath("$.result.before").value(nullValue()))
            .andExpect(jsonPath("$.result.delta").value(nullValue()))
            .andExpect(jsonPath("$.result.after.totalScore").value(60))
            .andExpect(jsonPath("$.result.after.errorCount").value(3))
            .andExpect(jsonPath("$.result.correctedNoteIndices.length()").value(0))
            .andExpect(jsonPath("$.result.remainingErrors.length()").value(3))
    }

    @Test fun `different tempo is compared and flagged`() {
        login()
        save(18, 2, 3, 70, bpm = 60)
        notes[18] = listOf(note(2,"PITCH"), note(3,"PITCH"))
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "18")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.bpmChanged").value(true))
            .andExpect(jsonPath("$.result.errorComparable").value(true))
            .andExpect(jsonPath("$.result.before.bpm").value(60))
            .andExpect(jsonPath("$.result.after.bpm").value(48))
            .andExpect(jsonPath("$.result.delta.errorCount").value(-1))
    }

    @Test fun `different model keeps the error comparison`() {
        login()
        save(19, 2, 3, 70, model = "m2")
        notes[19] = listOf(note(2,"PITCH"), note(3,"PITCH"))
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "19")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.errorComparable").value(true))
            .andExpect(jsonPath("$.result.notComparableReasons[0]").value("MODEL_CHANGED"))
            .andExpect(jsonPath("$.result.notComparableReasons[1]").value("NO_NOTE_SCORES"))
            .andExpect(jsonPath("$.result.delta.errorCount").value(-1))
    }

    @Test fun `baseline rules follow the parent session rules`() {
        login()
        save(13, 0, 5, 50, songId = 2)
        save(14, 0, 5, 50, status = "PENDING")
        save(15, 3, 5, 50)
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "12")).andExpect(status().isBadRequest)
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "13")).andExpect(status().isBadRequest)
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "15")).andExpect(status().isBadRequest)
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "14")).andExpect(status().isConflict)
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "99")).andExpect(status().isNotFound)
    }

    @Test fun `deleted parent falls through to the previous session`() {
        login();previous = 10
        sessions.remove(11L)
        mvc.perform(get("/api/practice-sessions/12/comparison")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.baselineSessionId").value("10"))
            .andExpect(jsonPath("$.result.baselineSource").value("PREVIOUS"))
    }

    @Test fun `deleted parent without a previous session is a first performance`() {
        login()
        sessions.remove(11L)
        mvc.perform(get("/api/practice-sessions/12/comparison")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.baselineSessionId").value(nullValue()))
            .andExpect(jsonPath("$.result.baselineSource").value(nullValue()))
            .andExpect(jsonPath("$.result.before").value(nullValue()))
    }

    @Test fun `requested baseline that was deleted stays not found`() {
        login();previous = 10
        sessions.remove(11L)
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "11")).andExpect(status().isNotFound)
    }

    @Test fun `unfinished parent stays a state conflict`() {
        login();previous = 10
        save(11, 2, 3, 70, status = "PENDING")
        mvc.perform(get("/api/practice-sessions/12/comparison")).andExpect(status().isConflict)
    }

    @Test fun `parent of another song stays a bad request`() {
        login();previous = 10
        save(11, 2, 3, 70, songId = 2)
        mvc.perform(get("/api/practice-sessions/12/comparison")).andExpect(status().isBadRequest)
    }

    @Test fun `baseline must belong to the user and come before the retake`() {
        login()
        save(20, 2, 3, 50, owner = 3)
        save(21, 2, 3, 50, startedAt = time.plusSeconds(60))
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "20")).andExpect(status().isNotFound)
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "21")).andExpect(status().isBadRequest)
    }

    @Test fun `baseline on an older score is reported without matching note indices`() {
        login()
        save(22, 0, 1, 50, scoreVersion = 0)
        notes[22] = listOf(note(0,"PITCH"), note(1,"PITCH"))
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "22")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.errorComparable").value(false))
            .andExpect(jsonPath("$.result.notComparableReasons[0]").value("SCORE_VERSION_CHANGED"))
            .andExpect(jsonPath("$.result.notComparableReasons[1]").value("NO_NOTE_SCORES"))
            .andExpect(jsonPath("$.result.before.errorCount").hasJsonPath())
            .andExpect(jsonPath("$.result.before.errorCount").value(nullValue()))
            .andExpect(jsonPath("$.result.delta.errorCount").hasJsonPath())
            .andExpect(jsonPath("$.result.delta.errorCount").value(nullValue()))
            .andExpect(jsonPath("$.result.correctedNoteIndices.length()").value(0))
            .andExpect(jsonPath("$.result.remainingErrors[0].noteIdx").value(3))
    }

    @Test fun `changed current score is a state conflict`() {
        login();scoreChanged = true
        mvc.perform(get("/api/practice-sessions/12/comparison")).andExpect(status().isConflict)
    }

    @Test fun `current session must be owned and finished`() {
        login()
        save(16, 2, 3, 0, parent = 11, status = "FAILED")
        mvc.perform(get("/api/practice-sessions/99/comparison")).andExpect(status().isNotFound)
        mvc.perform(get("/api/practice-sessions/16/comparison")).andExpect(status().isConflict)
    }
}

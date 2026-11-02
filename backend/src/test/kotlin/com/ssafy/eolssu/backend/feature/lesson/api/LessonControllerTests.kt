package com.ssafy.eolssu.backend.feature.lesson.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.lesson.application.LessonQueries
import com.ssafy.eolssu.backend.feature.lesson.application.MarkLessonCompletion
import com.ssafy.eolssu.backend.feature.lesson.domain.JudgeType
import com.ssafy.eolssu.backend.feature.lesson.domain.LearnTrack
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonContent
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonDetail
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonRepository
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonStep
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonTask
import com.ssafy.eolssu.backend.feature.lesson.domain.PassCriteria
import com.ssafy.eolssu.backend.feature.lesson.domain.PitchNote
import com.ssafy.eolssu.backend.feature.lesson.domain.PitchRange
import com.ssafy.eolssu.backend.feature.lesson.domain.SongCandidate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class LessonControllerTests {
    private val repository = FakeLessonRepository()
    private val now = Instant.parse("2026-11-02T03:04:05.123456789Z")
    private val mvc = MockMvcBuilders
        .standaloneSetup(LessonController(LessonQueries(repository), MarkLessonCompletion(repository, Clock.fixed(now, ZoneOffset.UTC))))
        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    // 실제 토큰 검증은 보안 필터 검사가 맡는다. 여기서는 검증을 통과한 회원 7로 둔다.
    @BeforeEach
    fun signIn() {
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "HS256").subject("7").build())
    }

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    @Test
    fun `lists the published stages of the track with the signed in member's progress`() {
        mvc.perform(get("/api/lessons?track=DANSO"))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"httpStatus":"OK","isSuccess":true,"code":200,"result":{"track":"DANSO","completedCount":1,"requiredCount":2,
                    "nextLessonId":"2","items":[
                    {"lessonId":"1","seq":1,"title":"악기 알기·감상","judgeType":"READ","optional":false},
                    {"lessonId":"2","seq":2,"title":"자세·호흡","judgeType":"READ","optional":false,"completedAt":null},
                    {"lessonId":"9","seq":9,"title":"장식음·시김새","judgeType":"LISTEN","optional":true,"completedAt":null}]}}""",
                ),
            )
            .andExpect(jsonPath("$.result.items[0].completedAt").isNotEmpty)
            .andExpect(jsonPath("$.result.items[0].thumbnailUrl").doesNotExist())
        assertEquals(listOf(7L to LearnTrack.DANSO), repository.listCalls)
    }

    @Test
    fun `rejects a missing lower case or unknown track`() {
        for (query in listOf("", "?track=", "?track=danso", "?track=PIRI")) {
            mvc.perform(get("/api/lessons$query"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
        assertEquals(emptyList(), repository.listCalls)
    }

    @Test
    fun `returns every content field of the stage detail`() {
        mvc.perform(get("/api/lessons/4"))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"httpStatus":"OK","isSuccess":true,"message":"학습 단계 상세 조회 성공","code":200,"result":{"lessonId":"4","track":"DANSO","seq":4,"title":"기본음",
                    "optional":false,"judgeType":"PITCH","goal":"쉬운 음부터 율명과 운지를 익힌다","why":"쉬운 음부터 배워요.",
                    "tasks":[{"title":"앞 꾸밈음","description":null}],
                    "passCriteria":{"title":"음마다 2초 이상 유지","description":"±25센트 이내(예시)."},
                    "pitchNames":["汰","潢"],"pitchNote":{"text":"汰부터 내려가요.","caution":null},"parts":["취구"],
                    "ranges":[{"blow":"HIGH","label":"역취","description":"세게 불어요.","firstPitch":"㳞"}],
                    "songs":[{"title":"아리랑","meta":"세마치장단"}],"source":"단소 지도서","completedAt":null}}""",
                    true,
                ),
            )
        assertEquals(listOf(7L to 4L), repository.detailCalls)
    }

    @Test
    fun `hides a missing or draft stage and rejects a non numeric id`() {
        mvc.perform(get("/api/lessons/99"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
        mvc.perform(get("/api/lessons/abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
    }

    @Test
    fun `marks a stage once at microsecond precision and returns the track progress`() {
        mvc.perform(put("/api/lessons/2/completion").contentType(MediaType.APPLICATION_JSON).content("""{"completed":true}"""))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"httpStatus":"OK","isSuccess":true,"message":"학습 단계 완료 표시 성공","code":200,
                    "result":{"lessonId":"2","completed":true,"completedCount":2,"nextLessonId":null}}""",
                ),
            )
            .andExpect(jsonPath("$.result.completedAt").isNotEmpty)
        assertEquals(listOf(Triple(7L, 2L, Instant.parse("2026-11-02T03:04:05.123456Z"))), repository.marks)

        mvc.perform(put("/api/lessons/1/completion").contentType(MediaType.APPLICATION_JSON).content("""{"completed":false}"""))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"message":"학습 단계 완료 표시 해제 성공","result":{"lessonId":"1","completed":false,"completedAt":null,
                    "completedCount":1,"nextLessonId":"1"}}""",
                ),
            )
        assertEquals(listOf(7L to 1L), repository.clears)
    }

    @Test
    fun `does not write for a missing or draft stage`() {
        mvc.perform(put("/api/lessons/99/completion").contentType(MediaType.APPLICATION_JSON).content("""{"completed":true}"""))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
        assertEquals(emptyList(), repository.marks)
    }

    @Test
    fun `rejects a completion body that is not a JSON boolean`() {
        for (body in listOf("{}", """{"completed":null}""", """{"completed":"true"}""", """{"completed":1}""", "not json", "")) {
            mvc.perform(put("/api/lessons/2/completion").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
        assertEquals(emptyList(), repository.marks)
        assertEquals(emptyList(), repository.clears)
    }

    private class FakeLessonRepository : LessonRepository {
        val listCalls = mutableListOf<Pair<Long, LearnTrack>>()
        val detailCalls = mutableListOf<Pair<Long, Long>>()
        val marks = mutableListOf<Triple<Long, Long, Instant>>()
        val clears = mutableListOf<Pair<Long, Long>>()
        private val completed = mutableMapOf(1L to Instant.parse("2026-11-02T03:00:00Z"))

        override fun findPublishedStep(userId: Long, lessonId: Long): LessonStep? =
            steps(LearnTrack.DANSO).firstOrNull { it.id == lessonId }

        override fun markCompleted(userId: Long, lessonId: Long, at: Instant) {
            marks += Triple(userId, lessonId, at)
            completed.putIfAbsent(lessonId, at)
        }

        override fun clearCompletion(userId: Long, lessonId: Long) {
            clears += userId to lessonId
            completed.remove(lessonId)
        }

        private fun steps(track: LearnTrack) = listOf(
            LessonStep(9, track, 9, "장식음·시김새", JudgeType.LISTEN, true, completed[9]),
            LessonStep(1, track, 1, "악기 알기·감상", JudgeType.READ, false, completed[1]),
            LessonStep(2, track, 2, "자세·호흡", JudgeType.READ, false, completed[2]),
        ).sortedBy { it.seq }

        override fun findPublishedSteps(userId: Long, track: LearnTrack): List<LessonStep> {
            listCalls += userId to track
            return steps(track)
        }

        override fun findPublishedDetail(userId: Long, lessonId: Long): LessonDetail? {
            detailCalls += userId to lessonId
            if (lessonId != 4L) return null
            return LessonDetail(
                LessonStep(4, LearnTrack.DANSO, 4, "기본음", JudgeType.PITCH, false, null),
                LessonContent(
                    goal = "쉬운 음부터 율명과 운지를 익힌다",
                    why = "쉬운 음부터 배워요.",
                    tasks = listOf(LessonTask("앞 꾸밈음", null)),
                    passCriteria = PassCriteria("음마다 2초 이상 유지", "±25센트 이내(예시)."),
                    pitchNames = listOf("汰", "潢"),
                    pitchNote = PitchNote("汰부터 내려가요.", null),
                    parts = listOf("취구"),
                    ranges = listOf(PitchRange("HIGH", "역취", "세게 불어요.", "㳞")),
                    songs = listOf(SongCandidate("아리랑", "세마치장단")),
                    source = "단소 지도서",
                ),
            )
        }
    }
}

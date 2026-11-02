package com.ssafy.eolssu.backend.feature.lesson.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.lesson.application.LessonCompletion
import com.ssafy.eolssu.backend.feature.lesson.application.LessonList
import com.ssafy.eolssu.backend.feature.lesson.application.LessonQueries
import com.ssafy.eolssu.backend.feature.lesson.application.MarkLessonCompletion
import com.ssafy.eolssu.backend.feature.lesson.domain.LearnTrack
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonDetail
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonStep
import com.ssafy.eolssu.backend.feature.practice.api.StrictBooleanDeserializer
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.annotation.JsonDeserialize

/** 기초부터 배우기(앱 화면 LEARN-02) 단계 API. 계약은 docs/specs/learning-progress-apis.md(LRN-01·02·03). */
@RestController
@Profile("db | ops")
class LessonController(private val queries: LessonQueries, private val completion: MarkLessonCompletion) {
    @GetMapping("/api/lessons")
    fun list(@AuthenticationPrincipal jwt: Jwt, @RequestParam track: String): ResponseEntity<*> {
        val learnTrack = LearnTrack.parse(track) ?: return error(ErrorCode.INVALID_REQUEST)
        return ok("기초 학습 목록·나의 진도 조회 성공", queries.list(jwt.userId(), learnTrack).toResult())
    }

    @GetMapping("/api/lessons/{lessonId}")
    fun detail(@AuthenticationPrincipal jwt: Jwt, @PathVariable lessonId: Long): ResponseEntity<*> {
        val detail = queries.detail(jwt.userId(), lessonId) ?: return error(ErrorCode.RESOURCE_NOT_FOUND)
        return ok("학습 단계 상세 조회 성공", detail.toResult())
    }

    @PutMapping("/api/lessons/{lessonId}/completion")
    fun complete(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable lessonId: Long,
        @Valid @RequestBody request: LessonCompletionRequest,
    ): ResponseEntity<*> = when (val outcome = completion.execute(jwt.userId(), lessonId, requireNotNull(request.completed))) {
        LessonCompletion.NotFound -> error(ErrorCode.RESOURCE_NOT_FOUND)
        is LessonCompletion.Done -> ok(
            if (outcome.completed) "학습 단계 완료 표시 성공" else "학습 단계 완료 표시 해제 성공",
            LessonCompletionResult(
                lessonId = outcome.lessonId.toString(),
                completed = outcome.completed,
                completedAt = outcome.completedAt,
                completedCount = outcome.progress.completedCount,
                nextLessonId = outcome.progress.nextLessonId?.toString(),
            ),
        )
    }

    // 토큰 검증에서 subject가 양의 정수인지 확인한다(AccessTokenConfig).
    private fun Jwt.userId() = requireNotNull(subject).toLong()

    private fun <T> ok(message: String, result: T) = ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, message, result))

    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}

private fun LessonList.toResult() = LessonListResult(
    track = track.name,
    completedCount = progress.completedCount,
    requiredCount = progress.requiredCount,
    nextLessonId = progress.nextLessonId?.toString(),
    items = steps.map { it.toItem() },
)

private fun LessonStep.toItem() = LessonItemResult(id.toString(), seq, title, judgeType.name, optional, completedAt)

private fun LessonDetail.toResult() = LessonDetailResult(
    lessonId = step.id.toString(),
    track = step.track.name,
    seq = step.seq,
    title = step.title,
    optional = step.optional,
    judgeType = step.judgeType.name,
    goal = content.goal,
    why = content.why,
    tasks = content.tasks.map { LessonTaskResult(it.title, it.description) },
    passCriteria = content.passCriteria?.let { PassCriteriaResult(it.title, it.description) },
    pitchNames = content.pitchNames,
    pitchNote = content.pitchNote?.let { PitchNoteResult(it.text, it.caution) },
    parts = content.parts,
    ranges = content.ranges.map { PitchRangeResult(it.blow, it.label, it.description, it.firstPitch) },
    songs = content.songs.map { SongCandidateResult(it.title, it.meta) },
    source = content.source,
    completedAt = step.completedAt,
)

// completed는 JSON 불리언만 받는다. "true"·1·null·누락은 400이다(StrictJsonDeserializers).
data class LessonCompletionRequest(
    @field:NotNull @param:JsonDeserialize(using = StrictBooleanDeserializer::class) val completed: Boolean? = null,
)

data class LessonCompletionResult(
    val lessonId: String,
    val completed: Boolean,
    val completedAt: Instant?,
    val completedCount: Int,
    val nextLessonId: String?,
)

data class LessonListResult(
    val track: String,
    val completedCount: Int,
    val requiredCount: Int,
    val nextLessonId: String?,
    val items: List<LessonItemResult>,
)

data class LessonItemResult(
    val lessonId: String,
    val seq: Int,
    val title: String,
    val judgeType: String,
    val optional: Boolean,
    val completedAt: Instant?,
)

data class LessonDetailResult(
    val lessonId: String,
    val track: String,
    val seq: Int,
    val title: String,
    val optional: Boolean,
    val judgeType: String,
    val goal: String,
    val why: String,
    val tasks: List<LessonTaskResult>,
    val passCriteria: PassCriteriaResult?,
    val pitchNames: List<String>,
    val pitchNote: PitchNoteResult?,
    val parts: List<String>,
    val ranges: List<PitchRangeResult>,
    val songs: List<SongCandidateResult>,
    val source: String,
    val completedAt: Instant?,
)

data class LessonTaskResult(val title: String, val description: String?)

data class PassCriteriaResult(val title: String, val description: String)

data class PitchNoteResult(val text: String, val caution: String?)

data class PitchRangeResult(val blow: String, val label: String, val description: String, val firstPitch: String)

data class SongCandidateResult(val title: String, val meta: String)

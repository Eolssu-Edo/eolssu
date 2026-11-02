package com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.lesson.domain.LessonContent
import com.ssafy.eolssu.backend.feature.lesson.domain.LessonTask
import com.ssafy.eolssu.backend.feature.lesson.domain.PassCriteria
import com.ssafy.eolssu.backend.feature.lesson.domain.PitchNote
import com.ssafy.eolssu.backend.feature.lesson.domain.PitchRange
import com.ssafy.eolssu.backend.feature.lesson.domain.SongCandidate
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** `lessons.content_json`이 단계 상세 형식이 아니다. 마이그레이션으로만 넣는 값이라 서버 오류로 다룬다. */
class LessonContentException(lessonId: Long, problem: String) : IllegalStateException("lessons.content_json of $lessonId: $problem")

/**
 * V14가 넣는 단계 상세 JSON을 읽는다. 문자열·목록 칸은 반드시 있어야 하고, passCriteria·pitchNote는 null(또는 없음)일 수 있다.
 * 항목을 조용히 건너뛰면 화면 문구가 빠진 채 보이므로 형식이 다르면 실패한다.
 */
class LessonContentJson(private val jsonMapper: JsonMapper) {
    fun parse(lessonId: Long, contentJson: String): LessonContent {
        val node = try {
            jsonMapper.readTree(contentJson)
        } catch (_: JacksonException) {
            throw LessonContentException(lessonId, "not JSON")
        }
        val read = Reader(lessonId)
        return LessonContent(
            goal = read.text(node, "goal"),
            why = read.text(node, "why"),
            tasks = read.list(node, "tasks") { LessonTask(read.text(it, "title"), read.textOrNull(it, "description")) },
            passCriteria = read.objectOrNull(node, "passCriteria") { PassCriteria(read.text(it, "title"), read.text(it, "description")) },
            pitchNames = read.list(node, "pitchNames") { read.text(it) },
            pitchNote = read.objectOrNull(node, "pitchNote") { PitchNote(read.text(it, "text"), read.textOrNull(it, "caution")) },
            parts = read.list(node, "parts") { read.text(it) },
            ranges = read.list(node, "ranges") {
                PitchRange(read.text(it, "blow"), read.text(it, "label"), read.text(it, "description"), read.text(it, "firstPitch"))
            },
            songs = read.list(node, "songs") { SongCandidate(read.text(it, "title"), read.text(it, "meta")) },
            source = read.text(node, "source"),
        )
    }

    private class Reader(private val lessonId: Long) {
        fun text(node: JsonNode): String = node.takeIf { it.isString }?.asString() ?: throw LessonContentException(lessonId, "expected text")

        fun text(node: JsonNode, field: String): String =
            node.get(field)?.takeIf { it.isString }?.asString() ?: throw LessonContentException(lessonId, "$field must be text")

        fun textOrNull(node: JsonNode, field: String): String? {
            val value = node.get(field)
            return when {
                value == null || value.isNull -> null
                value.isString -> value.asString()
                else -> throw LessonContentException(lessonId, "$field must be text or null")
            }
        }

        fun <T> list(node: JsonNode, field: String, item: (JsonNode) -> T): List<T> =
            // JsonNode.map은 노드 자체에 함수를 적용하므로 values()로 항목을 꺼낸다.
            node.get(field)?.takeIf { it.isArray }?.values()?.map(item) ?: throw LessonContentException(lessonId, "$field must be a list")

        fun <T> objectOrNull(node: JsonNode, field: String, read: (JsonNode) -> T): T? {
            val value = node.get(field)
            return when {
                value == null || value.isNull -> null
                value.isObject -> read(value)
                else -> throw LessonContentException(lessonId, "$field must be an object or null")
            }
        }
    }
}

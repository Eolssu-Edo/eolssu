package com.ssafy.eolssu.testing

import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.data.user.LessonApi
import com.ssafy.eolssu.data.user.LessonDetail
import com.ssafy.eolssu.data.user.LessonItem
import com.ssafy.eolssu.data.user.LessonPitchNote
import com.ssafy.eolssu.data.user.LessonRange
import com.ssafy.eolssu.data.user.LessonText
import com.ssafy.eolssu.data.user.LessonTrack

/**
 * 학습 API(LRN-01·02·03)를 대신하는 메모리 서버. 트랙마다 7단계(V37로 8·9단계는 숨김)이고 단계 ID는 `트랙-단계`다.
 * 숨긴 8·9단계는 목록에 없고 상세는 받지 못한다(서버 404).
 * 완료 표시는 [owner](로그인한 회원)마다 따로 남는다.
 */
internal class FakeLessonApi(private val owner: () -> String? = { "me" }) : LessonApi {
    /** 회원 → 완료한 단계 ID. */
    val completed = mutableMapOf<String?, MutableSet<String>>()

    /** 완료 표시 요청(단계 ID, 표시 여부) 순서대로. */
    val completionCalls = mutableListOf<Pair<String, Boolean>>()

    /** false면 단계 목록·상세를 받지 못한다(오프라인). */
    var online = true

    /** 완료 표시를 남기지 못하는 단계 ID. */
    val failing = mutableSetOf<String>()

    fun id(trackId: String, seq: Int) = "$trackId-$seq"

    fun doneSeqs(trackId: String, member: String? = owner()): Set<Int> =
        (1..9).filter { id(trackId, it) in completed[member].orEmpty() }.toSet()

    override suspend fun track(trackId: String): LessonTrack? {
        if (!online) return null
        val done = completed[owner()].orEmpty()
        val items = (1..STAGES).map { seq -> LessonItem(id(trackId, seq), seq, "단계 $seq", judge(seq), false, id(trackId, seq) in done) }
        return LessonTrack(items, items.count { it.completed && !it.optional }, STAGES)
    }

    override suspend fun lesson(lessonId: String): LessonDetail? {
        if (!online) return null
        val seq = lessonId.substringAfterLast('-').toIntOrNull()?.takeIf { it in 1..STAGES } ?: return null
        return LessonDetail(
            lessonId, seq, "단계 $seq", judge(seq), false,
            goal = "목표 $seq", why = "이유 $seq", source = "출처 $seq",
            tasks = listOf(LessonText("할 일", "설명"), LessonText("제목만", null)),
            pass = if (seq in 3..7) LessonText("기준", "참고") else null,
            pitchNames = when (seq) {
                3 -> listOf("汰")
                4 -> listOf("汰", "潢", "無", "林", "仲")
                5 -> listOf("仲", "林", "無", "潢", "汰")
                else -> emptyList()
            },
            pitchNote = if (seq == 4) LessonPitchNote("순서 설명", "주의") else null,
            parts = if (seq == 1) listOf("취구") else emptyList(),
            ranges = if (seq == 6) listOf(LessonRange(Blow.High, "역취", "세게", "㳞")) else emptyList(),
            songs = emptyList(),
        )
    }

    override suspend fun setCompleted(lessonId: String, completed: Boolean): Boolean? {
        completionCalls += lessonId to completed
        if (!online || lessonId in failing) return null
        val set = this.completed.getOrPut(owner()) { mutableSetOf() }
        if (completed) set += lessonId else set -= lessonId
        return completed
    }

    private fun judge(seq: Int) = when (seq) {
        1, 2 -> "READ"
        in 3..6 -> "PITCH"
        else -> "BEAT"
    }

    private companion object {
        /** V37 뒤 공개 단계 수. */
        const val STAGES = 7
    }
}

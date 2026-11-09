package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.data.user.LessonDetail
import com.ssafy.eolssu.data.user.LessonItem
import com.ssafy.eolssu.data.user.LessonPitchNote
import com.ssafy.eolssu.data.user.LessonRange
import com.ssafy.eolssu.data.user.LessonSong
import com.ssafy.eolssu.data.user.LessonText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 서버 단계 목록·상세(LRN-01·02) → 화면 단계와 5단계 패턴 규칙. 단계 문구는 서버가 출처라 앱에 두지 않는다. */
class LearnCurriculumTest {

    @Test
    fun judgeCodesMapToBottomBarActionsAndUnknownIsRead() {
        assertEquals(
            listOf(LearnJudge.Read, LearnJudge.Pitch, LearnJudge.Beat, LearnJudge.Listen, LearnJudge.Read),
            listOf("READ", "PITCH", "BEAT", "LISTEN", "NEW").map(LearnJudge::of),
        )
    }

    @Test
    fun listItemBecomesStageWithLessonId() {
        val stage = LessonItem("42", 9, "장식음·시김새", "LISTEN", optional = true, completed = false).toStage()
        assertEquals(LearnStage(9, "장식음·시김새", LearnJudge.Listen, optional = true, lessonId = "42"), stage)
    }

    @Test
    fun detailKeepsServerTextAndTrackContent() {
        val detail = LessonDetail(
            "4", 4, "기본음", "PITCH", false,
            goal = "목표", why = "이유", source = "출처",
            tasks = listOf(LessonText("악기 잡기", "가로로 잡아요."), LessonText("굴리기", null)),
            pass = LessonText("음마다 2초", null),
            pitchNames = listOf("汰", "潢"),
            pitchNote = LessonPitchNote("순서", "주의"),
            parts = listOf("취구"),
            ranges = listOf(LessonRange(Blow.Low, "저취", "느슨하게", "㑣")),
            songs = listOf(LessonSong("아리랑", "세마치장단")),
        ).toStageDetail()

        assertEquals(listOf(LearnTask("악기 잡기", "가로로 잡아요."), LearnTask("굴리기", null)), detail.tasks)
        assertEquals(LearnPass("음마다 2초", ""), detail.pass)
        assertEquals("순서" to "주의", detail.pitchNote to detail.pitchCaution)
        assertEquals(listOf(LearnRange(Blow.Low, "저취", "느슨하게", "㑣")), detail.ranges)
        assertEquals(listOf(LearnSong("아리랑", "세마치장단")), detail.songs)
        assertEquals(listOf("취구"), detail.parts)
    }

    @Test
    fun detailWithoutOptionalPartsHasNoPassOrNote() {
        val detail = LessonDetail(
            "1", 1, "악기 알기", "READ", false, "목표", "이유", "출처",
            emptyList(), null, emptyList(), null, emptyList(), emptyList(), emptyList(),
        ).toStageDetail()
        assertNull(detail.pass)
        assertNull(detail.pitchNote)
        assertNull(detail.pitchCaution)
    }

    @Test
    fun patternsFollowWireframeOrder() {
        val scale = listOf("仲", "林", "無", "潢", "汰")
        assertEquals(listOf("仲", "林", "無", "潢", "汰"), LearnPattern.Ascending.notes(scale))
        assertEquals(listOf("汰", "潢", "無", "林", "仲"), LearnPattern.Descending.notes(scale))
        assertEquals(listOf("仲", "無", "汰", "無", "仲"), LearnPattern.SkipOne.notes(scale))
        assertEquals(listOf("仲", "潢", "林", "汰", "林"), LearnPattern.SkipTwo.notes(scale))
        assertEquals(listOf("無", "林", "仲", "林", "無"), LearnPattern.ThreeNoteSong.notes(scale))
    }

    @Test
    fun shortScaleMakesNoPattern() {
        LearnPattern.entries.forEach { assertEquals(emptyList(), it.notes(listOf("仲", "林"))) }
    }

    @Test
    fun plainDropsSourceAndExampleMarksButKeepsMeaningfulParentheses() {
        assertEquals("음료수병을 불듯 가늘고 곧게 불어요.", "음료수병을 불듯 가늘고 곧게 불어요(대금 기초편 7차시).".plain())
        assertEquals("목표 음과 ±25센트 이내면 통과.", "목표 음과 ±25센트 이내면 통과(예시).".plain())
        assertEquals("요성(떠는 소리)·퇴성(흘러내리는 소리)", "요성(떠는 소리)·퇴성(흘러내리는 소리)".plain())
    }
}

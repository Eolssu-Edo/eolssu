package com.ssafy.eolssu.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 완료 알림 '열기'가 다른 내 악보 화면 위에서도 알림의 악보를 여는지(S15P31E102-1038). */
class SheetCompletionNavigationTest {
    private fun args(vararg pairs: Pair<String, Any>): (String) -> Any? = mapOf(*pairs)::get

    @Test
    fun anotherMineSheetOnTopStillOpensTheNotifiedSheet() {
        assertFalse(showsSheet(EolssuDestination.PRACTICE, args("songId" to "11", "mine" to true), "12", SheetCompletionTarget.Practice))
        assertFalse(showsSheet(SheetEditDestination.EDIT, args("from" to "mine", "id" to "11"), "12", SheetCompletionTarget.Editor))
    }

    @Test
    fun sameSheetOnTopIsNotStackedAgain() {
        assertTrue(showsSheet(EolssuDestination.PRACTICE, args("songId" to "12", "mine" to true), "12", SheetCompletionTarget.Practice))
        assertTrue(showsSheet(SheetEditDestination.EDIT, args("from" to "mine", "id" to "12"), "12", SheetCompletionTarget.Editor))
    }

    @Test
    fun audioCompletionOpensPracticeWhereverPersonalAudioCanPlay() {
        // S15P31E102-1261: 웹은 스트리밍으로 틀 수 있어 앱처럼 연습으로 간다. 틀 수 없는 플랫폼만 편집기로 간다.
        assertEquals(SheetCompletionTarget.Practice, sheetAudioCompletionTarget(personalAudioPlayable = true))
        assertEquals(SheetCompletionTarget.Editor, sheetAudioCompletionTarget(personalAudioPlayable = false))
    }

    @Test
    fun otherScreensWithTheSameIdAreNotTheTarget() {
        // 같은 ID의 곡 연습(mine 아님)·복사 편집·다른 대상 화면은 알림의 악보 화면이 아니다.
        assertFalse(showsSheet(EolssuDestination.PRACTICE, args("songId" to "12", "mine" to false), "12", SheetCompletionTarget.Practice))
        assertFalse(showsSheet(SheetEditDestination.EDIT, args("from" to "sheet", "id" to "12"), "12", SheetCompletionTarget.Editor))
        assertFalse(showsSheet(SheetEditDestination.EDIT, args("from" to "mine", "id" to "12"), "12", SheetCompletionTarget.Practice))
        assertFalse(showsSheet(EolssuDestination.HOME, args(), "12", SheetCompletionTarget.Editor))
        assertFalse(showsSheet(null, args(), "12", SheetCompletionTarget.Editor))
    }
}

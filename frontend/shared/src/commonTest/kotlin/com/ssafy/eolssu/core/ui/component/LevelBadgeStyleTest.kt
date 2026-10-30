package com.ssafy.eolssu.core.ui.component

import com.ssafy.eolssu.core.model.SongLevel
import kotlin.test.Test
import kotlin.test.assertEquals

/** 난이도 배지는 공통 컴포넌트 보드의 `.lv.l1~l3` 순서(입문 · 초급 · 중급)를 따른다. */
class LevelBadgeStyleTest {
    @Test
    fun eachLevelUsesItsBoardStep() {
        assertEquals(EolssuBadgeStyle.Level1, levelBadgeStyle(SongLevel.Intro))
        assertEquals(EolssuBadgeStyle.Level2, levelBadgeStyle(SongLevel.Elementary))
        assertEquals(EolssuBadgeStyle.Level3, levelBadgeStyle(SongLevel.Intermediate))
    }
}

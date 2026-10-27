package com.ssafy.eolssu.core.ui.score

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 정간보 크기. 화면마다 프리셋을 고르거나 `copy`로 바꾼다.
 * 한 정간을 나눈 음은 [yulSize]에 [splitScale]을 곱한 크기로 줄여 그린다.
 */
@Immutable
data class JeongganboMetrics(
    val columnWidth: Dp,
    val cellHeight: Dp,
    val yulSize: TextUnit,
    val auxSize: TextUnit,
    val headHeight: Dp = 20.dp,
    val columnGap: Dp = 6.dp,
) {
    /** 한 정간에 든 음 수에 맞춘 율명 크기. 와이어프레임의 16 → 12 → 10 비율을 따른다. */
    fun yulSizeFor(noteCount: Int): TextUnit = yulSize * splitScale(noteCount)

    fun auxSizeFor(noteCount: Int): TextUnit = if (noteCount > 1) auxSize * 0.88f else auxSize

    companion object {
        /** 휴대폰의 곡 상세 미리보기 등 기본 크기. */
        val Phone = JeongganboMetrics(columnWidth = 46.dp, cellHeight = 38.dp, yulSize = 16.sp, auxSize = 8.5.sp)

        /** 태블릿 기본 크기. */
        val Tablet = JeongganboMetrics(columnWidth = 50.dp, cellHeight = 54.dp, yulSize = 18.sp, auxSize = 10.sp)

        private fun splitScale(noteCount: Int): Float = when {
            noteCount <= 1 -> 1f
            noteCount == 2 -> 0.75f
            else -> 0.625f
        }
    }
}

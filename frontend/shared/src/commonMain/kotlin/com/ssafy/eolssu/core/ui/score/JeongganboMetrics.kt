package com.ssafy.eolssu.core.ui.score

import androidx.compose.runtime.Immutable
import com.ssafy.eolssu.core.model.Jangdan
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 정간보 모양. [Style.Classic]은 옛 와이어프레임(각 바탕 `background`, 현재 정간 연한 바탕·테두리, 지난 정간 흐리게),
 * [Style.Board]는 연두 보드 `.ms`(S15P31E102-308 덮어쓰기): 흰 각 · 머리 `background` · 모든 정간 흰 바탕 ·
 * 지난 정간은 글자만 회색 · 현재 정간은 2dp 안쪽 테두리(연주 중 붉은색) · 진행 선은 붉은색 · 대강 선 2dp 먹색 · ㅡ·△는 산세리프.
 *
 * @property lyricLane 곁줄(가사 칸) 너비. 보드 25dp(크게 보기 34dp). 가사 데이터가 없으면 빈 칸으로 둔다.
 * @property headCompact 각 머리를 ‘시작 · 1각’·‘2각’ 한 줄로 적는다(보드 `.ms-lab`). 아니면 ‘시작’ + 번호.
 * @property daegangWhenFits 공통 악보라도 각 길이가 장단의 정간 수와 같으면 대강 선을 긋는다(보드).
 * @property upcomingMark 연주 중 각의 마지막 정간에 들어서면 다음 각 머리를 ‘← 다음’으로 강조한다(보드).
 * @property joined 각 사이를 띄우지 않고 붙여 바깥 테두리 하나로 감싼다(보드 결과 `.ms`: 바깥 `#8E887D`, 각 경계 `#B9B3A6`).
 * @property headStartPill 첫 각 머리에 연두 ‘시작’ 알약을 번호 앞에 둔다(보드 결과 `.ms-lab`).
 * @property soft 옛 모양에서 각 바탕을 흰색, 각·정간 선을 옅은 `#E8E4DA`, 현재 칸 바탕을 `#EFF2D6`로 그린다(보드 RECORD-02).
 * @property softDaegang 보드의 대강 선(2dp)을 먹색 대신 회갈색([com.ssafy.eolssu.core.theme.EolssuColors.lineStrong])으로 긋는다.
 *   틀린 종류 색이 함께 보이는 평가 결과·틀린 부분 악보에서 경계가 오류 표시보다 튀지 않게 한다.
 */
@Immutable
data class JeongganboLook(
    val style: Style = Style.Classic,
    val lyricLane: Dp? = null,
    val headCompact: Boolean = false,
    val daegangWhenFits: Boolean = false,
    val upcomingMark: Boolean = false,
    val joined: Boolean = false,
    val headStartPill: Boolean = false,
    val soft: Boolean = false,
    val softDaegang: Boolean = false,
    /** 율명 오른쪽 시김새 그림의 높이(율명 글자 크기 대비). 편집기는 율명이 주가 되도록 조금 더 작게 쓴다. */
    val sigimsaeScale: Float = 0.7f,
) {
    enum class Style { Classic, Board }

    val board: Boolean get() = style == Style.Board

    companion object {
        val Classic = JeongganboLook()

        /** 연습 화면 PRACTICE-01의 악보(`.practice-score .ms`). */
        fun practice(big: Boolean) = JeongganboLook(
            style = Style.Board, lyricLane = if (big) 34.dp else 25.dp, headCompact = true, daegangWhenFits = true, upcomingMark = true,
        )

        /**
         * 연습 화면을 뺀 모든 화면의 공통 모양([ReadingScoreFormat]): 연습과 같은 보드에 대강 선은 회갈색이고 ‘← 다음’ 강조는 없다.
         * 곁줄은 [readingScoreFormat]이 곡에 가사가 있을 때만 둔다.
         */
        val Reading = practice(big = false).copy(upcomingMark = false, softDaegang = true)
    }
}

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
    /** 한 정간을 두 음으로 나눴을 때 율명 크기 비율. 옛 와이어프레임 0.75, 연두 보드 `.n2` 14/21 ≈ 0.667. */
    val splitScaleTwo: Float = 0.75f,
    /** 세 소박 이상으로 나눈 율명 크기 비율. 화면별로 글자·행 높이를 함께 조정할 때 쓴다. */
    val splitScaleMany: Float = 0.625f,
    /**
     * 0보다 크면 위아래로 나뉜 정간의 율명을 한 줄 높이의 이 비율까지 키운다([splitYulSize]). [yulSize]보다 크게 하지 않는다.
     * 기본 0은 나뉜 수로 정한 고정 크기([yulSizeFor])만 쓴다. 휴대폰 연습 악보만 켠다(S15P31E102-560).
     */
    val splitLineFill: Float = 0f,
) {
    /** sp 글꼴 확대와 같은 기하. 부모의 줄 나누기와 실제 렌더가 함께 사용한다. */
    fun forFontScale(scale: Float): JeongganboMetrics {
        val factor = scale.coerceAtLeast(1f)
        return copy(columnWidth = columnWidth * factor, cellHeight = cellHeight * factor, headHeight = headHeight * factor)
    }

    /** 한 정간에 든 음 수에 맞춘 율명 크기. 와이어프레임의 16 → 12 → 10 비율을 따른다. */
    fun yulSizeFor(noteCount: Int): TextUnit = yulSize * splitScale(noteCount, splitScaleTwo, splitScaleMany)

    /**
     * 위아래로 나뉜 정간에서 한 줄 높이가 [lineHeight]일 때의 율명 크기. [splitLineFill]이 꺼져 있으면 [yulSizeFor]와 같다.
     * 켜져 있으면 줄 높이 × [splitLineFill]을 글꼴 배율로 나눈 sp까지 키우되 [yulSizeFor]보다 작게, [yulSize]보다 크게 하지 않는다.
     */
    fun splitYulSize(noteCount: Int, lineHeight: Dp, fontScale: Float): TextUnit {
        val base = yulSizeFor(noteCount)
        if (splitLineFill <= 0f || noteCount <= 1 || fontScale <= 0f) return base
        val grown = lineHeight.value * splitLineFill / fontScale
        return grown.coerceIn(base.value, maxOf(base.value, yulSize.value)).sp
    }

    fun auxSizeFor(noteCount: Int): TextUnit = if (noteCount > 1) auxSize * 0.88f else auxSize

    companion object {
        /** 휴대폰의 곡 상세 미리보기 등 기본 크기. */
        val Phone = JeongganboMetrics(columnWidth = 46.dp, cellHeight = 38.dp, yulSize = 16.sp, auxSize = 8.5.sp)

        /** 태블릿 기본 크기. */
        val Tablet = JeongganboMetrics(columnWidth = 50.dp, cellHeight = 54.dp, yulSize = 18.sp, auxSize = 10.sp)

        /**
         * 연습 화면 크기. 한 줄의 정간이 [availableHeight]에 세로 스크롤 없이 들어가도록 정간 높이를 맞춘다.
         * 한 줄이 11정간 이상인 장단은 글자를 줄이고([dense]), 크게 보기([big])는 각·글자를 키운다(와이어프레임 `fitCellsIn`).
         * [tablet]이면 각·글자를 태블릿 크기(각 72dp, 크게 보기 92dp)로 그리고 정간은 [practiceMinCellHeight] 이상이다.
         */
        fun practice(jangdan: Jangdan?, availableHeight: Dp, big: Boolean, tablet: Boolean = false, longestStrip: Int = jangdan?.strips?.max() ?: Jangdan.MAX_STRIP_CELLS): JeongganboMetrics {
            val rows = longestStrip.coerceAtLeast(1)
            if (tablet) return tabletPractice(rows, availableHeight, big)
            val dense = rows >= 11
            val minHeight = when {
                big -> 60.dp
                dense -> 28.dp
                else -> 40.dp
            }
            val fitted = ((availableHeight - 20.dp - 14.dp) / rows).let { if (it < minHeight) minHeight else it }
            return when {
                big -> JeongganboMetrics(columnWidth = 72.dp, cellHeight = fitted, yulSize = 26.sp, auxSize = 11.sp)
                dense -> JeongganboMetrics(columnWidth = if (tablet) 64.dp else 50.dp, cellHeight = fitted, yulSize = 16.sp, auxSize = 8.5.sp)
                else -> JeongganboMetrics(columnWidth = if (tablet) 64.dp else 56.dp, cellHeight = fitted, yulSize = 19.sp, auxSize = 9.5.sp)
            }
        }

        /**
         * 연두 보드 PRACTICE-01 휴대폰 악보(`.practice-score .ms`): 각 78dp(크게 보기 104), 머리 22dp, 정간 44dp 고정
         * (11정간 이상 줄은 32, 크게 보기 60), 율명 21sp(18 · 26), 보조 9sp(9 · 11), 각 사이 6dp. 세로로 넘치면 화면이 스크롤된다.
         */
        fun practiceBoard(big: Boolean, longestStrip: Int): JeongganboMetrics {
            val dense = longestStrip >= 11
            return when {
                big -> JeongganboMetrics(columnWidth = 104.dp, cellHeight = 60.dp, yulSize = 26.sp, auxSize = 11.sp, headHeight = 22.dp, splitScaleTwo = 17f / 26f)
                dense -> JeongganboMetrics(columnWidth = 78.dp, cellHeight = 32.dp, yulSize = 18.sp, auxSize = 9.sp, headHeight = 22.dp, splitScaleTwo = 12f / 18f)
                else -> JeongganboMetrics(columnWidth = 78.dp, cellHeight = 44.dp, yulSize = 21.sp, auxSize = 9.sp, headHeight = 22.dp, splitScaleTwo = 14f / 21f)
            }
        }

        /** 태블릿 연습 화면의 정간 최소 높이. 와이어프레임 `cellMinH`. */
        fun practiceMinCellHeight(big: Boolean): Dp = if (big) 60.dp else 44.dp

        /** 태블릿 연습 화면의 각 너비. 와이어프레임 `colW`. */
        fun tabletPracticeColumnWidth(big: Boolean): Dp = if (big) 92.dp else 72.dp

        private fun tabletPractice(rows: Int, availableHeight: Dp, big: Boolean): JeongganboMetrics {
            val minHeight = practiceMinCellHeight(big)
            val fitted = ((availableHeight - 20.dp - 12.dp) / rows).let { if (it < minHeight) minHeight else it }
            return JeongganboMetrics(
                columnWidth = tabletPracticeColumnWidth(big),
                cellHeight = fitted,
                yulSize = if (big) 30.sp else 24.sp,
                auxSize = if (big) 12.sp else 11.sp,
            )
        }

        private fun splitScale(noteCount: Int, two: Float, many: Float): Float = when {
            noteCount <= 1 -> 1f
            noteCount == 2 -> two
            else -> many
        }
    }
}

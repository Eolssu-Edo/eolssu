package com.ssafy.eolssu.feature.practice

import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreUnit
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.scoreStrips
import com.ssafy.eolssu.feature.practice.component.PracticeSplitLineFill
import com.ssafy.eolssu.feature.practice.component.practiceScorePacking
import com.ssafy.eolssu.data.song.Song2Json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PracticeScorePackingTest {
    private val note = ScoreNote.Pitch(Yul.Hwangjong)
    private val cell = ScoreCell(note)
    private val metrics = JeongganboMetrics.practiceBoard(false, 3)

    @Test
    fun shorterColumnsStretchTheirCellsToAlignBothBordersAtEachRow() {
        val triple = ScoreCell(note, note, note)
        val score = Score(List(10) { column -> List(3) { if (column in listOf(3, 7, 9)) triple else cell } })
        val packing = practiceScorePacking(score, metrics, 550.dp)
        assertEquals(3, packing.columnsPerStack)
        for ((column, cells) in score.columns.withIndex()) {
            val renderedHeight = metrics.headHeight + cells.indices.fold(0.dp) { total, row ->
                total + (packing.cellHeights[column to row] ?: packing.metrics.cellHeight)
            }
            assertEquals(packing.stackRowHeights[column % 3] - 2.dp, renderedHeight, "${column + 1}각 하단")
        }
        assertTrue(packing.cellHeights.getValue(0 to 0) > packing.metrics.cellHeight)
    }

    @Test
    fun stretchPreservesThreePartRatioAndWorksWithOneAnglePerStack() {
        val triple = ScoreCell(note, note, note)
        val score = Score(listOf(listOf(cell, triple), List(4) { cell }))
        // 두 각을 쌓으면 정간이 14dp보다 작아지는 높이: 한 줄에 한 각을 두고 정간을 줄인다.
        val packing = practiceScorePacking(score, metrics, 150.dp)
        assertEquals(1, packing.columnsPerStack)
        val normal = assertNotNull(packing.cellHeights[0 to 0])
        val expanded = assertNotNull(packing.cellHeights[0 to 1])
        assertEquals(normal * 1.5f, expanded)
        assertEquals(packing.stackRowHeights[0] - 2.dp, metrics.headHeight + normal + expanded)
        assertTrue(packing.height <= 150.dp)
    }

    @Test
    fun eachStackUsesTheTallestColumnAtEverySharedRow() {
        val triple = ScoreCell(note, note, note)
        val score = Score(List(12) { column -> List(3) { if (column in listOf(3, 7, 11)) triple else cell } })
        val packing = practiceScorePacking(score, metrics, 550.dp)
        assertEquals(3, packing.columnsPerStack)
        // 4각·8각·12각이 각각 첫째·둘째·셋째 가로줄의 가장 높은 각이다.
        val tallest = metrics.headHeight + packing.cellHeights.getValue(3 to 0) * 3 + 2.dp
        assertEquals(listOf(tallest, tallest, tallest), packing.stackRowHeights)
    }

    @Test
    fun incompleteLastStackAlsoSetsTheCommonRowHeight() {
        val triple = ScoreCell(note, note, note)
        val score = Score(List(10) { column -> List(3) { if (column == 9) triple else cell } })
        val packing = practiceScorePacking(score, metrics, 550.dp)
        assertEquals(3, packing.columnsPerStack)
        val tall = metrics.headHeight + packing.cellHeights.getValue(9 to 0) * 3 + 2.dp
        val normal = metrics.headHeight + packing.metrics.cellHeight * 3 + 2.dp
        val expectedHeights = listOf(tall, normal, normal)
        assertEquals(expectedHeights.size, packing.stackRowHeights.size)
        expectedHeights.forEachIndexed { index, expectedHeight ->
            assertEquals(
                expected = expectedHeight.value,
                actual = packing.stackRowHeights[index].value,
                absoluteTolerance = 0.0001f,
                message = "묶음 가로줄[$index] 높이",
            )
        }
        assertTrue(packing.height < 550.dp)
    }

    @Test
    fun shortColumnsFitThreePerStackWithinAvailableHeight() {
        val score = Score(List(12) { List(3) { cell } })
        val packing = practiceScorePacking(score, metrics, 500.dp)
        assertEquals(3, packing.columnsPerStack)
        assertEquals(44.dp, packing.metrics.cellHeight)
        assertTrue(packing.height <= 500.dp)
    }

    @Test
    fun threePartCellsGrowAndFitWithoutDroppingAnyColumn() {
        val triple = ScoreCell(note, note, note)
        val score = Score(List(12) { listOf(cell, triple, cell) })
        val packing = practiceScorePacking(score, metrics, 510.dp)
        assertEquals(3, packing.columnsPerStack)
        assertTrue(packing.cellHeights.getValue(0 to 1) > 44.dp)
        assertTrue(packing.height <= 510.dp)
        assertEquals((0..11).toList(), packing.columnGroups.flatten())
        assertEquals(listOf(0, 1, 2), packing.columnGroups[0])
        assertEquals(listOf(3, 4, 5), packing.columnGroups[1])
        assertEquals(listOf(6, 7, 8), packing.columnGroups[2])
    }

    @Test
    fun sourceUnitsCountVerticalRowsInsteadOfNotesBesideEachOther() {
        val horizontal = ScoreCell(listOf(note, note, note), units = List(3) { ScoreUnit(0, it, "黃", "h$it") })
        val vertical = ScoreCell(listOf(note, note, note), units = List(3) { ScoreUnit(it, 0, "黃", "v$it") })
        val packing = practiceScorePacking(Score(listOf(listOf(horizontal, vertical))), metrics, 500.dp)
        assertEquals(44.dp, packing.cellHeights[0 to 0] ?: packing.metrics.cellHeight)
        assertEquals(66.dp, packing.cellHeights[0 to 1])
    }

    @Test
    fun denseThreePartColumnsStillFitThreeStacksWithTallerSubdividedCells() {
        val triple = ScoreCell(note, note, note)
        val score = Score(List(20) { List(3) { triple } })
        val packing = practiceScorePacking(score, metrics, 550.dp)
        assertEquals(3, packing.columnsPerStack)
        assertTrue(packing.cellHeights.getValue(0 to 0) > 44.dp)
        assertTrue(packing.height < 550.dp)
    }

    @Test
    fun dorajiCatalogFixtureFitsThreeColumnsInRecordingSpace() {
        val score = dorajiCatalogScore()
        val packing = practiceScorePacking(score, metrics, 500.dp)
        assertEquals(3, packing.columnsPerStack)
        assertEquals(28, packing.columnGroups.flatten().size)
        score.columns.forEachIndexed { column, cells ->
            cells.forEachIndexed { row, cell ->
                if (cell.units.map { it.row }.distinct().size >= 3) {
                    assertTrue(packing.cellHeights.getValue(column to row) > packing.metrics.cellHeight)
                }
            }
        }
        assertTrue(packing.height < 500.dp)
    }

    @Test
    fun limitedHeightAndLargeFontsKeepStackingWithinTheHeight() {
        val score = Score(List(7) { List(3) { cell } })
        assertEquals(2, practiceScorePacking(score, metrics, 300.dp).columnsPerStack)
        val large = JeongganboMetrics.practiceBoard(true, 3)
        val packing = practiceScorePacking(score, large, 500.dp, fontScale = 1.5f)
        assertEquals(2, packing.columnsPerStack)
        assertTrue(packing.metrics.cellHeight >= 44.dp)
        assertTrue(packing.height <= 500.dp)
        assertEquals((0..6).toList(), packing.columnGroups.flatten())
    }

    @Test
    fun enlargedSystemFontStillStacksShortColumnsWithoutVerticalScroll() {
        // 글꼴 1.15배 휴대폰(악보 영역 약 420dp)에서 3정간 각: 1각 아래 2각, 왼쪽에 3·4각.
        val score = Score(List(28) { List(3) { cell } })
        for (scale in listOf(1.15f, 1.3f)) {
            val packing = practiceScorePacking(score, metrics, 420.dp, fontScale = scale)
            assertTrue(packing.columnsPerStack >= 2, "글꼴 ${scale}배 묶음 수")
            assertEquals(listOf(0, 1), packing.columnGroups[0].take(2))
            assertTrue(packing.metrics.cellHeight >= 32.dp)
            assertTrue(renderedHeight(packing, scale) <= 420.dp, "글꼴 ${scale}배 높이")
        }
    }

    @Test
    fun catalogDorajiShowsOneColumnPerStackWhenStackingWouldMakeCellsTooSmall() {
        // SM-S936N 글꼴 1.15배 도라지타령 연습 화면의 악보 영역(약 226dp). 두 각을 쌓으면 정간이 24dp보다 작아진다.
        val score = dorajiCatalogScore()
        val packing = practiceScorePacking(score, metrics, 226.dp, fontScale = 1.15f)
        assertEquals(1, packing.columnsPerStack)
        assertEquals(listOf(0), packing.columnGroups[0])
        assertTrue(packing.metrics.cellHeight >= 24.dp)
        assertTrue(renderedHeight(packing, 1.15f) <= 226.dp)
    }

    @Test
    fun shortColumnsStackTwoOnlyWhileCellsStayAtLeastTheStackMinimum() {
        // 3정간 각: 두 각을 쌓은 정간 = (높이 − 68dp) ÷ 6. 212dp면 24dp로 쌓고, 211dp면 한 각씩 둔다.
        val score = Score(List(6) { List(3) { cell } })
        val stacked = practiceScorePacking(score, metrics, 212.dp)
        assertEquals(2, stacked.columnsPerStack)
        assertEquals(24.dp, stacked.metrics.cellHeight)
        assertTrue(stacked.height <= 212.dp)

        val single = practiceScorePacking(score, metrics, 211.dp)
        assertEquals(1, single.columnsPerStack)
        assertEquals(metrics.cellHeight, single.metrics.cellHeight)
        assertEquals(metrics.yulSize, single.metrics.yulSize)
        assertTrue(single.height <= 211.dp)
    }

    @Test
    fun splitCellsStackOnlyWhenTheirGrownGlyphReachesSixteenSp() {
        // 휴대폰 연습 악보: 나뉜 정간의 율명 = 한 줄(정간 ÷ 2) × 0.8. 16sp가 되려면 정간 40dp가 필요하다.
        val practice = metrics.copy(splitLineFill = PracticeSplitLineFill)
        // 2줄 정간: 두 각을 쌓은 정간 = (높이 − 68dp) ÷ 6. 300dp는 약 38.7dp라 일반 기준(32dp)은 넘지만 한 각씩 둔다.
        val double = Score(List(6) { List(3) { ScoreCell(note, note) } })
        val single = practiceScorePacking(double, practice, 300.dp)
        assertEquals(1, single.columnsPerStack)
        assertEquals(metrics.cellHeight, single.metrics.cellHeight)
        val stacked = practiceScorePacking(double, practice, 308.dp)
        assertEquals(2, stacked.columnsPerStack)
        assertEquals(40.dp, stacked.metrics.cellHeight)

        // 3줄 정간은 정간을 1.5배로 키워 한 줄이 역시 정간 ÷ 2다. 두 각을 쌓은 정간 = (높이 − 68dp) ÷ 9.
        val triple = Score(List(6) { List(3) { ScoreCell(note, note, note) } })
        assertEquals(1, practiceScorePacking(triple, practice, 420.dp).columnsPerStack)
        assertEquals(2, practiceScorePacking(triple, practice, 428.dp).columnsPerStack)

        // 글자를 키우지 않는 화면(결과·비교 등과 같은 기본 메트릭)은 기존 기준대로 32dp에서 쌓는다.
        assertEquals(2, practiceScorePacking(triple, metrics, 420.dp).columnsPerStack)
    }

    @Test
    fun splitGlyphGrowsWithLineHeightOnlyWhenEnabled() {
        val practice = metrics.copy(splitLineFill = PracticeSplitLineFill)
        // 3줄 고정 크기 21 × 0.625 = 13.125sp. 한 줄 22dp × 0.8 = 17.6sp까지 키우고, 21sp보다 크게 하지 않는다.
        assertEquals(13.125f, metrics.splitYulSize(3, 22.dp, 1f).value, 0.001f)
        assertEquals(17.6f, practice.splitYulSize(3, 22.dp, 1f).value, 0.001f)
        assertEquals(13.125f, practice.splitYulSize(3, 16.dp, 1f).value, 0.001f)
        assertEquals(21f, practice.splitYulSize(2, 40.dp, 1f).value, 0.001f)
        // 글꼴 1.3배에서는 줄도 1.3배라 같은 sp가 된다.
        assertEquals(17.6f, practice.splitYulSize(3, 22.dp * 1.3f, 1.3f).value, 0.001f)
        assertEquals(21f, practice.splitYulSize(1, 22.dp, 1f).value, 0.001f)
    }

    @Test
    fun longColumnsDoNotShrinkToStackTwo() {
        val packing = practiceScorePacking(Score(List(6) { List(8) { cell } }), metrics, 330.dp)
        assertEquals(1, packing.columnsPerStack)
        assertTrue(packing.metrics.cellHeight >= 32.dp)
    }

    @Test
    fun tooLowAreaShrinksCellsAndTextTogetherInsteadOfScrolling() {
        // 8정간 각을 두 줄(4·4)로 나눠도 들어가지 않는 높이: 나눈 채로 정간과 글자를 함께 줄인다.
        val score = Score(List(6) { List(8) { cell } })
        val packing = practiceScorePacking(score, metrics, 140.dp, fontScale = 1.3f)
        assertEquals(1, packing.columnsPerStack)
        assertTrue(packing.splitColumns)
        assertTrue(packing.metrics.cellHeight < 32.dp)
        assertTrue(renderedHeight(packing, 1.3f) <= 140.dp)
        val ratio = packing.metrics.cellHeight / 32.dp
        assertEquals(metrics.yulSize.value * ratio, packing.metrics.yulSize.value, 0.01f)
        assertEquals(metrics.auxSize.value * ratio, packing.metrics.auxSize.value, 0.01f)
    }

    @Test
    fun veryLowAreaNeverClipsAColumnEvenWithTinyText() {
        // 360×640dp 휴대폰에서 아리랑 각(12정간 중 8개가 3소박)의 악보 영역은 약 183dp다. 두 줄로 나눈 뒤에도 아래가 잘리면 안 된다.
        val score = Score(List(4) { arirangColumn() })
        for (scale in listOf(1f, 1.3f)) {
            val packing = practiceScorePacking(score, metrics, 183.dp, fontScale = scale)
            assertTrue(packing.splitColumns)
            assertTrue(renderedHeight(packing, scale) <= 183.dp, "글꼴 ${scale}배 높이 ${renderedHeight(packing, scale)}")
            assertTrue(packing.metrics.cellHeight > 0.dp)
            assertTrue(packing.metrics.yulSize < metrics.yulSize)
        }
    }

    @Test
    fun columnThatDoesNotFitAtMinimumSplitsInTwoAtTheMiddleAndGetsTallerCells() {
        // S15P31E102-654 단소 아리랑: 한 각(12정간)을 한 줄에 넣으면 정간이 약 15dp까지 준다. 6·6 두 줄로 나누면 두 배 가까이 커진다.
        val score = Score(List(4) { arirangColumn() })
        val packing = practiceScorePacking(score, metrics, 300.dp)
        assertTrue(packing.splitColumns)
        assertEquals(1, packing.columnsPerStack)
        // 한 줄 무게 8(정간 2 + 3줄 정간 4×1.5): (300 − 14 − 머리 − 2) ÷ 8
        val expected = (300.dp - 14.dp - metrics.headHeight - 2.dp) / 8f
        assertEquals(expected.value, packing.metrics.cellHeight.value, 0.01f)
        assertTrue(renderedHeight(packing, 1f) <= 300.dp)
        // 원본 좌표마다 높이가 있고 3줄 정간은 1.5배다.
        assertEquals(packing.metrics.cellHeight * 1.5f, packing.cellHeights.getValue(0 to 1))
        val strips = scoreStrips(score, null, splitColumns = true)
        assertEquals(8, strips.size)
        assertEquals(listOf(0 until 6, 6 until 12), strips.filter { it.column == 0 }.map { it.rows })
        // 각 번호 머리는 첫 줄(strip 0)에만 있다.
        assertEquals(listOf(0, 1), strips.filter { it.column == 0 }.map { it.strip })
    }

    @Test
    fun twelveCellGakStaysOneColumnWhenItFitsAtTheReadableFloor() {
        // S15P31E102-922 정악대금 한오백년: 12정간 각이 32dp로는 안 들어가도 24dp 이상이면 6·6으로 쪼개지 않고 한 각을 한 줄로 둔다.
        val score = Score(List(4) { List(12) { cell } })
        val height = 14.dp + metrics.headHeight + 2.dp + 28.dp * 12
        val packing = practiceScorePacking(score, metrics, height)
        assertTrue(!packing.splitColumns)
        assertEquals(1, packing.columnsPerStack)
        assertEquals(28f, packing.metrics.cellHeight.value, 0.01f)
        assertTrue(renderedHeight(packing, 1f) <= height)
        // 글자도 정간과 같은 비율로 줄인다.
        assertEquals(metrics.yulSize.value * 28f / 32f, packing.metrics.yulSize.value, 0.01f)
    }

    @Test
    fun columnsThatFitAtMinimumKeepOneLinePerColumn() {
        val score = Score(List(4) { arirangColumn() })
        val packing = practiceScorePacking(score, metrics, 700.dp)
        assertTrue(!packing.splitColumns)
        assertEquals(1, packing.columnsPerStack)
    }

    @Test
    fun knownJangdanSplitsAtTheDaegangBoundaryNearestTheMiddle() {
        val semachi = Jangdan("세마치", listOf(3, 3, 3), beats = 3, subdivision = 3)
        assertEquals(6, com.ssafy.eolssu.core.ui.score.columnSplitAt(9, semachi))
        val gutgeori = Jangdan("굿거리", listOf(3, 3, 3, 3), beats = 4, subdivision = 3)
        assertEquals(6, com.ssafy.eolssu.core.ui.score.columnSplitAt(12, gutgeori))
        // 장단과 각 길이가 다르거나 장단을 모르면 가운데에서 나눈다.
        assertEquals(6, com.ssafy.eolssu.core.ui.score.columnSplitAt(12, semachi))
        assertEquals(5, com.ssafy.eolssu.core.ui.score.columnSplitAt(9, null))
    }

    private fun arirangColumn(): List<ScoreCell> {
        val triple = ScoreCell(note, note, note)
        return listOf(cell, triple, triple, cell, triple, triple, cell, triple, triple, cell, triple, triple)
    }

    private fun dorajiCatalogScore(): Score {
        val columns = Json.parseToJsonElement(Song2Json).jsonObject.getValue("score").jsonObject.getValue("columns").jsonArray
        return Score(columns.map { column ->
            column.jsonObject.getValue("cells").jsonArray.map { cell ->
                val units = cell.jsonObject.getValue("units").jsonArray.mapIndexed { index, unit ->
                    val fields = unit.jsonObject
                    ScoreUnit(fields.getValue("row").jsonPrimitive.int, fields.getValue("col").jsonPrimitive.int, "黃", "event$index")
                }
                ScoreCell(listOf(note), units = units)
            }
        })
    }

    /** JeongganboView가 글꼴 배율을 적용해 실제로 차지하는 세로 높이. */
    private fun renderedHeight(packing: com.ssafy.eolssu.feature.practice.component.PracticeScorePacking, scale: Float) =
        packing.stackRowHeights.fold(0.dp) { total, row -> total + row } * scale +
            com.ssafy.eolssu.feature.practice.component.PracticeStackGap * (packing.stackRowHeights.size - 1)

    @Test
    fun columnsSplitByTheRendererKeepTheirExistingLayout() {
        val packing = practiceScorePacking(Score(List(4) { List(16) { cell } }), metrics, 1000.dp)
        assertEquals(1, packing.columnsPerStack)
    }

    @Test
    fun columnsSplitIntoStripsShrinkToFitLowAreaWithoutVerticalScroll() {
        val score = Score(List(4) { List(16) { cell } })
        val longest = scoreStrips(score, null).maxOf { it.rows.count() }
        for (scale in listOf(1f, 1.3f, 2f)) {
            val packing = practiceScorePacking(score, metrics, 300.dp, fontScale = scale)
            assertEquals(1, packing.columnsPerStack)
            assertTrue(packing.metrics.cellHeight < metrics.cellHeight, "글꼴 ${scale}배 정간")
            assertTrue(packing.metrics.yulSize < metrics.yulSize)
            val rendered = (metrics.headHeight + packing.metrics.cellHeight * longest) * scale + 12.dp
            assertTrue(rendered <= 300.dp, "글꼴 ${scale}배 높이 $rendered")
        }
        // 공간이 넉넉하면 원래 정간 크기를 유지한다.
        assertEquals(metrics.cellHeight, practiceScorePacking(score, metrics, 1000.dp).metrics.cellHeight)
    }

    @Test
    fun originalJangdanStripBreaksAreNotFlattenedIntoStacks() {
        val jangdan = Jangdan("가곡 16박", daegang = listOf(11, 5), beats = 16, subdivision = 1)
        val packing = practiceScorePacking(Score(List(4) { List(12) { cell } }), metrics, 1000.dp, jangdan = jangdan)
        assertEquals(1, packing.columnsPerStack)
    }

    @Test
    fun oneCellColumnsUseSevenPerStackThenWrapLeft() {
        val score = Score(List(12) { listOf(cell) })
        val packing = practiceScorePacking(score, metrics, 500.dp)
        assertEquals(7, packing.columnsPerStack)
        assertEquals((0..6).toList(), packing.columnGroups[0])
        assertEquals((7..11).toList(), packing.columnGroups[1])
        assertTrue(packing.height <= 500.dp)
    }

    @Test
    fun fiveCellColumnsStackTwoWhenBothFit() {
        val packing = practiceScorePacking(Score(List(6) { List(5) { cell } }), metrics, 500.dp)
        assertEquals(2, packing.columnsPerStack)
        assertTrue(packing.height <= 500.dp)
    }

    @Test
    fun singleEightCellColumnFitsTheAvailableHeightWithoutVerticalScrolling() {
        val packing = practiceScorePacking(Score(List(3) { List(8) { cell } }), metrics, 330.dp)
        assertEquals(1, packing.columnsPerStack)
        assertTrue(packing.metrics.cellHeight >= 32.dp)
        assertTrue(packing.height <= 330.dp)
    }

    @Test
    fun variableColumnLengthsIncludeTheLongerThirdColumnInTheFit() {
        val lengths = listOf(1, 1, 8, 1, 2, 1)
        val packing = practiceScorePacking(Score(lengths.map { length -> List(length) { cell } }), metrics, 500.dp)
        assertEquals(3, packing.columnsPerStack)
        assertEquals(lengths.indices.toList(), packing.columnGroups.flatten())
        assertTrue(packing.height <= 500.dp)
        assertTrue(packing.stackRowHeights[2] > packing.stackRowHeights[0])
    }

    // S15P31E102-1022: 휴대폰 연습 악보는 3소박 정간을 곡과 상관없이 66dp·율명 17.6sp로 지킨다.
    private fun keepMetrics(longestStrip: Int) =
        JeongganboMetrics.practiceBoard(false, longestStrip, keepBaseCell = true).copy(splitLineFill = PracticeSplitLineFill)

    @Test
    fun threeSplitScoresUseTheNormalCellEvenForLongColumns() {
        val keep = JeongganboMetrics.practiceBoard(false, 12, keepBaseCell = true)
        assertEquals(44.dp, keep.cellHeight)
        assertEquals(21f, keep.yulSize.value)
        // 3소박이 없는 긴 각은 그대로 32dp다.
        assertEquals(32.dp, JeongganboMetrics.practiceBoard(false, 12).cellHeight)
    }

    @Test
    fun twelveCellGakWithThreeSplitCellsSplitsInTwoInsteadOfShrinking() {
        // 단소 아리랑: 휴대폰 악보 영역 약 466dp. 예전에는 한 각을 한 줄에 넣느라 정간이 약 27dp(3소박 40dp)까지 줄었다.
        val score = Score(List(4) { arirangColumn() })
        val before = practiceScorePacking(score, JeongganboMetrics.practiceBoard(false, 12).copy(splitLineFill = PracticeSplitLineFill), 466.dp)
        assertTrue(!before.splitColumns)
        assertTrue(before.metrics.cellHeight < 32.dp)

        val packing = practiceScorePacking(score, keepMetrics(12), 466.dp, keepBaseCell = true)
        assertTrue(packing.splitColumns)
        assertEquals(44.dp, packing.metrics.cellHeight)
        assertEquals(21f, packing.metrics.yulSize.value)
        assertEquals(66.dp, packing.metrics.threeSplitGlyphCell)
        assertTrue(packing.cellHeights.getValue(0 to 1) >= 66.dp)
        assertTrue(renderedHeight(packing, 1f) <= 466.dp)
    }

    @Test
    fun eightCellGakWithThreeSplitCellsSplitsInTwoToKeepSixtySixDp() {
        // 박연폭포·싸름처럼 8정간 각: 한 줄 무게 10이면 (466 − 38) ÷ 10 = 42.8dp라 44dp가 안 된다. 4·4로 나눈다.
        val triple = ScoreCell(note, note, note)
        val score = Score(List(4) { listOf(cell, triple, cell, triple, cell, triple, cell, triple) })
        val packing = practiceScorePacking(score, keepMetrics(8), 466.dp, keepBaseCell = true)
        assertTrue(packing.splitColumns)
        assertEquals(44.dp, packing.metrics.cellHeight)
        assertTrue(packing.cellHeights.getValue(0 to 1) >= 66.dp)
        assertTrue(renderedHeight(packing, 1f) <= 466.dp)
    }

    @Test
    fun stretchedThreeSplitCellsKeepTheSameGlyphSize() {
        // 같은 가로줄의 더 긴 각(3소박 세 개)에 맞춰 짧은 각의 칸을 늘려도 3소박 율명은 17.6sp에 머문다.
        val triple = ScoreCell(note, note, note)
        // 300dp에서는 한 각씩 세로줄에 두므로 두 각이 같은 가로줄을 나눠 쓴다(무게 3.5 → 4.5로 늘림).
        val score = Score(listOf(listOf(cell, triple, cell), List(3) { triple }))
        val packing = practiceScorePacking(score, keepMetrics(3), 300.dp, keepBaseCell = true)
        assertEquals(1, packing.columnsPerStack)
        assertEquals(44.dp, packing.metrics.cellHeight)
        val stretched = packing.cellHeights.getValue(0 to 1)
        assertTrue(stretched > 66.dp)
        val glyphCell = assertNotNull(packing.metrics.threeSplitGlyphCell)
        assertEquals(66.dp, glyphCell)
        assertEquals(17.6f, packing.metrics.splitYulSize(3, minOf(stretched, glyphCell) / 3, 1f).value, 0.001f)
    }

    // S15P31E102-1028: 3소박이 없는 긴 각 곡도 단소 도라지타령·소금 아리랑과 같은 정간 44dp·율명 21sp를 지킨다.
    @Test
    fun twelveCellGakWithoutThreeSplitCellsKeepsTheBaseCellAndSplitsInTwo() {
        // 늴리리야·태평가·한오백년처럼 12정간 각(2줄 정간 포함). 예전에는 32dp·18sp(2줄 율명 12.8sp)로 그렸다.
        val double = ScoreCell(note, note)
        val score = Score(List(4) { List(12) { row -> if (row % 3 == 1) double else cell } })
        val before = practiceScorePacking(score, JeongganboMetrics.practiceBoard(false, 12).copy(splitLineFill = PracticeSplitLineFill), 466.dp)
        assertEquals(32.dp, before.metrics.cellHeight)

        val packing = practiceScorePacking(score, keepMetrics(12), 466.dp, keepBaseCell = true)
        assertTrue(packing.splitColumns)
        assertEquals(44.dp, packing.metrics.cellHeight)
        assertEquals(21f, packing.metrics.yulSize.value)
        assertEquals(null, packing.metrics.threeSplitGlyphCell)
        // 2줄 정간의 율명은 짧은 각 곡과 같은 17.6sp다.
        assertEquals(17.6f, packing.metrics.splitYulSize(2, packing.metrics.cellHeight / 2, 1f).value, 0.001f)
        assertTrue(renderedHeight(packing, 1f) <= 466.dp)
    }

    @Test
    fun tenCellGakWithoutThreeSplitCellsNoLongerShrinksToFitOneLine() {
        // 강원도아리랑 10정간: 한 줄 무게 10이면 (466 − 38) ÷ 10 = 42.8dp라 예전에는 정간을 42.8dp로 줄여 한 줄에 넣었다.
        val score = Score(List(8) { List(10) { cell } })
        val packing = practiceScorePacking(score, keepMetrics(10), 466.dp, keepBaseCell = true)
        assertTrue(packing.splitColumns)
        assertEquals(44.dp, packing.metrics.cellHeight)
        assertEquals(21f, packing.metrics.yulSize.value)
    }

    @Test
    fun shortGakWithoutThreeSplitCellsKeepsItsLayout() {
        // 단소 도라지타령처럼 3소박 없는 3정간 각은 기존과 같은 크기·묶음이다.
        val double = ScoreCell(note, note)
        val score = Score(List(8) { List(3) { row -> if (row == 1) double else cell } })
        val plain = practiceScorePacking(score, metrics.copy(splitLineFill = PracticeSplitLineFill), 466.dp)
        val kept = practiceScorePacking(score, keepMetrics(3), 466.dp, keepBaseCell = true)
        assertEquals(plain, kept)
    }
}

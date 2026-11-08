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
    fun catalogDorajiStacksTwoPerColumnOnLowPhoneAreaWithSmallerText() {
        // SM-S936N 글꼴 1.15배 도라지타령 연습 화면의 악보 영역(약 226dp).
        val score = dorajiCatalogScore()
        val packing = practiceScorePacking(score, metrics, 226.dp, fontScale = 1.15f)
        assertEquals(2, packing.columnsPerStack)
        assertEquals(listOf(0, 1), packing.columnGroups[0])
        assertEquals(listOf(2, 3), packing.columnGroups[1])
        assertTrue(packing.metrics.cellHeight >= 14.dp)
        assertTrue(packing.metrics.yulSize < metrics.yulSize)
        assertTrue(renderedHeight(packing, 1.15f) <= 226.dp)
    }

    @Test
    fun longColumnsDoNotShrinkToStackTwo() {
        val packing = practiceScorePacking(Score(List(6) { List(8) { cell } }), metrics, 330.dp)
        assertEquals(1, packing.columnsPerStack)
        assertTrue(packing.metrics.cellHeight >= 32.dp)
    }

    @Test
    fun tooLowAreaShrinksCellsAndTextTogetherInsteadOfScrolling() {
        val score = Score(List(6) { List(8) { cell } })
        val packing = practiceScorePacking(score, metrics, 260.dp, fontScale = 1.3f)
        assertEquals(1, packing.columnsPerStack)
        assertTrue(packing.metrics.cellHeight < 32.dp)
        assertTrue(renderedHeight(packing, 1.3f) <= 260.dp)
        val ratio = packing.metrics.cellHeight / 32.dp
        assertEquals(metrics.yulSize.value * ratio, packing.metrics.yulSize.value, 0.01f)
        assertEquals(metrics.auxSize.value * ratio, packing.metrics.auxSize.value, 0.01f)
    }

    @Test
    fun veryLowAreaNeverClipsAColumnEvenWithTinyText() {
        // 360×640dp 휴대폰에서 아리랑 각(12정간 중 8개가 3소박)의 악보 영역은 약 183dp다. 정간 하한 때문에 아래가 잘리면 안 된다.
        val triple = ScoreCell(note, note, note)
        val column = listOf(cell, triple, triple, cell, triple, triple, cell, triple, triple, cell, triple, triple)
        val score = Score(List(4) { column })
        for (scale in listOf(1f, 1.3f)) {
            val packing = practiceScorePacking(score, metrics, 183.dp, fontScale = scale)
            assertTrue(renderedHeight(packing, scale) <= 183.dp, "글꼴 ${scale}배 높이 ${renderedHeight(packing, scale)}")
            assertTrue(packing.metrics.cellHeight > 0.dp)
            assertTrue(packing.metrics.yulSize < metrics.yulSize)
        }
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
}

package com.ssafy.eolssu.feature.practice

import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreUnit
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.feature.practice.component.practiceScorePacking
import com.ssafy.eolssu.data.song.Song2Json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PracticeScorePackingTest {
    private val note = ScoreNote.Pitch(Yul.Hwangjong)
    private val cell = ScoreCell(note)
    private val metrics = JeongganboMetrics.practiceBoard(false, 3)

    @Test
    fun eachStackUsesTheTallestColumnAtEverySharedRow() {
        val triple = ScoreCell(note, note, note)
        val score = Score(List(12) { column -> List(3) { if (column in listOf(3, 7, 11)) triple else cell } })
        val packing = practiceScorePacking(score, metrics, 800.dp)
        assertEquals(3, packing.columnsPerStack)
        // 4각·8각·12각이 각각 첫째·둘째·셋째 가로줄의 가장 높은 각이다.
        assertEquals(listOf(222.dp, 222.dp, 222.dp), packing.stackRowHeights)
    }

    @Test
    fun incompleteLastStackAlsoSetsTheCommonRowHeight() {
        val triple = ScoreCell(note, note, note)
        val score = Score(List(10) { column -> List(3) { if (column == 9) triple else cell } })
        val packing = practiceScorePacking(score, metrics, 700.dp)
        assertEquals(3, packing.columnsPerStack)
        assertEquals(listOf(222.dp, 156.dp, 156.dp), packing.stackRowHeights)
        assertTrue(packing.height < 700.dp)
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
        val columns = Json.parseToJsonElement(Song2Json).jsonObject.getValue("score").jsonObject.getValue("columns").jsonArray
        val score = Score(columns.map { column ->
            column.jsonObject.getValue("cells").jsonArray.map { cell ->
                val units = cell.jsonObject.getValue("units").jsonArray.mapIndexed { index, unit ->
                    val fields = unit.jsonObject
                    ScoreUnit(fields.getValue("row").jsonPrimitive.int, fields.getValue("col").jsonPrimitive.int, "黃", "event$index")
                }
                ScoreCell(listOf(note), units = units)
            }
        })
        val packing = practiceScorePacking(score, metrics, 500.dp)
        assertEquals(3, packing.columnsPerStack)
        assertEquals(28, packing.columnGroups.flatten().size)
        assertTrue(packing.cellHeights.values.all { it > 44.dp })
        assertTrue(packing.height < 500.dp)
    }

    @Test
    fun limitedHeightAndLargeFontsReduceStackCountWithoutShrinkingLargeScore() {
        val score = Score(List(7) { List(3) { cell } })
        assertEquals(2, practiceScorePacking(score, metrics, 300.dp).columnsPerStack)
        val large = JeongganboMetrics.practiceBoard(true, 3)
        val packing = practiceScorePacking(score, large, 500.dp, fontScale = 1.5f)
        assertEquals(1, packing.columnsPerStack)
        assertEquals(60.dp, packing.metrics.cellHeight)
        assertEquals((0..6).toList(), packing.columnGroups.flatten())
    }

    @Test
    fun longColumnsKeepTheirExistingSingleColumnLayout() {
        val packing = practiceScorePacking(Score(List(4) { List(12) { cell } }), metrics, 1000.dp)
        assertEquals(1, packing.columnsPerStack)
    }
}

package com.ssafy.eolssu.core.ui.score

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.boundingRect
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 구간 정간의 연두 테두리(S15P31E102-826): 칸 선 바로 안쪽에 붙고, 틀린 정간 테두리는 그 안쪽에 겹치지 않게 놓인다.
 * 길이는 px로 본다: 각 경계선 1, 구간 테두리 2, 정간 윗선 1(대강 2), 각 모서리 반지름 6.
 */
class SectionOutlineTest {
    private val cell = Size(78f, 44f)
    private val width = 2f
    private val cover = 1f
    private val corner = 6f

    private fun shape(edges: RingEdges, topLine: Float = 1f) = sectionOutlineShape(cell, edges, width, cover, topLine, corner)

    @Test
    fun outlineStartsRightInsideTheCellLinesOnEverySide() {
        // 각 가운데의 정간 하나짜리 구간: 왼쪽·오른쪽은 각 경계선(1) 안쪽, 위는 정간 윗선(1) 아래, 아래는 다음 정간 윗선 바로 위.
        val edges = RingEdges()
        assertEquals(CellInsets(left = 3f, top = 3f, right = 3f, bottom = 2f), sectionOutlineReach(edges, width, cover, topLine = 1f))
        val shape = shape(edges)
        // 윗선(0..1)은 덮지 않고, 각 경계선이 덮는 왼쪽·오른쪽은 가장자리부터 칠해 경계선 아래로 이어진다.
        assertEquals(Rect(0f, 1f, 78f, 44f), shape.outer)
        assertEquals(Rect(3f, 3f, 75f, 42f), shape.inner.boundingRect)
    }

    @Test
    fun thickDaegangLineStaysVisibleAboveTheOutline() {
        val shape = shape(RingEdges(), topLine = 2f)
        assertEquals(2f, shape.outer.top)
        assertEquals(4f, shape.inner.top)
    }

    @Test
    fun outlineAroundCellWithLyricLaneEndsUnderTheColumnLine() {
        // 곁줄이 있어도 구간 테두리는 율명 칸과 곁줄을 한 칸으로 감싸므로, 오른쪽 끝은 곁줄 오른쪽의 각 경계선(1) 안쪽이다.
        val withLane = Size(cell.width + 25f, cell.height)
        val shape = sectionOutlineShape(withLane, RingEdges(), width, cover, topLine = 1f, corner = corner)
        assertEquals(withLane.width - 3f, shape.inner.right)
        assertEquals(3f, shape.inner.left)
    }

    @Test
    fun neighbouringSectionCellsLeaveNoLineBetweenThem() {
        // 위아래가 모두 구간이면 양옆만 긋는다. 안쪽을 정간 밖까지 열어 위아래 선이 생기지 않는다.
        val edges = RingEdges(sharedTop = true, sharedBottom = true)
        assertEquals(CellInsets(left = 3f, top = 0f, right = 3f, bottom = 0f), sectionOutlineReach(edges, width, cover, topLine = 1f))
        val shape = shape(edges)
        assertEquals(0f, shape.outer.top)
        assertEquals(true, shape.inner.top < 0f && shape.inner.bottom > cell.height)
    }

    @Test
    fun lastCellOfColumnFollowsTheRoundedColumnCorner() {
        // 줄의 마지막 정간: 아래도 각 경계선(1) 안쪽이고, 안쪽 모서리를 각 모서리와 같은 중심(6 − 1 − 2 = 3)으로 둥글린다.
        val shape = shape(RingEdges(coveredBottom = true))
        assertEquals(41f, shape.inner.bottom)
        assertEquals(CornerRadius(3f), shape.inner.bottomLeftCornerRadius)
        assertEquals(CornerRadius(3f), shape.inner.bottomRightCornerRadius)
        assertEquals(CornerRadius.Zero, shape.inner.topLeftCornerRadius)
    }

    @Test
    fun splitRingContinuesIntoTheLyricLaneWithoutAMiddleLine() {
        // 줄의 맨 오른쪽 소박 테두리는 오른쪽을 열어 가장자리까지 긋고, 곁줄 쪽 조각은 왼쪽을 열어 가장자리부터 긋는다.
        val unit = cellRingInsets(RingEdges(coveredLeft = false, openRight = true), cover)
        val lane = cellRingInsets(RingEdges(openLeft = true), cover)
        assertEquals(0f, unit.right)
        assertEquals(0f, lane.left)
        // 곁줄의 바깥 오른쪽은 각 경계선(1)만큼 들인다.
        assertEquals(1f, lane.right)
        // 구간 테두리가 오른쪽에 있어도 열린 변은 들이지 않는다.
        assertEquals(0f, cellRingInsets(RingEdges(openRight = true), cover, CellInsets(right = 3f)).right)
    }

    @Test
    fun laneSegmentsFollowOnlyTheRightmostWrongUnitOfEachLine() {
        // 위아래 세 줄 중 1·2번째 줄의 맨 오른쪽 소박이 틀렸다: 그 두 줄만 곁줄까지 잇고, 맞닿은 변은 반 두께로 나눈다.
        val cell = RingEdges(coveredBottom = true)
        val edges = laneSegmentEdges(listOf(true, true, false), cell)
        assertEquals(RingEdges(sharedBottom = true, openLeft = true), edges[0])
        assertEquals(RingEdges(sharedTop = true, openLeft = true), edges[1])
        assertEquals(null, edges[2])
        // 마지막 줄은 정간의 아래 변(줄 끝의 각 경계선)을 따른다.
        assertEquals(RingEdges(sharedTop = true, coveredBottom = true, openLeft = true), laneSegmentEdges(listOf(true, true), cell)[1])
        // 첫 줄은 위 정간과 맞닿는지를 정간 테두리에서 받는다.
        assertEquals(true, laneSegmentEdges(listOf(true), RingEdges(sharedTop = true)).single()?.sharedTop)
    }

    @Test
    fun firstCellOfContinuedStripFollowsTheRoundedTopCorner() {
        // 긴 각을 나눈 이어지는 줄의 첫 정간은 윗선 대신 각 경계선이 위를 덮는다.
        val shape = shape(RingEdges(coveredTop = true), topLine = 0f)
        assertEquals(0f, shape.outer.top)
        assertEquals(3f, shape.inner.top)
        assertEquals(CornerRadius(3f), shape.inner.topLeftCornerRadius)
        assertEquals(CornerRadius(3f), shape.inner.topRightCornerRadius)
        assertEquals(CornerRadius.Zero, shape.inner.bottomLeftCornerRadius)
    }

    @Test
    fun errorRingSitsExactlyInsideTheOutline() {
        // 구간 처음 정간이 틀린 정간: 틀린 테두리의 바깥 끝이 구간 테두리의 안쪽 끝과 같아 두 겹으로 보인다.
        val section = RingEdges(sharedBottom = true)
        val reach = sectionOutlineReach(section, width, cover, topLine = 1f)
        val ring = cellRingInsets(RingEdges(), cover, reach)
        assertEquals(CellInsets(left = 3f, top = 3f, right = 3f, bottom = 0f), ring)
        val inner = shape(section).inner
        assertEquals(inner.left, ring.left)
        assertEquals(inner.top, ring.top)
        assertEquals(cell.width - inner.right, ring.right)
    }

    @Test
    fun ringWithoutOutlineKeepsItsPlace() {
        // 구간 밖 정간의 틀린 테두리는 그대로다: 양옆만 각 경계선만큼 들이고, 줄 끝에서는 아래도 들인다.
        assertEquals(CellInsets(left = 1f, right = 1f), cellRingInsets(RingEdges(), cover))
        assertEquals(CellInsets(left = 1f, right = 1f, bottom = 1f), cellRingInsets(RingEdges(coveredBottom = true), cover))
        // 가로로 나뉜 소박은 바깥쪽만 들인다.
        assertEquals(CellInsets(left = 1f), cellRingInsets(RingEdges(coveredRight = false), cover))
    }

    @Test
    fun tagAndPillMoveInWithTheRing() {
        // 종류 아이콘·구간 알약은 테두리가 안으로 들어간 만큼만 따라 들어간다.
        val ringEdges = RingEdges(coveredBottom = true)
        val reach = sectionOutlineReach(RingEdges(coveredBottom = true), width, cover, topLine = 1f)
        val shift = cellRingInsets(ringEdges, cover, reach) - cellRingInsets(ringEdges, cover)
        assertEquals(CellInsets(left = 2f, top = 3f, right = 2f, bottom = 2f), shift)
        // 구간 테두리가 없으면 옮기지 않는다.
        assertEquals(CellInsets.None, cellRingInsets(ringEdges, cover, CellInsets.None) - cellRingInsets(ringEdges, cover))
    }

    @Test
    fun insetsScaleFromDpToPx() {
        assertEquals(CellInsets(7.5f, 5f, 2.5f, 0f), CellInsets(3f, 2f, 1f, 0f) * 2.5f)
    }
}

package com.ssafy.eolssu.feature.guide.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SigimsaeBrushTest {

    @Test
    fun maskSlidesFromHiddenToFullyShownOverTheDrawTime() {
        assertEquals(-80f, brushMaskX(0f))
        assertEquals(0f, brushMaskX(SigimsaeDrawSeconds), 0.001f)
        assertEquals(0f, brushMaskX(SigimsaeDrawSeconds + 1f), 0.001f)
    }

    @Test
    fun brushStartsSlowlyThenFlows() {
        val early = brushMaskX(SigimsaeDrawSeconds * 0.25f) - brushMaskX(0f)
        val late = brushMaskX(SigimsaeDrawSeconds * 0.75f) - brushMaskX(SigimsaeDrawSeconds * 0.5f)
        assertTrue(early < late, "처음 1/4보다 가운데 뒤 1/4에서 더 많이 나아간다: $early, $late")
    }

    @Test
    fun brushHeadLeavesTheFigureWhenDone() {
        assertEquals(-5f, brushHead(0f))
        assertEquals(999f, brushHead(SigimsaeDrawSeconds))
    }

    @Test
    fun onlyTheStringNearTheBrushTipShakes() {
        val t = SigimsaeDrawSeconds / 2
        val head = brushHead(t)
        val far = (0 until 40).maxOf { kotlin.math.abs(waveOffset(it + 20, 200, head + 20f, t)) }
        val near = (0 until 40).maxOf { kotlin.math.abs(waveOffset(it + 20, 200, head, t + it * 0.01f)) }
        assertTrue(far < 0.001f, "붓끝에서 먼 자리는 떨지 않는다: $far")
        assertTrue(near > 0.3f, "붓끝 자리는 떤다: $near")
    }

    @Test
    fun endsOfTheLineAreTiedDown() {
        val t = SigimsaeDrawSeconds / 2
        val head = brushHead(t)
        assertEquals(0f, waveOffset(0, 200, head, t), 1e-6f)
        assertEquals(0f, waveOffset(199, 200, head, t), 1e-6f)
    }
}

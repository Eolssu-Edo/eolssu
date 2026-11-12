package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.feature.result.component.AnalysisTimeConstantMillis
import com.ssafy.eolssu.feature.result.component.PendingProgressCap
import com.ssafy.eolssu.feature.result.component.UploadShare
import com.ssafy.eolssu.feature.result.component.analysisProgress
import com.ssafy.eolssu.feature.result.component.chaseProgress
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 분석 대기 진행률: 전송은 앞 몫만 채우고, 분석이 끝나기 전에는 100%가 되지 않는다(S15P31E102-777). */
class AnalysisProgressTest {
    @Test
    fun uploadFillsOnlyItsShare() {
        assertEquals(0f, analysisProgress(0f, 0.0, done = false))
        assertEquals(UploadShare / 2, analysisProgress(0.5f, 0.0, done = false))
        // 다 보낸 순간은 전송 몫의 끝이며 100%가 아니다.
        assertEquals(UploadShare, analysisProgress(1f, 0.0, done = false))
    }

    @Test
    fun analysisWaitRisesButNeverShowsFullBeforeDone() {
        var previous = analysisProgress(1f, 0.0, done = false)
        for (millis in listOf(500.0, 1_000.0, 3_000.0, 4_300.0, 10_000.0, 70_000.0, 600_000.0)) {
            val progress = analysisProgress(1f, millis, done = false)
            assertTrue(progress > previous || progress == PendingProgressCap, "$millis ms에서 진행률이 줄었다")
            assertTrue(progress <= PendingProgressCap)
            assertTrue(floor(progress * 100).toInt() <= 99, "$millis ms에서 완료 전 100%")
            previous = progress
        }
        // 시간 상수만큼 기다리면 남은 거리의 약 63%를 채운다.
        val atTau = analysisProgress(1f, AnalysisTimeConstantMillis, done = false)
        val expected = UploadShare + (PendingProgressCap - UploadShare) * 0.632f
        assertTrue(kotlin.math.abs(atTau - expected) < 0.01f)
    }

    @Test
    fun onlyDoneIsFull() {
        assertEquals(1f, analysisProgress(1f, 1_000.0, done = true))
        assertEquals(1f, analysisProgress(0.4f, 0.0, done = true))
    }

    @Test
    fun shownProgressChasingAnalysisWaitStaysBelowFull() {
        // 10분 동안 매 프레임 따라가도 완료 전에는 99%를 넘지 않는다.
        val dt = 1f / 60
        var shown = 0f
        var millis = 0.0
        repeat(60 * 600) {
            millis += dt * 1_000.0
            shown = chaseProgress(shown, analysisProgress(1f, millis, done = false), dt)
        }
        assertTrue(shown <= PendingProgressCap)
        assertTrue(floor(shown * 100).toInt() <= 99)
    }
}

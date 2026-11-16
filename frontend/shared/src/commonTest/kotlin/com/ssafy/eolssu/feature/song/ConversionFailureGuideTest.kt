package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.data.song.SheetConversionFailure
import com.ssafy.eolssu.data.song.SheetNotation
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversionFailureGuideTest {
    @Test
    fun scoreNotFoundIsSplitByNotation() {
        assertEquals(ConversionFailureGuide.NoGrid, conversionFailureGuide(SheetConversionFailure.NoScoreFound, SheetNotation.Jeongganbo))
        assertEquals(ConversionFailureGuide.NoStaff, conversionFailureGuide(SheetConversionFailure.NoScoreFound, SheetNotation.Staff))
        assertEquals(ConversionFailureGuide.NoScore, conversionFailureGuide(SheetConversionFailure.NoScoreFound, null))
    }

    @Test
    fun fileTimeoutAndServerFailuresKeepTheirOwnGuide() {
        assertEquals(ConversionFailureGuide.File, conversionFailureGuide(SheetConversionFailure.UnsupportedFile, SheetNotation.Staff))
        assertEquals(ConversionFailureGuide.Timeout, conversionFailureGuide(SheetConversionFailure.Timeout, SheetNotation.Jeongganbo))
        assertEquals(ConversionFailureGuide.Server, conversionFailureGuide(SheetConversionFailure.ServerError, SheetNotation.Staff))
        assertEquals(ConversionFailureGuide.Server, conversionFailureGuide(null, null), "원인을 모르면 악보 탓을 하지 않는다")
    }

    @Test
    fun nextActionMatchesWhatTheUserCanChange() {
        // 악보·사진 문제는 다시 찍거나 자르고, 파일 문제는 다른 파일, 시간 초과는 나눠 올리고, 서버 문제는 같은 파일로 잠시 뒤 다시 한다.
        assertEquals(ConversionNextAction.Retake, ConversionFailureGuide.NoGrid.action)
        assertEquals(ConversionNextAction.Retake, ConversionFailureGuide.NoStaff.action)
        assertEquals(ConversionNextAction.Retake, ConversionFailureGuide.NoScore.action)
        assertEquals(ConversionNextAction.OtherFile, ConversionFailureGuide.File.action)
        assertEquals(ConversionNextAction.Split, ConversionFailureGuide.Timeout.action)
        assertEquals(ConversionNextAction.TryLater, ConversionFailureGuide.Server.action)
    }
}

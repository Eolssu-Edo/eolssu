package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.data.song.SheetConversion
import com.ssafy.eolssu.data.song.SheetConversionFailure
import com.ssafy.eolssu.data.song.SheetConversionStatus
import com.ssafy.eolssu.data.song.SheetNotation
import kotlin.test.Test
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_staff_row
import eolssu.shared.generated.resources.mysongs_conversion_failed_unknown_row
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
    fun listRowDoesNotBlameTheServerBeforeTheFailureDetailArrives() {
        // 목록(SHT-01)만 받았거나 상세 조회(SHT-10)가 실패하면 원인 코드가 없다. 이때 '서버 문제'라고 단정하지 않는다.
        assertEquals(Res.string.mysongs_conversion_failed_unknown_row, conversionFailureRow(SheetConversion("1", SheetConversionStatus.Failed)))
        assertEquals(Res.string.mysongs_conversion_failed_unknown_row, conversionFailureRow(null))
        assertEquals(
            Res.string.mysongs_conversion_failed_no_staff_row,
            conversionFailureRow(SheetConversion("1", SheetConversionStatus.Failed, failure = SheetConversionFailure.NoScoreFound, notation = SheetNotation.Staff)),
        )
    }

    @Test
    fun nextActionMatchesWhatTheUserCanChange() {
        // 악보 문제는 또렷한 PDF로 다시 올리거나 자르고, 파일 문제는 다른 파일로 한다.
        // 시간 초과는 서버가 밀려 대기 중에 기한이 지나도 나므로(ConversionQueue.expire) 서버 문제처럼 잠시 뒤 다시 한다.
        assertEquals(ConversionNextAction.Retake, ConversionFailureGuide.NoGrid.action)
        assertEquals(ConversionNextAction.Retake, ConversionFailureGuide.NoStaff.action)
        assertEquals(ConversionNextAction.Retake, ConversionFailureGuide.NoScore.action)
        assertEquals(ConversionNextAction.OtherFile, ConversionFailureGuide.File.action)
        assertEquals(ConversionNextAction.TryLater, ConversionFailureGuide.Timeout.action)
        assertEquals(ConversionNextAction.TryLater, ConversionFailureGuide.Server.action)
    }
}

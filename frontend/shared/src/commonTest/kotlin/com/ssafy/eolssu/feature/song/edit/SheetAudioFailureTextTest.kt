package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.SheetAudioFailure
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheetedit_audio_failure_expired
import eolssu.shared.generated.resources.sheetedit_audio_failure_invalid
import eolssu.shared.generated.resources.sheetedit_audio_failure_server
import eolssu.shared.generated.resources.sheetedit_audio_failure_tempo
import eolssu.shared.generated.resources.sheetedit_audio_failure_timeout
import eolssu.shared.generated.resources.sheetedit_audio_failure_too_large
import eolssu.shared.generated.resources.sheetedit_audio_failure_too_long
import eolssu.shared.generated.resources.sheetedit_audio_failure_unknown
import kotlin.test.Test
import kotlin.test.assertEquals

/** S15P31E102-1097: 음원 실패 코드를 이유와 할 일 문구로 옮기고 모르는 코드는 일반 안내로 돌린다. */
class SheetAudioFailureTextTest {
    @Test fun serverCodesMapToTheirOwnMessage() {
        val expected = mapOf(
            "TIMEOUT" to Res.string.sheetedit_audio_failure_timeout,
            "TOO_LARGE" to Res.string.sheetedit_audio_failure_too_large,
            "SERVER_ERROR" to Res.string.sheetedit_audio_failure_server,
            "AUDIO_EXPIRED" to Res.string.sheetedit_audio_failure_expired,
            "EXPORT_TOO_LONG" to Res.string.sheetedit_audio_failure_too_long,
            "TEMPO_UNRENDERABLE" to Res.string.sheetedit_audio_failure_tempo,
            "INVALID_REQUEST" to Res.string.sheetedit_audio_failure_invalid,
        )
        expected.forEach { (code, text) -> assertEquals(text, SheetAudioFailure.fromCode(code).reasonText(), code) }
    }

    @Test fun unknownOrMissingCodesFallBackToTheGeneralMessage() {
        assertEquals(Res.string.sheetedit_audio_failure_unknown, SheetAudioFailure.fromCode("NEW_CODE").reasonText())
        assertEquals(Res.string.sheetedit_audio_failure_unknown, SheetAudioFailure.fromCode(null).reasonText())
        assertEquals(Res.string.sheetedit_audio_failure_unknown, (null as SheetAudioFailure?).reasonText())
    }
}

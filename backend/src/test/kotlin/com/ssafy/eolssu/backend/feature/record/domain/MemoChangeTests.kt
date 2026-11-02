package com.ssafy.eolssu.backend.feature.record.domain

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class MemoChangeTests {
    @Test
    fun `trims surrounding spaces and keeps the text inside`() {
        assertEquals(MemoChange.Replace("3–4각에서 박자가 밀림"), MemoChange.of("  3–4각에서 박자가 밀림\n"))
        assertEquals(MemoChange.Replace("첫 줄\n둘째 줄"), MemoChange.of("　첫 줄\n둘째 줄　"))
    }

    @Test
    fun `blank text clears the memo`() {
        for (blank in listOf("", "   ", "\n\t", "　")) assertEquals(MemoChange.Clear, MemoChange.of(blank), blank)
    }

    @Test
    fun `allows up to 500 characters after trimming and counts an emoji as one`() {
        val longest = "가".repeat(500)
        assertEquals(MemoChange.Replace(longest), MemoChange.of(" $longest "))
        assertNull(MemoChange.of("가".repeat(501)))

        val emoji = "🎵" // 음표 이모지 하나(UTF-16 두 단위)
        assertEquals(MemoChange.Replace(emoji.repeat(500)), MemoChange.of(emoji.repeat(500)))
        assertNull(MemoChange.of(emoji.repeat(501)))
    }
}

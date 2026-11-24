package com.ssafy.eolssu.core.theme

import java.awt.Font
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 본문 글꼴은 사용자가 입력한 글자도 그리므로 한글 음절 11,172자를 모두 담아야 한다. 글자를 줄이면 웹에서 드문 음절이 빈 상자가 된다.
 * IBM Plex Sans KR을 고친 파일이라 OFL 예약 이름 "Plex"를 글꼴 이름에 쓰지 않는다(`tools/fonts/build_body_font.py`).
 */
class BodyFontCoverageTest {
    // Gradle은 모듈 폴더(shared/)에서 실행한다. IDE가 frontend/에서 실행해도 찾도록 두 곳을 본다.
    private val fontDir = listOf("src/commonMain/composeResources/font", "shared/src/commonMain/composeResources/font")
        .map(::File).firstOrNull(File::isDirectory)
        ?: error("글꼴 폴더를 찾지 못했다. 작업 폴더: ${File("").absolutePath}")

    @Test
    fun bodyFontsKeepEveryHangulSyllableUnderTheRenamedFamily() {
        listOf("eolssu_sans_kr_regular.otf", "eolssu_sans_kr_semibold.otf", "eolssu_sans_kr_bold.otf").forEach { name ->
            val font = File(fontDir, name).inputStream().use { Font.createFont(Font.TRUETYPE_FONT, it) }
            val missingHangul = (0xAC00..0xD7A3).filterNot(font::canDisplay)
            assertEquals(0, missingHangul.size, "$name 에 없는 한글 음절: ${missingHangul.take(5).map { Character.toString(it) }}")
            val missingLatin = (0x20..0x7E).filterNot(font::canDisplay)
            assertEquals(0, missingLatin.size, "$name 에 없는 기본 라틴 글자: $missingLatin")
            val family = font.getFamily(Locale.ROOT)
            assertTrue(family.startsWith("Eolssu Sans KR"), "$name 글꼴 이름: $family")
            assertFalse("Plex" in font.getFontName(Locale.ROOT) || "Plex" in font.psName, "$name 에 예약 이름이 남았다: ${font.psName}")
        }
    }
}

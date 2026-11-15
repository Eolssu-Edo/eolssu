package com.ssafy.eolssu.core.ui.score

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.fingering.SerifYulHanja
import java.awt.Font
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** 번들 글꼴 파일에 율명 글리프가 실제로 있는지 확인한다. 없으면 기기 글꼴(산세리프)로 대체되어 악기마다 모양이 달라진다. */
class YulFontCoverageTest {
    // Gradle은 모듈 폴더(shared/)에서 실행한다. IDE가 frontend/에서 실행해도 찾도록 두 곳을 본다.
    private val fontDir = listOf("src/commonMain/composeResources/font", "shared/src/commonMain/composeResources/font")
        .map(::File).firstOrNull(File::isDirectory)
        ?: error("글꼴 폴더를 찾지 못했다. 작업 폴더: ${File("").absolutePath}")

    private fun font(name: String): Font = File(fontDir, name).inputStream().use { Font.createFont(Font.TRUETYPE_FONT, it) }

    private fun missing(font: Font, symbols: Iterable<String>): List<String> =
        symbols.filter { symbol -> symbol.codePoints().toArray().any { !font.canDisplay(it) } }

    @Test
    fun serifFontsContainEveryYulTheRendererDrawsAsIs() {
        val serifSymbols = buildList {
            addAll(SerifYulHanja.map(Char::toString))
            for (octave in -2..2) for (yul in listOf("황", "대", "태", "협", "고", "중", "유", "임", "이", "남", "무", "응")) {
                val glyph = noteGlyphOf(ScoreNote.SourcePitch(yul, octave))
                if (glyph is NoteGlyph.Serif) add(glyph.text)
            }
        }
        listOf("noto_serif_kr_semibold.otf", "noto_serif_kr_bold.otf").forEach { name ->
            val absent = missing(font(name), serifSymbols)
            assertTrue(absent.isEmpty(), "$name 에 없는 율명: $absent")
        }
    }

    @Test
    fun supplementFontContainsTheSupplementaryPlaneYul() {
        val absent = missing(font("eolssu_yul_supplement.ttf"), listOf("𢓡", "𠐭", "𣴘"))
        assertTrue(absent.isEmpty(), "보조 글꼴에 없는 율명: $absent")
    }
}

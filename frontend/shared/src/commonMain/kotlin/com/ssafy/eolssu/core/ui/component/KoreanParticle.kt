package com.ssafy.eolssu.core.ui.component

/**
 * 앞 낱말의 끝소리에 따라 모양이 바뀌는 조사. 문구 자원에 '로'·'을'을 박아 두면 `기본음로`처럼 틀리므로,
 * 바뀌는 낱말 뒤의 조사는 [withParticle]로 붙인다.
 *
 * @property afterConsonant 받침 있는 낱말 뒤에 붙는 모양(`대금으로`·`장단을`).
 * @property afterVowel 받침 없는 낱말 뒤에 붙는 모양(`단소로`·`반주를`).
 */
enum class KoreanParticle(internal val afterConsonant: String, internal val afterVowel: String) {
    /** 으로/로. ㄹ 받침 뒤에도 '로'다(`한 줄로`). */
    EuroRo("으로", "로"),
    EulReul("을", "를"),
    IGa("이", "가"),
    EunNeun("은", "는"),
    GwaWa("과", "와"),
}

/**
 * 끝소리에 맞는 [particle]을 붙인다. 끝의 공백·닫는 괄호·따옴표는 건너뛰고 읽는 글자를 본다.
 * 한글 음절과 숫자(`4/4` → 사, `12/8` → 팔)를 읽고, 그 밖의 글자는 받침 없는 쪽으로 붙인다.
 */
fun String.withParticle(particle: KoreanParticle): String {
    val ending = finalSound(this)
    val suffix = when {
        ending == FinalSound.None -> particle.afterVowel
        ending == FinalSound.Rieul && particle == KoreanParticle.EuroRo -> particle.afterVowel
        else -> particle.afterConsonant
    }
    return this + suffix
}

private enum class FinalSound { None, Rieul, Other }

/** 숫자를 한자어로 읽을 때의 끝소리: 영·일·이·삼·사·오·육·칠·팔·구. */
private val DigitFinalSounds = listOf(
    FinalSound.Other, FinalSound.Rieul, FinalSound.None, FinalSound.Other, FinalSound.None,
    FinalSound.None, FinalSound.Other, FinalSound.Rieul, FinalSound.Rieul, FinalSound.None,
)

private const val HangulSyllableFirst = 0xAC00
private const val HangulSyllableLast = 0xD7A3
private const val RieulJongseong = 8

private fun finalSound(text: String): FinalSound {
    val last = text.trimEnd { it.isWhitespace() || it in ")]}'\"’”」』" }.lastOrNull() ?: return FinalSound.None
    return when {
        last.code in HangulSyllableFirst..HangulSyllableLast -> when ((last.code - HangulSyllableFirst) % 28) {
            0 -> FinalSound.None
            RieulJongseong -> FinalSound.Rieul
            else -> FinalSound.Other
        }
        last in '0'..'9' -> DigitFinalSounds[last - '0']
        else -> FinalSound.None
    }
}

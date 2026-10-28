package com.ssafy.eolssu.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 1차 와이어프레임 기준 색 토큰.
 * 글자로 쓰는 조합은 WCAG AA(4.5:1)를 지키고, [lineStrong]처럼 글자에 쓰지 않는 색은 이름에 용도를 드러낸다.
 */
@Immutable
data class EolssuColors(
    val background: Color,
    val surface: Color,
    val surfaceStrong: Color,
    val line: Color,
    /** 화살표·구분선처럼 글자가 아닌 보조 표시에만 쓴다. */
    val lineStrong: Color,
    val text: Color,
    val textMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    /** 현재 위치·선택 강조(노랑). */
    val accent: Color,
    val accentSoft: Color,
    val accentText: Color,
    /** 모범 연주(재생) 상태(파랑). */
    val play: Color,
    val playSoft: Color,
    val playText: Color,
    /** 평가 연주(녹음) 상태(빨강). */
    val rec: Color,
    val recSoft: Color,
    val recText: Color,
    /** 재생·연주 중 지나간 정간의 바탕(옅은 회색). 글자는 [text]. */
    val doneSoft: Color,
    /** 평가 오류 위치: 음정(주황) — 테두리·바탕·글자. */
    val pitchError: Color,
    val pitchErrorSoft: Color,
    val pitchErrorText: Color,
    /** 평가 오류 위치: 박자(파랑) — 테두리·바탕·글자. 음 길이 오류는 [text] 점선과 [surfaceStrong] 바탕. */
    val rhythmError: Color,
    val rhythmErrorSoft: Color,
    val rhythmErrorText: Color,
)

val LightEolssuColors = EolssuColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F4),
    surfaceStrong = Color(0xFFECECEA),
    line = Color(0xFFE4E4E1),
    lineStrong = Color(0xFFB5B5B0),
    text = Color(0xFF121212),
    textMuted = Color(0xFF6E6E6A),
    primary = Color(0xFF121212),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFFF3C24A),
    accentSoft = Color(0xFFFDF1CC),
    accentText = Color(0xFF8A6A1A),
    play = Color(0xFF2F6FED),
    playSoft = Color(0xFFE6EEFC),
    playText = Color(0xFF2559C9),
    rec = Color(0xFFC23A30),
    recSoft = Color(0xFFFBE6E4),
    recText = Color(0xFFB3352B),
    doneSoft = Color(0xFFE3E3E0),
    pitchError = Color(0xFFC9671A),
    pitchErrorSoft = Color(0xFFFDE7CF),
    pitchErrorText = Color(0xFF9A4A0C),
    rhythmError = Color(0xFF2A62D6),
    rhythmErrorSoft = Color(0xFFDFE9FB),
    rhythmErrorText = Color(0xFF1F4FB3),
)

package com.ssafy.eolssu.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 2026-10-29 연두 디자인 기준 색 토큰.
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
    /** 먹색을 배경으로 사용하는 선택 탭·완료 배지의 전경. */
    val onInk: Color,
    val textMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    /** 현재 위치·선택 강조(연두). */
    val accent: Color,
    val accentSoft: Color,
    val accentText: Color,
    /** 모범 연주(재생) 상태(짙은 연두). */
    val play: Color,
    val onPlay: Color,
    val playSoft: Color,
    val playText: Color,
    /** 평가 연주(녹음) 상태(빨강). */
    val rec: Color,
    val onRec: Color,
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
    /** 학습 화면의 녹색: 다음 단추 · 지금 쪽 표시. 글자는 [onPrimary]. */
    val green: Color,
    /** 율명 부수(氵·亻) 강조(이끼). 흰 바탕 대비가 3:1대라 24sp 이상 큰 글자에만 쓴다. */
    val radical: Color,
    /** 지금 자리의 빛(연두). 정간보 읽는 순서 그림에서 지나는 칸 글자의 번짐에만 쓰고 글자색으로 쓰지 않는다. */
    val glow: Color,
)

val LightEolssuColors = EolssuColors(
    background = Color(0xFFF9FAF9),
    surface = Color(0xFFFFFFFF),
    surfaceStrong = Color(0xFFEEEBE3),
    line = Color(0xFFD9D3C6),
    lineStrong = Color(0xFF8B857B),
    text = Color(0xFF27221E),
    onInk = Color.White,
    textMuted = Color(0xFF6B655D),
    primary = Color(0xFFADBE31),
    onPrimary = Color(0xFF27221E),
    accent = Color(0xFFADBE31),
    accentSoft = Color(0xFFF6F8E3),
    accentText = Color(0xFF4A5316),
    play = Color(0xFF64721D),
    onPlay = Color.White,
    playSoft = Color(0xFFF6F8E3),
    playText = Color(0xFF4A5316),
    rec = Color(0xFFC8402F),
    onRec = Color.White,
    recSoft = Color(0xFFFBE5E1),
    recText = Color(0xFFB5331F),
    doneSoft = Color(0xFFEEEBE3),
    pitchError = Color(0xFFC9671A),
    pitchErrorSoft = Color(0xFFFDE7CF),
    pitchErrorText = Color(0xFF9A4A0C),
    rhythmError = Color(0xFF2A62D6),
    rhythmErrorSoft = Color(0xFFDFE9FB),
    rhythmErrorText = Color(0xFF1F4FB3),
    green = Color(0xFFADBE31),
    radical = Color(0xFF7A984A),
    glow = Color(0xFFADBE31),
)

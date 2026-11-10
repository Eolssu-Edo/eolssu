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
    /** 칩·흰 카드·원형 버튼의 옅은 테두리(`#E8E4DA`). 글자에 쓰지 않는다. */
    val lineSoft: Color,
    /** 화살표·구분선처럼 글자가 아닌 보조 표시에만 쓴다. */
    val lineStrong: Color,
    val text: Color,
    /** 먹색을 배경으로 사용하는 선택 탭·완료 배지의 전경. */
    val onInk: Color,
    val textMuted: Color,
    /** 고르지 않은 칩·페이지 설명처럼 [text]보다 한 단계 낮춘 글자. */
    val textSub: Color,
    /** 비활성 칩·버튼 글자. 비활성 요소는 WCAG 대비 기준에서 빠지므로 4.5:1을 넘지 않는다. 다른 글자에는 쓰지 않는다. */
    val textDisabled: Color,
    val primary: Color,
    val onPrimary: Color,
    /** 현재 위치·선택 강조(연두). */
    val accent: Color,
    /** 연두 10%. */
    val accentSoft: Color,
    /** 연두 20%: 상태 배지 바탕. */
    val accentTint: Color,
    /** 연두 40%: 완료 아이콘 고리 · 강조 태그 바탕. */
    val accentTintStrong: Color,
    /** 연두 60%: 연두 바탕 상자의 테두리. 글자에 쓰지 않는다. */
    val accentTintLine: Color,
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
    /** 틀린 종류 표시(2차 결합본): 음정 — 정간 테두리·아이콘 상자 바탕, 정간 바탕, 글자. 아이콘은 흰색. */
    val pitchError: Color,
    val pitchErrorSoft: Color,
    val pitchErrorText: Color,
    /** 틀린 종류 표시: 박자 — 정간 테두리·아이콘 상자 바탕, 정간 바탕, 글자. */
    val rhythmError: Color,
    val rhythmErrorSoft: Color,
    val rhythmErrorText: Color,
    /** 틀린 종류 표시: 음 길이 — 정간 점선 테두리·아이콘 상자 바탕, 정간 바탕. */
    val lengthError: Color,
    val lengthErrorSoft: Color,
    /**
     * 악보의 틀린 정간 표시(연한 색): 종류마다 정간 테두리(`…Line`)와 바탕(`…Faint`). 음정·박자·음 길이·미검출 순.
     * 칸 표시가 율명보다 튀지 않게 [pitchError] 등보다 연하게 둔다. 아이콘 상자는 흰 아이콘이 보이게 진한 색을 그대로 쓴다.
     * 선·바탕으로만 쓰고 글자색으로 쓰지 않는다.
     */
    val pitchErrorLine: Color,
    val pitchErrorFaint: Color,
    val rhythmErrorLine: Color,
    val rhythmErrorFaint: Color,
    val lengthErrorLine: Color,
    val lengthErrorFaint: Color,
    val missedErrorLine: Color,
    val missedErrorFaint: Color,
    /** 이전 연주에서 틀린 정간·기록 상세의 틀린 정간 바탕(옅은 붉은색). 테두리는 [rec]. 글자 바탕으로만 쓴다. */
    val recFaint: Color,
    /** 학습 화면의 녹색: 다음 단추 · 지금 쪽 표시. 글자는 [onPrimary]. */
    val green: Color,
    /** 율명 부수(氵·亻) 강조(이끼). 흰 바탕 대비가 3:1대라 24sp 이상 큰 글자에만 쓴다. */
    val radical: Color,
    /** 지금 자리의 빛(연두). 정간보 읽는 순서 그림에서 지나는 칸 글자의 번짐에만 쓰고 글자색으로 쓰지 않는다. */
    val glow: Color,
    /** 홈 카드 격자·마이페이지 카드의 옅은 초록빛 테두리. 글자에 쓰지 않는다. */
    val cardLine: Color,
    /** 홈 카드 격자 위 구분선처럼 카드 묶음을 나누는 가는 선. 글자에 쓰지 않는다. */
    val sectionLine: Color,
    /** 홈·마이페이지 흰 카드의 제목 글자(초록빛 먹색). */
    val cardInk: Color,
)

val LightEolssuColors = EolssuColors(
    background = Color(0xFFF9FAF9),
    surface = Color(0xFFFFFFFF),
    surfaceStrong = Color(0xFFEEEBE3),
    line = Color(0xFFD9D3C6),
    lineSoft = Color(0xFFE8E4DA),
    lineStrong = Color(0xFF8B857B),
    text = Color(0xFF27221E),
    onInk = Color.White,
    textMuted = Color(0xFF6B655D),
    textSub = Color(0xFF5C564F),
    textDisabled = Color(0xFF9A948A),
    primary = Color(0xFFADBE31),
    onPrimary = Color(0xFF27221E),
    accent = Color(0xFFADBE31),
    accentSoft = Color(0xFFF6F8E3),
    accentTint = Color(0xFFEFF2D6),
    accentTintStrong = Color(0xFFDEE5AD),
    accentTintLine = Color(0xFFCED883),
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
    pitchError = Color(0xFFBB7B43),
    pitchErrorSoft = Color(0xFFF6E7D2),
    pitchErrorText = Color(0xFF7A4A1F),
    rhythmError = Color(0xFF6475C5),
    rhythmErrorSoft = Color(0xFFE3E9F9),
    rhythmErrorText = Color(0xFF3B4A99),
    lengthError = Color(0xFF2C2C2C),
    lengthErrorSoft = Color(0xFFECECEA),
    pitchErrorLine = Color(0xFFE9C6A4),
    pitchErrorFaint = Color(0xFFFDF7EF),
    rhythmErrorLine = Color(0xFFBFC8EE),
    rhythmErrorFaint = Color(0xFFF5F7FE),
    lengthErrorLine = Color(0xFFB8B8B4),
    lengthErrorFaint = Color(0xFFF7F7F5),
    missedErrorLine = Color(0xFFD2CCC1),
    missedErrorFaint = Color(0xFFF8F6F2),
    recFaint = Color(0xFFFDF4F2),
    green = Color(0xFFADBE31),
    radical = Color(0xFF7E8C1C),
    glow = Color(0xFFADBE31),
    cardLine = Color(0xFFE3E9E3),
    sectionLine = Color(0xFFE5EAE5),
    cardInk = Color(0xFF2E3E32),
)

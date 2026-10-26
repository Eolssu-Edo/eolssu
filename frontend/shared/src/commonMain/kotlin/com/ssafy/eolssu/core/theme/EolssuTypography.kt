package com.ssafy.eolssu.core.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.noto_serif_kr_bold
import eolssu.shared.generated.resources.noto_serif_kr_semibold
import eolssu.shared.generated.resources.pretendard_bold
import eolssu.shared.generated.resources.pretendard_regular
import eolssu.shared.generated.resources.pretendard_semibold
import org.jetbrains.compose.resources.Font

/**
 * 와이어프레임의 세리프 글자 스타일. Material [Typography]에 없는 율명·곡 제목용이다.
 *
 * 세리프 글꼴에는 자주 쓰는 한글 2,350자와 율명 한자만 들어 있다(`tools/fonts/build_fonts.py`).
 * 사용자가 입력한 글자(내 악보 제목 등)는 세리프로 쓰지 말고 Material 글자 스타일(Pretendard)을 쓴다.
 */
@Immutable
data class EolssuSerifTypography(
    /** 앱 이름 '얼쑤' 같은 큰 제목. */
    val brand: TextStyle,
    /** 곡 상세의 곡 제목. */
    val songTitle: TextStyle,
    /** 정간보의 율명. 크기는 칸에 맞춰 `copy(fontSize = ...)`로 바꾼다. */
    val yul: TextStyle,
)

@Composable
internal fun pretendardFamily() = FontFamily(
    Font(Res.font.pretendard_regular, FontWeight.Normal),
    Font(Res.font.pretendard_semibold, FontWeight.SemiBold),
    Font(Res.font.pretendard_bold, FontWeight.Bold),
)

@Composable
internal fun notoSerifKrFamily() = FontFamily(
    Font(Res.font.noto_serif_kr_semibold, FontWeight.SemiBold),
    Font(Res.font.noto_serif_kr_bold, FontWeight.Bold),
)

/** Material 기본 크기를 유지하고 글꼴만 Pretendard로 바꾼다. 와이어프레임 상단 제목(16sp 굵게)만 굵기를 맞춘다. */
internal fun eolssuTypography(sans: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = sans),
        displayMedium = base.displayMedium.copy(fontFamily = sans),
        displaySmall = base.displaySmall.copy(fontFamily = sans),
        headlineLarge = base.headlineLarge.copy(fontFamily = sans),
        headlineMedium = base.headlineMedium.copy(fontFamily = sans),
        headlineSmall = base.headlineSmall.copy(fontFamily = sans),
        titleLarge = base.titleLarge.copy(fontFamily = sans),
        titleMedium = base.titleMedium.copy(fontFamily = sans, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = sans),
        bodyLarge = base.bodyLarge.copy(fontFamily = sans),
        bodyMedium = base.bodyMedium.copy(fontFamily = sans),
        bodySmall = base.bodySmall.copy(fontFamily = sans),
        labelLarge = base.labelLarge.copy(fontFamily = sans, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = sans),
        labelSmall = base.labelSmall.copy(fontFamily = sans),
    )
}

internal fun eolssuSerifTypography(serif: FontFamily) = EolssuSerifTypography(
    brand = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    songTitle = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    yul = TextStyle(fontFamily = serif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
)

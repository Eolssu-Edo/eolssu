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
import eolssu.shared.generated.resources.ibm_plex_sans_kr_bold
import eolssu.shared.generated.resources.ibm_plex_sans_kr_regular
import eolssu.shared.generated.resources.ibm_plex_sans_kr_semibold
import eolssu.shared.generated.resources.song_myung_regular
import org.jetbrains.compose.resources.Font

/**
 * 와이어프레임의 세리프 글자 스타일. Material [Typography]에 없는 율명·곡 제목용이다.
 *
 * 세리프 글꼴에는 자주 쓰는 한글 2,350자와 율명 한자만 들어 있다(`tools/fonts/build_fonts.py`).
 * 사용자가 입력한 글자(내 악보 제목 등)는 본문 글자 스타일(IBM Plex Sans KR)을 쓴다.
 */
@Immutable
data class EolssuSerifTypography(
    /** 앱 이름 '얼쑤' 같은 큰 제목. */
    val brand: TextStyle,
    /** 곡 상세의 곡 제목. */
    val songTitle: TextStyle,
    /** 홈 안내 영역의 대표 문구처럼 여러 줄로 쓰는 큰 문구. */
    val headline: TextStyle,
    /** 정간보의 율명. 크기는 칸에 맞춰 `copy(fontSize = ...)`로 바꾼다. */
    val yul: TextStyle,
)

@Composable
internal fun ibmPlexSansKrFamily() = FontFamily(
    Font(Res.font.ibm_plex_sans_kr_regular, FontWeight.Normal),
    Font(Res.font.ibm_plex_sans_kr_semibold, FontWeight.SemiBold),
    Font(Res.font.ibm_plex_sans_kr_bold, FontWeight.Bold),
)

@Composable
internal fun songMyungFamily() = FontFamily(Font(Res.font.song_myung_regular, FontWeight.Normal))

@Composable
internal fun notoSerifKrFamily() = FontFamily(
    Font(Res.font.noto_serif_kr_semibold, FontWeight.SemiBold),
    Font(Res.font.noto_serif_kr_bold, FontWeight.Bold),
)

/** 본문 IBM Plex Sans KR, 제목 Song Myung. 율명은 별도 Noto Serif KR를 유지한다. */
internal fun eolssuTypography(sans: FontFamily, heading: FontFamily = sans): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = sans),
        displayMedium = base.displayMedium.copy(fontFamily = sans),
        displaySmall = base.displaySmall.copy(fontFamily = sans),
        headlineLarge = base.headlineLarge.copy(fontFamily = heading),
        headlineMedium = base.headlineMedium.copy(fontFamily = heading),
        headlineSmall = base.headlineSmall.copy(fontFamily = heading),
        titleLarge = base.titleLarge.copy(fontFamily = heading),
        titleMedium = base.titleMedium.copy(fontFamily = heading, fontWeight = FontWeight.Normal),
        titleSmall = base.titleSmall.copy(fontFamily = sans),
        bodyLarge = base.bodyLarge.copy(fontFamily = sans),
        bodyMedium = base.bodyMedium.copy(fontFamily = sans),
        bodySmall = base.bodySmall.copy(fontFamily = sans),
        labelLarge = base.labelLarge.copy(fontFamily = sans, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = sans),
        labelSmall = base.labelSmall.copy(fontFamily = sans),
    )
}

internal fun eolssuSerifTypography(serif: FontFamily, heading: FontFamily = serif) = EolssuSerifTypography(
    brand = TextStyle(fontFamily = heading, fontWeight = FontWeight.Normal, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    songTitle = TextStyle(fontFamily = heading, fontWeight = FontWeight.Normal, fontSize = 30.sp),
    headline = TextStyle(fontFamily = heading, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
    yul = TextStyle(fontFamily = serif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
)

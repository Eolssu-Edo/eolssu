package com.ssafy.eolssu.core.theme

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LocalEolssuColors = staticCompositionLocalOf { LightEolssuColors }
private val LocalEolssuSerifTypography = staticCompositionLocalOf { eolssuSerifTypography(FontFamily.Serif) }

/**
 * 와이어프레임 모서리 기준: 입력·정보 칸 10dp, 카드 16dp, 팝업 20dp, 하단 시트 24dp.
 * 알약 모양 버튼·배지는 `CircleShape`를 쓴다.
 */
private val EolssuShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Material 컴포넌트가 와이어프레임 색을 쓰도록 [EolssuColors]를 Material 색 이름에 대응시킨다. */
private fun EolssuColors.toMaterialColorScheme() = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = surface,
    onPrimaryContainer = text,
    secondary = primary,
    onSecondary = onPrimary,
    secondaryContainer = surfaceStrong,
    onSecondaryContainer = text,
    tertiary = play,
    onTertiary = onPlay,
    tertiaryContainer = playSoft,
    onTertiaryContainer = playText,
    background = background,
    onBackground = text,
    surface = background,
    onSurface = text,
    surfaceVariant = surface,
    onSurfaceVariant = textMuted,
    surfaceTint = background,
    surfaceContainerLowest = background,
    surfaceContainerLow = background,
    surfaceContainer = background,
    surfaceContainerHigh = background,
    surfaceContainerHighest = surface,
    outline = line,
    outlineVariant = line,
    error = rec,
    onError = onRec,
    errorContainer = recSoft,
    onErrorContainer = recText,
)

@Composable
fun EolssuTheme(content: @Composable () -> Unit) {
    val colors = LightEolssuColors
    val sans = ibmPlexSansKrFamily()
    val serif = notoSerifKrFamily()
    // 제목도 본문 글꼴을 쓴다(S15P31E102-768).
    val heading = sans
    val typography = remember(sans, heading) { eolssuTypography(sans, heading) }
    val serifTypography = remember(serif, heading) { eolssuSerifTypography(serif, heading) }
    CompositionLocalProvider(
        LocalEolssuColors provides colors,
        LocalEolssuSerifTypography provides serifTypography,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            shapes = EolssuShapes,
            typography = typography,
        ) {
            // 스크롤 끝에서 화면이 늘어나는 Android 12+ 기본 효과를 끄고, 끝에 닿으면 그대로 멈추게 한다(S15P31E102-768).
            CompositionLocalProvider(LocalOverscrollFactory provides null, content = content)
        }
    }
}

/** 화면에서 `EolssuTheme.colors.play`, `EolssuTheme.serif.yul`처럼 와이어프레임 전용 토큰을 꺼낸다. */
object EolssuTheme {
    val colors: EolssuColors
        @Composable
        @ReadOnlyComposable
        get() = LocalEolssuColors.current

    val serif: EolssuSerifTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalEolssuSerifTypography.current
}

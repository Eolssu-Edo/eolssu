package com.ssafy.eolssu.feature.song.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.song_heading_griun
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font

/**
 * SONG-01 머리 문구(S15P31E102-1280): 홈 'AI 연주 평가 시작' 버튼과 같은 그리운 경찰감성체로 한 줄에 쓰고,
 * 화면에 처음 들어올 때 앞 글자부터 한 글자씩 적어 나간다.
 * 글자 자리는 처음부터 모두 잡아 두고 글자색 투명도만 바꿔서, 적는 동안 줄 길이와 아래 목록이 움직이지 않는다.
 * 스크린리더는 처음부터 전체 문구를 제목으로 읽는다. 목록을 내렸다 다시 올려도 다시 적지 않는다.
 * 동작 줄이기(애니메이션 배율 0)에서는 tween이 바로 끝나 전체 문구가 곧장 보인다.
 */
@Composable
internal fun SongListHeading(title: String, modifier: Modifier = Modifier) {
    val fontFamily = FontFamily(Font(Res.font.song_heading_griun, FontWeight.Normal))
    var written by rememberSaveable(title) { mutableStateOf(false) }
    val progress = remember(title) { Animatable(if (written) title.length.toFloat() else 0f) }
    LaunchedEffect(title) {
        if (written) return@LaunchedEffect
        // 웹은 글꼴을 비동기로 읽으므로, 다른 글꼴로 적다가 중간에 바뀌지 않게 잠깐 기다린 뒤 적기 시작한다.
        delay(WriteStartDelayMillis)
        val remaining = title.length - progress.value
        progress.animateTo(title.length.toFloat(), tween((remaining * WriteMillisPerChar).toInt(), easing = LinearEasing))
        written = true
    }
    val color = EolssuTheme.colors.text
    Text(
        writtenHeading(title, progress.value, color),
        modifier = modifier.fillMaxWidth().semantics { heading() },
        style = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSynthesis = FontSynthesis.None,
            fontSize = HeadingFontSize,
            lineHeight = 38.sp,
        ),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = HeadingFontSize, stepSize = 0.5.sp),
    )
}

/** [index]번째 글자가 [progress](적은 글자 수, 소수는 적는 중인 글자)에서 얼마나 진하게 보이는지(0~1). */
internal fun songHeadingCharAlpha(index: Int, progress: Float): Float = (progress - index).coerceIn(0f, 1f)

private fun writtenHeading(title: String, progress: Float, color: Color): AnnotatedString = buildAnnotatedString {
    title.forEachIndexed { index, char ->
        withStyle(SpanStyle(color = color.copy(alpha = color.alpha * songHeadingCharAlpha(index, progress)))) { append(char) }
    }
}

private val HeadingFontSize = 28.sp
private const val WriteMillisPerChar = 95
private const val WriteStartDelayMillis = 250L

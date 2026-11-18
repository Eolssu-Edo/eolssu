package com.ssafy.eolssu.feature.result.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.BrushEnso
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.feature.practice.component.practiceScorePacking
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.analysis_progress
import eolssu.shared.generated.resources.tiger_analysis_failed
import eolssu.shared.generated.resources.tiger_confused_atlas
import eolssu.shared.generated.resources.tiger_play
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/*
 * 연두 보드 ANALYSIS-01 부품과 연주 결과 정간보 높이. 크기·색은 보드 CSS(`.topbar` · `.badge.lite`)를 dp로 옮겼다.
 */

/** 보드 `.topbar`(56dp): 가운데 제목(Song Myung 19sp)과 보조 문구(12sp). 분석·결과 화면은 뒤로 가기·오른쪽 버튼이 없다. */
@Composable
fun BoardTopBar(title: String, modifier: Modifier = Modifier, subtitle: String? = null, maxWidth: Dp = CompactContentMaxWidth) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = maxWidth).fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 56.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 22.8.sp),
                color = EolssuTheme.colors.text,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = EolssuTheme.colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 보드 분석 대기 그림(220dp): 붓 일원상이 7시에서 시계 방향으로 그려지고([BrushEnso]) 가운데에 연주하는 호랑이(128dp).
 * 녹음 전송 비율([uploadProgress] 0~1)과 분석 대기를 합친 진행률([rememberAnalysisProgress])만큼 획을 그리고,
 * 호랑이 발과 고리 사이에 같은 값의 퍼센트를 적는다. 100%는 분석이 끝났을 때([done])만 보인다.
 * [uploadProgress]가 null이면 진행률을 모르므로 획을 되풀이해 그린다.
 */
@Composable
fun AnalyzingFigure(uploadProgress: Float? = null, done: Boolean = false, modifier: Modifier = Modifier) {
    // 붓 획과 숫자가 같은 값을 쓰도록 부드럽게 따라가는 값을 여기서 한 번만 계산한다.
    val shown = rememberAnalysisProgress(uploadProgress, done)
    val percent = shown?.let { floor(it * 100).toInt().coerceIn(0, 100) }
    val description = percent?.let { stringResource(Res.string.analysis_progress, it) }
    Box(
        modifier.size(220.dp).clearAndSetSemantics {
            if (description != null) contentDescription = description
        },
    ) {
        BrushEnso(color = EolssuTheme.colors.text, progress = shown, modifier = Modifier.fillMaxSize())
        // 보드 `translate(-50%, -52%)`에서 퍼센트 자리를 내려고 6dp 더 위로.
        Image(
            painterResource(Res.drawable.tiger_play),
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).offset(y = (-8).dp).width(128.dp),
            contentScale = ContentScale.FillWidth,
        )
        if (percent != null) {
            ProgressPercent(percent, Modifier.align(Alignment.TopCenter).offset(y = 167.dp))
        }
    }
}

/** 진행 퍼센트: 숫자는 Song Myung 18sp, 뒤의 `%`는 작고 옅게. 숫자 폭이 바뀌어도 흔들리지 않게 고정폭 숫자를 쓴다. */
@Composable
private fun ProgressPercent(percent: Int, modifier: Modifier = Modifier) {
    val muted = EolssuTheme.colors.textMuted
    Text(
        buildAnnotatedString {
            append(percent.toString())
            withStyle(SpanStyle(fontSize = (18 * 0.62).sp, color = muted)) { append("%") }
        },
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum"),
        color = EolssuTheme.colors.text,
    )
}

// 사용자 제공 ‘머리 긁는 호랑이’ GIF의 9프레임(각 160ms)을 배경만 투명하게 뺀 240px 세로 atlas.
private const val ConfusedFrameSize = 240
private const val ConfusedFrameCount = 9
private const val ConfusedFrameMillis = 160L

/**
 * 분석 실패 그림. 녹음 문제로 다시 연주해야 하면([recordingProblem]) 물음표를 띄우고 머리를 긁는 호랑이 움짤(160dp),
 * 서버·전송 문제면 실패 전용 호랑이 그림(132dp)이다.
 */
@Composable
fun AnalysisFailedFigure(recordingProblem: Boolean) {
    if (!recordingProblem) {
        Image(
            painterResource(Res.drawable.tiger_analysis_failed),
            contentDescription = null,
            modifier = Modifier.size(132.dp),
            contentScale = ContentScale.Fit,
        )
        return
    }
    val bitmap = imageResource(Res.drawable.tiger_confused_atlas)
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(ConfusedFrameMillis)
            frame = (frame + 1) % ConfusedFrameCount
        }
    }
    Canvas(Modifier.size(160.dp).clearAndSetSemantics {}) {
        if (bitmap.height >= ConfusedFrameSize * ConfusedFrameCount) drawImage(
            image = bitmap,
            srcOffset = IntOffset(0, frame * ConfusedFrameSize),
            srcSize = IntSize(ConfusedFrameSize, ConfusedFrameSize),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
    }
}

/** 보드 `.badge.lite`: 흰 바탕 · 옅은 테두리 알약(26dp)에 12sp 회색 글자. 곡 이름 등에 쓴다. */
@Composable
fun LiteBadge(text: String, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier.heightIn(min = 26.dp).background(colors.surface, shape).border(1.dp, colors.lineSoft, shape).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.textMuted, maxLines = 1)
    }
}

/**
 * 연습·평가 악보의 최소 정간(연습 [practiceScorePacking]의 최소 정간) 아래로 줄지 않는 가장 낮은 악보 높이.
 * 연습 배치를 그대로 돌려 최소 정간 이상이 되는 높이를 찾는다. 자리가 이보다 낮아도 이 높이로 배치해 크기를 지킨다.
 * [oneLine]이면 긴 각을 두 줄로 나누는 배치([PracticeScorePacking.splitColumns])는 건너뛰고 각이 한 줄에 들어가는 높이를 찾는다
 * (연주 결과 정간보, S15P31E102-740).
 */
internal fun weakSectionScoreHeight(song: Song, base: JeongganboMetrics, fontScale: Float, oneLine: Boolean = false): Dp {
    if (song.score.columns.isEmpty()) return 0.dp
    val minimum = minOf(base.cellHeight, if (base.cellHeight > 44.dp) 44.dp else 32.dp)
    var height = WeakSectionMinScan
    while (height < WeakSectionMaxScan) {
        val packing = practiceScorePacking(song.score, base, height, fontScale, song.jangdan)
        if (packing.metrics.cellHeight >= minimum && !(oneLine && packing.splitColumns)) return height
        height += WeakSectionScanStep
    }
    return WeakSectionMaxScan
}

private val WeakSectionMinScan = 40.dp
private val WeakSectionMaxScan = 1600.dp
private val WeakSectionScanStep = 4.dp

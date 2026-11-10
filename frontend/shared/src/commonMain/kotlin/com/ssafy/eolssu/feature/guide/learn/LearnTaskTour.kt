package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.HolePoint
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_tour_breath_in
import eolssu.shared.generated.resources.learn_tour_breath_out
import eolssu.shared.generated.resources.learn_tour_left_body
import eolssu.shared.generated.resources.learn_tour_left_hand
import eolssu.shared.generated.resources.learn_tour_right_body
import eolssu.shared.generated.resources.learn_tour_right_hand
import eolssu.shared.generated.resources.learn_tour_thumb_body
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos

/*
 * 2단계 '자세·호흡'·3단계 '첫 소리' 할 일 박스. 1단계처럼 서버가 준 할 일을 박스 하나씩 펼치며
 * 박스마다 그림(단계 그림·손 위치·숨쉬기·취구·첫 음 운지)과 한 줄 설명을 보인다. 할 일 문구는 서버 그대로 쓴다.
 */

/** 할 일 한 칸에 보일 그림. */
internal enum class TaskVisual {
    /** 2단계 그림(앉아서 악기를 든 호랑이). */
    Posture,

    /** 악기 그림에 왼손·오른손이 막는 구멍. */
    Hands,

    /** 들이쉬고 내쉬는 숨 동그라미. */
    Breath,

    /** 악기 그림에 취구. */
    Mouth,

    /** 3단계 그림(입김을 부는 호랑이). */
    Blow,

    /** 처음 낼 음의 운지 카드. */
    FirstNote,
}

/** 할 일 따라 배우기가 있는 단계. 4~7단계는 [LearnStageTour]가 칸을 만든다. */
internal fun hasTaskTour(stage: Int): Boolean = stage in 2..7

/**
 * [stage]의 [index]번째 할 일에 보일 그림. 서버 할 일 순서(2단계 앉은 자세·악기 잡기·복식호흡,
 * 3단계 취구에 입술 대기·입김 방향·세기 찾기·한 소리 길게 내기)와 짝 짓고, 더 있으면 단계 그림을 보인다.
 */
internal fun taskVisual(stage: Int, index: Int): TaskVisual = when (stage) {
    2 -> listOf(TaskVisual.Posture, TaskVisual.Hands, TaskVisual.Breath).getOrElse(index) { TaskVisual.Posture }
    else -> listOf(TaskVisual.Mouth, TaskVisual.Blow, TaskVisual.FirstNote).getOrElse(index) { TaskVisual.Blow }
}

/** 왼손이 막는 구멍(단소는 뒷구멍 엄지 포함). 1단계 짚는 곳의 손 구분을 그대로 쓴다. */
internal val GuideInstrument.leftHandHoles: List<HolePoint>
    get() = tourSpots.filter { it.body == Res.string.learn_tour_thumb_body || it.body == Res.string.learn_tour_left_body }.flatMap { it.points }

/** 오른손이 막는 구멍. */
internal val GuideInstrument.rightHandHoles: List<HolePoint>
    get() = tourSpots.filter { it.body == Res.string.learn_tour_right_body }.flatMap { it.points }

/** 숨 한 번(들이쉬기 4초 · 내쉬기 4초)의 길이. */
private const val BreathCycleMillis = 8000

/** 숨 한 번 가운데 [phase](0~1)가 들이쉬는 앞 절반인지. */
internal fun breathInhaling(phase: Float): Boolean = phase < 0.5f

/** 숨 동그라미 크기(0 가장 작음 ~ 1 가장 큼). 들이쉬며 부드럽게 커지고 내쉬며 작아진다. */
internal fun breathSize(phase: Float): Float = ((1f - cos(2f * PI.toFloat() * phase)) / 2f)

/**
 * 2·3단계 할 일 박스. 펼친 박스에 그 할 일의 그림(단계 그림·손 위치·숨쉬기·취구·첫 음 운지)과 한 줄 설명을 보인다.
 *
 * @param firstNote 3단계 '한 소리 길게 내기'에 보일 첫 음. 없으면 단계 그림을 보인다.
 */
@Composable
internal fun LearnTaskTour(
    instrument: GuideInstrument,
    stage: Int,
    tasks: List<LearnTask>,
    tour: LearnTour,
    firstNote: FingeringNote?,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
    imageMaxWidth: Dp = Dp.Unspecified,
    onAllDone: () -> Unit = {},
    practice: StepPractice? = null,
) {
    TourPanel(tasks, tour, onAllDone, practice) { s ->
        TaskFigure(taskVisual(stage, s), instrument, stage, firstNote, noteAudioContent, imageMaxWidth)
    }
}

@Composable
private fun TaskFigure(
    visual: TaskVisual,
    instrument: GuideInstrument,
    stage: Int,
    firstNote: FingeringNote?,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
    imageMaxWidth: Dp,
) {
    val colors = EolssuTheme.colors
    when (visual) {
        TaskVisual.Posture, TaskVisual.Blow -> StageArtFigure(stage)
        TaskVisual.Hands -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TourInstrumentImage(instrument, imageMaxWidth = imageMaxWidth) {
                SpotRings(instrument, TourSpot(Res.string.learn_tour_left_hand, Res.string.learn_tour_left_body, instrument.leftHandHoles), colors.rec)
                SpotRings(instrument, TourSpot(Res.string.learn_tour_right_hand, Res.string.learn_tour_right_body, instrument.rightHandHoles), colors.play)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HandLegend(stringResource(Res.string.learn_tour_left_hand), colors.rec)
                HandLegend(stringResource(Res.string.learn_tour_right_hand), colors.play)
            }
        }
        TaskVisual.Breath -> BreathFigure()
        TaskVisual.Mouth -> TourInstrumentImage(instrument, imageMaxWidth = imageMaxWidth) {
            SpotRings(instrument, instrument.tourSpots.first())
        }
        TaskVisual.FirstNote ->
            if (firstNote != null) {
                NoteCard(instrument, firstNote, noteAudioContent, Modifier.widthIn(max = 360.dp))
            } else {
                StageArtFigure(stage)
            }
    }
}

@Composable
internal fun StageArtFigure(stage: Int) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(16.dp)
    LearnStageArt(stage, Modifier.size(180.dp).clip(shape).background(colors.background).border(1.dp, colors.lineSoft, shape))
}

@Composable
private fun HandLegend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(12.dp).border(2.5.dp, color, CircleShape))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** 숨쉬기: 동그라미가 4초 동안 커지며 '들이쉬기', 4초 동안 작아지며 '내쉬기'. */
@Composable
private fun BreathFigure() {
    val colors = EolssuTheme.colors
    val phase by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BreathCycleMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "breathPhase",
    )
    val size = breathSize(phase)
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(180.dp).clearAndSetSemantics {}) {
            val max = this.size.minDimension / 2
            val r = max * (0.45f + 0.55f * size)
            drawCircle(colors.accentSoft, radius = max)
            drawCircle(colors.accentTint, radius = r)
            drawCircle(colors.play, radius = r, style = Stroke(3.dp.toPx()))
        }
        Text(
            stringResource(if (breathInhaling(phase)) Res.string.learn_tour_breath_in else Res.string.learn_tour_breath_out),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            color = colors.accentText,
        )
    }
}

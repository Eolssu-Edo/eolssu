package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import eolssu.shared.generated.resources.Res
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eolssu.shared.generated.resources.learn_step_all_done
import eolssu.shared.generated.resources.learn_step_box_description
import eolssu.shared.generated.resources.learn_step_progress
import eolssu.shared.generated.resources.learn_step_state_done
import eolssu.shared.generated.resources.learn_step_state_locked
import eolssu.shared.generated.resources.learn_step_state_open
import eolssu.shared.generated.resources.learn_step_try

/*
 * 단계의 할 일 박스(S15P31E102-688). 할 일마다 박스 하나를 위에서 아래로 쌓는다. 처음에는 1번 박스가 펼쳐져 그림과 한 줄 설명,
 * '해 봤어요'를 보이고, 누르면 그 박스가 체크로 접히며 다음 박스가 펼쳐진다. 마친 박스는 눌러서 다시 볼 수 있고,
 * 아직 차례가 아닌 박스는 옅게 잠겨 있다. 위의 진행 막대가 마친 수만큼 차오른다.
 */

/** 단계마다 할 일 박스 수. 1단계는 짚을 곳, 2·3단계는 서버 할 일, 4~7단계는 따라 배우기 칸이다. 박스가 없으면 0. */
internal fun learnStepCount(stage: Int, detail: LearnStageDetail, instrument: GuideInstrument): Int = when {
    stage == 1 -> instrument.tourSpots.size
    stage == 2 || stage == 3 -> detail.tasks.size
    hasTaskTour(stage) -> stageTourCount(stage, detail, instrument)
    else -> 0
}

/**
 * 단계 화면 안에서 하는 연습(S15P31E102-688). 3~7단계는 할 일 박스 뒤에 이 박스를 하나 더 두고, 펼치면 '듣기 시작'으로 바로 판정한다.
 *
 * @property title 연습 박스 이름.
 * @property content 펼친 연습 박스 내용(통과 기준 · 듣기 판 · 듣기 버튼). '해 봤어요' 대신 통과·스스로 확인으로 마친다.
 */
class StepPractice(val title: String, val content: @Composable () -> Unit)

/**
 * 할 일 박스 묶음. [figure]는 펼친 박스에 보일 그림이다. [practice]가 있으면 마지막에 연습 박스를 붙인다.
 * 할 일 박스를 모두 마치면 연습 박스가 펼쳐지고, 연습 박스가 없으면 [onAllDone]을 부른다.
 */
@Composable
internal fun TourPanel(
    tasks: List<LearnTask>,
    tour: LearnTour,
    onAllDone: () -> Unit = {},
    practice: StepPractice? = null,
    figure: @Composable (Int) -> Unit,
) {
    if (tasks.isEmpty() && practice == null) return
    val total = tasks.size + if (practice != null) 1 else 0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepProgress(done = tour.doneCount.coerceAtMost(total), total = total)
        tasks.forEachIndexed { i, task ->
            StepBox(
                index = i,
                task = task,
                done = tour.isDone(i),
                open = tour.open == i,
                locked = i > tour.doneCount && tour.open != i,
                onToggle = { tour.toggle(i) },
                onTry = { if (tour.complete(i, total)) onAllDone() },
            ) { figure(i) }
        }
        if (practice != null) {
            val i = tasks.size
            StepBox(
                index = i,
                task = LearnTask(practice.title),
                done = tour.isDone(i),
                open = tour.open == i,
                locked = i > tour.doneCount && tour.open != i,
                onToggle = { tour.toggle(i) },
                onTry = {},
                practice = practice.content,
            ) {}
        }
    }
}

/** 진행 막대: '할 일 n / 전체'와 마친 만큼 부드럽게 차오르는 막대. 다 마치면 축하 문구로 바뀐다. */
@Composable
private fun StepProgress(done: Int, total: Int) {
    val colors = EolssuTheme.colors
    val fraction by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, tween(600, easing = FastOutSlowInEasing), label = "stepProgress")
    val allDone = done >= total
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (allDone) stringResource(Res.string.learn_step_all_done) else stringResource(Res.string.learn_step_progress, done, total),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = if (allDone) colors.accentText else colors.textMuted,
        )
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(colors.line)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape).background(colors.primary))
        }
    }
}

/**
 * 할 일 박스 하나. 처음 그릴 때 차례대로 아래에서 떠오르고, 펼치고 접힐 때 높이가 부드럽게 바뀐다.
 * 마치면 번호 동그라미가 체크로 통통 튀며 바뀐다.
 */
@Composable
private fun StepBox(
    index: Int,
    task: LearnTask,
    done: Boolean,
    open: Boolean,
    locked: Boolean,
    onToggle: () -> Unit,
    onTry: () -> Unit,
    practice: (@Composable () -> Unit)? = null,
    figure: @Composable () -> Unit,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * EntranceStaggerMillis)
        appear.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    val background by animateColorAsState(
        when {
            open -> colors.surface
            done -> colors.accentSoft
            else -> colors.surface
        },
        label = "stepBackground",
    )
    val borderColor by animateColorAsState(
        when {
            open -> colors.play
            done -> colors.accentTintLine
            else -> colors.lineSoft
        },
        label = "stepBorder",
    )
    val borderWidth by animateDpAsState(if (open) 2.dp else 1.dp, label = "stepBorderWidth")
    // 처음 그릴 때가 아니라 '해 봤어요'·누르기로 새로 펼쳐지면, 펼쳐지는 동안 그 박스가 보이도록 화면을 내린다.
    val requester = remember { BringIntoViewRequester() }
    var firstOpen by remember { mutableStateOf(true) }
    LaunchedEffect(open) {
        if (open && !firstOpen) {
            delay(BringIntoViewDelayMillis)
            requester.bringIntoView()
        }
        firstOpen = false
    }
    val state = stringResource(
        when {
            done -> Res.string.learn_step_state_done
            locked -> Res.string.learn_step_state_locked
            else -> Res.string.learn_step_state_open
        },
    )
    val description = stringResource(Res.string.learn_step_box_description, index + 1, task.title, state)
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value * if (locked) 0.55f else 1f
                translationY = (1f - appear.value) * 28.dp.toPx()
            }
            .bringIntoViewRequester(requester)
            .clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape)
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !locked, onClick = onToggle)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepBadge(index + 1, done, open)
            Text(
                task.title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 16.sp,
                    fontWeight = if (open) FontWeight.Bold else FontWeight.SemiBold,
                ),
                color = if (locked) colors.textMuted else colors.text,
            )
            if (!locked) {
                val turn by animateFloatAsState(if (open) 180f else 0f, label = "stepChevron")
                Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp).rotate(turn), tint = colors.textMuted)
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(260, delayMillis = 80)),
            exit = shrinkVertically(tween(220)) + fadeOut(tween(140)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (practice != null) {
                    practice()
                    return@Column
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { figure() }
                task.detail?.let {
                    Text(
                        it,
                        Modifier.fillMaxWidth().background(colors.accentSoft, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                    )
                }
                EolssuButton(stringResource(Res.string.learn_step_try), onTry, Modifier.fillMaxWidth(), icon = EolssuIcons.Check)
            }
        }
    }
}

/** 번호 동그라미. 마치면 연두로 칠해지며 체크가 튀어나오고, 지금 할 일은 진한 테두리다. */
@Composable
private fun StepBadge(number: Int, done: Boolean, open: Boolean) {
    val colors = EolssuTheme.colors
    val pop by animateFloatAsState(if (done) 1f else 0f, spring(dampingRatio = 0.4f, stiffness = 380f), label = "stepCheckPop")
    val fill by animateColorAsState(if (done) colors.primary else colors.surface, label = "stepBadgeFill")
    Box(
        Modifier
            .size(30.dp)
            .graphicsLayer {
                val s = 1f + 0.18f * pop * (1f - pop) * 4f
                scaleX = s
                scaleY = s
            }
            .background(fill, CircleShape)
            .border(if (open && !done) 2.dp else 1.dp, if (done) colors.primary else if (open) colors.play else colors.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (pop > 0.01f) {
            Icon(
                EolssuIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp).graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                },
                tint = colors.onPrimary,
            )
        }
        if (pop < 0.99f) {
            Text(
                number.toString(),
                Modifier.graphicsLayer { alpha = 1f - pop },
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = if (open) colors.text else colors.textMuted,
            )
        }
    }
}

/** 박스가 펼쳐지기 시작하고 화면을 내리기까지 기다리는 시간(펼친 높이가 어느 정도 잡힌 뒤). */
private const val BringIntoViewDelayMillis = 320L

/** 박스가 하나씩 떠오르는 간격. */
private const val EntranceStaggerMillis = 70L

package com.ssafy.eolssu.feature.guide.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget
import com.ssafy.eolssu.core.diagnostics.QaKind

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuTextLink
import com.ssafy.eolssu.feature.guide.learn.LearnJudge
import com.ssafy.eolssu.feature.guide.learn.LearnTask
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_judge_beat
import eolssu.shared.generated.resources.learn_judge_listen
import eolssu.shared.generated.resources.learn_judge_pitch
import eolssu.shared.generated.resources.learn_judge_read
import org.jetbrains.compose.resources.StringResource

/*
 * 기초부터 배우기 화면 조각(`.lp-*`). 단계 목록과 단계 상세가 함께 쓴다.
 */

val LearnJudge.label: StringResource
    get() = when (this) {
        LearnJudge.Read -> Res.string.learn_judge_read
        LearnJudge.Pitch -> Res.string.learn_judge_pitch
        LearnJudge.Beat -> Res.string.learn_judge_beat
        LearnJudge.Listen -> Res.string.learn_judge_listen
    }

@Immutable
private data class TagColors(val background: Color, val content: Color)

/** 판정 방식 태그(`.lp-judge`). 설명은 흰 바탕 회색 글자에 옅은 테두리, 음높이는 옅은 연두에 이끼 글자, 박 · 듣기는 연두(#DEE5AD)에 검은 글자. */
@Composable
internal fun JudgeTag(text: String, judge: LearnJudge, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val tag = when (judge) {
        LearnJudge.Read -> TagColors(colors.surface, colors.textMuted)
        LearnJudge.Pitch -> TagColors(colors.accentTint, colors.playText)
        LearnJudge.Beat, LearnJudge.Listen -> TagColors(colors.accentTintStrong, colors.text)
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .height(20.dp)
            .background(tag.background, shape)
            .then(if (judge == LearnJudge.Read) Modifier.border(1.dp, colors.lineSoft, shape) else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = tag.content, maxLines = 1)
    }
}

/** 둥근 사각형 테두리를 점선으로 그린다(선택 단계 `.lp-stage.optional`). */
internal fun Modifier.dashedRoundBorder(color: Color, radius: Dp, width: Dp = 1.dp): Modifier = drawBehind {
    val stroke = width.toPx()
    val inset = stroke / 2
    drawRoundRect(
        color,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = size.copy(width = size.width - stroke, height = size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx() - inset),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/**
 * 단계 상세의 한 묶음. 묶음마다 붙던 이름표('악기 살펴보기'·'이 단계에서 할 일' 등)는 내용과 겹쳐 뺐다(S15P31E102-793).
 * [more]가 있으면 묶음 위 오른쪽에 '운지법 보기' 같은 글자 링크를 둔다.
 */
@Composable
internal fun LearnSection(
    modifier: Modifier = Modifier,
    more: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        if (more != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End) { more() }
        }
        content()
    }
}

/**
 * '더 보기' 글자 링크(`.lp-more`). 섹션 머리 줄은 28dp 알약 높이만 차지하고, 48dp 누르는 영역은 위아래로 넘쳐 걸친다.
 * 알약 안쪽 여백(12dp) 때문에 글자가 안으로 들어가므로, 글자를 끝선에 맞출 곳은 부르는 쪽에서 [modifier]로 당긴다(S15P31E102-968).
 */
@Composable
internal fun MoreLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    EolssuTextLink(onClick, modifier, pillHeight = 28.dp, reserveTouchHeight = false, qaTarget = QaTarget.LEARN_PIECES_LINK_01) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
        Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(14.dp))
    }
}

/**
 * 할 일 목록(`.lp-tasks`): 흰 바탕에 옅은 테두리, 번호는 옅은 회색 원. 태블릿은 [columns] 2칸 격자로 놓고 같은 줄의 높이를 맞춘다.
 * [collapsible]이면 제목만 보이고, 설명이 있는 줄은 눌러서 펼친다(단계 상세에서 글을 줄이려고).
 */
@Composable
internal fun LearnTaskList(tasks: List<LearnTask>, columns: Int = 1, collapsible: Boolean = false) {
    val colors = EolssuTheme.colors
    var openIndex by remember(tasks) { mutableStateOf<Int?>(null) }
    GuideGrid(tasks, columns) { i, task, cell ->
        val canOpen = collapsible && task.detail != null
        val open = !collapsible || openIndex == i
        Row(
            cell
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (canOpen) {
                        Modifier
                            .clickable { UserActionLog.event(QaKind.CLICK, QaTarget.LEARN_PIECES_01); openIndex = if (openIndex == i) null else i }
                            .semantics { if (open) collapse { openIndex = null; true } else expand { openIndex = i; true } }
                    } else {
                        Modifier
                    },
                )
                .background(colors.surface, RoundedCornerShape(12.dp))
                .border(1.dp, colors.lineSoft, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.padding(top = 1.dp).size(22.dp).border(1.5.dp, GuideFaint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = colors.textMuted)
            }
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                if (task.detail != null && open) {
                    GuideText(
                        task.detail,
                        Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.5.sp),
                        color = colors.textMuted,
                    )
                }
            }
            if (canOpen) {
                Icon(
                    EolssuIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 5.dp).size(12.dp).rotate(if (open) 180f else 0f),
                    tint = GuideFaint,
                )
            }
        }
    }
}

/**
 * 율명 칩을 ›로 잇는다(`.lp-chips`). 운지표에 없는 글자는 건너뛴다.
 * 순서를 한눈에 보도록 항상 한 줄에 놓고, 칸이 모자라면 칩 폭을 줄인다(율명 위 · 읽는 법 아래).
 */
@Composable
internal fun NoteChips(notes: List<FingeringNote>, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gaps = (notes.size - 1).coerceAtLeast(0)
        val chipWidth = ((maxWidth - NoteChipArrow * gaps) / notes.size.coerceAtLeast(1)).coerceAtMost(NoteChipMaxWidth)
        Row(verticalAlignment = Alignment.CenterVertically) {
            notes.forEachIndexed { i, note ->
                if (i > 0) {
                    Text(
                        "›",
                        Modifier.width(NoteChipArrow).clearAndSetSemantics {},
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = GuideFaint,
                        textAlign = TextAlign.Center,
                    )
                }
                Column(
                    Modifier
                        .width(chipWidth)
                        .height(52.dp)
                        .background(colors.surface, RoundedCornerShape(10.dp))
                        .border(1.dp, colors.lineSoft, RoundedCornerShape(10.dp))
                        .semantics(mergeDescendants = true) { contentDescription = note.name },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    HanjaGlyph(note.hanja, 18.sp, Modifier.clearAndSetSemantics {})
                    Text(
                        note.reading,
                        Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        color = colors.textMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 율명 칩 사이 › 칸과 칩의 최대 폭. */
private val NoteChipArrow = 12.dp
private val NoteChipMaxWidth = 56.dp

/** 흰 바탕 묶음(`.lp-pattern`). */
@Composable
internal fun LearnPatternBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = EolssuTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.lineSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** 안내 상자(`.lp-note`): 흰 바탕에 옅은 테두리. [caution]은 아래에 회색으로 덧붙인다. */
@Composable
internal fun LearnNote(raw: String, modifier: Modifier = Modifier, caution: String? = null) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp)
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.lineSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        GuideText(raw, style = style)
        if (caution != null) GuideText(caution, Modifier.padding(top = 4.dp), style = style, color = colors.textMuted)
    }
}

/** 회색 작은 설명(`.muted.small`). */
@Composable
internal fun LearnSmallNote(raw: String, modifier: Modifier = Modifier) {
    GuideText(raw, modifier, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp), color = EolssuTheme.colors.textMuted)
}

/** 정간보 쉽게 읽기에서 가져온 그림 카드(`.gcard.borrow`). 자세한 설명은 묶음 제목의 '더 보기'로 연다. */
@Composable
internal fun BorrowCard(content: @Composable ColumnScope.() -> Unit) {
    GuideCard { content() }
}

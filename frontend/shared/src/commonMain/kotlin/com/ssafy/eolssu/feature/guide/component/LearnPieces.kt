package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.guide.FingeringNote
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

/** 박 판정(노랑)·듣기(초록) 태그 색. 와이어프레임 값에서 듣기 글자색만 대비 4.5:1이 되도록 어둡게 했다. */
private val BeatTag = TagColors(Color(0xFFFFF3CF), Color(0xFF8A6400))
private val ListenTag = TagColors(Color(0xFFE6F2DF), Color(0xFF2A7A50))

/** 판정 방식 태그(`.lp-judge`). 설명은 회색, 음높이는 파랑, 박은 노랑, 듣기는 초록. */
@Composable
internal fun JudgeTag(text: String, judge: LearnJudge, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val tag = when (judge) {
        LearnJudge.Read -> TagColors(colors.surface, colors.textMuted)
        LearnJudge.Pitch -> TagColors(colors.playSoft, colors.playText)
        LearnJudge.Beat -> BeatTag
        LearnJudge.Listen -> ListenTag
    }
    Box(
        modifier.height(20.dp).background(tag.background, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp),
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

/** 단계 상세의 한 묶음(`.lp-sec`). 제목 오른쪽에 '더 보기' 링크를 둘 수 있다. */
@Composable
internal fun LearnSection(
    title: String,
    modifier: Modifier = Modifier,
    more: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = EolssuTheme.colors.textMuted,
            )
            more?.invoke()
        }
        content()
    }
}

/** '더 보기' 글자 링크(`.lp-more`). */
@Composable
internal fun MoreLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
        Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(14.dp))
    }
}

/** 할 일 목록(`.lp-tasks`). */
@Composable
internal fun LearnTaskList(tasks: List<LearnTask>) {
    val colors = EolssuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tasks.forEachIndexed { i, task ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.background, RoundedCornerShape(12.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.padding(top = 1.dp).size(22.dp).border(1.5.dp, colors.lineStrong, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = colors.textMuted)
                }
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    if (task.detail != null) {
                        GuideText(
                            task.detail,
                            Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.5.sp),
                            color = colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

/** 율명 칩을 ›로 잇는다(`.lp-chips`). 운지표에 없는 글자는 건너뛴다. */
@Composable
internal fun NoteChips(notes: List<FingeringNote>, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        notes.forEachIndexed { i, note ->
            if (i > 0) {
                Text("›", Modifier.padding(horizontal = 1.dp).clearAndSetSemantics {}, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.lineStrong)
            }
            Row(
                Modifier
                    .height(38.dp)
                    .widthIn(min = 38.dp)
                    .background(colors.background, RoundedCornerShape(10.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp)
                    .semantics(mergeDescendants = true) { contentDescription = note.name },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HanjaGlyph(note.hanja, 19.sp, Modifier.clearAndSetSemantics {})
                Text(note.reading, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = colors.textMuted)
            }
        }
    }
}

/** 흰 바탕 묶음(`.lp-pattern`). */
@Composable
internal fun LearnPatternBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = EolssuTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.background, RoundedCornerShape(12.dp))
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** 노란 안내 상자(`.lp-note`). [caution]은 아래에 회색으로 덧붙인다. */
@Composable
internal fun LearnNote(raw: String, modifier: Modifier = Modifier, caution: String? = null) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp)
    Column(modifier.fillMaxWidth().background(colors.accentSoft, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        GuideText(raw, style = style)
        if (caution != null) GuideText(caution, Modifier.padding(top = 4.dp), style = style, color = colors.textMuted)
    }
}

/** 회색 작은 설명(`.muted.small`). */
@Composable
internal fun LearnSmallNote(raw: String, modifier: Modifier = Modifier) {
    GuideText(raw, modifier, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp), color = EolssuTheme.colors.textMuted)
}

/** 정간보 쉽게 읽기에서 가져온 설명 카드(`.gcard.borrow`). 아래에 [caption]을 단다. */
@Composable
internal fun BorrowCard(caption: String, content: @Composable ColumnScope.() -> Unit) {
    GuideCard {
        content()
        Text(
            caption,
            Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.5.sp),
            color = EolssuTheme.colors.textMuted,
        )
    }
}

package com.ssafy.eolssu.feature.guide.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.ui.component.EolssuMenuItem
import com.ssafy.eolssu.core.ui.component.EolssuMenuPanel
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.guide.ReadingPage
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_reading_nav_next
import eolssu.shared.generated.resources.guide_reading_page_dot
import eolssu.shared.generated.resources.guide_reading_page_intro
import eolssu.shared.generated.resources.guide_reading_nav_prev
import eolssu.shared.generated.resources.guide_reading_toc_closed
import eolssu.shared.generated.resources.guide_reading_toc_open
import eolssu.shared.generated.resources.guide_reading_length
import eolssu.shared.generated.resources.guide_reading_order
import eolssu.shared.generated.resources.guide_reading_pitch
import eolssu.shared.generated.resources.guide_reading_sigimsae
import eolssu.shared.generated.resources.guide_sigimsae_more
import org.jetbrains.compose.resources.stringResource

/*
 * 정간보 쉽게 읽기 휴대폰 배치의 책 조각: 위쪽 쪽 목차 토글(`.rb-toc`)과 아래쪽 이전 · 점 · 다음(`.rb-nav`).
 */

/** 쪽 이름(목차 · 쪽 머리). */
@Composable
internal fun ReadingPage.title(): String = stringResource(
    when (this) {
        ReadingPage.Intro -> Res.string.guide_reading_page_intro
        ReadingPage.Order -> Res.string.guide_reading_order
        ReadingPage.Length -> Res.string.guide_reading_length
        ReadingPage.Pitch -> Res.string.guide_reading_pitch
        ReadingPage.Sigimsae -> Res.string.guide_reading_sigimsae
        ReadingPage.SigimsaeMore -> Res.string.guide_sigimsae_more
    },
)

/** 쪽 목차 단추: 지금 쪽의 표시 · 이름과 펼침 화살표. */
@Composable
internal fun ReadingTocButton(page: ReadingPage, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val state = stringResource(if (open) Res.string.guide_reading_toc_open else Res.string.guide_reading_toc_closed)
    val turn by animateFloatAsState(if (open) 180f else 0f)
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = UserActionLog.click(QaTarget.READING_BOOK_01, onClick))
            .semantics { stateDescription = state }
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PageMark(page.mark)
        Text(
            page.title(),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(16.dp).rotate(turn), tint = colors.textMuted)
    }
}

/** 펼친 쪽 목차. 앱 공통 메뉴 모양([EolssuMenuPanel])이고 지금 쪽은 연한 연두 바탕 · 확인 표시다(S15P31E102-768). */
@Composable
internal fun ReadingTocMenu(current: ReadingPage, onSelect: (ReadingPage) -> Unit, modifier: Modifier = Modifier) {
    EolssuMenuPanel(modifier = modifier) {
        ReadingPage.entries.forEach { page ->
            EolssuMenuItem(page.title(), page == current, onClick = UserActionLog.click(QaTarget.READING_BOOK_02, { onSelect(page) }), leading = { PageMark(page.mark) })
        }
    }
}

/**
 * 아래 넘김 줄: 이전 · 쪽 점 · 다음([GuidePageNav], 기초 배우기 단계 상세와 같은 모양, S15P31E102-877).
 * 첫 쪽의 이전과 마지막 쪽의 다음은 옅게 둔다. 구분선과 여백은 기초 배우기 아래 영역과 같다.
 */
@Composable
internal fun ReadingBookNav(current: ReadingPage, onGo: (ReadingPage) -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val previous = current.previous
    val next = current.next
    val previousLabel = previous?.let { stringResource(Res.string.guide_reading_nav_prev, it.ordinal + 1, it.title()) }
    val nextLabel = next?.let { stringResource(Res.string.guide_reading_nav_next, it.ordinal + 1, it.title()) }
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.surfaceStrong)
        GuidePageNav(
            previousLabel = previousLabel,
            nextLabel = nextLabel,
            onPrevious = { previous?.let(onGo) },
            onNext = { next?.let(onGo) },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp),
        ) {
            // 점마다 32dp 폭 누르는 칸이 붙어 있어 따로 간격을 두지 않는다(S15P31E102-968).
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReadingPage.entries.forEach { page -> PageDot(page, on = page == current) { onGo(page) } }
            }
        }
    }
}

/**
 * 쪽 점. 지금 쪽은 길고 녹색, 나머지는 옅은 회색이다.
 * 누르는 영역은 32×48dp로 점보다 넓고, 눌림 효과는 점 둘레 32dp 원으로 그린다(S15P31E102-968).
 */
@Composable
private fun PageDot(page: ReadingPage, on: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val width by animateDpAsState(if (on) 20.dp else 8.dp)
    val description = stringResource(Res.string.guide_reading_page_dot, page.ordinal + 1, page.title())
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(width = 32.dp, height = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = UserActionLog.click(QaTarget.READING_BOOK_03, onClick))
            .semantics {
                contentDescription = description
                selected = on
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).indication(interaction, ripple()), contentAlignment = Alignment.Center) {
            Box(Modifier.width(width).height(8.dp).background(if (on) colors.green else GuideFaint, RoundedCornerShape(4.dp)))
        }
    }
}

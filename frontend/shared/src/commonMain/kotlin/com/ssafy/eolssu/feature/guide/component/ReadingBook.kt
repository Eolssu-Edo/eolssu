package com.ssafy.eolssu.feature.guide.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import eolssu.shared.generated.resources.guide_reading_next
import eolssu.shared.generated.resources.guide_reading_page_dot
import eolssu.shared.generated.resources.guide_reading_page_intro
import eolssu.shared.generated.resources.guide_reading_prev
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
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = state }
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PageMark(page.mark)
        Text(
            page.title(),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
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
            EolssuMenuItem(page.title(), page == current, onClick = { onSelect(page) }, leading = { PageMark(page.mark) })
        }
    }
}

/** 아래 이전 · 점 · 다음. 첫 쪽에는 이전, 마지막 쪽에는 다음이 없고, 빈자리를 남겨 점은 가운데에 그대로 둔다. 다음은 녹색. */
@Composable
internal fun ReadingBookNav(current: ReadingPage, onGo: (ReadingPage) -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val pages = ReadingPage.entries
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.lineSoft)
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                current.previous?.let { prev -> PageButton(stringResource(Res.string.guide_reading_prev), next = false) { onGo(prev) } }
            }
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                pages.forEach { page -> PageDot(page, on = page == current) { onGo(page) } }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                current.next?.let { next -> PageButton(stringResource(Res.string.guide_reading_next), next = true) { onGo(next) } }
            }
        }
    }
}

@Composable
private fun PageButton(label: String, next: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    // 다른 버튼([com.ssafy.eolssu.core.ui.component.EolssuButton] 작은 크기)과 같은 모서리 12dp 사각 버튼(S15P31E102-768).
    val shape = RoundedCornerShape(12.dp)
    val fg = if (next) colors.onPrimary else colors.text
    Row(
        Modifier
            .height(44.dp)
            .clip(shape)
            .background(if (next) colors.green else colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!next) Icon(EolssuIcons.Back, contentDescription = null, modifier = Modifier.size(16.dp), tint = fg)
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = fg, maxLines = 1)
        if (next) Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp), tint = fg)
    }
}

/** 쪽 점. 지금 쪽은 길고 녹색, 나머지는 옅은 회색이다. 누르는 영역은 점보다 넓다. */
@Composable
private fun PageDot(page: ReadingPage, on: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val width by animateDpAsState(if (on) 20.dp else 8.dp)
    val description = stringResource(Res.string.guide_reading_page_dot, page.ordinal + 1, page.title())
    Box(
        Modifier
            .height(28.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                selected = on
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(width).height(8.dp).background(if (on) colors.green else GuideFaint, RoundedCornerShape(4.dp)))
    }
}

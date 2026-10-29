package com.ssafy.eolssu.feature.guide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.guide.component.GuideCard
import com.ssafy.eolssu.feature.guide.component.GuideTopBar
import com.ssafy.eolssu.feature.guide.component.LengthRow
import com.ssafy.eolssu.feature.guide.component.PitchSteps
import com.ssafy.eolssu.feature.guide.component.ReadIntro
import com.ssafy.eolssu.feature.guide.component.ReadOrderBoard
import com.ssafy.eolssu.feature.guide.component.ReadingBookNav
import com.ssafy.eolssu.feature.guide.component.ReadingTocButton
import com.ssafy.eolssu.feature.guide.component.ReadingTocMenu
import com.ssafy.eolssu.feature.guide.component.SigimsaeBasicBlock
import com.ssafy.eolssu.feature.guide.component.SigimsaeMorePage
import com.ssafy.eolssu.feature.guide.component.guidePageMaxWidth
import com.ssafy.eolssu.feature.guide.component.guideSidePadding
import com.ssafy.eolssu.feature.guide.component.title
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_reading_sigimsae_small
import eolssu.shared.generated.resources.guide_reading_title
import eolssu.shared.generated.resources.guide_sigimsae_credit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource

/**
 * GUIDE-02 정간보 쉽게 읽기. 정간보란 · 1 읽는 순서 · 2 음의 길이 · 3 음의 높낮이 · 4 시김새 · 더 많은 시김새를 한 쪽씩 둔 책이다.
 * 휴대폰은 쪽 목차 토글 · 가로로 넘기는 쪽 · 이전/점/다음으로 한 쪽씩 보고(키보드 ←·→ · PageUp·PageDown, 목차는 Esc로 닫음),
 * 태블릿은 넘기지 않고 모든 쪽을 격자로 펼친다([readingTabletRows]).
 * 상태는 지금 쪽 · 목차 열림 · 시김새 창 · '더 많은 시김새'에서 설명을 펼친 칸뿐이라 ViewModel 없이 화면이 들고 있다.
 *
 * @param initialPage 처음 펼칠 쪽. 기초부터 배우기 7·8·9단계가 해당 쪽을 넘긴다.
 */
@Composable
fun ReadingScreen(
    onBack: () -> Unit,
    initialPage: ReadingPage = ReadingPage.Intro,
    widthClass: WindowWidthClass = currentWindowLayout().widthClass,
) {
    var moreTip by rememberSaveable { mutableStateOf<Int?>(null) }
    EolssuScaffold(
        topBar = { GuideTopBar(stringResource(Res.string.guide_reading_title), onBack = onBack, widthClass = widthClass) },
        contentMaxWidth = widthClass.guidePageMaxWidth,
    ) {
        if (widthClass == WindowWidthClass.Compact) {
            ReadingBook(initialPage, moreTip, onMoreTip = { moreTip = it })
        } else {
            ReadingTabletGrid(widthClass, moreTip, onMoreTip = { moreTip = it })
        }
    }
}

/** 휴대폰: 한 쪽씩 넘기는 책. 보이지 않는 쪽은 화면 읽기·누르기에서 뺀다. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ReadingBook(initialPage: ReadingPage, moreTip: Int?, onMoreTip: (Int?) -> Unit) {
    val pages = ReadingPage.entries
    val pagerState = rememberPagerState(initialPage = initialPage.ordinal) { pages.size }
    var tocOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val current = pages[pagerState.currentPage]
    val settled = pages[pagerState.settledPage]
    val go = { page: ReadingPage ->
        tocOpen = false
        scope.launch { pagerState.animateScrollToPage(page.ordinal) }
        Unit
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(enabled = tocOpen) { tocOpen = false }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || tocOpen || e.isAltPressed || e.isCtrlPressed || e.isMetaPressed) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionRight, Key.PageDown -> pages.getOrNull(settled.ordinal + 1)?.let(go) != null
                    Key.DirectionLeft, Key.PageUp -> pages.getOrNull(settled.ordinal - 1)?.let(go) != null
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            ReadingTocButton(current, tocOpen, onClick = { tocOpen = !tocOpen }, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
            HorizontalPager(pagerState, Modifier.weight(1f).fillMaxWidth().mouseDragPaging(pagerState)) { index ->
                val page = pages[index]
                val visible = index == pagerState.settledPage
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
                ) {
                    val viewport = maxHeight
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        // 내용이 짧으면 위아래 여백을 똑같이 나눠 가운데에 놓고, 길면 위에서부터 스크롤한다
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = viewport).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ReadingPageCard(
                                page,
                                Modifier,
                                sigimsaeColumns = 1,
                                moreColumns = 4,
                                animate = visible && page == ReadingPage.Order,
                                moreTip = moreTip,
                                onMoreTip = onMoreTip,
                                interactive = visible,
                            )
                        }
                    }
                }
            }
            ReadingBookNav(current, onGo = go)
        }
        if (tocOpen) {
            // 목차 밖을 누르면 닫는다
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tocOpen = false },
            )
            ReadingTocMenu(current, onSelect = go, Modifier.padding(start = 20.dp, end = 20.dp, top = 46.dp))
        }
    }
}

/**
 * 마우스로 끌어 쪽 넘기기(Web · 데스크톱). Pager는 손가락 밀기만 받아서 마우스 끌기를 직접 옮긴다.
 * 너비의 12% 넘게 끌면 옆 쪽으로, 덜 끌면 제자리로 돌아간다. 끌기 시작하면 안쪽 단추의 누름은 취소된다.
 */
private fun Modifier.mouseDragPaging(state: PagerState): Modifier = pointerInput(state) {
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Mouse) return@awaitEachGesture
            val from = state.currentPage
            var total = 0f
            var dragging = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dx = change.position.x - change.previousPosition.x
                total += dx
                if (!dragging && abs(total) > viewConfiguration.touchSlop) dragging = true
                if (dragging) {
                    state.dispatchRawDelta(-dx)
                    change.consume()
                }
            }
            if (dragging) {
                val threshold = size.width * 0.12f
                val target = when {
                    total < -threshold -> from + 1
                    total > threshold -> from - 1
                    else -> from
                }.coerceIn(0, state.pageCount - 1)
                launch { state.animateScrollToPage(target) }
            }
        }
    }
}

/** 태블릿: 넘기지 않고 모든 쪽을 격자로 펼친다. 세로 2열 · 가로 3열이고 같은 줄 카드의 높이를 맞춘다. 읽는 순서 그림은 늘 움직인다. */
@Composable
private fun ReadingTabletGrid(widthClass: WindowWidthClass, moreTip: Int?, onMoreTip: (Int?) -> Unit) {
    val expanded = widthClass == WindowWidthClass.Expanded
    val side = widthClass.guideSidePadding
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = side, end = side, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        readingTabletRows(if (expanded) 3 else 2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().then(if (row.size > 1) Modifier.height(IntrinsicSize.Min) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { page ->
                    ReadingPageCard(
                        page,
                        Modifier.weight(1f).then(if (row.size > 1) Modifier.fillMaxHeight() else Modifier),
                        sigimsaeColumns = if (expanded) 3 else 2,
                        moreColumns = if (expanded) 9 else 6,
                        animate = true,
                        moreTip = moreTip,
                        onMoreTip = onMoreTip,
                    )
                }
            }
        }
    }
}

/** 쪽 하나의 카드. 휴대폰 쪽과 태블릿 격자가 함께 쓴다. */
@Composable
private fun ReadingPageCard(
    page: ReadingPage,
    modifier: Modifier,
    sigimsaeColumns: Int,
    moreColumns: Int,
    animate: Boolean,
    moreTip: Int?,
    onMoreTip: (Int?) -> Unit,
    interactive: Boolean = true,
) {
    when (page) {
        ReadingPage.Intro -> GuideCard(modifier, mark = page.mark, title = page.title(), intro = true) { ReadIntro() }
        ReadingPage.Order -> GuideCard(modifier, mark = page.mark, title = page.title()) { ReadOrderBoard(animate = animate) }
        ReadingPage.Length -> GuideCard(modifier, mark = page.mark, title = page.title()) { LengthRow() }
        ReadingPage.Pitch -> GuideCard(modifier, mark = page.mark, title = page.title()) { PitchSteps() }
        ReadingPage.Sigimsae -> GuideCard(modifier, mark = page.mark, title = page.title(), small = stringResource(Res.string.guide_reading_sigimsae_small)) {
            SigimsaeBasicBlock(columns = sigimsaeColumns, enabled = interactive)
            SigimsaeCredit()
        }
        ReadingPage.SigimsaeMore -> GuideCard(modifier, mark = page.mark, title = page.title(), small = "$MoreSigimsaeCount", subMark = true) {
            SigimsaeMorePage(moreTip, onMoreTip, columns = moreColumns, enabled = interactive)
            SigimsaeCredit()
        }
    }
}

/** 기호 그림 출처(`.sig-credit`). */
@Composable
private fun SigimsaeCredit() {
    Text(
        stringResource(Res.string.guide_sigimsae_credit),
        Modifier.padding(top = 16.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = EolssuTheme.colors.textMuted,
    )
}
